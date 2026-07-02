# V-Tempe — Промпты для генерации фото упражнений
## Инструкция по использованию ChatGPT / GPT-4o

---

## КАК РАБОТАТЬ (прочитай один раз)

### Модель
Используй **GPT-4o** (не DALL-E 3 отдельно). GPT-4o лучше держит внешность персонажа.

### Для каждого изображения:
1. Открой **новый чат** (или продолжай в том же — GPT-4o помнит контекст)
2. **Загрузи фото тренера** (аватарку) — это критично для consistency
3. Вставь **БАЗОВЫЙ БЛОК** нужного тренера
4. Добавь в конец **СТРОКУ УПРАЖНЕНИЯ** из списка ниже
5. Генерируй → сохраняй как `coach_{тренер}_{id}.jpg`

### Лимит ChatGPT Plus
~40 изображений каждые 3 часа. 318 изображений = ~8 сессий = 1-2 дня.

### Важно для consistency
- Загружай аватарку КАЖДЫЙ РАЗ (не только в начале сессии)
- Не меняй БАЗОВЫЙ БЛОК ни на букву
- Если внешность поплыла — начни новую сессию, снова загрузи аватарку

---

## БАЗОВЫЙ БЛОК — MIA
> Вставлять в каждый промпт перед строкой упражнения

```
Using the person from the uploaded reference photo (exact same face, curly dark brown hair, black racerback sports bra, black high-waist compression leggings, white sneakers, smart watch on left wrist, toned athletic build), generate a professional fitness instruction image.

BACKGROUND (identical to reference photo): warm honey-colored wood floor, soft vertical warm light strips on light gray wall, tall indoor plant in white pot on left, dark rubber weight plates visible on right, black yoga mat partially visible.

FORMAT: Vertical portrait image (3:4 ratio). Multi-panel composite — LEFT PANEL shows starting position, RIGHT PANEL shows ending position. White dashed movement trajectory lines and white directional arrows overlaid showing the movement path. Clean studio lighting matching reference photo.

EXERCISE: 
```

---

## БАЗОВЫЙ БЛОК — ARTUR
> Вставлять в каждый промпт перед строкой упражнения

```
Using the person from the uploaded reference photo (exact same face, curly dark black hair, short stubble beard, small pendant necklace, fitted all-black short-sleeve polo shirt, black jogger pants, black sneakers, athletic muscular build), generate a professional fitness instruction image.

BACKGROUND: warm honey-colored wood floor, soft vertical warm light strips on light gray wall, tall indoor plant in white pot on left, dark rubber weight plates visible, clean modern gym interior, same professional studio lighting as reference photo.

FORMAT: Vertical portrait image (3:4 ratio). Multi-panel composite — LEFT PANEL shows starting position, RIGHT PANEL shows ending position. White dashed movement trajectory lines and white directional arrows overlaid showing the movement path. Clean professional lighting.

EXERCISE:
```

---

## БАЗОВЫЙ БЛОК — VTEMPE
> Вставлять в каждый промпт перед строкой упражнения

```
Using the character from the uploaded reference photo (exact same futuristic AI robot figure: sleek all-black form-fitting athletic suit, glowing CYAN/TEAL blue neon accent lines running along arms, torso and legs, full black helmet with dark reflective visor, no visible face, black and cyan athletic high-top sneakers, humanoid athletic body), generate a professional fitness instruction image.

BACKGROUND: warm honey-colored wood floor, soft vertical warm light strips on dark wall, tall indoor plant in pot on left, dark rubber weight plates visible on right, sleek modern luxury gym interior.

FORMAT: Vertical portrait image (3:4 ratio). Multi-panel composite — LEFT PANEL shows starting position, RIGHT PANEL shows ending position. Cyan/teal movement indicator lines and arrows overlaid (matching the character's neon accent color). Clean cinematic lighting.

EXERCISE:
```

---

# СПИСОК УПРАЖНЕНИЙ
## Копируй строку после "EXERCISE:" в базовый блок

> Имя файла для сохранения указано в скобках: `coach_{тренер}_{id}.jpg`

---

### 🦵 НОГИ / ПРИСЕДАНИЯ

**squat** → `coach_mia_squat.jpg` *(уже есть — пропустить)*
**deadlift** → *(уже есть — пропустить)*
**Миа** → *все упражнения сгенерированы
```
goblet_squat
Barbell squat holding a kettlebell with both hands at chest height. LEFT: standing tall, kettlebell at chest, feet shoulder-width. RIGHT: deep squat position, thighs parallel to floor, kettlebell held at chest, knees tracking over toes. Key form: upright torso, knees out, full depth.
```

```
front_squat
Barbell front squat with bar in front rack position on front deltoids. LEFT: standing upright, barbell resting on front shoulders, elbows high. RIGHT: deep squat, elbows high maintaining bar position, torso very upright. Key form: elbows up, torso vertical.
```

```
sumo_squat
Wide-stance sumo squat holding dumbbell between legs. LEFT: standing with very wide foot stance, toes pointed out 45°, dumbbell hanging. RIGHT: deep squat with dumbbell near floor, knees pushed out in line with toes. Key form: wide stance, knees tracking toes, neutral spine.
```

```
wall_sit
Wall sit isometric hold. LEFT: standing next to wall, about to sit. RIGHT: back flat against wall, thighs perfectly parallel to floor, knees at exact 90°, arms crossed or extended forward, holding position. Key form: 90° knee angle, flat back on wall.
```

```
box_squat
Barbell box squat sitting back onto low box. LEFT: standing with barbell on back above box. RIGHT: seated momentarily on box, barbell still on back, controlled sit-back position, shin vertical. Key form: sit BACK not down, vertical shins.
```

```
hack_squat  →  coach_mia_hack_squat.jpg
Hack squat MACHINE exercise. LEFT: in machine at top, legs extended, shoulders under pads. RIGHT: deep squat in machine, thighs below parallel, controlled descent. Key form: full depth, feet on platform shoulder-width.
```

```
barbell_hack_squat  →  coach_mia_barbell_hack_squat.jpg
Barbell hack squat with barbell BEHIND the legs on the floor. LEFT: barbell on floor behind heels, athlete reaching back to grip it with overhand grip, hinge position. RIGHT: standing fully upright, barbell at hip level lifted from behind. Key form: keep bar dragging close to legs all the way up, neutral back.
```

```
leg_extension  →  coach_mia_leg_extension.jpg
Seated leg extension machine. LEFT: seated, knees bent at 90°, ankles behind the padded lever. RIGHT: legs fully extended and locked out, quadriceps fully contracted. Key form: controlled extension, squeeze quads at top.
```

