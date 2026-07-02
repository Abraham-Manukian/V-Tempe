#!/usr/bin/env python3
"""
V-Tempe Exercise Image Generator — ChatGPT Web Automation
============================================================
Drives chat.openai.com (chatgpt.com) through a real Chromium browser to
generate exercise photos for all 3 coaches, using your existing ChatGPT
Plus subscription instead of a paid API.

⚠️  IMPORTANT — read before running:
  - Automating the consumer chat.openai.com UI is against OpenAI's Terms
    of Service (they only permit programmatic access via the paid API).
    Running this script carries a real risk of your ChatGPT account being
    flagged or suspended. This is a deliberate choice made by the project
    owner to avoid additional API costs — proceed at your own risk.
  - This script does NOT store or ask for your password. It opens a real
    browser window pointed at a persistent local profile
    (scripts/.chatgpt_browser_profile/). The FIRST time you run it, log in
    manually in that window (Google/email/however you normally do it).
    Every run after that reuses the saved session automatically.
  - GPT-4o/ChatGPT Plus image generation is rate-limited (~40 images per
    rolling 3 hours). The script tracks its own request count and sleeps
    automatically when it hits the limit — it does not try to bypass it.
  - Fully resumable: already-existing output files are skipped, and
    progress is checkpointed to scripts/.chatgpt_gen_state.json after every
    image, so killing/restarting the script loses at most one in-flight image.

Usage:
    pip install playwright
    playwright install chromium
    python scripts/generate_via_chatgpt_web.py [--coach mia|artur|vtempe] [--dry-run]

Output: ui/src/commonMain/composeResources/drawable/coach_{trainer}_{exercise}.jpg
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path

try:
    from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout
except ImportError:
    print("Install deps: pip install playwright && playwright install chromium")
    sys.exit(1)

# ── Paths ──────────────────────────────────────────────────────────────────────

ROOT = Path(__file__).parent.parent
DRAWABLES_DIR = ROOT / "ui/src/commonMain/composeResources/drawable"
PROMPTS_MD = Path(__file__).parent / "ПРОМПТЫ_ДЛЯ_CHATGPT.md"
PROFILE_DIR = Path(__file__).parent / ".chatgpt_browser_profile"
STATE_FILE = Path(__file__).parent / ".chatgpt_gen_state.json"

# ── Rate limiting ─────────────────────────────────────────────────────────────

RATE_LIMIT_COUNT = 38          # stay under the ~40/3h ChatGPT Plus image quota
RATE_LIMIT_WINDOW_SEC = 3 * 60 * 60
GENERATION_TIMEOUT_MS = 120_000  # 2 min max wait for one image to finish
POLL_INTERVAL_SEC = 3

CHATGPT_URL = "https://chatgpt.com/"

# ── Coach base blocks (kept in sync with ПРОМПТЫ_ДЛЯ_CHATGPT.md by parsing it) ──

@dataclass
class CoachSpec:
    key: str
    avatar_file: str
    base_block_header: str  # markdown "## БАЗОВЫЙ БЛОК — X" heading text to search for


COACHES = {
    "mia": CoachSpec("mia", "coach_mia_avatar.jpg", "БАЗОВЫЙ БЛОК — MIA"),
    "artur": CoachSpec("artur", "coach_artur_avatar.jpg", "БАЗОВЫЙ БЛОК — ARTUR"),
    "vtempe": CoachSpec("vtempe", "coach_vtempe_avatar.jpg", "БАЗОВЫЙ БЛОК — VTEMPE"),
}


# ── Parse ПРОМПТЫ_ДЛЯ_CHATGPT.md ─────────────────────────────────────────────
# Single source of truth: same file a human would copy-paste from manually.

def parse_base_block(md_text: str, header: str) -> str:
    """Extract the fenced code block that follows a '## БАЗОВЫЙ БЛОК — X' heading."""
    idx = md_text.find(header)
    if idx == -1:
        raise ValueError(f"Base block header not found: {header}")
    after = md_text[idx:]
    match = re.search(r"```\n(.*?)\n```", after, re.DOTALL)
    if not match:
        raise ValueError(f"No fenced block found after header: {header}")
    return match.group(1).strip()


def parse_exercise_blocks(md_text: str) -> list[tuple[str, str]]:
    """
    Returns [(exercise_id, description), ...] from every fenced code block in the
    "СПИСОК УПРАЖНЕНИЙ" section (i.e. every ``` block whose first line looks like
    a bare exercise_id and second+ line is the movement description).
    """
    section_start = md_text.find("# СПИСОК УПРАЖНЕНИЙ")
    if section_start == -1:
        raise ValueError("Could not find '# СПИСОК УПРАЖНЕНИЙ' section")
    section_end = md_text.find("## НЕЙМИНГ ФАЙЛОВ")
    body = md_text[section_start: section_end if section_end != -1 else len(md_text)]

    results: list[tuple[str, str]] = []
    for block in re.finditer(r"```\n(.*?)\n```", body, re.DOTALL):
        content = block.group(1).strip()
        lines = content.split("\n", 1)
        if len(lines) != 2:
            continue
        first_line = lines[0].strip()
        # exercise id line looks like "squat" or "squat  ->  coach_mia_squat.jpg"
        id_match = re.match(r"^([a-z0-9_]+)", first_line)
        if not id_match:
            continue
        exercise_id = id_match.group(1)
        description = lines[1].strip()
        results.append((exercise_id, description))
    return results


# ── State (resumability + rate limiting) ─────────────────────────────────────

def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {"request_timestamps": []}


def save_state(state: dict) -> None:
    STATE_FILE.write_text(json.dumps(state, indent=2), encoding="utf-8")


def wait_for_rate_limit_slot(state: dict) -> None:
    """Blocks until we're under RATE_LIMIT_COUNT requests in the rolling window."""
    now = time.time()
    state["request_timestamps"] = [
        t for t in state["request_timestamps"] if now - t < RATE_LIMIT_WINDOW_SEC
    ]
    if len(state["request_timestamps"]) < RATE_LIMIT_COUNT:
        return
    oldest = min(state["request_timestamps"])
    sleep_for = RATE_LIMIT_WINDOW_SEC - (now - oldest) + 30  # +30s safety margin
    resume_at = time.strftime("%H:%M:%S", time.localtime(now + sleep_for))
    print(f"\n⏸  Rate limit reached ({RATE_LIMIT_COUNT}/{RATE_LIMIT_WINDOW_SEC//3600}h). "
          f"Sleeping until ~{resume_at} ({sleep_for/60:.0f} min)...")
    time.sleep(sleep_for)


def record_request(state: dict) -> None:
    state["request_timestamps"].append(time.time())
    save_state(state)


# ── Browser automation ────────────────────────────────────────────────────────

def generate_one(page, avatar_path: Path, base_block: str, exercise_id: str,
                  description: str, output_path: Path) -> bool:
    """Drives one ChatGPT turn: new chat, upload avatar, send prompt, download image."""
    try:
        # Start a fresh conversation so context doesn't drift across exercises.
        page.goto(f"{CHATGPT_URL}?model=gpt-4o", wait_until="domcontentloaded")
        page.wait_for_selector('div[contenteditable="true"], textarea#prompt-textarea', timeout=30_000)

        # Attach the reference photo.
        file_input = page.locator('input[type="file"]').first
        file_input.set_input_files(str(avatar_path))
        page.wait_for_timeout(1500)  # let the thumbnail attach

        prompt_text = f"{base_block} {description}"
        composer = page.locator('div[contenteditable="true"], textarea#prompt-textarea').first
        composer.click()
        composer.fill(prompt_text)
        composer.press("Enter")

        # Wait for generation to finish: the "stop streaming" button disappears
        # and a generated <img> shows up in the last assistant message.
        deadline = time.time() + GENERATION_TIMEOUT_MS / 1000
        img_locator = page.locator("article img[src*='oaiusercontent'], article img[alt]").last
        while time.time() < deadline:
            if img_locator.count() > 0:
                # Give it a moment to make sure it's the final (not a low-res preview).
                page.wait_for_timeout(2000)
                break
            page.wait_for_timeout(POLL_INTERVAL_SEC * 1000)
        else:
            print(f"  ✗ timeout waiting for image ({exercise_id})")
            return False

        img_src = img_locator.get_attribute("src")
        if not img_src:
            print(f"  ✗ no image src found ({exercise_id})")
            return False

        # Download via the page's own network context (keeps auth cookies).
        response = page.request.get(img_src)
        if response.status != 200:
            print(f"  ✗ download failed status={response.status} ({exercise_id})")
            return False

        output_path.write_bytes(response.body())
        return True

    except PWTimeout:
        print(f"  ✗ timeout ({exercise_id})")
        return False
    except Exception as e:
        print(f"  ✗ error: {e} ({exercise_id})")
        return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--coach", choices=list(COACHES.keys()), default=None,
                         help="Only generate for one coach (default: all 3)")
    parser.add_argument("--dry-run", action="store_true",
                         help="List what would be generated without opening a browser")
    args = parser.parse_args()

    if not PROMPTS_MD.exists():
        print(f"Prompts file not found: {PROMPTS_MD}")
        sys.exit(1)

    md_text = PROMPTS_MD.read_text(encoding="utf-8")
    exercises = parse_exercise_blocks(md_text)
    print(f"Parsed {len(exercises)} exercise prompts from {PROMPTS_MD.name}")

    coaches_to_run = [COACHES[args.coach]] if args.coach else list(COACHES.values())

    # Build the work queue, skipping images that already exist on disk.
    queue: list[tuple[CoachSpec, str, str, Path]] = []
    for coach in coaches_to_run:
        base_block = parse_base_block(md_text, coach.base_block_header)
        for exercise_id, description in exercises:
            output_path = DRAWABLES_DIR / f"coach_{coach.key}_{exercise_id}.jpg"
            if output_path.exists():
                continue
            queue.append((coach, base_block, description, output_path))

    print(f"Queue: {len(queue)} images to generate "
          f"({sum(1 for c in coaches_to_run for _ in exercises) - len(queue)} already exist)")

    if args.dry_run:
        for coach, _, description, output_path in queue[:20]:
            print(f"  [{coach.key}] {output_path.name}: {description[:60]}...")
        if len(queue) > 20:
            print(f"  ... and {len(queue) - 20} more")
        return

    if not queue:
        print("Nothing to do.")
        return

    state = load_state()

    with sync_playwright() as p:
        print(f"\nOpening browser with persistent profile: {PROFILE_DIR}")
        print("If this is the first run, log into ChatGPT manually in the window that opens.")
        context = p.chromium.launch_persistent_context(
            user_data_dir=str(PROFILE_DIR),
            headless=False,
            channel="chrome",
            viewport={"width": 1280, "height": 900},
        )
        page = context.pages[0] if context.pages else context.new_page()
        page.goto(CHATGPT_URL, wait_until="domcontentloaded")

        if page.locator("text=Log in").count() > 0 or "auth" in page.url:
            input("\n⏸  Please log into ChatGPT in the opened browser window, "
                  "then press Enter here to continue...")

        done, failed = 0, 0
        for coach, base_block, description, output_path in queue:
            avatar_path = DRAWABLES_DIR / coach.avatar_file
            if not avatar_path.exists():
                print(f"⚠️  Avatar missing, skipping coach {coach.key}: {avatar_path}")
                continue

            wait_for_rate_limit_slot(state)

            exercise_id = output_path.stem.split("_", 2)[2]
            print(f"[{coach.key}] {exercise_id}...", end=" ", flush=True)

            ok = generate_one(page, avatar_path, base_block, exercise_id, description, output_path)
            record_request(state)

            if ok:
                done += 1
                kb = output_path.stat().st_size // 1024
                print(f"✓ saved ({kb} KB)")
            else:
                failed += 1

        print(f"\n{'─'*50}")
        print(f"Done: {done}  |  Failed: {failed}  |  Remaining in queue: {len(queue) - done - failed}")
        if done > 0:
            print("\nNext steps:")
            print("  python scripts/compress_images.py")
            print("  python scripts/update_coach_visuals.py")
            print("  .\\gradlew.bat :ui:compileDebugKotlinAndroid")

        context.close()


if __name__ == "__main__":
    main()
