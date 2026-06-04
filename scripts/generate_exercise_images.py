#!/usr/bin/env python3
"""
V-Tempe Exercise Image Generator
=================================
Generates all exercise demonstration images for 3 coaches using fal.ai FLUX Kontext.

Usage:
    pip install fal-client Pillow requests
    export FAL_KEY="your-fal-ai-api-key"   # get at fal.ai/dashboard
    python generate_exercise_images.py

Output: ui/src/commonMain/composeResources/drawable/coach_{trainer}_{exercise}.jpg
Already-existing files are skipped automatically.

Estimated cost: ~$0.05 per image × 318 images ≈ $16 total at fal.ai pricing.
"""

import os
import sys
import time
import base64
import requests
from pathlib import Path

try:
    import fal_client
except ImportError:
    print("Install deps: pip install fal-client requests")
    sys.exit(1)

# ── Config ─────────────────────────────────────────────────────────────────────

DRAWABLES_DIR = Path(__file__).parent.parent / "ui/src/commonMain/composeResources/drawable"
SCRIPT_DIR    = Path(__file__).parent

# Seconds to wait between API calls to avoid rate limits
REQUEST_DELAY = 1.5

# ── Coach descriptions ─────────────────────────────────────────────────────────

COACHES = {
    "artur": {
        "avatar_file": "coach_artur_avatar.jpg",
        "description": (
            "athletic Mediterranean man, curly black hair, short beard, "
            "wearing all-black fitted polo shirt and black jogger pants, black sneakers, "
            "muscular build, confident posture"
        ),
    },
    "mia": {
        "avatar_file": "coach_mia_avatar.jpg",
        "description": (
            "athletic woman with curly brown hair, wearing black sports bra crop top "
            "and black high-waist leggings, white sneakers, fit and toned physique, "
            "professional fitness model"
        ),
    },
    "vtempe": {
        "avatar_file": "coach_vtempe_avatar.jpg",
        "description": (
            "futuristic AI fitness robot in sleek all-black form-fitting suit with "
            "glowing blue neon accent lines, full black helmet with visor, "
            "black and blue athletic sneakers, humanoid body"
        ),
    },
}

# ── Exercise definitions ───────────────────────────────────────────────────────
# Format: exercise_id → (action description, phase 1 label, phase 2 label)