```
jump_squat  →  coach_mia_jump_squat.jpg
Explosive jump squat, bodyweight. LEFT: in athletic squat position, arms back, about to jump. RIGHT: at peak of jump — fully airborne, arms overhead, full body extension. Key form: land softly, immediately into next rep.
```

---

### 🍑 ТЯГА / ЯГОДИЦЫ

```
romanian_deadlift
Romanian deadlift (RDL) with barbell. LEFT: standing tall, barbell at hip level, soft knee bend. RIGHT: hinged forward, barbell slides down thighs to mid-shin level, back flat, hips pushed back. Key form: bar stays close to legs, flat back, hinge don't squat.
```

```
glute_bridge
Glute bridge on floor, bodyweight. LEFT: lying on back, knees bent 90°, feet flat, arms at sides. RIGHT: hips thrust high — body forms straight diagonal from knees to shoulders, glutes squeezed hard. Key form: drive through heels, squeeze glutes at top for 1 second.
```

```
kettlebell_swing
Two-handed kettlebell swing. LEFT: hinge position — knees soft, hips pushed back, kettlebell swinging between legs like a pendulum. RIGHT: standing tall, hips fully extended, kettlebell floated to shoulder height by hip drive, arms loose. Key form: it's a HIP HINGE not a squat, power from hips.
```

```
single_leg_deadlift
Single-leg Romanian deadlift with dumbbell. LEFT: standing on one leg, slight hinge beginning, opposite leg starting to extend back. RIGHT: fully hinged forward — standing leg soft, free leg extended straight behind at hip height, dumbbell near floor, T-shape body. Key form: square hips to floor, keep back flat.
```

```
sumo_deadlift
Sumo stance barbell deadlift with very wide foot placement. LEFT: wide stance over barbell, hands inside knees gripping bar, chest up, hips low. RIGHT: standing fully upright with barbell, wide stance, knees out, hips locked. Key form: push knees out, drive hips forward to lockout.
```

```
good_morning
Good morning exercise with barbell on upper back. LEFT: standing tall, barbell across upper back, slight knee bend. RIGHT: hinged forward to near-parallel with floor, back flat, bar still on back, hips pushed back. Key form: flat back maintained throughout, controlled tempo.
```

```
nordic_curl
Nordic hamstring curl. LEFT: kneeling, ankles anchored under something heavy/bar, body upright, hands at chest. RIGHT: body slowly lowered toward floor with straight spine and hips, hands catching just before hitting floor. Key form: resist with hamstrings, lower as slowly as possible.
```

```
leg_curl
Lying hamstring curl machine. LEFT: lying face down on machine, ankles just behind padded lever, legs nearly straight. RIGHT: heels curled up to glutes, full contraction at top, only lower legs moved. Key form: keep hips down, full range of motion.
```

---

### 🦶 ОДНА НОГА

```
reverse_lunge
Reverse lunge (step back). LEFT: standing tall, feet together. RIGHT: one foot stepped back, back knee lowered toward floor (1 inch above), front thigh parallel to floor, front knee tracking over toes. Key form: step straight back, keep front shin vertical.
```

```
bulgarian_split_squat
Bulgarian split squat with rear foot elevated on bench. LEFT: front foot stepped forward, rear foot on bench, standing upright. RIGHT: lowered deep — front thigh parallel, rear knee near floor, torso upright. Key form: front foot far enough forward, shin stays relatively vertical.
```

```
step_up
Step-up onto box with dumbbells. LEFT: standing facing box, dumbbell in each hand, one foot placed on box. RIGHT: fully stepped up — both feet on box, standing tall at top, one knee optionally driven up. Key form: drive through the heel on the box, don't push off back foot.
```

```
lateral_lunge
Lateral lunge stepping out to side. LEFT: standing with feet together. RIGHT: one leg stepped wide out to side — bent knee over foot, other leg completely straight, hips pushed back, weight in bent leg heel. Key form: keep straight leg truly straight, chest up.
```

```
pistol_squat
Pistol squat single-leg squat. LEFT: standing on one leg, other leg extended forward at hip height, arms forward for balance. RIGHT: deep single-leg squat — standing leg bent fully, extended leg parallel to floor in front, arms counterbalancing. Key form: control the descent, keep heel flat.
```

```
skater_lunge
Lateral skater lunge bounds. LEFT: balanced on left foot, right leg crossed behind in curtsy position, body low, arms counterbalancing. RIGHT: bounding laterally to land on right foot, left leg now crossing behind, explosive side-to-side movement. Key form: soft landing, load the hip, explosive push-off.
```

---
### 💪 ГРУДЬ / ГОРИЗОНТАЛЬНЫЙ ЖИМ

```
incline_bench
Incline barbell or dumbbell press on inclined bench. LEFT: lying on inclined bench (~45°), bar or dumbbells at upper chest level, elbows at 45°. RIGHT: weight pressed overhead at top, arms extended, bar over upper chest. Key form: keep shoulder blades retracted, elbows not flared too wide.
```

```
diamond_pushup
Diamond push-up with hands forming diamond shape. LEFT: high plank, hands close together forming diamond shape under chest, arms extended. RIGHT: chest lowered to nearly touch hands, elbows pointing back (not out), arms close to body. Key form: elbows travel back alongside ribs.
```

```
wide_pushup
Wide-grip push-up. LEFT: high plank with hands much wider than shoulders, arms extended. RIGHT: chest lowered to just above floor with wide hand position, elbows flared. Key form: extra chest stretch at bottom, wider than shoulder-width.
```

```
decline_pushup
Decline push-up with feet elevated on bench. LEFT: feet on bench behind, hands on floor, body angled downward, arms extended. RIGHT: chest lowered toward floor, controlled descent, arms bent. Key form: body stays straight, targets upper chest.
```

```
incline_pushup
Incline push-up (easier variation) with hands elevated on bench. LEFT: hands on bench edge, feet on floor, body angled, arms extended. RIGHT: chest nearly touching bench, elbows bent. Key form: body in straight line, easier than floor push-up.
```

```
pike_pushup
Pike push-up targeting shoulders. LEFT: inverted V / downward dog position — hips high in air, hands on floor, arms extended. RIGHT: head lowered toward floor between hands, elbows bent, forehead close to floor. Key form: hips stay high, works shoulders, not chest.
```

```
dumbbell_fly
Dumbbell chest fly on flat bench. LEFT: lying on bench, dumbbells extended wide with slight elbow bend, like hugging a barrel. RIGHT: dumbbells brought together above chest in arc motion, inner chest contracted. Key form: keep slight elbow bend throughout, stretch at bottom.
```

