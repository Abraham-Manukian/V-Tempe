"""
Manual prompt variant testing for Gemini 2.5 Flash via OpenRouter.
Tests 4 approaches to training plan generation and compares which exercises AI actually picks.

Run: python test_prompts.py
"""

import json
import requests
import time
import sys
import io

# Force UTF-8 output on Windows
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

API_KEY = os.environ.get("OPENROUTER_API_KEY", "")
MODEL = "google/gemini-2.5-flash"
BASE_URL = "https://openrouter.ai/api/v1/chat/completions"

# Expected exercises per variant — we'll check if AI follows them
EXPECTED_EXERCISES = {
    "Push":  ["bench_press", "ohp", "incline_db_press", "tricep_pushdown", "plank"],
    "Pull":  ["pullup", "barbell_row", "cable_seated_row", "barbell_curl", "face_pull"],
    "Legs":  ["barbell_squat", "rdl", "bulgarian_split_squat", "leg_press", "plank"],
}

TODAY = "2026-06-18"
DATES = ["2026-06-18", "2026-06-19", "2026-06-20"]

SYSTEM_PROMPT = (
    "You must reply with a single valid JSON object that exactly matches the user's schema. "
    "Do not add explanations, markdown, apologies, or text outside the JSON object."
)

JSON_SCHEMA = """{
  "trainingPlan": {
    "weekIndex": 0,
    "workouts": [
      {
        "id": "string",
        "label": "string",
        "date": "YYYY-MM-DD",
        "sets": [
          {"exerciseId": "string", "reps": 1, "weightKg": 0.0, "rpe": 0.0}
        ]
      }
    ]
  }
}"""

# ─────────────────────────────────────────────
# VARIANT A: Current approach (skeleton with pre-resolved IDs in slot format)
# ─────────────────────────────────────────────
VARIANT_A = f"""You are an elite strength coach. User speaks Russian, reply in Russian for labels.
TODAY'S DATE: {TODAY}
Return ONLY a single JSON object. No markdown, no text outside JSON.

RESPONSE SCHEMA:
{JSON_SCHEMA}

ATHLETE:
- 25 y/o male, 80kg, 180cm
- Goal: MUSCLE_GAIN
- Experience: 3/5 (intermediate)
- Equipment: full gym
- Training days: Thu, Fri, Sat

MANDATORY WORKOUT SKELETON — follow exactly, no deviations.
exerciseId values are PRE-ASSIGNED below — copy them EXACTLY, do NOT substitute or change.

Session 1 (Thu — Push):
  Slot 1: [PRIMARY]    bench_press — 3×6–10 reps — RPE 7.5 — 180s rest
  Slot 2: [SECONDARY]  ohp — 3×8–12 reps — RPE 7.5 — 120s rest
  Slot 3: [SECONDARY]  incline_db_press — 3×8–12 reps — RPE 7.0 — 120s rest
  Slot 4: [ISOLATION]  tricep_pushdown — 3×10–15 reps — RPE 7.0 — 60s rest
  Slot 5: [ISOLATION]  plank — 3×10–15 reps — RPE 7.0 — 60s rest

Session 2 (Fri — Pull):
  Slot 1: [PRIMARY]    pullup — 3×6–10 reps — RPE 7.5 — 180s rest
  Slot 2: [SECONDARY]  barbell_row — 3×8–12 reps — RPE 7.5 — 120s rest
  Slot 3: [SECONDARY]  cable_seated_row — 3×8–12 reps — RPE 7.0 — 120s rest
  Slot 4: [ISOLATION]  barbell_curl — 3×10–15 reps — RPE 7.0 — 60s rest
  Slot 5: [ISOLATION]  face_pull — 3×10–15 reps — RPE 7.0 — 60s rest

Session 3 (Sat — Legs):
  Slot 1: [PRIMARY]    barbell_squat — 3×6–10 reps — RPE 7.5 — 180s rest
  Slot 2: [SECONDARY]  rdl — 3×8–12 reps — RPE 7.5 — 120s rest
  Slot 3: [SECONDARY]  bulgarian_split_squat — 3×8–12 reps — RPE 7.0 — 120s rest
  Slot 4: [ISOLATION]  leg_press — 3×10–15 reps — RPE 7.0 — 60s rest
  Slot 5: [ISOLATION]  plank — 3×10–15 reps — RPE 7.0 — 60s rest

GLOBAL RULES:
- Workout dates MUST be exactly: {DATES[0]}, {DATES[1]}, {DATES[2]}
- exerciseId values must be EXACTLY the exercise IDs listed in the skeleton above — no other values allowed.
- pullup, plank: weightKg = null (bodyweight only)
- Barbell exercises: assign realistic weights (intermediate male: squat ~90kg, bench ~80kg, deadlift ~110kg, ohp ~55kg)
- Session labels must be exactly: Push, Pull, Legs
"""