EXERCISES = {
    # ── LEGS / KNEE DOMINANT ────────────────────────────────────────────────────
    "squat": (
        "performing a barbell back squat, holding a loaded barbell across upper traps",
        "standing upright with barbell on back",
        "in deep squat position with thighs parallel to floor"
    ),
    "goblet_squat": (
        "performing a goblet squat holding a kettlebell at chest height with both hands",
        "standing tall holding kettlebell at chest",
        "in deep squat position with kettlebell at chest"
    ),
    "front_squat": (
        "performing a barbell front squat with bar resting on front deltoids",
        "standing upright with bar in front rack position",
        "in deep squat position with upright torso"
    ),
    "leg_press": (
        "using a leg press machine, feet on platform, pushing weight away",
        "legs extended, feet on leg press platform",
        "knees bent at 90 degrees, lowering weight"
    ),
    "sumo_squat": (
        "performing a wide-stance sumo squat with feet turned out at 45 degrees, holding dumbbell",
        "standing wide stance with dumbbell hanging between legs",
        "in wide squat with dumbbell lowered toward floor"
    ),
    "wall_sit": (
        "holding a wall sit position with back flat against wall, thighs parallel to floor, no equipment",
        "standing upright next to wall",
        "in wall sit position with 90-degree knee bend, arms forward"
    ),
    "box_squat": (
        "performing a box squat, sitting back onto a low box or bench with barbell",
        "standing with barbell, box behind",
        "seated on box with barbell, controlled position"
    ),
    "hack_squat": (
        "using a hack squat machine, feet on angled platform",
        "extended position on hack squat machine",
        "deep squat on hack squat machine, full depth"
    ),
    "leg_extension": (
        "using a seated leg extension machine, extending legs from bent to straight",
        "seated at machine with knees bent, ankles behind pad",
        "legs fully extended, quadriceps contracted"
    ),
    "jump_squat": (
        "performing explosive jump squats, bodyweight only",
        "in squat position, arms back, about to jump",
        "at peak of jump, arms overhead, fully airborne"
    ),

    # ── HINGE / POSTERIOR CHAIN ────────────────────────────────────────────────
    "deadlift": (
        "performing a conventional barbell deadlift from floor",
        "hinging at hips, gripping barbell on floor, back flat",
        "standing tall with barbell at hip level, fully locked out"
    ),
    "romanian_deadlift": (
        "performing Romanian deadlift with barbell, hinging at hips",
        "standing tall holding barbell at hip level",
        "hinging forward, barbell sliding down legs, slight knee bend"
    ),
    "hip_thrust": (
        "performing a barbell hip thrust with back on bench, feet flat on floor",
        "seated on floor with back against bench, barbell over hips",
        "hips fully extended, body parallel to floor, barbell at hip crease"
    ),
    "glute_bridge": (
        "performing a glute bridge on floor, bodyweight or with plate on hips",
        "lying on back with knees bent, feet flat on floor",
        "hips lifted high off floor, glutes squeezed at top"
    ),
    "kettlebell_swing": (
        "performing a two-handed kettlebell swing, explosive hip hinge",
        "hinging at hips, kettlebell swinging back between legs",
        "standing tall, kettlebell swung up to shoulder height by hip extension"
    ),
    "single_leg_deadlift": (
        "performing a single-leg Romanian deadlift with dumbbell, balancing on one leg",
        "standing on one leg, dumbbell in hand, slight hinge beginning",
        "fully hinged forward, free leg extended behind, dumbbell near floor"
    ),
    "sumo_deadlift": (
        "performing a sumo stance deadlift with wide foot placement",
        "wide stance over barbell, hands inside knees, starting position",
        "barbell lifted to hip height, fully standing with wide stance"
    ),
    "good_morning": (
        "performing a good morning with barbell across upper back, hinging forward",
        "standing tall, barbell on back, straight posture",
        "hinged forward at 90 degrees, back flat, barbell still on back"
    ),
    "nordic_curl": (
        "performing a Nordic hamstring curl, anchoring feet, lowering chest to floor",
        "kneeling with ankles anchored, body upright",
        "body lowered toward floor with straight spine, hands catching before impact"
    ),
    "leg_curl": (
        "using a lying or seated leg curl machine",
        "lying prone on machine with knees nearly straight",
        "heels curled toward glutes, full hamstring contraction"
    ),

    # ── SINGLE LEG ────────────────────────────────────────────────────────────
    "lunge": (
        "performing walking lunges with dumbbells",
        "standing upright holding dumbbells at sides",
        "in deep lunge, front knee at 90 degrees, back knee near floor"
    ),
    "reverse_lunge": (
        "performing reverse lunges, stepping backward",
        "standing tall, about to step back",
        "in lunge position, rear foot stepped back, front thigh parallel to floor"
    ),
    "bulgarian_split_squat": (
        "performing Bulgarian split squats with rear foot elevated on bench",
        "standing with rear foot on bench, front foot stepped forward",
        "lowered into deep split squat, rear knee near floor"
    ),
    "step_up": (
        "performing step-ups onto a sturdy box or bench with dumbbells",
        "standing facing the box, dumbbell in each hand",
        "fully stepping up, one foot on box, other leg lifted, standing tall"
    ),
    "lateral_lunge": (
        "performing lateral lunges, stepping out to the side",
        "standing tall with feet together",
        "in deep lateral lunge to one side, bent knee over foot, other leg straight"
    ),
    "pistol_squat": (
        "performing a pistol squat on one leg, other leg extended forward",
        "standing on one leg, other leg extended forward at hip height",
        "in full single-leg squat, extended leg parallel to floor"
    ),
    "skater_lunge": (
        "performing skater lunges with lateral hops",
        "balanced on one leg, opposite leg reaching back and across",
        "landing on other foot, skater-like lateral position"
    ),

    # ── HORIZONTAL PUSH / CHEST ────────────────────────────────────────────────
    "bench": (
        "performing a barbell bench press lying on flat bench",
        "lying on bench, barbell held with straight arms above chest",
        "barbell lowered to mid-chest, elbows at 45 degrees"
    ),
    "incline_bench": (
        "performing an incline dumbbell or barbell press on inclined bench",
        "on inclined bench, weights at chest level",
        "pressing weights up over upper chest, arms extended"
    ),
    "pushup": (
        "performing standard push-ups",
        "in high plank position, arms extended, body straight",
        "lowered to floor with chest near ground, elbows at sides"
    ),
    "dip": (
        "performing parallel bar dips, tricep and chest dip",
        "arms fully extended above parallel bars, body between bars",
        "lowered down with elbows bent to 90 degrees, leaning forward slightly"
    ),
    "diamond_pushup": (
        "performing diamond push-ups with hands close together forming diamond shape",
        "hands in diamond position under chest, arms extended",
        "lowered with elbows close to body, chest near hands"
    ),
    "wide_pushup": (
        "performing wide-grip push-ups with hands placed wider than shoulder-width",
        "hands wider than shoulders, arms extended, body straight",
        "chest lowered to floor, wide elbow flare"
    ),
    "decline_pushup": (
        "performing decline push-ups with feet elevated on bench",
        "feet on bench, hands on floor, arms extended, angled downward",
        "chest lowered toward floor, elevated feet position"
    ),
    "incline_pushup": (
        "performing incline push-ups with hands on elevated surface like bench",
        "hands on bench, feet on floor, arms extended, body angled",
        "chest near bench, elbows bent, controlled lowering"
    ),
    "pike_pushup": (
        "performing pike push-ups in inverted V position targeting shoulders",
        "in downward dog / inverted V shape, hands on floor",
        "head lowered toward floor between hands, elbows bent"
    ),
    "dumbbell_fly": (
        "performing dumbbell chest flyes lying on flat bench",
        "lying on bench, dumbbells extended wide above chest, slight elbow bend",
        "dumbbells brought together above chest in arc motion, chest contracted"
    ),
    "cable_fly": (
        "performing cable chest flyes at cable machine with high or mid pulleys",
        "arms extended wide holding cable handles, standing at cables",
        "arms brought together in front of chest, handles meeting at center"
    ),
    "close_grip_bench": (
        "performing close-grip bench press with hands shoulder-width apart",
        "lying on bench, hands close together on barbell, arms extended",
        "barbell lowered to lower chest, elbows tucked close to body"
    ),

    # ── HORIZONTAL PULL / BACK ─────────────────────────────────────────────────
    "row": (
        "performing a bent-over barbell row, hinging forward",
        "hinged forward at 45 degrees, barbell hanging with straight arms",
        "barbell pulled to lower rib cage, elbows back"
    ),
    "dumbbell_row": (
        "performing single-arm dumbbell rows with knee and hand on bench",
        "one knee on bench, arm extended holding dumbbell below",
        "dumbbell pulled up to hip, elbow pointing toward ceiling"
    ),
    "cable_row": (
        "performing a seated cable row with a close-grip handle at a cable machine",
        "sitting at cable machine, arms extended forward holding handle",
        "elbows pulled back to sides, handle at abdomen, upright posture"
    ),
    "inverted_row": (
        "performing inverted rows under a bar, body horizontal",
        "hanging under bar with arms extended, body in a straight diagonal line",
        "chest pulled up to bar, elbows bent and pulled back"
    ),
    "face_pull": (
        "performing face pulls at cable machine with rope attachment to face height",
        "arms extended forward gripping rope at eye level",
        "rope pulled to face with elbows flared high, external shoulder rotation"
    ),
    "band_row": (
        "performing a resistance band row, band anchored at waist height",
        "arms extended forward gripping resistance band handles",
        "elbows pulled back in rowing motion, band stretched"
    ),
    "chest_supported_row": (
        "performing chest-supported dumbbell rows lying on inclined bench",
        "lying face-down on inclined bench, arms hanging with dumbbells",
        "dumbbells pulled up with elbows bent, rowing motion"
    ),
    "t_bar_row": (
        "performing a T-bar row with barbell in corner and V-handle grip",
        "hinging forward over T-bar, arms extended gripping handles",
        "weight pulled up to chest, elbows back, back contracted"
    ),
    "rowing_machine": (
        "using a rowing machine (ergometer) with full rowing stroke",
        "in catch position: knees bent, arms extended, leaning forward",
        "in finish position: legs extended, leaning back slightly, hands pulled to chest"
    ),
    "battle_rope": (
        "performing battle rope waves, holding thick ropes with alternating or simultaneous waves",
        "standing in athletic stance, holding rope ends with arms raised",
        "arms making powerful wave motion, ropes creating wave down their length"
    ),

    # ── VERTICAL PUSH / SHOULDERS ──────────────────────────────────────────────
    "ohp": (
        "performing an overhead barbell press, standing",
        "barbell held at shoulder level, elbows forward, starting position",
        "barbell pressed overhead with straight arms, fully extended"
    ),
    "dumbbell_shoulder_press": (
        "performing a seated or standing dumbbell overhead press",
        "dumbbells at shoulder height, elbows bent at 90 degrees",
        "dumbbells pressed overhead, arms fully extended above head"
    ),
    "arnold_press": (
        "performing an Arnold press with dumbbells, with rotation",
        "dumbbells at chin level, palms facing body, elbows forward",
        "dumbbells overhead, palms rotated facing forward during press"
    ),
    "lateral_raise": (
        "performing lateral raises with dumbbells, arms lifting to the sides",
        "standing with dumbbells at sides, arms relaxed",
        "dumbbells raised to shoulder height in a T position"
    ),
    "front_raise": (
        "performing front raises with dumbbells, arms lifting forward",
        "standing with dumbbells in front of thighs",
        "one or both arms raised straight forward to shoulder height"
    ),
    "handstand_pushup": (
        "performing handstand push-ups against a wall",
        "in full handstand against wall, arms extended, feet against wall",
        "lowered to head near floor in handstand, elbows bent, about to press back up"
    ),
    "upright_row": (
        "performing an upright row with barbell, pulling to chin height",
        "standing holding barbell with close overhand grip at hip level",
        "barbell pulled up to chin, elbows flared high above bar"
    ),

    # ── VERTICAL PULL / PULLUP BAR ─────────────────────────────────────────────
    "pullup": (
        "performing overhand grip pull-ups on a pull-up bar",
        "hanging from pull-up bar with overhand grip, arms fully extended",
        "chin above bar, elbows bent, muscles fully contracted"
    ),
    "chin_up": (
        "performing underhand grip chin-ups on a pull-up bar",
        "hanging from bar with underhand (supinated) grip, arms extended",
        "chin above bar with underhand grip, biceps fully contracted"
    ),
    "wide_pullup": (
        "performing wide-grip pull-ups with hands placed wide on bar",
        "hanging from bar with wide overhand grip, arms very wide",
        "chin over bar, wide grip, lats fully contracted"
    ),
    "lat_pulldown": (
        "performing a lat pulldown at a cable machine with wide bar",
        "seated at pulldown machine, gripping wide bar with arms extended up",
        "bar pulled down to upper chest, elbows pointing down, lats contracted"
    ),
    "band_pulldown": (
        "performing band pulldowns with resistance band anchored above",
        "standing facing anchor point, arms extended up holding band",
        "band pulled down to thighs, arms straight, lats contracted"
    ),
    "assisted_pullup": (
        "performing assisted pull-ups with resistance band around knees for assistance",
        "hanging from pull-up bar with band around knees providing assistance",
        "chin above bar, assisted pull-up completed"
    ),
    "muscle_up": (
        "performing a muscle-up on pull-up bar, transitioning from pull to push above bar",
        "hanging below bar, beginning explosive pull",
        "transitioned above bar with arms extended, body supported above bar"
    ),
    "hanging_leg_raise": (
        "performing hanging leg raises while hanging from pull-up bar",
        "hanging from pull-up bar with straight body and extended arms",
        "legs raised horizontally or higher while hanging, core contracted"
    ),
    "toes_to_bar": (
        "performing toes-to-bar while hanging from pull-up bar",
        "hanging from bar with arms extended, body straight",
        "toes brought up to touch bar, core fully contracted, legs straight"
    ),
    "l_sit": (
        "holding an L-sit position on parallel bars or floor supports",
        "supporting body weight on both hands with legs hanging straight down",
        "legs extended horizontally, body forming L-shape, core braced"
    ),

    # ── ARM FLEXION / BICEPS ───────────────────────────────────────────────────
    "curl": (
        "performing barbell bicep curls standing",
        "standing holding barbell at hip level, arms fully extended",
        "barbell curled up to shoulder height, biceps fully contracted"
    ),
    "hammer_curl": (
        "performing hammer curls with dumbbells, neutral grip",
        "standing with dumbbells at sides, neutral grip (thumbs up)",
        "dumbbells curled to shoulder height with neutral grip"
    ),
    "concentration_curl": (
        "performing a concentration curl seated with elbow braced on inner thigh",
        "seated, elbow braced on inner thigh, arm extended holding dumbbell",
        "dumbbell curled up, bicep fully contracted in isolated position"
    ),
    "incline_curl": (
        "performing incline dumbbell curls on inclined bench, arms hanging back",
        "lying back on inclined bench, arms hanging with dumbbells, fully extended",
        "dumbbells curled up, biceps fully stretched and contracted"
    ),
    "cable_curl": (
        "performing a cable bicep curl at a cable machine with low pulley",
        "standing at cable machine, arm extended down holding cable handle",
        "cable curled up, bicep contracted, elbow stationary"
    ),
    "reverse_curl": (
        "performing reverse curls with overhand grip on barbell",
        "standing, overhand grip on barbell, arms extended",
        "barbell curled up with overhand grip, forearms contracted"
    ),

    # ── ARM EXTENSION / TRICEPS ────────────────────────────────────────────────
    "tricep_extension": (
        "performing an overhead tricep extension with dumbbell or EZ bar",
        "arms extended overhead holding weight",
        "weight lowered behind head, elbows bent, triceps fully stretched"
    ),
    "skull_crusher": (
        "performing skull crushers lying on bench with barbell or dumbbells",
        "lying on bench, arms extended straight up holding barbell",
        "bar lowered toward forehead, elbows bent, triceps stretched"
    ),
    "tricep_pushdown": (
        "performing a cable tricep pushdown at cable machine with rope or bar",
        "standing at cable machine, elbows at sides, handle at chest level",
        "cable pushed down with arms fully extended, triceps contracted"
    ),
    "tricep_kickback": (
        "performing dumbbell tricep kickbacks with one arm, body hinged forward",
        "hinged forward, elbow bent at 90 degrees with dumbbell",
        "arm extended straight back, dumbbell kicked back, tricep contracted"
    ),

    # ── CORE ──────────────────────────────────────────────────────────────────
    "plank": (
        "holding a forearm plank position, body perfectly straight",
        "in forearm plank starting position, about to engage",
        "in full forearm plank, body rigid from head to toe, holding"
    ),
    "side_plank": (
        "holding a side plank on one forearm, body perfectly lateral",
        "lying on side, about to raise into side plank",
        "in full side plank, body elevated, forming straight diagonal line"
    ),
    "crunch": (
        "performing ab crunches lying on back",
        "lying on back, knees bent, hands at temples",
        "upper body crunched up, shoulders off floor, abs contracted"
    ),
    "bicycle_crunch": (
        "performing bicycle crunches with alternating elbow-to-knee",
        "lying on back, hands at temples, legs in tabletop position",
        "twisting with elbow toward opposite knee, extending other leg"
    ),
    "leg_raise": (
        "performing lying leg raises on floor",
        "lying flat on back, arms at sides, legs straight along floor",
        "both legs raised straight up to 90 degrees, lower abs contracted"
    ),
    "mountain_climber": (
        "performing mountain climbers in plank position, alternating knee drives",
        "in high plank position, arms extended",
        "one knee driven toward chest at speed, other leg extended"
    ),
    "russian_twist": (
        "performing Russian twists seated, rotating side to side",
        "seated with knees bent, leaning back at 45 degrees, hands clasped",
        "torso twisted to one side, hands reaching to that side"
    ),
    "dead_bug": (
        "performing dead bug exercise on back, alternating arm and leg extensions",
        "lying on back, arms extended straight up, knees bent at 90 degrees",
        "opposite arm and leg extended low toward floor while maintaining low back flat"
    ),
    "hollow_body": (
        "holding a hollow body hold on floor",
        "lying on back, arms overhead, legs together along floor",
        "arms and legs raised, lower back pressed to floor, banana curve shape"
    ),
    "v_up": (
        "performing V-ups, simultaneously lifting legs and torso to form V shape",
        "lying flat, arms overhead, legs along floor",
        "arms and legs raised simultaneously to meet above hips, forming V shape"
    ),
    "hanging_leg_raise_core": (  # Use hanging_leg_raise defined above
        "performing hanging knee raises while hanging from bar",
        "hanging from pull-up bar, knees tucked",
        "knees driven to chest, core contracted"
    ),
    "ab_wheel": (
        "performing ab wheel rollouts from kneeling position",
        "kneeling, hands on ab wheel, rolled in close to body",
        "rolled out with body extended low to floor, arms ahead, core braced"
    ),
    "cable_crunch": (
        "performing cable crunches kneeling at cable machine with rope attachment",
        "kneeling at cable machine, hands holding rope at face level",
        "crunching down, bringing elbows toward knees, abs contracted"
    ),
    "l_sit_core": (  # Use l_sit defined above
        "holding an L-sit on parallel bars",
        "on parallel bars, body supported on straight arms",
        "legs extended horizontally, L shape formed"
    ),

    # ── CONDITIONING / CARDIO ─────────────────────────────────────────────────
    "run": (
        "running outdoors or on treadmill, dynamic running form",
        "in mid-stride run, one foot on ground, other leg forward",
        "at full running pace, lean forward, arms driving"
    ),
    "sprint": (
        "performing explosive sprint, maximum speed",
        "in sprint starting position, leaning forward",
        "at top sprint speed, fully airborne between strides, maximum effort"
    ),
    "bike": (
        "cycling on a stationary exercise bike",
        "seated on exercise bike, pedaling at moderate pace",
        "high cadence cycling, leaning slightly forward on bike"
    ),
    "burpee": (
        "performing burpees, full body explosive movement",
        "standing tall at start of burpee",
        "at peak of jump with arms overhead after completing burpee"
    ),
    "jumping_jack": (
        "performing jumping jacks, arms and legs spreading and closing",
        "standing with arms at sides and feet together",
        "arms overhead and feet wide, mid-jump"
    ),
    "high_knees": (
        "performing high knees in place, knees driving up to hip height",
        "standing, beginning high knee run in place",
        "one knee driven high to chest, arms pumping, mid-movement"
    ),
    "jump_rope": (
        "jumping rope with a skipping rope",
        "standing with rope behind, about to swing",
        "mid-jump with rope swinging under feet, slight bounce"
    ),
    "box_jump": (
        "performing box jumps, explosive jump onto sturdy box",
        "in squat position, facing box, arms loaded back",
        "landed on top of box with both feet, standing tall"
    ),
    "elliptical": (
        "using an elliptical trainer machine",
        "on elliptical, arms and legs in mid-stride position",
        "completing forward stride on elliptical, smooth fluid motion"
    ),
    "stair_climb": (
        "climbing stairs or using stair climber machine",
        "foot on first step, about to begin climb",
        "mid-stride on stairs, one leg extended, climbing"
    ),
    "skater_jump": (
        "performing lateral skater jumps, bounding side to side",
        "balanced on one foot, body low, other leg behind",
        "airborne mid-lateral jump, bounding to other foot"
    ),
    "swim": (
        "performing freestyle swimming stroke",
        "arm extended overhead in water, beginning stroke",
        "pulling arm through water, head turned to breathe, full freestyle stroke"
    ),

    # ── MOBILITY / RECOVERY ───────────────────────────────────────────────────
    "yoga": (
        "performing a yoga flow, warrior or downward dog pose",
        "in downward facing dog position, hips high, hands and feet on mat",
        "flowing into warrior two pose, arms extended, lunge position"
    ),
    "stretching": (
        "performing full body stretching routine on yoga mat",
        "sitting on mat in butterfly stretch, feet together, leaning forward",
        "lying on back in supine twist, one leg crossed over"
    ),
    "foam_rolling": (
        "foam rolling the quadriceps or back, myofascial release",
        "positioned on foam roller under quads, hands on floor",
        "slowly rolling up quad muscle, applying body weight pressure"
    ),
    "hip_flexor_stretch": (
        "performing a kneeling hip flexor stretch or couch stretch",
        "in kneeling lunge position, rear knee on mat",
        "hips pressed forward, torso upright, deep hip flexor stretch"
    ),
    "world_greatest_stretch": (
        "performing the world's greatest stretch, thoracic rotation in lunge",
        "in deep lunge position, front hand on floor",
        "thoracic rotation with top arm reaching to sky, twisting open"
    ),
    "cat_cow": (
        "performing cat-cow stretches on hands and knees on mat",
        "on hands and knees, spine neutral, tabletop position",
        "spine flexed upward in cat position, then arched down in cow position"
    ),
}