```
cable_fly
Cable chest fly standing at cable machine. LEFT: arms extended out to sides gripping cable handles at shoulder height, slight forward lean. RIGHT: hands brought together in front of chest in sweeping arc motion, slight forward lean. Key form: squeeze inner chest at full contraction.
```

```
close_grip_bench
Close-grip bench press with hands shoulder-width apart. LEFT: lying on bench, barbell gripped close, arms extended. RIGHT: barbell lowered to lower chest, elbows tracking close alongside body. Key form: elbows stay tucked in, targets triceps more than chest.
```

---
### 🏋️ СПИНА / ГОРИЗОНТАЛЬНАЯ ТЯГА

```
dumbbell_row
Single-arm dumbbell row with knee on bench. LEFT: one knee and hand on bench, other hand gripping dumbbell hanging straight down. RIGHT: dumbbell pulled up to hip, elbow pointing to ceiling, full lat contraction. Key form: don't rotate torso, pull elbow up not back.
```

```
cable_row
Seated cable row with close-grip handle. LEFT: sitting at cable machine, arms fully extended forward gripping handle, slight forward lean. RIGHT: handle pulled to abdomen, elbows behind body, upright posture, shoulders back. Key form: row TO the body, don't lean back excessively.
```

```
inverted_row
Inverted row (bodyweight row) under bar. LEFT: hanging under bar, arms extended, body in straight diagonal line, heels on floor. RIGHT: chest pulled up to bar, elbows bent and pulled back, body horizontal. Key form: keep body rigid, pull bar to lower chest.
```

```
face_pull
Face pull at cable machine with rope attachment at eye level. LEFT: standing, arms extended forward gripping rope at eye level. RIGHT: rope pulled to face level, elbows flared out HIGH and wide, hands beside ears, external shoulder rotation. Key form: elbows ABOVE wrists, rear delts worked.
```

```
band_row
Resistance band row with band anchored at waist height. LEFT: standing, arms extended gripping band handles, band taut. RIGHT: elbows pulled back, hands at sides, band stretched, back contracted. Key form: keep torso still, squeeze shoulder blades together at finish.
```

```
chest_supported_row
Chest-supported dumbbell row on inclined bench. LEFT: lying face-down on inclined bench, arms hanging with dumbbells below. RIGHT: dumbbells pulled up to ribcage, elbows bent, back contracted, chest stays on bench throughout. Key form: chest on pad prevents cheating.
```

```
t_bar_row
T-bar row with barbell in corner and V-handle. LEFT: hinging over T-bar, arms extended gripping V-handle, back flat. RIGHT: weight pulled up to chest, elbows back, back contracted. Key form: stay hinged, don't stand up during row.
```

```
rowing_machine
Rowing machine (ergometer) stroke. LEFT: catch position — knees bent, arms extended forward, leaning slightly forward on seat. RIGHT: finish position — legs fully extended, leaning slightly back, handle pulled to lower chest. Key form: legs first, then lean back, then arms — sequence matters.
```

```
battle_rope
Battle rope waves exercise. LEFT: athletic stance, holding rope ends, arms at shoulder height about to create wave. RIGHT: powerful alternating wave motion — one arm up, one down, creating undulating waves down the rope. Key form: athletic stance, drive with whole arm, core braced.
```

---

### 🏆 ПЛЕЧИ / ВЕРТИКАЛЬНЫЙ ЖИМ

```
dumbbell_shoulder_press
Seated or standing dumbbell overhead press. LEFT: dumbbells at shoulder height, palms forward, elbows at 90°. RIGHT: dumbbells pressed overhead, arms fully extended, slight inward arc at top. Key form: don't arch lower back, brace core.
```

```
arnold_press
Arnold press with dumbbells with rotation. LEFT: dumbbells at chin height, palms FACING body, elbows forward. RIGHT: dumbbells overhead, arms extended, palms have ROTATED to face forward during pressing motion. Key form: the rotation is the key — pronation to supination.
```

```
lateral_raise
Dumbbell lateral raises. LEFT: standing, dumbbells at sides. RIGHT: both arms raised out to sides to shoulder height forming T shape, slight bend in elbows, pinky slightly higher than thumb. Key form: lead with elbows not hands, don't shrug.
```

```
front_raise
Dumbbell front raises. LEFT: standing, dumbbells in front of thighs, palms facing back. RIGHT: one or both arms raised straight forward to shoulder height, arms parallel to floor. Key form: controlled lift, don't swing, slight elbow bend.
```

```
handstand_pushup
Handstand push-up against wall. LEFT: full handstand against wall, arms completely extended, feet against wall. RIGHT: head lowered to just above floor in handstand, elbows bent ~90°, about to press back up. Key form: tuck chin, keep core tight, press through palms.
```

```
upright_row
Barbell upright row. LEFT: standing, barbell held with close overhand grip at hip level. RIGHT: barbell pulled up to chin level, elbows flared HIGH above bar level. Key form: elbows lead the movement, go above shoulder height.
```

---

### 🔝 ТУРНИК / ВЕРТИКАЛЬНАЯ ТЯГА

```
chin_up
Underhand (supinated) grip chin-up on pull-up bar. LEFT: hanging with underhand grip shoulder-width, arms fully extended, dead hang. RIGHT: chin cleared above bar, biceps fully contracted, chest approaching bar. Key form: underhand grip (palms face you) = more bicep involvement than pull-up.
```

```
wide_pullup
Wide-grip overhand pull-up. LEFT: hanging from bar with very wide grip, arms extended. RIGHT: upper chest brought to bar level, elbows pointing straight down. Key form: wider grip = more lat focus, shorter range of motion than neutral.
```

```
lat_pulldown
Lat pulldown at cable machine with wide bar. LEFT: seated, arms extended overhead gripping wide bar. RIGHT: bar pulled down to upper chest, elbows pointing down, lats contracted. Key form: lean back slightly, pull to CHEST not chin.
```

```
band_pulldown
Resistance band pulldown standing. LEFT: facing anchor point high above, arms fully extended up gripping band ends. RIGHT: band pulled down to thighs, arms straight, lats contracted with straight-arm pulldown motion. Key form: keep arms straight, lat stretch to contraction.
```

```
assisted_pullup
Pull-up with resistance band assistance around knees. LEFT: band looped around pull-up bar and around knees, dead hang with band providing assistance. RIGHT: chin above bar, band supporting some of bodyweight. Key form: band reduces effective bodyweight, good for learning pull-ups.
```

```
muscle_up
Muscle-up on pull-up bar. LEFT: dead hang below bar, about to initiate explosive pull. RIGHT: body above bar, arms extended in dip position, supported above bar. Key form: explosive pull transitioning to push above bar.
```