# ─────────────────────────────────────────────
# VARIANT B: Template-fill approach (give partial JSON, AI fills numbers only)
# ─────────────────────────────────────────────
VARIANT_B = f"""You are a weight assignment assistant for a strength training app.
TODAY: {TODAY}
Return ONLY the completed JSON. No markdown, no extra text.

YOUR TASK: Fill in reps, weightKg, and rpe for each set below.
- Do NOT change any exerciseId — they are fixed.
- Do NOT add or remove sets.
- Bodyweight exercises (pullup, plank): weightKg = null
- Intermediate male (80kg, goal: muscle gain):
  bench_press ~80kg, ohp ~55kg, barbell_squat ~90kg, rdl ~90kg, barbell_row ~70kg

Fill this JSON template (replace ?? with numbers):
{{
  "trainingPlan": {{
    "weekIndex": 0,
    "workouts": [
      {{
        "id": "w_0_0", "label": "Push", "date": "{DATES[0]}",
        "sets": [
          {{"exerciseId": "bench_press",         "reps": 8,  "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "ohp",                 "reps": 10, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "incline_db_press",    "reps": 10, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "tricep_pushdown",     "reps": 12, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "plank",               "reps": 30, "weightKg": null, "rpe": ??}}
        ]
      }},
      {{
        "id": "w_0_1", "label": "Pull", "date": "{DATES[1]}",
        "sets": [
          {{"exerciseId": "pullup",              "reps": 8,  "weightKg": null, "rpe": ??}},
          {{"exerciseId": "barbell_row",         "reps": 8,  "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "cable_seated_row",    "reps": 10, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "barbell_curl",        "reps": 12, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "face_pull",           "reps": 15, "weightKg": ??,   "rpe": ??}}
        ]
      }},
      {{
        "id": "w_0_2", "label": "Legs", "date": "{DATES[2]}",
        "sets": [
          {{"exerciseId": "barbell_squat",          "reps": 8,  "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "rdl",                    "reps": 10, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "bulgarian_split_squat",  "reps": 10, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "leg_press",              "reps": 12, "weightKg": ??,   "rpe": ??}},
          {{"exerciseId": "plank",                  "reps": 30, "weightKg": null, "rpe": ??}}
        ]
      }}
    ]
  }}
}}
"""

# ─────────────────────────────────────────────
# VARIANT C: Ultra-minimal (no schema noise, just exercises + "give me weights")
# ─────────────────────────────────────────────
VARIANT_C = f"""Assign realistic starting weights and RPE for an intermediate male (80kg, goal: muscle gain).
Return JSON only.

Rules:
- bench_press → ~80kg | ohp → ~55kg | barbell_squat → ~90kg | rdl → ~90kg | barbell_row → ~70kg
- pullup, plank → weightKg must be null
- RPE 7.0–8.0 range

{{"trainingPlan":{{"weekIndex":0,"workouts":[
  {{"id":"w_0_0","label":"Push","date":"{DATES[0]}","sets":[
    {{"exerciseId":"bench_press","reps":8,"weightKg":null,"rpe":null}},
    {{"exerciseId":"ohp","reps":10,"weightKg":null,"rpe":null}},
    {{"exerciseId":"incline_db_press","reps":10,"weightKg":null,"rpe":null}},
    {{"exerciseId":"tricep_pushdown","reps":12,"weightKg":null,"rpe":null}},
    {{"exerciseId":"plank","reps":30,"weightKg":null,"rpe":null}}
  ]}},
  {{"id":"w_0_1","label":"Pull","date":"{DATES[1]}","sets":[
    {{"exerciseId":"pullup","reps":8,"weightKg":null,"rpe":null}},
    {{"exerciseId":"barbell_row","reps":8,"weightKg":null,"rpe":null}},
    {{"exerciseId":"cable_seated_row","reps":10,"weightKg":null,"rpe":null}},
    {{"exerciseId":"barbell_curl","reps":12,"weightKg":null,"rpe":null}},
    {{"exerciseId":"face_pull","reps":15,"weightKg":null,"rpe":null}}
  ]}},
  {{"id":"w_0_2","label":"Legs","date":"{DATES[2]}","sets":[
    {{"exerciseId":"barbell_squat","reps":8,"weightKg":null,"rpe":null}},
    {{"exerciseId":"rdl","reps":10,"weightKg":null,"rpe":null}},
    {{"exerciseId":"bulgarian_split_squat","reps":10,"weightKg":null,"rpe":null}},
    {{"exerciseId":"leg_press","reps":12,"weightKg":null,"rpe":null}},
    {{"exerciseId":"plank","reps":30,"weightKg":null,"rpe":null}}
  ]}}
]}}}}
"""