# ── Image style ──────────────────────────────────────────────────────────────

STYLE_SUFFIX = (
    "professional fitness instruction photo, multi-frame composite showing two phases of movement, "
    "white dashed and solid technique indicator lines overlaid, white directional arrows showing movement path, "
    "modern luxury gym background, warm wood floor, indoor plant, dark rubber weight plates visible, "
    "vertical portrait orientation, soft studio lighting, photorealistic, ultra high quality"
)

# ── Generation function ────────────────────────────────────────────────────────

def load_image_as_data_url(path: Path) -> str:
    """Load image and return as base64 data URL for fal.ai."""
    with open(path, "rb") as f:
        data = base64.b64encode(f.read()).decode()
    ext = path.suffix.lower().strip(".")
    mime = "image/jpeg" if ext in ("jpg", "jpeg") else "image/png"
    return f"data:{mime};base64,{data}"


def generate_image(coach_name: str, exercise_id: str, coach_desc: str,
                   action_desc: str, avatar_data_url: str) -> bytes | None:
    """Call fal.ai FLUX Kontext to generate one exercise photo."""
    action, phase1, phase2 = action_desc

    prompt = (
        f"{coach_desc}, {action}, "
        f"left frame showing: {phase1}, right frame showing: {phase2}, "
        f"{STYLE_SUFFIX}"
    )

    try:
        result = fal_client.run(
            "fal-ai/flux-pro/kontext",
            arguments={
                "prompt": prompt,
                "image_url": avatar_data_url,   # character reference
                "image_size": "portrait_4_3",
                "num_inference_steps": 28,
                "guidance_scale": 3.5,
                "num_images": 1,
                "output_format": "jpeg",
            }
        )
        image_url = result["images"][0]["url"]
        response = requests.get(image_url, timeout=30)
        response.raise_for_status()
        return response.content
    except Exception as e:
        print(f"  ✗ API error: {e}")
        return None