```
hanging_leg_raise
Hanging leg raise from pull-up bar. LEFT: hanging from bar, arms extended, body straight, legs hanging. RIGHT: legs raised horizontally or to 90°, toes at bar height, core fully contracted. Key form: control the lowering, don't swing.
```

```
toes_to_bar
Toes to bar from pull-up bar. LEFT: hanging from bar, arms extended, core braced. RIGHT: straight legs raised all the way up until toes touch the bar, maximum core contraction. Key form: keep legs straight, it's about core not hip flexors.
```

```
l_sit
L-sit hold on parallel bars or dip handles. LEFT: both hands on bars, body supported on straight arms, legs hanging. RIGHT: legs extended perfectly horizontal forming capital L shape, held isometrically. Key form: depress shoulder blades, lock knees, point toes.
```

---

### 💪 БИЦЕПС

```
hammer_curl
Hammer curl with neutral grip (thumbs up). LEFT: standing, dumbbells at sides with neutral grip (thumbs pointing forward). RIGHT: dumbbells curled to shoulder height maintaining neutral grip throughout. Key form: elbows stay at sides, neutral grip targets brachialis and brachioradialis.
```

```
incline_curl
Incline dumbbell curl on inclined bench. LEFT: lying back on inclined bench, arms hanging behind body fully extended, maximum bicep stretch. RIGHT: dumbbells curled to shoulder height, full contraction, extra range of motion. Key form: incline position creates greater stretch than standing curl.
```

```
concentration_curl
Concentration curl seated. LEFT: seated, elbow braced against inside of thigh, arm fully extended holding dumbbell. RIGHT: dumbbell curled to shoulder, bicep peak fully contracted, elbow never leaves thigh. Key form: elbow stays on thigh, pure isolated bicep movement.
```

```
cable_curl
Cable bicep curl at low pulley cable machine. LEFT: standing, arm extended down gripping cable handle, cable running from floor pulley. RIGHT: cable curled up to shoulder height, bicep fully contracted. Key form: constant tension from cable throughout movement.
```

```
reverse_curl
Reverse curl with overhand (pronated) grip. LEFT: standing, barbell or dumbbells with overhand grip, arms extended. RIGHT: weight curled to shoulder height with overhand grip maintained. Key form: overhand grip targets forearms and brachialis, harder than regular curl.
```

---

### 💪 ТРИЦЕПС

```
skull_crusher
Skull crushers lying on bench. LEFT: lying on bench, barbell or dumbbells held with arms straight up over face. RIGHT: weight lowered toward forehead by bending elbows, upper arms stay vertical. Key form: only forearms move, upper arms stay perpendicular to bench.
```

```
tricep_pushdown
Cable tricep pushdown with rope attachment. LEFT: standing at cable, elbows at sides, rope at chest level. RIGHT: rope pushed down and slightly out, arms fully extended, triceps contracted. Key form: elbows stay at sides and fixed, only forearms move.
```

```
tricep_kickback
Dumbbell tricep kickback. LEFT: hinged forward, upper arm parallel to floor, elbow at 90° holding dumbbell. RIGHT: forearm kicked back until arm fully extended and parallel to floor, tricep contracted. Key form: upper arm stays perfectly parallel to floor throughout.
```

---
### 🧘 КОР

```
side_plank
Side plank on forearm. LEFT: lying on side, about to push up. RIGHT: full side plank — body elevated on one forearm and side of foot, body in perfectly straight diagonal line, top arm on hip. Key form: hips lifted, straight line from head to feet.
```

```
crunch
Ab crunches. LEFT: lying on back, knees bent 90°, hands lightly at temples. RIGHT: upper body crunched up, shoulder blades off floor, chin slightly tucked. Key form: don't pull neck with hands, focus on shortening abs.
```

```
bicycle_crunch
Bicycle crunches. LEFT: lying on back, hands at temples, knees at 90°. RIGHT: twisting — right elbow toward left knee, right leg extended. Arrow showing rotation. Key form: don't rush, full rotation each side.
```

```
leg_raise
Lying leg raise on floor. LEFT: lying flat on back, legs together along floor, arms at sides. RIGHT: both legs raised to 90° (vertical), lower back stays flat. Key form: don't arch lower back, lower legs slowly.
```

```
mountain_climber
Mountain climbers in plank. LEFT: in high plank, arms straight, body rigid. RIGHT: one knee driven explosively toward chest, other leg straight. Key form: keep hips down and level, don't bounce hips up.
```

```
russian_twist
Russian twist seated. LEFT: seated with knees bent, leaning back 45°, hands clasped in front. RIGHT: torso rotated to one side with hands reaching toward floor beside hip. Key form: lean back to engage abs, rotate through thoracic spine.
```

```
dead_bug
Dead bug on back. LEFT: lying on back, arms pointing straight up, knees bent 90° (tabletop). RIGHT: opposite arm and leg SLOWLY extended low toward floor (right arm, left leg), lower back stays pressed to floor. Key form: lower back NEVER lifts off floor.
```

```
hollow_body
Hollow body hold. LEFT: lying flat on back, arms overhead. RIGHT: arms and legs raised slightly off floor, lower back pressed firmly to ground, body forming a slight hollow banana curve. Key form: the tension comes from back pressing to floor.
```

```
v_up
V-ups. LEFT: lying flat on back, arms overhead, legs straight on floor. RIGHT: arms and legs simultaneously raised to meet above hips, forming V shape. Key form: keep legs straight, core does the work.
```

```
ab_wheel
Ab wheel rollout from knees. LEFT: kneeling, both hands on ab wheel, wheel near knees. RIGHT: rolled out — body nearly parallel to floor, arms extended forward, core braced preventing back from arching. Key form: brace like you're about to get punched.
```

```
cable_crunch
Cable crunch kneeling. LEFT: kneeling at cable, hands holding rope at forehead level. RIGHT: crunching down — elbows toward knees, flexing spine, rope pulled down. Key form: ROUND the spine, don't just hinge at hips.
```

---

### 🏃 КАРДИО

```
sprint
Sprint running at maximum speed. LEFT: sprint starting position — low forward lean, weight on front foot, explosive start. RIGHT: at full sprint speed — one foot airborne between strides, forward lean, arms driving powerfully. Key form: drive knees forward not up, lean from ankles.
```

```
burpee
Burpee full movement. LEFT: standing at beginning of burpee movement. RIGHT: at peak of jump at top of burpee — arms overhead, fully airborne, explosive jump after completing push-up and stand sequence. Key form: show the explosive jump finish.
```

```
jumping_jack
Jumping jacks. LEFT: standing, arms at sides, feet together. RIGHT: mid-air — arms raised overhead, feet spread wide. Key form: fully extend arms, jump with controlled rhythm.
```