# ─────────────────────────────────────────────
# VARIANT D: Free generation — no skeleton, AI picks exercises itself (baseline "worst case")
# ─────────────────────────────────────────────
VARIANT_D = f"""You are an elite strength coach. Create a 3-day PPL training plan.
TODAY: {TODAY}. Return ONLY valid JSON, no markdown.

SCHEMA:
{JSON_SCHEMA}

ATHLETE: 25 y/o male, 80kg, 180cm, intermediate (3/5), goal: muscle gain, full gym.
Training days: Thu {DATES[0]}, Fri {DATES[1]}, Sat {DATES[2]}.

Rules:
- Day 1: Push (chest, shoulders, triceps) — bench, OHP, incline press + accessories
- Day 2: Pull (back, biceps) — pullups/rows + accessories
- Day 3: Legs (quads, hamstrings, glutes) — squat, RDL + accessories
- Use only lowercase_snake_case exercise IDs
- 5 exercises per workout, 3 sets each
- Assign realistic weights for intermediate male
- pullup: weightKg = null
"""


def call_openrouter(prompt: str, label: str) -> dict:
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://vtempe.app",
        "X-Title": "VTempe-PromptTest"
    }
    body = {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user",   "content": prompt}
        ],
        "temperature": 0.35
    }
    print(f"\n{'='*60}")
    print(f"SENDING: {label}")
    print(f"Prompt length: {len(prompt)} chars")
    print(f"{'='*60}")

    resp = requests.post(BASE_URL, headers=headers, json=body, timeout=120)
    resp.raise_for_status()
    data = resp.json()

    raw = data["choices"][0]["message"]["content"].strip()
    # Strip markdown fences if present
    if raw.startswith("```"):
        raw = raw.split("```")[1]
        if raw.startswith("json"):
            raw = raw[4:]
        raw = raw.strip()

    usage = data.get("usage", {})
    tokens_in  = usage.get("prompt_tokens", "?")
    tokens_out = usage.get("completion_tokens", "?")
    print(f"Tokens: {tokens_in} in / {tokens_out} out")

    return {"label": label, "raw": raw, "tokens_in": tokens_in, "tokens_out": tokens_out}


def analyze(result: dict):
    label = result["label"]
    raw = result["raw"]

    print(f"\n{'─'*60}")
    print(f"RESULT: {label}")
    print(f"{'─'*60}")

    try:
        parsed = json.loads(raw)
        workouts = parsed.get("trainingPlan", {}).get("workouts", [])

        all_correct = True
        for wo in workouts:
            wo_label = wo.get("label", "?")
            sets = wo.get("sets", [])
            actual_ids = [s.get("exerciseId", "?") for s in sets]
            expected = EXPECTED_EXERCISES.get(wo_label, [])

            matches = sum(1 for a in actual_ids if a in expected)
            total   = len(actual_ids)
            correct = matches == total and set(actual_ids) == set(expected)
            if not correct:
                all_correct = False

            status = "✓ PERFECT" if correct else f"✗ WRONG ({matches}/{total} match)"
            print(f"\n  [{wo_label}] {status}")
            for i, (act, exp) in enumerate(zip(actual_ids, expected + ["?"]*(total-len(expected)))):
                ok = "✓" if act == exp else "✗"
                weight = sets[i].get("weightKg")
                rpe    = sets[i].get("rpe")
                print(f"    Slot {i+1}: {ok} {act:30s}  w={weight}  rpe={rpe}")
            if len(actual_ids) != len(expected):
                print(f"    ⚠ Count mismatch: got {len(actual_ids)}, expected {len(expected)}")

        print(f"\n  OVERALL: {'✓ ALL CORRECT' if all_correct else '✗ HAS ERRORS'}")

    except json.JSONDecodeError as e:
        print(f"  ✗ INVALID JSON: {e}")
        print(f"  Raw (first 500 chars): {raw[:500]}")