def main():
    api_key = os.environ.get("FAL_KEY")
    if not api_key:
        print("ERROR: Set FAL_KEY environment variable")
        print("  Get your key at https://fal.ai/dashboard")
        sys.exit(1)

    os.environ["FAL_KEY"] = api_key

    # Remove duplicate keys (hanging_leg_raise_core, l_sit_core)
    exercises_to_generate = {
        k: v for k, v in EXERCISES.items()
        if not k.endswith("_core")  # skip aliases
    }

    total = len(COACHES) * len(exercises_to_generate)
    done = 0
    skipped = 0
    failed = 0

    print(f"V-Tempe Exercise Image Generator")
    print(f"{'─' * 50}")
    print(f"Coaches: {len(COACHES)}  |  Exercises: {len(exercises_to_generate)}  |  Total: {total}")
    print(f"Output: {DRAWABLES_DIR}")
    print()

    for coach_name, coach_info in COACHES.items():
        avatar_path = DRAWABLES_DIR / coach_info["avatar_file"]
        if not avatar_path.exists():
            print(f"⚠️  Avatar not found: {avatar_path}")
            continue

        print(f"Coach: {coach_name.upper()}")
        avatar_data_url = load_image_as_data_url(avatar_path)

        for exercise_id, action_desc in exercises_to_generate.items():
            output_path = DRAWABLES_DIR / f"coach_{coach_name}_{exercise_id}.jpg"

            if output_path.exists():
                skipped += 1
                print(f"  ↩  {exercise_id} (already exists)")
                continue

            print(f"  ⟳  {exercise_id}...", end=" ", flush=True)

            image_bytes = generate_image(
                coach_name=coach_name,
                exercise_id=exercise_id,
                coach_desc=coach_info["description"],
                action_desc=action_desc,
                avatar_data_url=avatar_data_url,
            )

            if image_bytes:
                output_path.write_bytes(image_bytes)
                done += 1
                print(f"✓ saved ({len(image_bytes) // 1024} KB)")
            else:
                failed += 1
                print("✗ failed")

            time.sleep(REQUEST_DELAY)

        print()

    print(f"{'─' * 50}")
    print(f"Done: {done}  |  Skipped: {skipped}  |  Failed: {failed}")

    if done > 0:
        print()
        print("Next step: add new exercise IDs to CoachVisuals.kt maps")
        print("Then run: ./gradlew :ui:compileDebugKotlinAndroid")


if __name__ == "__main__":
    main()