```
high_knees
High knees running in place. LEFT: standing, beginning movement. RIGHT: one knee driven HIGH to hip height, opposite arm forward, on ball of opposite foot, mid-movement. Key form: drive KNEE up (not just lifting foot), stay on balls of feet.
```

```
jump_rope
Jump rope / skipping. LEFT: standing with rope behind, about to swing forward, slight knee bend. RIGHT: mid-jump — both feet slightly off ground, rope passing under feet, smooth timing. Key form: stay on balls of feet, minimal jump height.
```

```
box_jump
Box jump onto sturdy box. LEFT: athletic squat position facing box, arms loaded back, about to jump. RIGHT: landed on top of box — both feet on box, standing tall, fully extended. Key form: land with soft knees, jump from hips not just legs.
```

```
elliptical
Elliptical trainer use. LEFT: on elliptical, neutral stride position. RIGHT: full forward stride on elliptical, opposite arm and leg forward in smooth oval motion. Key form: full range of motion, use arms too.
```

```
stair_climb
Stair climbing. LEFT: foot on first step, body upright, about to climb. RIGHT: mid-stride on stairs — one leg extended, climbing powerfully, body angled slightly forward. Key form: drive through full foot, don't tip-toe.
```

```
skater_jump
Lateral skater jumps. LEFT: balanced on left foot, body low, right leg crossed behind (like a speed skater). RIGHT: bounding to right foot — airborne mid-lateral jump, left arm reaching across. Key form: explosive lateral bound, soft landing, load the hip.
```

```
swim
Freestyle swimming stroke. LEFT: extended arm reaching forward in water at catch position. RIGHT: full freestyle stroke — pulling arm through water, head turned to breathe, body in streamlined rotation. Key form: rotate body on long axis, reach forward fully.
```

---


### 🧘 МОБИЛЬНОСТЬ И ВОССТАНОВЛЕНИЕ

```
stretching
Full body stretching sequence on yoga mat. LEFT: seated butterfly stretch — feet together, leaning forward, pressing knees toward floor. RIGHT: lying on back in supine twist — one knee crossed over, both shoulders on mat, looking opposite direction. Key form: breathe into each stretch, 30+ seconds per position.
```

```
foam_rolling
Foam rolling quadriceps (quads). LEFT: lying face down, foam roller positioned under one quad, hands on floor for support. RIGHT: slowly rolling the foam roller up the quad muscle from knee to hip, applying controlled body weight. Key form: slow rolling, pause on tight spots, breathe.
```

```
hip_flexor_stretch
Kneeling hip flexor stretch (couch stretch). LEFT: in kneeling lunge — rear knee on mat, front foot flat on floor, upright posture. RIGHT: hips pressed forward and down, deepening hip flexor stretch, hands on front knee, upright torso. Key form: posterior pelvic tilt to deepen stretch, feel in front of rear thigh.
```

```
world_greatest_stretch
World's greatest stretch. LEFT: in deep lunge, right foot forward, left knee near floor, right hand on floor beside foot. RIGHT: thoracic rotation — left arm reaching straight up to sky, eyes following hand, chest opening. Key form: elbow to floor first, then rotate open.
```

```
cat_cow
Cat-cow spinal mobility on hands and knees. LEFT: cow pose — spine arched downward, chest open, head up, tailbone up (neutral/extended spine). RIGHT: cat pose — spine rounded upward like angry cat, chin tucked, tailbone tucked, full spinal flexion. Key form: move with breath, smooth transition.
```

---

# НОВЫЕ УПРАЖНЕНИЯ (расширение каталога)

### 🦵 НОГИ / ПРИСЕДАНИЯ (новые)

```
bodyweight_squat
Bodyweight air squat. LEFT: standing tall, feet shoulder-width, arms forward for balance. RIGHT: deep squat, thighs parallel to floor, chest up, knees tracking over toes. Key form: sit back and down, heels down.
```

```
prisoner_squat
Prisoner squat with hands behind the head. LEFT: standing, hands clasped behind head, elbows wide, chest up. RIGHT: deep squat, elbows still wide, torso upright, thighs parallel. Key form: elbows wide, chest proud, upright torso.
```

```
wall_sit_march
Wall sit with marching foot lifts. LEFT: wall sit hold, both feet flat, thighs parallel, back on wall. RIGHT: same wall sit but one foot lifted a few inches off the floor, hips level. Key form: hold 90° knees, lift feet without shifting the torso.
```

```
calf_raise
Standing bodyweight calf raise. LEFT: standing flat, feet together, heels down. RIGHT: up on the balls of the feet, heels raised high, calves contracted. Key form: rise fully, pause at top, slow lower.
```

```
kettlebell_goblet_squat
Kettlebell goblet squat holding the bell by the horns. LEFT: standing tall, kettlebell held at the chest by the horns. RIGHT: deep squat, thighs parallel, elbows inside the knees, torso upright. Key form: chest tall, full depth, drive through heels.
```

---

### 🍑 ТЯГА / ЯГОДИЦЫ (новые)

```
single_leg_glute_bridge
Single-leg glute bridge on the floor. LEFT: lying on back, one knee bent foot flat, other leg extended straight, hips down. RIGHT: hips driven high on one leg, body straight from shoulder to extended foot, hips level. Key form: drive through heel, keep hips even.
```

```
bodyweight_good_morning
Bodyweight good morning hip hinge. LEFT: standing tall, hands crossed on chest, soft knees. RIGHT: torso hinged forward to near parallel with the floor, hips pushed back, flat spine. Key form: hips travel back, flat long spine, hinge not squat.
```

```
hip_hinge_wall
Wall hip hinge drill tapping the wall with the hips. LEFT: standing just in front of a wall, tall posture, soft knees. RIGHT: hips pushed straight back tapping the wall, torso hinged forward, flat back. Key form: hips reach back to wall, not a squat.
```

```
prone_leg_curl
Prone bodyweight leg curl on the floor. LEFT: lying face down, legs straight, hips pressed down. RIGHT: heels curled up toward the glutes, hamstrings contracted, hips staying flat. Key form: pelvis stays down, controlled curl.
```

```
glute_kickback
Quadruped glute kickback on hands and knees. LEFT: on hands and knees, flat back, one knee bent under hip. RIGHT: same leg driven up and back, sole of foot toward ceiling, thigh in line with torso. Key form: flat back, move only from the hip.
```

```
fire_hydrant
Fire hydrant hip abduction on hands and knees. LEFT: on hands and knees, neutral spine, knees under hips. RIGHT: one bent knee lifted out to the side, thigh near parallel to floor, torso stable. Key form: no torso rotation, open at the hip.
```

