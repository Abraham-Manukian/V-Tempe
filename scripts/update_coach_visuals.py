#!/usr/bin/env python3
"""
V-Tempe CoachVisuals.kt Updater
=================================
Scans ui/src/commonMain/composeResources/drawable/ for coach_*.jpg files
and automatically adds any missing entries to CoachVisuals.kt maps.

Run AFTER generate_exercise_images.py finishes.

Usage:
    python update_coach_visuals.py [--dry-run]
"""

import re
import sys
from pathlib import Path

DRAWABLES_DIR  = Path(__file__).parent.parent / "ui/src/commonMain/composeResources/drawable"
COACH_VISUALS  = Path(__file__).parent.parent / "ui/src/commonMain/kotlin/com/vtempe/ui/screens/CoachVisuals.kt"

DRY_RUN = "--dry-run" in sys.argv

# ── Discover existing images ───────────────────────────────────────────────────

def discover_exercises() -> dict[str, set[str]]:
    """Returns {coach_name: {exercise_id, ...}} from image files on disk."""
    result: dict[str, set[str]] = {}
    for f in DRAWABLES_DIR.glob("coach_*.jpg"):
        name = f.stem  # e.g. coach_artur_squat
        parts = name.split("_", 2)  # ["coach", "artur", "squat"]
        if len(parts) < 3:
            continue
        coach = parts[1]   # "artur"
        exercise = parts[2]  # "squat"
        if exercise == "avatar":
            continue
        result.setdefault(coach, set()).add(exercise)
    return result


# ── Parse current CoachVisuals.kt ─────────────────────────────────────────────

def parse_current_entries(content: str, coach: str) -> set[str]:
    """Extract exercise IDs already present in the coach's map."""
    pattern = rf'private val {coach}ExerciseIllustrations = mapOf\((.*?)\)'
    match = re.search(pattern, content, re.DOTALL)
    if not match:
        return set()
    block = match.group(1)
    keys = re.findall(r'"([^"]+)"\s+to\s+Res\.drawable', block)
    return set(keys)


# ── Generate new map entries ───────────────────────────────────────────────────

def new_entries(coach: str, exercises: set[str], existing: set[str]) -> list[str]:
    """Return Kotlin map entry lines for exercises not yet in the map."""
    entries = []
    for ex in sorted(exercises - existing):
        entries.append(f'    "{ex}" to Res.drawable.coach_{coach}_{ex},')
    return entries


# ── Insert into map ────────────────────────────────────────────────────────────

def insert_entries(content: str, coach: str, lines: list[str]) -> str:
    """Append new entries to the end of the coach's mapOf block."""
    if not lines:
        return content
    # Find the closing ) of the mapOf for this coach
    pattern = rf'(private val {coach}ExerciseIllustrations = mapOf\()(.*?)(\))'
    def replacer(m):
        block = m.group(2).rstrip()
        # Remove trailing comma if present, then add new entries
        if block.endswith(","):
            block = block[:-1]
        new_block = block + ",\n" + "\n".join(lines)
        return m.group(1) + new_block + "\n" + m.group(3)
    return re.sub(pattern, replacer, content, flags=re.DOTALL)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    if not COACH_VISUALS.exists():
        print(f"CoachVisuals.kt not found: {COACH_VISUALS}")
        sys.exit(1)

    content = COACH_VISUALS.read_text(encoding="utf-8")
    on_disk = discover_exercises()

    total_added = 0
    for coach, exercises in on_disk.items():
        existing = parse_current_entries(content, coach)
        new = new_entries(coach, exercises, existing)
        if new:
            print(f"Coach {coach}: adding {len(new)} entries")
            for line in new:
                print(f"  + {line.strip()}")
            if not DRY_RUN:
                content = insert_entries(content, coach, new)
            total_added += len(new)
        else:
            print(f"Coach {coach}: already up to date")

    if total_added == 0:
        print("Nothing to add.")
        return

    if DRY_RUN:
        print(f"\nDry run — {total_added} entries would be added. Run without --dry-run to apply.")
        return

    COACH_VISUALS.write_text(content, encoding="utf-8")
    print(f"\n✓ CoachVisuals.kt updated (+{total_added} entries)")
    print("Now run: .\\gradlew.bat :ui:compileDebugKotlinAndroid")


if __name__ == "__main__":
    main()