# ─────────────────────────────────────────────
# VARIANT E: Fixed A — clarify "one entry per exercise, not per set"
# ─────────────────────────────────────────────
VARIANT_E = f"""You are an elite strength coach. User speaks Russian, reply in Russian for labels.
TODAY'S DATE: {TODAY}
Return ONLY a single JSON object. No markdown, no text outside JSON.

RESPONSE SCHEMA:
{JSON_SCHEMA}

IMPORTANT: Each exercise appears EXACTLY ONCE in the sets array.
The "3x8" notation means 3 sets of 8 reps to perform in the gym — NOT 3 separate JSON entries.
One exerciseId = one JSON object in sets. 5 slots = 5 JSON objects total per workout.

ATHLETE:
- 25 y/o male, 80kg, 180cm
- Goal: MUSCLE_GAIN
- Experience: 3/5 (intermediate)
- Equipment: full gym
- Training days: Thu {DATES[0]}, Fri {DATES[1]}, Sat {DATES[2]}

MANDATORY WORKOUT SKELETON:
exerciseId values are PRE-ASSIGNED — copy them EXACTLY, do NOT substitute.

Session 1 (Thu — Push), date: {DATES[0]}:
  1: bench_press        — 3 sets x 6-10 reps — RPE 7.5
  2: ohp                — 3 sets x 8-12 reps — RPE 7.5
  3: incline_db_press   — 3 sets x 8-12 reps — RPE 7.0
  4: tricep_pushdown    — 3 sets x 10-15 reps — RPE 7.0
  5: plank              — 3 sets x 30-60s — RPE 7.0

Session 2 (Fri — Pull), date: {DATES[1]}:
  1: pullup             — 3 sets x 6-10 reps — RPE 7.5
  2: barbell_row        — 3 sets x 8-12 reps — RPE 7.5
  3: cable_seated_row   — 3 sets x 8-12 reps — RPE 7.0
  4: barbell_curl       — 3 sets x 10-15 reps — RPE 7.0
  5: face_pull          — 3 sets x 12-15 reps — RPE 7.0

Session 3 (Sat — Legs), date: {DATES[2]}:
  1: barbell_squat         — 3 sets x 6-10 reps — RPE 7.5
  2: rdl                   — 3 sets x 8-12 reps — RPE 7.5
  3: bulgarian_split_squat — 3 sets x 8-12 reps — RPE 7.0
  4: leg_press             — 3 sets x 10-15 reps — RPE 7.0
  5: plank                 — 3 sets x 30-60s — RPE 7.0

RULES:
- pullup, plank: weightKg = null
- bench_press ~80kg, ohp ~55kg, barbell_squat ~90kg, rdl ~90kg, barbell_row ~70kg
- Session labels: exactly "Push", "Pull", "Legs"
- exerciseId must be EXACTLY as listed above
"""


def main():
    variants = [
        (VARIANT_A, "A — Skeleton with pre-resolved IDs (current approach)"),
        (VARIANT_B, "B — Template fill (AI fills numbers only)"),
        (VARIANT_C, "C — Ultra-minimal (naked JSON + weight hints)"),
        (VARIANT_D, "D — Free generation (AI picks exercises itself, baseline)"),
        (VARIANT_E, "E — Fixed A: clarified 1 entry per exercise"),
    ]

    results = []
    for prompt, label in variants:
        try:
            result = call_openrouter(prompt, label)
            results.append(result)
            analyze(result)
            time.sleep(2)  # avoid rate limits
        except Exception as e:
            print(f"\n✗ ERROR for {label}: {e}")

    # Summary
    print(f"\n\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")
    for r in results:
        print(f"  {r['label']}: {r['tokens_in']}↑ {r['tokens_out']}↓ tokens")


if __name__ == "__main__":
    main()