```
kettlebell_clean
Kettlebell clean to the rack position. LEFT: hinge position, kettlebell hanging between the legs, hips back. RIGHT: kettlebell racked at the shoulder, elbow tucked, bell resting on the forearm, standing tall. Key form: hip drive, keep the bell close.
```

---

### 🦶 ОДНА НОГА (новые)

```
cossack_squat
Cossack squat, deep lateral squat. LEFT: very wide stance, standing tall, toes slightly out. RIGHT: deep squat over one bent leg, other leg fully straight with heel down and toes up, torso upright. Key form: sit deep on one side, opposite leg straight.
```

```
split_squat
Bodyweight split squat, stationary lunge stance. LEFT: split stance, one foot forward, standing tall, torso upright. RIGHT: lowered straight down, back knee near the floor, front thigh parallel, torso upright. Key form: drop straight down, front shin vertical.
```

```
jump_lunge
Explosive jumping lunge switching legs midair. LEFT: bottom of a lunge, back knee low, arms loaded, about to jump. RIGHT: airborne at peak, legs switching positions midair, arms driving. Key form: soft landing, explode straight up, control each rep.
```

---

### 💪 ГРУДЬ / ГОРИЗОНТАЛЬНЫЙ ЖИМ (новые)

```
knee_pushup
Kneeling push-up. LEFT: kneeling plank, hands slightly wider than shoulders, arms extended, straight line from knees to head. RIGHT: chest lowered toward floor, elbows at 45°. Key form: straight body from knees, full chest range.
```

```
pseudo_planche_pushup
Pseudo planche push-up leaning forward. LEFT: push-up position, hands near the waist, fingers pointing back, shoulders leaned forward past the hands. RIGHT: lowered with the forward lean maintained, elbows in. Key form: shoulders stay ahead of hands, fingers back.
```

```
archer_pushup
Archer push-up shifting to one arm. LEFT: wide push-up position, arms extended, hands far apart. RIGHT: chest lowered toward one bent arm, opposite arm straight and extended out to the side. Key form: one arm works, the other stays straight.
```

```
clap_pushup
Explosive clap push-up. LEFT: bottom of a push-up, chest near floor, elbows bent. RIGHT: airborne with hands clapped together off the floor, body still rigid. Key form: explode up, clap, land soft on bent elbows.
```

```
db_bench_press
Dumbbell bench press on a flat bench. LEFT: lying on bench, dumbbells at chest level, elbows down and out. RIGHT: dumbbells pressed up over the chest, arms extended, dumbbells close together. Key form: shoulder blades retracted, deep stretch at bottom.
```

```
incline_db_press
Incline dumbbell press on a 30-45° bench. LEFT: reclined on incline bench, dumbbells at upper-chest level, elbows down. RIGHT: dumbbells pressed up over the collarbones, arms extended. Key form: press slightly back, upper-chest focus.
```

```
machine_chest_press
Machine chest press seated. LEFT: seated at machine, handles at mid-chest, elbows back. RIGHT: handles pressed forward, arms extended, chest contracted. Key form: seat set to mid-chest, controlled return.
```

```
svend_press
Svend press squeezing two plates together. LEFT: standing, two plates pressed together at the chest, elbows in. RIGHT: plates pressed straight out in front, arms extended, still squeezed together. Key form: crush the plates together throughout.
```

---

### 🏋️ СПИНА / ГОРИЗОНТАЛЬНАЯ ТЯГА (новые)

```
pendlay_row
Pendlay row, barbell from the floor. LEFT: hinged over, torso parallel to floor, arms straight gripping the bar on the ground. RIGHT: bar pulled explosively to the lower chest, elbows back, torso staying parallel. Key form: reset on floor each rep, flat back.
```

```
seal_row
Seal row lying face down on a high bench. LEFT: lying chest-down on bench, arms hanging with weights below. RIGHT: weights rowed to the ribs, elbows driven back, chest staying on the bench. Key form: chest stays down, no momentum.
```

```
meadows_row
Landmine single-arm row with a barbell anchored in a corner. LEFT: staggered stance, hinged over, one hand gripping the loaded barbell end, arm extended. RIGHT: bar end rowed up along the side, elbow high, lat contracted. Key form: staggered stance, pull along the body.
```

```
renegade_row
Renegade row from a plank on dumbbells. LEFT: high plank on two dumbbells, feet wide, hips square. RIGHT: one dumbbell rowed to the ribs, elbow high, balancing on the other hand, hips staying level. Key form: no hip twist, wide base.
```

```
reverse_fly
Reverse fly bent over with dumbbells. LEFT: hinged forward, dumbbells hanging below the chest, slight elbow bend. RIGHT: dumbbells raised out to the sides to shoulder level, shoulder blades squeezed. Key form: lead with elbows, flat back.
```

```
straight_arm_pulldown
Straight-arm cable pulldown. LEFT: standing at a high cable, arms extended overhead gripping the bar, slight forward lean. RIGHT: bar swept down to the thighs, arms nearly straight, lats contracted. Key form: minimal elbow bend, drive with the lats.
```

```
kettlebell_high_pull
Kettlebell high pull. LEFT: hinge position, kettlebell hanging between the legs. RIGHT: bell pulled up to chest height with the elbow high and back, hips fully extended. Key form: hip drive powers it, elbow leads high.
```

```
towel_row
Towel row anchored around a sturdy pole or banister, no equipment needed. LEFT: leaning back with straight arms gripping a towel looped around the anchor, feet braced. RIGHT: body pulled up toward the anchor, elbows driven back, chest to the towel. Key form: keep body rigid, pull with the back not the arms.
```

```
doorway_row
Doorway row using a door frame for resistance, bodyweight only. LEFT: gripping the door frame edges with both hands, arms extended, feet braced against the base of the door, body leaning back. RIGHT: chest pulled toward the frame, elbows driven back and close to the body. Key form: brace the door firmly, control the lean angle.
```

```
table_row
Underneath-table row using a sturdy table or desk, bodyweight only. LEFT: lying under the table gripping the edge with both hands, arms extended, body straight. RIGHT: chest pulled up to the table edge, elbows bent and pulled back, body rigid. Key form: keep the body in a straight line, no sagging hips.
```

```
prone_y_raise
Prone Y-raise lying face down on the floor, no equipment. LEFT: lying face down, arms extended overhead in a Y shape, forehead resting down. RIGHT: arms lifted off the floor to shoulder height, thumbs up, shoulder blades pulled down and together. Key form: lift with the upper back, not the lower back.
```

```
prone_w_raise
Prone W-raise (reverse snow angel) lying face down on the floor, no equipment. LEFT: lying face down, elbows bent at sides forming a W shape, forehead resting down. RIGHT: arms and elbows lifted off the floor, shoulder blades squeezed together, elbows staying bent. Key form: squeeze shoulder blades, keep neck neutral.
```

