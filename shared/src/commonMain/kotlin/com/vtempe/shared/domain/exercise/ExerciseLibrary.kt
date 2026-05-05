package com.vtempe.shared.domain.exercise

object ExerciseLibrary {

    private val exercises: List<ExerciseDefinition> = listOf(
        ExerciseDefinition(
            id = "squat",
            aliases = setOf("back_squat", "barbell_squat"),
            name = LocalizedText("Squat", "Приседания"),
            muscleGroups = listOf("quads", "glutes", "hamstrings"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Start with a weight you can squat to parallel with a flat back.",
                "Начни с веса, при котором можешь приседать до параллели с ровной спиной."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The squat builds quad and glute strength through a full range of motion under load.",
                    "Приседание развивает силу квадрицепсов и ягодиц через полную амплитуду движения под нагрузкой."
                ),
                focusEn = listOf("Quads", "Glutes", "Core"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Кор"),
                keyCue = LocalizedText(
                    "Chest up, knees track over toes, drive through the whole foot.",
                    "Грудь вверх, колени над носками, толкай через всю стопу."
                ),
                stepsEn = listOf(
                    "Set the bar on your upper traps, feet shoulder-width apart.",
                    "Brace your core and take a deep breath.",
                    "Break at the hips and knees simultaneously, descend to parallel.",
                    "Drive upward, pushing the floor away."
                ),
                stepsRu = listOf(
                    "Положи гриф на верхние трапеции, ноги на ширине плеч.",
                    "Напряги кор и сделай глубокий вдох.",
                    "Одновременно сгибай бёдра и колени, опустись до параллели.",
                    "Толкни пол вниз и поднимись."
                ),
                defaultRestSeconds = 120
            )
        ),
        ExerciseDefinition(
            id = "bench",
            aliases = setOf("bench_press", "barbell_bench"),
            name = LocalizedText("Bench Press", "Жим лёжа"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Choose a weight you can press 8–10 times with full range and stable shoulders.",
                "Выбери вес, с которым можешь выжать 8–10 раз с полной амплитудой."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The bench press develops horizontal pushing strength in the chest, shoulders, and triceps.",
                    "Жим лёжа развивает горизонтальную толкательную силу груди, плеч и трицепсов."
                ),
                focusEn = listOf("Chest", "Triceps", "Front delts"),
                focusRu = listOf("Грудь", "Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Retract and depress your shoulder blades, drive the bar in a slight arc.",
                    "Сведи и опусти лопатки, толкай штангу по лёгкой дуге."
                ),
                stepsEn = listOf(
                    "Lie on the bench with eyes under the bar.",
                    "Grip slightly wider than shoulder width, unrack.",
                    "Lower the bar to mid-chest with control.",
                    "Press back to lockout."
                ),
                stepsRu = listOf(
                    "Ляг на скамью, глаза под грифом.",
                    "Возьмись чуть шире плеч, снимь штангу.",
                    "Медленно опусти штангу к середине груди.",
                    "Выжми до полного выпрямления рук."
                ),
                defaultRestSeconds = 120
            )
        ),
        ExerciseDefinition(
            id = "deadlift",
            aliases = setOf("conventional_deadlift"),
            name = LocalizedText("Deadlift", "Становая тяга"),
            muscleGroups = listOf("hamstrings", "glutes", "lower_back", "traps"),
            difficulty = 4,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Start lighter than you think — technique must be perfect before adding load.",
                "Начни легче, чем кажется нужным — техника должна быть идеальной до увеличения веса."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The deadlift is a full-body hinge that builds posterior chain strength and total-body power.",
                    "Становая тяга — базовое шарнирное движение, развивающее заднюю цепь и общую силу."
                ),
                focusEn = listOf("Hamstrings", "Glutes", "Lower back", "Traps"),
                focusRu = listOf("Бицепс бедра", "Ягодицы", "Поясница", "Трапеции"),
                keyCue = LocalizedText(
                    "Push the floor away, keep the bar close to your legs throughout the pull.",
                    "Толкай пол от себя, держи гриф у ног на протяжении всего подъёма."
                ),
                stepsEn = listOf(
                    "Stand with mid-foot under the bar, hip-width stance.",
                    "Hinge to grip the bar just outside your legs.",
                    "Take the slack out: lift chest, brace hard.",
                    "Drive through the floor until lockout, hips and shoulders rise together."
                ),
                stepsRu = listOf(
                    "Встань с серединой стопы под грифом, ноги на ширине бёдер.",
                    "Наклонись и возьмись за гриф чуть снаружи ног.",
                    "Убери слабину: подними грудь, максимально напряги тело.",
                    "Толкни пол до полного выпрямления, бёдра и плечи поднимаются вместе."
                ),
                defaultRestSeconds = 150
            )
        ),
        ExerciseDefinition(
            id = "ohp",
            aliases = setOf("overhead_press", "press", "military_press"),
            name = LocalizedText("Overhead Press", "Жим стоя"),
            muscleGroups = listOf("shoulders", "triceps", "upper_traps"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.OVERHEAD,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Start light — most people are surprised how quickly fatigue builds overhead.",
                "Начни с малого веса — усталость при жиме над головой нарастает быстро."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The overhead press builds vertical pushing strength and shoulder stability.",
                    "Жим стоя развивает вертикальную толкательную силу и стабильность плеч."
                ),
                focusEn = listOf("Shoulders", "Triceps", "Core"),
                focusRu = listOf("Плечи", "Трицепс", "Кор"),
                keyCue = LocalizedText(
                    "Press the bar overhead in a straight line, finish with ears between arms.",
                    "Жми гриф прямо вверх, в конечной точке уши между руками."
                ),
                stepsEn = listOf(
                    "Hold the bar at shoulder height, elbows slightly in front.",
                    "Brace your core and glutes.",
                    "Press straight up, move your head back then forward to clear the bar.",
                    "Lock out overhead."
                ),
                stepsRu = listOf(
                    "Удержи гриф на уровне плеч, локти немного впереди.",
                    "Напряги кор и ягодицы.",
                    "Жми прямо вверх, слегка отклони голову назад и верни.",
                    "Зафиксируй руки в верхней точке."
                ),
                defaultRestSeconds = 120
            )
        ),
        ExerciseDefinition(
            id = "row",
            aliases = setOf("bent_over_row", "barbell_row", "db_row", "dumbbell_row"),
            name = LocalizedText("Row", "Тяга"),
            muscleGroups = listOf("lats", "rhomboids", "biceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Pick a weight you can row without swinging your torso.",
                "Возьми вес, при котором не приходится раскачивать корпус."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The row builds upper back thickness and horizontal pulling strength.",
                    "Тяга развивает толщину верхней спины и горизонтальную тянущую силу."
                ),
                focusEn = listOf("Lats", "Rhomboids", "Rear delts"),
                focusRu = listOf("Широчайшие", "Ромбовидные", "Задние дельты"),
                keyCue = LocalizedText(
                    "Drive elbows back, not up; squeeze the shoulder blades together at the top.",
                    "Тяни локти назад, а не вверх; своди лопатки в верхней точке."
                ),
                stepsEn = listOf(
                    "Hinge forward with a neutral spine, bar hanging at arm's length.",
                    "Retract the shoulder blades.",
                    "Pull the bar to lower chest/upper abs.",
                    "Lower under control."
                ),
                stepsRu = listOf(
                    "Наклонись вперёд с нейтральным позвоночником, гриф на вытянутых руках.",
                    "Сведи лопатки.",
                    "Подтяни гриф к нижней части груди/животу.",
                    "Опусти контролируемо."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "pullup",
            aliases = setOf("pull_up", "pullups", "chin_up", "chinup"),
            name = LocalizedText("Pull-up", "Подтягивания"),
            muscleGroups = listOf("lats", "biceps", "rear_delts"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "If you can't do 3 clean reps, use a band or assisted machine.",
                "Если не можешь сделать 3 чистых повтора — используй резину или ассист-тренажёр."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The pull-up develops vertical pulling strength and lat width.",
                    "Подтягивания развивают вертикальную тянущую силу и ширину широчайших."
                ),
                focusEn = listOf("Lats", "Biceps", "Core"),
                focusRu = listOf("Широчайшие", "Бицепс", "Кор"),
                keyCue = LocalizedText(
                    "Pull elbows down to your hips, not toward your ears.",
                    "Тяни локти вниз к бёдрам, а не к ушам."
                ),
                stepsEn = listOf(
                    "Dead hang with hands slightly wider than shoulders.",
                    "Depress the shoulder blades.",
                    "Pull until chin clears the bar.",
                    "Lower with control to a full hang."
                ),
                stepsRu = listOf(
                    "Вис на прямых руках, хват чуть шире плеч.",
                    "Опусти лопатки вниз.",
                    "Подтянись до уровня подбородка над перекладиной.",
                    "Опустись контролируемо в полный вис."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "lunge",
            aliases = setOf("walking_lunge", "forward_lunge"),
            name = LocalizedText("Lunge", "Выпады"),
            muscleGroups = listOf("quads", "glutes", "hamstrings"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Start bodyweight, add dumbbells when you can do 12 reps each leg with good balance.",
                "Начни без веса, добавь гантели когда можешь делать 12 повторов на каждую ногу с балансом."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Lunges build single-leg strength, balance, and quad/glute development.",
                    "Выпады развивают силу каждой ноги по отдельности, баланс и квадрицепсы с ягодицами."
                ),
                focusEn = listOf("Quads", "Glutes", "Balance"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Равновесие"),
                keyCue = LocalizedText(
                    "Front knee stays over front foot; back knee drops straight down.",
                    "Переднее колено над передней стопой; заднее колено опускается прямо вниз."
                ),
                stepsEn = listOf(
                    "Stand tall, step forward with one leg.",
                    "Lower the back knee toward the floor.",
                    "Push off the front foot to return to standing.",
                    "Alternate legs each rep."
                ),
                stepsRu = listOf(
                    "Встань прямо, сделай шаг вперёд одной ногой.",
                    "Опусти заднее колено к полу.",
                    "Оттолкнись передней ногой и вернись в стойку.",
                    "Чередуй ноги в каждом повторе."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "dip",
            aliases = setOf("parallel_bar_dip", "dips"),
            name = LocalizedText("Dip", "Отжимания на брусьях"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Use a band for assistance if you can't do 5 clean reps.",
                "Используй резину если не можешь сделать 5 чистых повторов."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Dips build chest, tricep, and front delt strength through a deep range of motion.",
                    "Отжимания на брусьях развивают грудь, трицепс и передние дельты через глубокую амплитуду."
                ),
                focusEn = listOf("Chest", "Triceps", "Front delts"),
                focusRu = listOf("Грудь", "Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Lean forward slightly for chest emphasis; keep torso upright for tricep focus.",
                    "Наклон вперёд — акцент на грудь; корпус вертикально — акцент на трицепс."
                ),
                stepsEn = listOf(
                    "Support your weight on the parallel bars with straight arms.",
                    "Lower by bending elbows until upper arm is parallel to floor.",
                    "Push back up to lockout."
                ),
                stepsRu = listOf(
                    "Упрись в брусья на прямых руках.",
                    "Опустись, сгибая руки, пока плечо не станет параллельно полу.",
                    "Выжмись до полного выпрямления рук."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "pushup",
            aliases = setOf("push_up", "push_ups"),
            name = LocalizedText("Push-up", "Отжимания"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "If regular push-ups are too hard, start from your knees.",
                "Если стандартные отжимания слишком сложны, начни с колен."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The push-up is a fundamental upper-body pressing movement requiring no equipment.",
                    "Отжимание — базовое упражнение для верхней части тела, не требующее оборудования."
                ),
                focusEn = listOf("Chest", "Triceps", "Core"),
                focusRu = listOf("Грудь", "Трицепс", "Кор"),
                keyCue = LocalizedText(
                    "Body forms a straight line from head to heels; lower chest to floor.",
                    "Тело — прямая линия от головы до пяток; опускай грудь к полу."
                ),
                stepsEn = listOf(
                    "Place hands slightly wider than shoulders, body straight.",
                    "Lower chest toward the floor, elbows at 45°.",
                    "Push back to start."
                ),
                stepsRu = listOf(
                    "Поставь руки чуть шире плеч, тело — прямая.",
                    "Опусти грудь к полу, локти под углом 45°.",
                    "Вернись в исходное положение."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "curl",
            aliases = setOf("bicep_curl", "biceps_curl", "biceps_curls", "dumbbell_curl"),
            name = LocalizedText("Biceps Curl", "Сгибание рук"),
            muscleGroups = listOf("biceps", "brachialis"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Pick a weight where the last 2–3 reps are challenging but form stays strict.",
                "Вес, при котором последние 2–3 повтора трудны, но техника остаётся строгой."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The biceps curl isolates and builds elbow flexor strength.",
                    "Сгибание рук изолирует и развивает силу сгибателей локтя."
                ),
                focusEn = listOf("Biceps", "Brachialis"),
                focusRu = listOf("Бицепс", "Брахиалис"),
                keyCue = LocalizedText(
                    "Keep elbows glued to your sides; don't swing the weight up.",
                    "Держи локти прижатыми к бокам; не раскачивай вес."
                ),
                stepsEn = listOf(
                    "Stand with dumbbells at your sides, palms forward.",
                    "Curl the weights up by bending at the elbow.",
                    "Lower under control."
                ),
                stepsRu = listOf(
                    "Встань с гантелями вдоль тела, ладони вперёд.",
                    "Подними гантели, сгибая руки в локтях.",
                    "Медленно опусти."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "tricep_extension",
            aliases = setOf("triceps_extension", "skull_crusher", "overhead_tricep"),
            name = LocalizedText("Triceps Extension", "Разгибание рук"),
            muscleGroups = listOf("triceps"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Use a weight where you can fully extend the arm without compensating at the shoulder.",
                "Вес, при котором можно полностью выпрямить руку без компенсации в плече."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Triceps extension isolates the long head of the triceps through overhead stretch.",
                    "Разгибание рук изолирует длинную головку трицепса через растяжение над головой."
                ),
                focusEn = listOf("Triceps"),
                focusRu = listOf("Трицепс"),
                keyCue = LocalizedText(
                    "Keep upper arms still and close to your head throughout the movement.",
                    "Держи верхнюю часть рук неподвижной и близко к голове на протяжении всего движения."
                ),
                stepsEn = listOf(
                    "Hold a dumbbell overhead with both hands.",
                    "Lower behind your head by bending elbows only.",
                    "Extend back to the start."
                ),
                stepsRu = listOf(
                    "Держи гантель над головой обеими руками.",
                    "Опусти за голову, сгибая только локти.",
                    "Выпрями руки в исходное положение."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "plank",
            aliases = setOf("plank_hold"),
            name = LocalizedText("Plank", "Планка"),
            muscleGroups = listOf("core", "shoulders"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Aim for 30–60 seconds with a perfectly neutral spine before progressing.",
                "Цель — 30–60 секунд с идеально нейтральным позвоночником перед усложнением."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The plank builds core anti-extension endurance and shoulder stability.",
                    "Планка развивает выносливость кора в сопротивлении разгибанию и стабильность плеч."
                ),
                focusEn = listOf("Core", "Shoulders", "Glutes"),
                focusRu = listOf("Кор", "Плечи", "Ягодицы"),
                keyCue = LocalizedText(
                    "Squeeze glutes and brace abs as if bracing for a punch.",
                    "Напряги ягодицы и кор, как будто ждёшь удар в живот."
                ),
                stepsEn = listOf(
                    "Support on forearms and toes, elbows below shoulders.",
                    "Create a straight line from head to heels.",
                    "Hold the position without letting hips sag or rise."
                ),
                stepsRu = listOf(
                    "Опора на предплечья и носки, локти под плечами.",
                    "Создай прямую линию от головы до пяток.",
                    "Держи позицию, не позволяя бёдрам опускаться или подниматься."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "hip_thrust",
            aliases = setOf("hipthrust", "hip_thrusts", "glute_bridge"),
            name = LocalizedText("Hip Thrust", "Ягодичный мостик"),
            muscleGroups = listOf("glutes", "hamstrings"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Set the bench at a comfortable height for your shoulder blades.",
                "Поставь скамью на комфортную высоту для лопаток."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The hip thrust maximally loads the glutes at full extension, building shape and strength.",
                    "Ягодичный мостик максимально нагружает ягодицы в полном разгибании, формируя их форму и силу."
                ),
                focusEn = listOf("Glutes", "Hamstrings"),
                focusRu = listOf("Ягодицы", "Бицепс бедра"),
                keyCue = LocalizedText(
                    "Squeeze hard at the top — body forms a straight line from knees to shoulders.",
                    "Сильно сожми ягодицы вверху — тело образует прямую линию от колен до плеч."
                ),
                stepsEn = listOf(
                    "Rest upper back on bench, bar over hips, feet flat.",
                    "Drive hips upward by squeezing glutes.",
                    "Hold at the top for one second.",
                    "Lower under control."
                ),
                stepsRu = listOf(
                    "Опри верхнюю часть спины на скамью, гриф на бёдрах, стопы на полу.",
                    "Поднимай бёдра, сжимая ягодицы.",
                    "Задержись на секунду в верхней точке.",
                    "Медленно опусти."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "leg_press",
            aliases = setOf("legpress"),
            name = LocalizedText("Leg Press", "Жим ногами"),
            muscleGroups = listOf("quads", "glutes"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Do not lock out knees fully at the top; keep a slight bend.",
                "Не выпрямляй колени полностью в верхней точке; оставляй лёгкий сгиб."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The leg press builds quad and glute strength in a machine-guided path.",
                    "Жим ногами развивает квадрицепсы и ягодицы по траектории, заданной тренажёром."
                ),
                focusEn = listOf("Quads", "Glutes"),
                focusRu = listOf("Квадрицепсы", "Ягодицы"),
                keyCue = LocalizedText(
                    "Keep your lower back pressed into the pad throughout the movement.",
                    "Держи поясницу прижатой к спинке на протяжении всего движения."
                ),
                stepsEn = listOf(
                    "Sit in the machine, feet shoulder-width on the platform.",
                    "Lower the platform until knees reach 90°.",
                    "Press back to the start."
                ),
                stepsRu = listOf(
                    "Сядь в тренажёр, ноги на ширине плеч на платформе.",
                    "Опусти платформу до угла 90° в коленях.",
                    "Выжми обратно."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "run",
            aliases = setOf("running", "jog", "jogging", "treadmill"),
            name = LocalizedText("Run", "Бег"),
            muscleGroups = listOf("legs", "cardiovascular"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_MINUTES,
            calibrationHint = LocalizedText(
                "Start at a conversational pace — you should be able to speak in short sentences.",
                "Начни в темпе разговора — ты должен быть в состоянии говорить короткими фразами."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Running improves cardiovascular capacity and metabolic conditioning.",
                    "Бег улучшает сердечно-сосудистую выносливость и метаболическую форму."
                ),
                focusEn = listOf("Cardiovascular", "Legs", "Endurance"),
                focusRu = listOf("Кардио", "Ноги", "Выносливость"),
                keyCue = LocalizedText(
                    "Land with foot under your hips, maintain upright posture.",
                    "Приземляйся стопой под бёдрами, держи корпус вертикально."
                ),
                stepsEn = listOf(
                    "Warm up with a 2-minute walk.",
                    "Increase to your target pace.",
                    "Maintain even breathing throughout.",
                    "Cool down with a 2-minute walk."
                ),
                stepsRu = listOf(
                    "Разомнись 2-минутной ходьбой.",
                    "Перейди к целевому темпу.",
                    "Поддерживай ровное дыхание.",
                    "Заверши 2-минутной ходьбой."
                ),
                defaultRestSeconds = 0
            )
        ),
        ExerciseDefinition(
            id = "bike",
            aliases = setOf("cycling", "bicycle", "stationary_bike"),
            name = LocalizedText("Bike", "Велосипед"),
            muscleGroups = listOf("legs", "cardiovascular"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Adjust seat so the knee has a slight bend at full pedal extension.",
                "Отрегулируй сиденье так, чтобы при полном вытяжении педали колено было слегка согнуто."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Cycling provides low-impact cardiovascular conditioning.",
                    "Велоезда обеспечивает кардиотренировку с низкой нагрузкой на суставы."
                ),
                focusEn = listOf("Cardiovascular", "Quads", "Glutes"),
                focusRu = listOf("Кардио", "Квадрицепсы", "Ягодицы"),
                keyCue = LocalizedText(
                    "Push through the whole pedal stroke, not just the downstroke.",
                    "Толкай педаль по всей окружности, а не только вниз."
                ),
                stepsEn = listOf(
                    "Set resistance to a moderate challenge.",
                    "Pedal at a steady cadence of 70–90 RPM.",
                    "Finish with 3 minutes at reduced resistance."
                ),
                stepsRu = listOf(
                    "Установи умеренное сопротивление.",
                    "Крути педали с каденсом 70–90 об/мин.",
                    "Заверши 3 минутами с пониженным сопротивлением."
                ),
                defaultRestSeconds = 0
            )
        ),
        ExerciseDefinition(
            id = "yoga",
            aliases = setOf("stretching", "flexibility"),
            name = LocalizedText("Yoga", "Йога"),
            muscleGroups = listOf("flexibility", "core"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Never push into pain — work to the edge of comfortable tension.",
                "Никогда не доводи до боли — работай на границе комфортного натяжения."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Yoga develops flexibility, balance, and mindful body awareness.",
                    "Йога развивает гибкость, баланс и осознанность тела."
                ),
                focusEn = listOf("Flexibility", "Balance", "Recovery"),
                focusRu = listOf("Гибкость", "Равновесие", "Восстановление"),
                keyCue = LocalizedText(
                    "Breathe into each stretch; hold for at least 3 full breaths.",
                    "Дыши в каждую растяжку; держи не менее 3 полных вдохов-выдохов."
                ),
                stepsEn = listOf(
                    "Begin with a gentle warm-up flow.",
                    "Move through your sequence, breathing steadily.",
                    "Hold each position for 3–5 breaths.",
                    "End with a 2-minute savasana."
                ),
                stepsRu = listOf(
                    "Начни с мягкой разминочной последовательности.",
                    "Проходи через последовательность, ровно дыша.",
                    "Задерживайся в каждой позиции на 3–5 вдохов.",
                    "Заверши 2-минутной шавасаной."
                ),
                defaultRestSeconds = 0
            )
        )
    )

    fun all(): List<ExerciseDefinition> = exercises

    fun findByIdOrAlias(id: String): ExerciseDefinition? {
        val normalized = id.trim().lowercase()
            .replace(' ', '_')
            .replace('-', '_')
        return exercises.firstOrNull { ex ->
            ex.id == normalized || normalized in ex.aliases
        }
    }
}