---

### 🏆 ПЛЕЧИ / ВЕРТИКАЛЬНЫЙ ЖИМ (новые)

```
wall_walk
Gymnastics wall walk. LEFT: push-up position with feet at the base of a wall, hands far out. RIGHT: near-handstand against the wall, feet walked up high, hands walked in close, body vertical. Key form: braced core, move hands and feet together.
```

```
pike_pushup_elevated
Elevated pike push-up with feet on a low box. LEFT: pike position, feet on box, hips high, arms extended, shoulders over hands. RIGHT: head lowered between the hands toward the floor, elbows bent. Key form: hips stay high, shoulders stack over hands.
```

```
kettlebell_snatch
One-arm kettlebell snatch overhead. LEFT: hinge, kettlebell hanging in one hand between the legs. RIGHT: kettlebell locked out overhead, arm straight, bell resting on the back of the forearm, body upright. Key form: hip drive, punch up through the bell.
```

---

### 🔝 ТУРНИК / ВЕРТИКАЛЬНАЯ ТЯГА (новые)

```
scapular_pullup
Scapular pull-up on a bar, straight arms. LEFT: dead hang from bar, arms straight, shoulders shrugged up toward ears. RIGHT: shoulders pulled down and back, body lifted slightly, arms still straight, chest raised. Key form: no elbow bend, only scapular movement.
```

```
neutral_grip_pullup
Neutral-grip pull-up on parallel handles. LEFT: dead hang, palms facing each other, arms extended. RIGHT: chest pulled up to the handles, elbows down and close to the body. Key form: elbows drive down, chest to the bar.
```

```
negative_pullup
Negative pull-up eccentric lowering. LEFT: at the top of a pull-up, chin above the bar, arms bent. RIGHT: slowly lowering, arms extending, body descending under control toward a full hang. Key form: 3-5 second slow descent, fight gravity.
```

---

### 💪 БИЦЕПС (новые)

```
preacher_curl
Preacher curl on an angled bench. LEFT: seated at preacher bench, arms extended down the pad holding the weight. RIGHT: weight curled up to full bicep contraction, arms still on the pad. Key form: arms stay glued to the pad, no swinging.
```

```
spider_curl
Spider curl lying chest-down on an incline bench. LEFT: chest on incline bench, arms hanging straight down holding dumbbells, palms forward. RIGHT: dumbbells curled up, upper arms vertical and still, biceps peaked. Key form: upper arms stay vertical, no swing.
```

```
zottman_curl
Zottman curl with dumbbells. LEFT: dumbbells curled up at the shoulders, palms up. RIGHT: at the shoulders with wrists rotated so palms face down, about to lower. Key form: palms up going up, palms down coming down.
```

```
chin_up_hold
Flexed-arm hang at the top of a chin-up. LEFT: hanging with chin over the bar, underhand grip, elbows fully bent, chest up. RIGHT: same held position sustained isometrically, biceps and lats contracted. Key form: chin over bar, elbows fully bent, hold steady.
```

---

### 💪 ТРИЦЕПС (новые)

```
bench_dip
Bench dip using a bench behind the body. LEFT: hands on bench edge behind hips, arms straight, hips off the bench, legs extended. RIGHT: elbows bent straight back to about 90°, hips lowered toward the floor. Key form: elbows point back, chest stays up.
```

```
diamond_pushup_knee
Kneeling diamond push-up. LEFT: kneeling plank, hands together in a diamond under chest, arms extended, body straight from knees. RIGHT: chest lowered to hands, elbows tucked back along the ribs. Key form: elbows brush ribs, hands form a diamond.
```

```
wall_tricep_extension
Standing wall triceps push-away with hands close together on a wall. LEFT: leaning toward wall, hands close at chest height, arms bent, forehead near wall. RIGHT: arms fully extended, body pushed back from wall, elbows tucked in. Key form: elbows stay tucked, only elbows bend.
```

```
overhead_dumbbell_extension
Overhead dumbbell triceps extension. LEFT: one dumbbell held overhead with both hands, arms fully extended, elbows in. RIGHT: dumbbell lowered behind the head, elbows bent and pointing forward. Key form: elbows tucked, only forearms move.
```

```
jm_press
JM press on a flat bench, close grip. LEFT: lying on bench, barbell held with close grip, arms extended over the chest. RIGHT: bar lowered toward the upper neck, elbows forward, forearms near the biceps. Key form: elbows travel forward, hybrid of press and skull crusher.
```

```
close_grip_pushup_feet_elevated
Decline close-grip push-up, feet elevated. LEFT: feet on a raised surface, hands close under the chest, arms extended, body straight and angled down. RIGHT: chest lowered, elbows tucked tight to the ribs. Key form: hands close, elbows back, feet elevated.
```

---

### 🧘 КОР (новые)

```
flutter_kick
Flutter kicks lying on the back. LEFT: lying on back, both legs straight and hovering low off the floor. RIGHT: legs alternating, one kicked slightly up and one slightly down, lower back pressed to floor. Key form: small quick kicks, low back stays down.
```

```
reverse_crunch
Reverse crunch on the floor. LEFT: lying on back, knees bent at tabletop, hips down, hands beside hips. RIGHT: pelvis curled up, hips lifted off floor, knees drawn toward chest. Key form: lift with pelvic curl, not momentum.
```

```
heel_touch
Heel touches crunching side to side. LEFT: lying on back, knees bent, shoulders slightly lifted, hand reaching toward one heel. RIGHT: crunched to the other side, opposite hand reaching that heel. Key form: shoulders stay lifted, crunch sideways.
```

```
plank_shoulder_tap
Plank shoulder tap in a high plank. LEFT: high plank, both hands on floor under shoulders, body straight. RIGHT: one hand lifted tapping the opposite shoulder, hips held square and level. Key form: no hip rock, keep hips square.
```

```
bird_dog
Bird dog on hands and knees. LEFT: on hands and knees, flat back, neutral spine. RIGHT: one arm reaching forward and the opposite leg extended straight back, body forming a line, back flat. Key form: no arching, reach long and stable.
```

```
plank_up_down
Up-down plank transitioning forearms to hands. LEFT: forearm plank, body straight. RIGHT: high plank on both hands, hips level. Key form: minimal hip rock, alternate lead arm.
```

```
side_plank_hip_dip
Side plank hip dip on a forearm. LEFT: forearm side plank, body in a straight diagonal, hip high. RIGHT: bottom hip lowered toward the floor without touching, obliques loaded. Key form: control the dip, drive hip high.
```

```
hollow_rock
Hollow rock on the back. LEFT: hollow hold, arms overhead, legs low, lower back pressed down, body in a slight banana curve. RIGHT: rocked forward on the hips maintaining the same rigid hollow shape. Key form: keep the banana shape, rock smoothly.
```

```
plank_reach
Plank arm reach in a high plank. LEFT: high plank, both hands down, body straight. RIGHT: one arm extended straight forward, hips square and level, body stable. Key form: hips stay square, reach long.
```

```
turkish_getup
Turkish get-up with a weight held overhead. LEFT: lying on the back, one arm pressing a kettlebell straight up. RIGHT: standing tall, weight locked out overhead, arm vertical, eyes on the weight. Key form: eyes on weight, arm stays vertical throughout.
```

---

### 🏃 КАРДИО (новые)

```
squat_thrust
Squat thrust burpee variation without push-up. LEFT: squat with hands on the floor, feet under the hips. RIGHT: feet jumped back into a plank position, body straight, arms extended. Key form: feet jump straight back, no push-up needed.
```

```
tuck_jump
Tuck jump pulling knees to chest. LEFT: slight dip, arms back, about to jump. RIGHT: airborne at peak with knees pulled up to the chest, torso upright. Key form: explode up, snap knees up, land soft.
```

```
bear_crawl
Bear crawl on hands and toes. LEFT: quadruped start, knees hovering just off the floor, flat back. RIGHT: crawling forward, opposite hand and foot advanced, hips low and level. Key form: knees hover, hips stay low, no rocking.
```

```
inchworm
Inchworm walkout. LEFT: standing hinged forward with hands on the floor near the feet, legs fairly straight. RIGHT: hands walked forward into a high plank, body extended straight. Key form: legs stay fairly straight, controlled walkout.
```

```
lateral_shuffle
Lateral shuffle in an athletic stance. LEFT: low athletic stance, knees bent, weight on one leg about to push off. RIGHT: shuffled sideways, feet apart, staying low, chest up. Key form: stay low, feet don't cross, push off trailing leg.
```

```
butt_kick
Butt kicks jogging in place. LEFT: standing tall, beginning the movement. RIGHT: one heel flicked up toward the glute, knee pointing down, on the ball of the opposite foot. Key form: heels to glutes, stay light and quick.
```

```
shadow_boxing
Shadow boxing in a boxing stance. LEFT: boxing stance, guard up, feet staggered. RIGHT: throwing a cross punch, torso rotated, rear foot pivoted, opposite hand guarding the chin. Key form: guard up, rotate through the hips, stay mobile.
```

```
jumping_jack_squat
Squat jack jumping feet wide into a squat. LEFT: standing with feet together, hands at chest. RIGHT: feet jumped out wide into a squat, hips back, chest up. Key form: land in a squat, chest up, steady rhythm.
```

```
sled_push
Sled push driving a loaded sled forward. LEFT: gripping the sled posts, body leaned forward, one leg driving. RIGHT: mid-drive, powerful stride, back leg fully extended, low forward lean. Key form: lean in, full leg drive each step.
```

```
medicine_ball_slam
Medicine ball slam. LEFT: standing tall, medicine ball raised overhead, body fully extended. RIGHT: ball slammed down to the floor, torso hinged forward, arms following through. Key form: full-body extension, slam hard through the core.
```

---

### 🧘 МОБИЛЬНОСТЬ И ВОССТАНОВЛЕНИЕ (новые)

```
downward_dog
Downward dog yoga pose. LEFT: hands and knees, toes tucked, preparing to lift. RIGHT: full inverted V shape, hips high, legs straight, heels reaching toward floor, spine long. Key form: hips up and back, long spine, chest toward thighs.
```

```
cobra_stretch
Cobra stretch lying face down. LEFT: lying face down, hands under shoulders, chest on the floor. RIGHT: chest lifted, gentle back extension, hips staying on the floor, shoulders down. Key form: lift through the chest, hips stay down.
```

```
childs_pose
Child's pose resting stretch. LEFT: kneeling upright, about to fold forward. RIGHT: hips sitting back on the heels, torso folded forward, arms reaching out front, forehead near the floor. Key form: sit back on heels, relax and breathe.
```

```
thoracic_rotation
Thoracic open-book rotation lying on the side. LEFT: lying on the side, knees bent, arms stacked out in front. RIGHT: top arm opened across the body toward the floor behind, chest rotated open, knees staying stacked. Key form: rotate through the upper back, knees pinned.
```

```
pigeon_stretch
Pigeon stretch hip opener. LEFT: front shin brought forward across the body, back leg extended straight behind, torso upright. RIGHT: torso folded forward over the front shin, hips square and lowered. Key form: hips square, sink and fold forward.
```

```
shoulder_dislocate
Shoulder pass-through with a band. LEFT: standing, band held wide in front with straight arms. RIGHT: band passed overhead and moving behind the back, arms staying straight. Key form: arms stay straight, grip wide enough to be pain-free.
```

```
neck_stretch
Neck stretch seated or standing. LEFT: sitting tall, head neutral, shoulders relaxed. RIGHT: head gently tilted toward one shoulder, opposite side of the neck stretched, shoulders down. Key form: slow and gentle, never force.
```

```
ankle_mobility
Ankle mobility knee-to-wall rock. LEFT: half-kneeling facing a wall, front foot planted a few inches away. RIGHT: front knee driven forward to touch the wall, heel staying flat on the floor. Key form: heel stays down, knee tracks over the toes.
```

```
hip_circle
Standing hip circle mobility drill. LEFT: standing tall, one knee lifted in front. RIGHT: same knee circled out to the side, hip opening, torso stable. Key form: big smooth circles, stable torso.
```

```
leg_swing
Dynamic leg swings holding a support. LEFT: standing tall holding a wall, leg at the back of the swing. RIGHT: same leg swung forward to the front, torso staying upright. Key form: torso stays tall, leg swings freely.
```

---

## НЕЙМИНГ ФАЙЛОВ
При сохранении называй точно так:

| Тренер | Формат |
|--------|--------|
| Mia | `coach_mia_{exercise_id}.jpg` |
| Artur | `coach_artur_{exercise_id}.jpg` |
| Vtempe | `coach_vtempe_{exercise_id}.jpg` |

Папка: `ui/src/commonMain/composeResources/drawable/`

Полный список ID упражнений из файла → совпадает с названием в скобках в каждом разделе выше.

---

## ПОСЛЕ ГЕНЕРАЦИИ
Запусти скрипт который автоматически обновит CoachVisuals.kt:
```bash
python scripts/update_coach_visuals.py
```
Затем проверь что компилируется:
```bash
.\gradlew.bat :ui:compileDebugKotlinAndroid
```
