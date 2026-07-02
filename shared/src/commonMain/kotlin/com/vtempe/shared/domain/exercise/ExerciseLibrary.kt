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
            aliases = setOf("bent_over_row", "barbell_row"),
            name = LocalizedText("Barbell Row", "Тяга штанги"),
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
            id = "dumbbell_row",
            aliases = setOf("db_row", "one_arm_row", "single_arm_row"),
            name = LocalizedText("Dumbbell Row", "Тяга гантели"),
            muscleGroups = listOf("lats", "rhomboids", "biceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Use a bench for support; keep your back flat.",
                "Обопрись на скамью; держи спину прямо."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Unilateral row that allows a full range of motion for the lats.",
                    "Односторонняя тяга с полной амплитудой для широчайших."
                ),
                focusEn = listOf("Lats", "Rhomboids", "Rear delts"),
                focusRu = listOf("Широчайшие", "Ромбовидные", "Задние дельты"),
                keyCue = LocalizedText(
                    "Pull the elbow straight back past your hip.",
                    "Тяни локоть прямо назад мимо бедра."
                ),
                stepsEn = listOf(
                    "Place one knee and hand on a bench for support.",
                    "Hold the dumbbell with a neutral grip.",
                    "Pull the dumbbell to your hip, elbow close to your side.",
                    "Lower slowly."
                ),
                stepsRu = listOf(
                    "Упрись коленом и рукой в скамью.",
                    "Возьми гантель нейтральным хватом.",
                    "Подтяни гантель к бедру, локоть вдоль тела.",
                    "Опусти медленно."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "cable_row",
            aliases = setOf("seated_cable_row", "low_cable_row", "seated_row"),
            name = LocalizedText("Cable Row", "Тяга в блоке сидя"),
            muscleGroups = listOf("lats", "rhomboids", "biceps"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Keep your torso upright; don't lean back to cheat.",
                "Держи корпус прямо; не откидывайся назад."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Cable row gives constant tension throughout the full range of motion.",
                    "Тяга в блоке даёт постоянное натяжение по всей амплитуде."
                ),
                focusEn = listOf("Mid back", "Lats", "Biceps"),
                focusRu = listOf("Средняя спина", "Широчайшие", "Бицепс"),
                keyCue = LocalizedText(
                    "Squeeze your shoulder blades together at the end of each rep.",
                    "Своди лопатки в конечной точке каждого повтора."
                ),
                stepsEn = listOf(
                    "Sit at the cable station, feet on the platform, knees slightly bent.",
                    "Grip the handle and straighten your back.",
                    "Pull the handle to your abdomen, driving elbows back.",
                    "Slowly return to the starting position."
                ),
                stepsRu = listOf(
                    "Сядь к тренажёру, ноги на платформе, колени слегка согнуты.",
                    "Возьмись за рукоять и выпрями спину.",
                    "Потяни рукоять к животу, отводя локти назад.",
                    "Медленно вернись в исходное положение."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "t_bar_row",
            aliases = setOf("tbar_row", "t_bar"),
            name = LocalizedText("T-Bar Row", "Тяга Т-грифа"),
            muscleGroups = listOf("lats", "rhomboids", "biceps"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Start light — this loads the lower back heavily.",
                "Начни с лёгкого веса — упражнение сильно грузит поясницу."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "T-bar row targets the mid-back with a narrower grip than a barbell row.",
                    "Тяга Т-грифа прорабатывает среднюю часть спины с узким хватом."
                ),
                focusEn = listOf("Lats", "Mid back", "Biceps"),
                focusRu = listOf("Широчайшие", "Средняя спина", "Бицепс"),
                keyCue = LocalizedText(
                    "Keep your chest on the pad and pull with your elbows.",
                    "Прижми грудь к подушке и тяни локтями."
                ),
                stepsEn = listOf(
                    "Set the bar in a landmine attachment or corner.",
                    "Straddle the bar, hinge at the hips.",
                    "Pull the plates to your chest.",
                    "Lower under control."
                ),
                stepsRu = listOf(
                    "Установи гриф в угол или специальный упор.",
                    "Встань над грифом, наклонись вперёд.",
                    "Подтяни блины к груди.",
                    "Опусти контролируемо."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "lat_pulldown",
            aliases = setOf("cable_pulldown", "pulldown", "lat_pull_down"),
            name = LocalizedText("Lat Pulldown", "Тяга верхнего блока"),
            muscleGroups = listOf("lats", "biceps", "rear_delts"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Use a weight where you can feel your lats, not just your arms.",
                "Выбери вес, при котором чувствуешь спину, а не только руки."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Lat pulldown is a beginner-friendly vertical pull that builds lat width.",
                    "Тяга верхнего блока — доступное вертикальное тяговое упражнение для ширины спины."
                ),
                focusEn = listOf("Lats", "Biceps", "Rear delts"),
                focusRu = listOf("Широчайшие", "Бицепс", "Задние дельты"),
                keyCue = LocalizedText(
                    "Pull the bar to your upper chest, lean back slightly.",
                    "Тяни гриф к верхней части груди, слегка откинувшись назад."
                ),
                stepsEn = listOf(
                    "Sit at the pulldown machine, thighs secured under the pad.",
                    "Grip the bar slightly wider than shoulder width.",
                    "Pull the bar to your upper chest, elbows pointing down.",
                    "Slowly return to the top."
                ),
                stepsRu = listOf(
                    "Сядь в тренажёр, бёдра зафиксированы под упором.",
                    "Возьмись за гриф чуть шире плеч.",
                    "Потяни гриф к верхней части груди, локти вниз.",
                    "Медленно верни гриф наверх."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "pullup",
            aliases = setOf("pull_up", "pullups", "overhand_pullup"),
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
            id = "chin_up",
            aliases = setOf("chinup", "supinated_pullup", "underhand_pullup"),
            name = LocalizedText("Chin-Up", "Подтягивания обратным хватом"),
            muscleGroups = listOf("biceps", "lats"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Supinated (underhand) grip — easier than pull-up due to greater bicep involvement.",
                "Обратный хват — легче подтягиваний прямым из-за большего участия бицепса."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The chin-up builds bicep and lat strength with a supinated grip.",
                    "Подтягивания обратным хватом развивают бицепс и широчайшие."
                ),
                focusEn = listOf("Biceps", "Lats"),
                focusRu = listOf("Бицепс", "Широчайшие"),
                keyCue = LocalizedText(
                    "Drive elbows down and back; squeeze the biceps at the top.",
                    "Тяни локти вниз и назад; сожми бицепс в верхней точке."
                ),
                stepsEn = listOf(
                    "Dead hang with palms facing you, hands shoulder-width.",
                    "Pull until chin clears the bar.",
                    "Lower with full control."
                ),
                stepsRu = listOf(
                    "Вис на прямых руках, ладони к себе, хват на ширине плеч.",
                    "Подтянись до уровня подбородка над перекладиной.",
                    "Опустись полностью контролируя движение."
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
            aliases = setOf("triceps_extension", "overhead_tricep", "overhead_tricep_extension"),
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
            id = "skull_crusher",
            aliases = setOf("french_press", "ez_skull_crusher", "lying_tricep_extension"),
            name = LocalizedText("Skull Crusher", "Французский жим"),
            muscleGroups = listOf("triceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Use an EZ-bar or dumbbells; go light until you're comfortable with the movement.",
                "Используй EZ-гриф или гантели; начни легко пока не освоишь технику."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Skull crushers isolate the triceps through a long range of motion lying on a bench.",
                    "Французский жим изолирует трицепс через большую амплитуду в положении лёжа."
                ),
                focusEn = listOf("Triceps"),
                focusRu = listOf("Трицепс"),
                keyCue = LocalizedText(
                    "Keep upper arms vertical and stationary; only the forearms move.",
                    "Держи плечи вертикально и неподвижно; двигаются только предплечья."
                ),
                stepsEn = listOf(
                    "Lie on a bench, hold the bar above your chest with arms straight.",
                    "Lower the bar toward your forehead by bending elbows only.",
                    "Extend back to start."
                ),
                stepsRu = listOf(
                    "Ляг на скамью, держи гриф над грудью на прямых руках.",
                    "Опусти гриф ко лбу, сгибая только локти.",
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
            aliases = setOf("hipthrust", "hip_thrusts", "barbell_hip_thrust"),
            name = LocalizedText("Hip Thrust", "Ягодичный мост"),
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
            id = "glute_bridge",
            aliases = setOf("bridge", "bodyweight_glute_bridge"),
            name = LocalizedText("Glute Bridge", "Ягодичный мостик"),
            muscleGroups = listOf("glutes", "hamstrings"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Master the bodyweight version before adding a barbell (use hip_thrust for loaded version).",
                "Освой без веса перед добавлением штанги (для нагруженной версии — ягодичный мост)."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "Glute bridge activates and strengthens the glutes from the floor with no equipment.",
                    "Ягодичный мостик активирует и укрепляет ягодицы без оборудования."
                ),
                focusEn = listOf("Glutes", "Hamstrings", "Core"),
                focusRu = listOf("Ягодицы", "Бицепс бедра", "Кор"),
                keyCue = LocalizedText(
                    "Squeeze glutes hard at the top; don't hyperextend the lower back.",
                    "Сильно сожми ягодицы вверху; не перегибай поясницу."
                ),
                stepsEn = listOf(
                    "Lie on your back, knees bent, feet flat on the floor.",
                    "Drive hips up by squeezing glutes.",
                    "Hold 1–2 seconds at the top.",
                    "Lower slowly."
                ),
                stepsRu = listOf(
                    "Ляг на спину, согни колени, стопы на полу.",
                    "Подними бёдра, сжимая ягодицы.",
                    "Задержись 1–2 секунды вверху.",
                    "Медленно опустись."
                ),
                defaultRestSeconds = 45
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
            id = "towel_row",
            aliases = setOf("door_towel_row"),
            name = LocalizedText("Towel Row", "Тяга с полотенцем"),
            muscleGroups = listOf("lats", "upper_back", "biceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Anchor a towel around a sturdy door edge; lean back to load your back.",
                "Закрепи полотенце за прочный край двери; отклоняйся назад, нагружая спину."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A no-equipment horizontal pull using a towel looped around a fixed door to train the back.",
                    "Горизонтальная тяга без оборудования: полотенце вокруг двери нагружает спину."
                ),
                focusEn = listOf("Lats", "Upper back", "Biceps"),
                focusRu = listOf("Широчайшие", "Верх спины", "Бицепс"),
                keyCue = LocalizedText(
                    "Drive your elbows back and squeeze your shoulder blades together.",
                    "Тяни локти назад и своди лопатки."
                ),
                stepsEn = listOf(
                    "Loop a strong towel around a firmly latched door and grip both ends.",
                    "Walk your feet forward and lean back with straight arms.",
                    "Pull your chest toward the door, elbows tracking past your ribs.",
                    "Lower under control until your arms are straight again."
                ),
                stepsRu = listOf(
                    "Оберни прочное полотенце вокруг закрытой двери и возьмись за оба конца.",
                    "Отойди стопами вперёд и отклонись назад на прямых руках.",
                    "Подтяни грудь к двери, локти идут вдоль рёбер.",
                    "Подконтрольно вернись, полностью выпрямив руки."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "doorway_row",
            aliases = setOf("door_frame_row"),
            name = LocalizedText("Doorway Row", "Тяга в дверном проёме"),
            muscleGroups = listOf("upper_back", "lats", "biceps"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Grip both sides of a sturdy doorframe and adjust foot position for difficulty.",
                "Возьмись за оба края крепкого дверного проёма; регулируй сложность положением стоп."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A beginner-friendly bodyweight row using a doorframe as the anchor point.",
                    "Простая тяга собственным весом с опорой на дверной проём."
                ),
                focusEn = listOf("Upper back", "Lats", "Biceps"),
                focusRu = listOf("Верх спины", "Широчайшие", "Бицепс"),
                keyCue = LocalizedText(
                    "Keep your body straight and pull your chest toward your hands.",
                    "Держи тело ровным и тяни грудь к рукам."
                ),
                stepsEn = listOf(
                    "Stand facing a doorway and grip both sides of the frame at chest height.",
                    "Walk your feet forward so your weight leans back on your arms.",
                    "Pull your chest toward the frame, keeping your body rigid.",
                    "Straighten your arms slowly to return."
                ),
                stepsRu = listOf(
                    "Встань лицом к проёму и возьмись за края рамы на уровне груди.",
                    "Отставь стопы вперёд, перенеся вес на руки.",
                    "Подтяни грудь к раме, держа тело жёстким.",
                    "Медленно выпрями руки, возвращаясь назад."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "table_row",
            aliases = setOf("under_table_row", "supine_table_row"),
            name = LocalizedText("Under-Table Row", "Тяга под столом"),
            muscleGroups = listOf("lats", "upper_back", "biceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Use a heavy, stable table you can lie under and grip the edge.",
                "Используй тяжёлый устойчивый стол, под который можно лечь и взяться за край."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A horizontal pull performed lying under a sturdy table, gripping its edge like a bar.",
                    "Горизонтальная тяга лёжа под прочным столом, держась за его край как за перекладину."
                ),
                focusEn = listOf("Lats", "Upper back", "Biceps"),
                focusRu = listOf("Широчайшие", "Верх спины", "Бицепс"),
                keyCue = LocalizedText(
                    "Pull your chest to the table edge and keep hips locked in a line.",
                    "Тяни грудь к краю стола, удерживая корпус в одну линию."
                ),
                stepsEn = listOf(
                    "Lie on your back under a stable table and grip the edge shoulder-width.",
                    "Straighten your body so only your heels touch the floor.",
                    "Pull your chest up to the table edge, elbows tracking down.",
                    "Lower slowly until your arms are fully extended."
                ),
                stepsRu = listOf(
                    "Ляг под устойчивый стол и возьмись за край на ширине плеч.",
                    "Выпрями тело так, чтобы пола касались только пятки.",
                    "Подтяни грудь к краю стола, локти идут вниз.",
                    "Медленно опустись до полного выпрямления рук."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "prone_y_raise",
            aliases = setOf("prone_y", "floor_y_raise"),
            name = LocalizedText("Prone Y-Raise", "Y-подъём лёжа"),
            muscleGroups = listOf("lower_traps", "rear_delts", "upper_back"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Start with bodyweight only; the range is small but the burn is real.",
                "Начни только с собственным весом — амплитуда мала, но жжёт ощутимо."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A prone raise into a Y shape that strengthens the lower traps and postural muscles.",
                    "Подъём лёжа в форме буквы Y для укрепления нижних трапеций и осанки."
                ),
                focusEn = listOf("Lower traps", "Rear delts", "Upper back"),
                focusRu = listOf("Нижние трапеции", "Задние дельты", "Верх спины"),
                keyCue = LocalizedText(
                    "Lift your thumbs toward the ceiling and keep your neck long.",
                    "Поднимай большие пальцы к потолку, шея вытянута."
                ),
                stepsEn = listOf(
                    "Lie face down with arms overhead in a Y, thumbs up.",
                    "Squeeze your shoulder blades down and back.",
                    "Raise both arms off the floor, leading with the thumbs.",
                    "Lower slowly without shrugging your neck."
                ),
                stepsRu = listOf(
                    "Ляг лицом вниз, руки над головой в форме Y, большие пальцы вверх.",
                    "Сведи и опусти лопатки.",
                    "Подними обе руки от пола, ведя большими пальцами.",
                    "Медленно опусти, не поднимая плечи к ушам."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "prone_w_raise",
            aliases = setOf("prone_w", "floor_w_raise", "reverse_snow_angel"),
            name = LocalizedText("Prone W-Raise", "W-подъём лёжа"),
            muscleGroups = listOf("rear_delts", "rhomboids", "lower_traps"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Bodyweight only; focus on squeezing the mid-back at the top.",
                "Только вес тела; в верхней точке сжимай середину спины."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A prone pull into a W shape that targets the rhomboids and rear delts for posture.",
                    "Подъём лёжа в форме буквы W для ромбовидных и задних дельт, улучшает осанку."
                ),
                focusEn = listOf("Rear delts", "Rhomboids", "Lower traps"),
                focusRu = listOf("Задние дельты", "Ромбовидные", "Нижние трапеции"),
                keyCue = LocalizedText(
                    "Pull your elbows down and back, pinching the shoulder blades.",
                    "Тяни локти вниз и назад, сжимая лопатки."
                ),
                stepsEn = listOf(
                    "Lie face down with elbows bent, forming a W with your arms.",
                    "Retract your shoulder blades hard.",
                    "Lift your arms and chest, driving elbows toward your hips.",
                    "Lower with control, keeping tension in the mid-back."
                ),
                stepsRu = listOf(
                    "Ляг лицом вниз, локти согнуты, руки образуют букву W.",
                    "Сильно сведи лопатки.",
                    "Подними руки и грудь, направляя локти к бёдрам.",
                    "Опусти подконтрольно, сохраняя напряжение в середине спины."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "scapular_pullup",
            aliases = setOf("scap_pull", "scapula_retraction_hang"),
            name = LocalizedText("Scapular Pull-Up", "Лопаточные подтягивания"),
            muscleGroups = listOf("lower_traps", "lats", "upper_back"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "A short-range hang movement; build it before full pull-ups.",
                "Движение с короткой амплитудой в висе; освой до полных подтягиваний."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A hanging scapular retraction that builds the shoulder control needed for pull-ups.",
                    "Втягивание лопаток в висе, развивает контроль плеч для подтягиваний."
                ),
                focusEn = listOf("Lower traps", "Lats", "Upper back"),
                focusRu = listOf("Нижние трапеции", "Широчайшие", "Верх спины"),
                keyCue = LocalizedText(
                    "Keep arms straight and pull your shoulders down away from your ears.",
                    "Держи руки прямыми и опускай плечи от ушей."
                ),
                stepsEn = listOf(
                    "Hang from a bar with straight arms, shoulders relaxed up.",
                    "Without bending your elbows, pull your shoulder blades down and back.",
                    "Your body should rise slightly as your chest lifts.",
                    "Relax back to a full hang under control."
                ),
                stepsRu = listOf(
                    "Повисни на перекладине с прямыми руками, плечи расслаблены вверх.",
                    "Не сгибая локти, опусти и сведи лопатки.",
                    "Тело слегка поднимется, грудь приподнимется.",
                    "Подконтрольно вернись в полный вис."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "bench_dip",
            aliases = setOf("chair_dip", "tricep_bench_dip"),
            name = LocalizedText("Bench Dip", "Отжимания от скамьи"),
            muscleGroups = listOf("triceps", "front_delts", "chest"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Use any stable chair or bench; bend knees to make it easier.",
                "Подойдёт любой устойчивый стул или скамья; согни колени для облегчения."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A bodyweight triceps dip using a bench or chair behind you.",
                    "Трицепсовые отжимания от скамьи или стула позади себя."
                ),
                focusEn = listOf("Triceps", "Front delts", "Chest"),
                focusRu = listOf("Трицепс", "Передние дельты", "Грудь"),
                keyCue = LocalizedText(
                    "Keep elbows pointing straight back, don't let them flare out.",
                    "Локти строго назад, не разводи в стороны."
                ),
                stepsEn = listOf(
                    "Sit on a bench edge, hands beside your hips gripping the edge.",
                    "Slide your hips off the bench, legs extended or knees bent.",
                    "Bend your elbows straight back, lowering your hips toward the floor.",
                    "Press through your palms to full arm extension."
                ),
                stepsRu = listOf(
                    "Сядь на край скамьи, руки у бёдер держат край.",
                    "Сдвинь таз со скамьи, ноги прямые или колени согнуты.",
                    "Согни локти строго назад, опуская таз к полу.",
                    "Выжми через ладони до полного выпрямления рук."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "diamond_pushup_knee",
            aliases = setOf("knee_diamond_pushup"),
            name = LocalizedText("Kneeling Diamond Push-Up", "Алмазные отжимания с колен"),
            muscleGroups = listOf("triceps", "chest", "front_delts"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Drop to your knees to build toward full diamond push-ups.",
                "Опусти колени, чтобы подготовиться к полным алмазным отжиманиям."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A knee-supported diamond push-up that emphasizes the triceps at reduced load.",
                    "Алмазные отжимания с колен с акцентом на трицепс при сниженной нагрузке."
                ),
                focusEn = listOf("Triceps", "Chest", "Front delts"),
                focusRu = listOf("Трицепс", "Грудь", "Передние дельты"),
                keyCue = LocalizedText(
                    "Hands form a diamond, elbows brush your ribs on the way down.",
                    "Ладони в форме ромба, локти касаются рёбер при опускании."
                ),
                stepsEn = listOf(
                    "Kneel and place hands together under your chest forming a diamond.",
                    "Walk your knees back so your body is a straight line from knees to head.",
                    "Lower your chest to your hands, elbows tracking back.",
                    "Press up to full extension, squeezing the triceps."
                ),
                stepsRu = listOf(
                    "Встань на колени, ладони вместе под грудью в форме ромба.",
                    "Отставь колени назад, тело — прямая линия от колен до головы.",
                    "Опусти грудь к рукам, локти идут назад.",
                    "Выжми до полного выпрямления, сжимая трицепс."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "wall_tricep_extension",
            aliases = setOf("standing_wall_tricep", "wall_pushaway"),
            name = LocalizedText("Wall Triceps Push-Away", "Разгибание рук от стены"),
            muscleGroups = listOf("triceps", "front_delts"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Step further from the wall to increase difficulty.",
                "Отойди дальше от стены, чтобы усложнить."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A standing triceps-focused push-away against a wall, ideal for beginners.",
                    "Отжимание от стены с акцентом на трицепс, стоя — идеально для начинающих."
                ),
                focusEn = listOf("Triceps", "Front delts"),
                focusRu = listOf("Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Keep your elbows tucked and press only with your triceps.",
                    "Держи локти прижатыми и жми только трицепсом."
                ),
                stepsEn = listOf(
                    "Stand facing a wall, hands close together at chest height.",
                    "Step your feet back so you lean into the wall.",
                    "Bend only at the elbows, bringing your forehead toward the wall.",
                    "Extend your arms to push yourself back, elbows tucked."
                ),
                stepsRu = listOf(
                    "Встань лицом к стене, ладони вместе на уровне груди.",
                    "Отставь стопы назад, наклоняясь к стене.",
                    "Сгибай только локти, приближая лоб к стене.",
                    "Разогни руки, отталкиваясь, локти прижаты."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "wall_walk",
            aliases = setOf("wall_walk_up"),
            name = LocalizedText("Wall Walk", "Заход на стену"),
            muscleGroups = listOf("shoulders", "triceps", "core"),
            difficulty = 4,
            visualFamily = ExerciseVisualFamily.OVERHEAD,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Only attempt once you can hold a solid plank and pike push-up.",
                "Пробуй, только когда уверенно держишь планку и делаешь пайк-отжимания."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A gymnastics-style wall walk that builds overhead pressing strength and shoulder stability.",
                    "Гимнастический заход на стену — развивает жимовую силу над головой и стабильность плеч."
                ),
                focusEn = listOf("Shoulders", "Triceps", "Core"),
                focusRu = listOf("Плечи", "Трицепс", "Кор"),
                keyCue = LocalizedText(
                    "Move your hands and feet together, keeping your core braced hard.",
                    "Двигай руки и ноги согласованно, сильно напрягая кор."
                ),
                stepsEn = listOf(
                    "Start in a push-up position with feet against the base of a wall.",
                    "Walk your feet up the wall while walking your hands closer in.",
                    "Climb until your chest is close to the wall in a near-handstand.",
                    "Reverse the movement back down under control."
                ),
                stepsRu = listOf(
                    "Прими упор лёжа, стопы у основания стены.",
                    "Иди стопами вверх по стене, приближая руки к стене.",
                    "Поднимись, пока грудь не окажется у стены, почти в стойке.",
                    "Подконтрольно спустись обратно."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "pike_pushup_elevated",
            aliases = setOf("elevated_pike_pushup", "feet_elevated_pike"),
            name = LocalizedText("Elevated Pike Push-Up", "Пайк-отжимания с возвышения"),
            muscleGroups = listOf("shoulders", "triceps", "upper_chest"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.OVERHEAD,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Elevating the feet shifts more load onto the shoulders.",
                "Подъём стоп переносит больше нагрузки на плечи."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A pike push-up with feet elevated to bias the shoulders more like an overhead press.",
                    "Пайк-отжимания с приподнятыми стопами для большего акцента на плечи, как в жиме над головой."
                ),
                focusEn = listOf("Shoulders", "Triceps", "Upper chest"),
                focusRu = listOf("Плечи", "Трицепс", "Верх груди"),
                keyCue = LocalizedText(
                    "Stack your shoulders over your hands and lower your head between them.",
                    "Плечи над кистями, опускай голову между руками."
                ),
                stepsEn = listOf(
                    "Place your feet on a low box or step, hips high in a pike.",
                    "Set your hands shoulder-width, shoulders stacked over them.",
                    "Bend your elbows and lower the crown of your head toward the floor.",
                    "Press back up until your arms are straight."
                ),
                stepsRu = listOf(
                    "Поставь стопы на низкую опору, таз высоко в форме уголка.",
                    "Руки на ширине плеч, плечи над кистями.",
                    "Согни локти, опуская макушку к полу.",
                    "Выжми вверх до выпрямления рук."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "single_leg_glute_bridge",
            aliases = setOf("one_leg_bridge", "sl_glute_bridge"),
            name = LocalizedText("Single-Leg Glute Bridge", "Ягодичный мост на одной ноге"),
            muscleGroups = listOf("glutes", "hamstrings", "core"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Keep hips level; if one drops, regress to a two-leg bridge.",
                "Держи таз ровно; если проседает — вернись к мосту на двух ногах."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A single-leg hip bridge that builds glute strength and pelvic stability.",
                    "Ягодичный мост на одной ноге для силы ягодиц и стабильности таза."
                ),
                focusEn = listOf("Glutes", "Hamstrings", "Core"),
                focusRu = listOf("Ягодицы", "Бицепс бедра", "Кор"),
                keyCue = LocalizedText(
                    "Drive through the planted heel and keep both hips level at the top.",
                    "Толкай через пятку опорной ноги, держи таз ровно наверху."
                ),
                stepsEn = listOf(
                    "Lie on your back, one knee bent with foot flat, other leg extended.",
                    "Brace your core and keep both hips square.",
                    "Drive through the planted heel to lift your hips high.",
                    "Lower with control without touching the floor between reps."
                ),
                stepsRu = listOf(
                    "Ляг на спину, одно колено согнуто, стопа на полу, вторая нога прямая.",
                    "Напряги кор, держи таз ровно.",
                    "Толкни через пятку опорной ноги, подняв таз высоко.",
                    "Опусти подконтрольно, не касаясь пола между повторениями."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "bodyweight_good_morning",
            aliases = setOf("standing_hip_hinge", "hip_hinge_drill"),
            name = LocalizedText("Bodyweight Good Morning", "Гуд морнинг без веса"),
            muscleGroups = listOf("hamstrings", "glutes", "lower_back"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Master the hinge pattern here before loading it with a barbell.",
                "Освой шарнир здесь, прежде чем нагружать штангой."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A bodyweight hip hinge that grooves posterior-chain mechanics and hamstring tension.",
                    "Шарнирное движение без веса, отрабатывает механику задней цепи и напряжение бицепса бедра."
                ),
                focusEn = listOf("Hamstrings", "Glutes", "Lower back"),
                focusRu = listOf("Бицепс бедра", "Ягодицы", "Поясница"),
                keyCue = LocalizedText(
                    "Push your hips straight back and keep a long, flat spine.",
                    "Отводи таз строго назад, держи спину ровной и вытянутой."
                ),
                stepsEn = listOf(
                    "Stand with feet hip-width, hands crossed on your chest.",
                    "Soften your knees and brace your core.",
                    "Push your hips back, hinging until your torso is near parallel.",
                    "Squeeze your glutes to stand tall again."
                ),
                stepsRu = listOf(
                    "Встань, стопы на ширине бёдер, руки скрещены на груди.",
                    "Слегка согни колени, напряги кор.",
                    "Отведи таз назад, наклоняясь почти до параллели.",
                    "Сожми ягодицы и выпрямись."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "hip_hinge_wall",
            aliases = setOf("wall_hip_hinge", "hip_tap_wall"),
            name = LocalizedText("Wall Hip Hinge", "Шарнир у стены"),
            muscleGroups = listOf("hamstrings", "glutes", "lower_back"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Use the wall as a target to learn the correct hinge depth.",
                "Используй стену как ориентир, чтобы освоить правильную глубину шарнира."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A hinge teaching drill where you tap a wall behind you with your hips.",
                    "Обучающее упражнение на шарнир: касайся тазом стены позади себя."
                ),
                focusEn = listOf("Hamstrings", "Glutes", "Lower back"),
                focusRu = listOf("Бицепс бедра", "Ягодицы", "Поясница"),
                keyCue = LocalizedText(
                    "Reach your hips back to tap the wall, not down into a squat.",
                    "Тянись тазом назад к стене, а не вниз в присед."
                ),
                stepsEn = listOf(
                    "Stand a few inches in front of a wall, feet hip-width.",
                    "Keep a slight knee bend and a flat back.",
                    "Push your hips straight back until they tap the wall.",
                    "Drive your hips forward to stand tall."
                ),
                stepsRu = listOf(
                    "Встань в нескольких сантиметрах от стены, стопы на ширине бёдер.",
                    "Сохрани лёгкий сгиб колен и ровную спину.",
                    "Отведи таз строго назад до касания стены.",
                    "Выведи таз вперёд и выпрямись."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "prone_leg_curl",
            aliases = setOf("floor_hamstring_curl", "prone_hamstring"),
            name = LocalizedText("Prone Bodyweight Leg Curl", "Сгибание ног лёжа без веса"),
            muscleGroups = listOf("hamstrings", "calves"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Squeeze the hamstring at the top; add ankle weights later.",
                "Сжимай бицепс бедра наверху; позже добавь утяжелители на лодыжки."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A prone hamstring curl using only bodyweight to isolate the hamstrings.",
                    "Сгибание ног лёжа с собственным весом для изоляции бицепса бедра."
                ),
                focusEn = listOf("Hamstrings", "Calves"),
                focusRu = listOf("Бицепс бедра", "Икры"),
                keyCue = LocalizedText(
                    "Curl your heels toward your glutes and pause at the top.",
                    "Подтяни пятки к ягодицам и задержись наверху."
                ),
                stepsEn = listOf(
                    "Lie face down with legs straight and hips pressed to the floor.",
                    "Brace your core to keep your pelvis still.",
                    "Curl both heels toward your glutes, squeezing the hamstrings.",
                    "Lower slowly until your legs are straight."
                ),
                stepsRu = listOf(
                    "Ляг лицом вниз, ноги прямые, таз прижат к полу.",
                    "Напряги кор, чтобы таз не двигался.",
                    "Подтяни пятки к ягодицам, сжимая бицепс бедра.",
                    "Медленно выпрями ноги."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "bodyweight_squat",
            aliases = setOf("air_squat", "bw_squat"),
            name = LocalizedText("Bodyweight Squat", "Приседания без веса"),
            muscleGroups = listOf("quads", "glutes", "hamstrings"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Sit to a comfortable depth with a flat back and heels down.",
                "Приседай на удобную глубину с ровной спиной и пятками на полу."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "The foundational bodyweight squat for lower-body strength anywhere.",
                    "Базовое приседание без веса для силы ног в любом месте."
                ),
                focusEn = listOf("Quads", "Glutes", "Hamstrings"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Бицепс бедра"),
                keyCue = LocalizedText(
                    "Sit back and down, knees tracking over your toes.",
                    "Садись назад и вниз, колени над носками."
                ),
                stepsEn = listOf(
                    "Stand with feet shoulder-width, toes slightly out.",
                    "Brace your core and raise your arms for balance.",
                    "Sit your hips back and down until thighs reach parallel.",
                    "Drive through your feet to stand tall."
                ),
                stepsRu = listOf(
                    "Встань, стопы на ширине плеч, носки слегка врозь.",
                    "Напряги кор, вытяни руки для баланса.",
                    "Отведи таз назад и вниз до параллели бёдер.",
                    "Толкни через стопы и выпрямись."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "prisoner_squat",
            aliases = setOf("hands_behind_head_squat"),
            name = LocalizedText("Prisoner Squat", "Приседания руки за головой"),
            muscleGroups = listOf("quads", "glutes", "upper_back"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Hands behind the head forces an upright torso.",
                "Руки за головой заставляют держать корпус вертикально."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A bodyweight squat with hands behind the head that reinforces upright posture.",
                    "Приседание без веса с руками за головой для вертикальной осанки."
                ),
                focusEn = listOf("Quads", "Glutes", "Upper back"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Верх спины"),
                keyCue = LocalizedText(
                    "Keep your elbows wide and chest proud throughout.",
                    "Держи локти в стороны и грудь раскрытой."
                ),
                stepsEn = listOf(
                    "Stand with feet shoulder-width, hands clasped behind your head.",
                    "Pull your elbows wide and lift your chest.",
                    "Squat down to parallel keeping your torso tall.",
                    "Drive up without letting your chest drop."
                ),
                stepsRu = listOf(
                    "Встань, стопы на ширине плеч, ладони за головой.",
                    "Разведи локти в стороны, подними грудь.",
                    "Присядь до параллели, держа корпус вертикально.",
                    "Поднимись, не опуская грудь."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "cossack_squat",
            aliases = setOf("cossack"),
            name = LocalizedText("Cossack Squat", "Казачий присед"),
            muscleGroups = listOf("quads", "glutes", "adductors"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Work within a pain-free range and build depth over time.",
                "Работай в безболезненной амплитуде, наращивай глубину со временем."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A deep lateral squat that trains single-leg strength, mobility, and the adductors.",
                    "Глубокий боковой присед, тренирует силу одной ноги, подвижность и приводящие."
                ),
                focusEn = listOf("Quads", "Glutes", "Adductors"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Приводящие"),
                keyCue = LocalizedText(
                    "Sit deep over one leg while the other stays straight with toes up.",
                    "Садись глубоко на одну ногу, другая прямая с носком вверх."
                ),
                stepsEn = listOf(
                    "Stand with a very wide stance, toes slightly out.",
                    "Shift your weight over one leg and bend that knee deeply.",
                    "Keep the other leg straight with the heel down and toes up.",
                    "Push back to center and repeat on the other side."
                ),
                stepsRu = listOf(
                    "Встань в очень широкую стойку, носки слегка врозь.",
                    "Перенеси вес на одну ногу и глубоко согни колено.",
                    "Другую ногу держи прямой, пятка на полу, носок вверх.",
                    "Вернись в центр и повтори на другую сторону."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "split_squat",
            aliases = setOf("stationary_lunge", "static_lunge"),
            name = LocalizedText("Split Squat", "Сплит-присед"),
            muscleGroups = listOf("quads", "glutes", "hamstrings"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "A stationary lunge; hold weights once bodyweight is easy.",
                "Выпад на месте; добавь вес, когда собственный станет лёгким."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A stationary split-stance squat that builds single-leg strength and balance.",
                    "Присед в сплит-стойке на месте для силы одной ноги и баланса."
                ),
                focusEn = listOf("Quads", "Glutes", "Hamstrings"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Бицепс бедра"),
                keyCue = LocalizedText(
                    "Drop straight down so both knees bend to about 90 degrees.",
                    "Опускайся строго вниз, оба колена сгибаются примерно до 90°."
                ),
                stepsEn = listOf(
                    "Step one foot forward into a split stance.",
                    "Keep your torso tall and core braced.",
                    "Lower straight down until your back knee nearly touches the floor.",
                    "Drive through your front heel to stand tall."
                ),
                stepsRu = listOf(
                    "Сделай шаг одной ногой вперёд в сплит-стойку.",
                    "Держи корпус вертикально, кор напряжён.",
                    "Опускайся строго вниз, пока заднее колено почти не коснётся пола.",
                    "Толкни через переднюю пятку и выпрямись."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "wall_sit_march",
            aliases = setOf("wall_sit_marching"),
            name = LocalizedText("Wall Sit March", "Марш у стены в приседе"),
            muscleGroups = listOf("quads", "glutes", "core"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Hold the wall sit while alternately lifting each foot.",
                "Держи присед у стены, поочерёдно поднимая стопы."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A wall sit with alternating foot lifts to add a stability and endurance challenge.",
                    "Присед у стены с поочерёдным подъёмом стоп для стабильности и выносливости."
                ),
                focusEn = listOf("Quads", "Glutes", "Core"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Кор"),
                keyCue = LocalizedText(
                    "Hold 90 degrees at the knees and lift each foot without shifting.",
                    "Держи 90° в коленях и поднимай стопы, не смещаясь."
                ),
                stepsEn = listOf(
                    "Sit against a wall with thighs parallel and knees at 90 degrees.",
                    "Brace your core and press your back flat to the wall.",
                    "Lift one foot a few inches, hold briefly, then switch.",
                    "Continue alternating while holding the wall sit."
                ),
                stepsRu = listOf(
                    "Сядь у стены, бёдра параллельны, колени под 90°.",
                    "Напряги кор, прижми спину к стене.",
                    "Подними одну стопу на несколько сантиметров, задержись, смени.",
                    "Продолжай чередовать, удерживая присед."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "calf_raise",
            aliases = setOf("standing_calf_raise", "bodyweight_calf_raise"),
            name = LocalizedText("Standing Calf Raise", "Подъём на носки стоя"),
            muscleGroups = listOf("calves"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Use a step edge for a bigger stretch; hold a wall for balance.",
                "Используй край ступени для растяжения; держись за стену для баланса."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A standing calf raise that builds ankle strength and lower-leg size.",
                    "Подъём на носки стоя для силы голеностопа и объёма голени."
                ),
                focusEn = listOf("Calves"),
                focusRu = listOf("Икры"),
                keyCue = LocalizedText(
                    "Rise as high as possible and pause at the top before lowering.",
                    "Поднимись максимально высоко и задержись наверху перед опусканием."
                ),
                stepsEn = listOf(
                    "Stand tall, optionally with the balls of your feet on a step edge.",
                    "Push through the balls of your feet to raise your heels high.",
                    "Pause and squeeze your calves at the top.",
                    "Lower your heels slowly for a full stretch."
                ),
                stepsRu = listOf(
                    "Встань прямо, при желании носками на краю ступени.",
                    "Толкнись подушечками стоп, подняв пятки высоко.",
                    "Задержись и сожми икры наверху.",
                    "Медленно опусти пятки для полного растяжения."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "glute_kickback",
            aliases = setOf("quadruped_kickback", "donkey_kick"),
            name = LocalizedText("Quadruped Glute Kickback", "Отведение ноги на четвереньках"),
            muscleGroups = listOf("glutes", "hamstrings"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Keep your back flat and move only from the hip.",
                "Держи спину ровной и двигай только тазобедренным суставом."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A quadruped hip extension that isolates the glutes with no equipment.",
                    "Разгибание бедра на четвереньках для изоляции ягодиц без оборудования."
                ),
                focusEn = listOf("Glutes", "Hamstrings"),
                focusRu = listOf("Ягодицы", "Бицепс бедра"),
                keyCue = LocalizedText(
                    "Squeeze the glute to drive the heel toward the ceiling.",
                    "Сжимай ягодицу, направляя пятку к потолку."
                ),
                stepsEn = listOf(
                    "Start on hands and knees with a flat back.",
                    "Brace your core to lock your spine in place.",
                    "Drive one heel up and back until the thigh is in line with your torso.",
                    "Lower with control and repeat, then switch sides."
                ),
                stepsRu = listOf(
                    "Встань на четвереньки, спина ровная.",
                    "Напряги кор, зафиксировав позвоночник.",
                    "Подними пятку вверх и назад, пока бедро не выйдет на линию корпуса.",
                    "Опусти подконтрольно и повтори, затем смени сторону."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "fire_hydrant",
            aliases = setOf("hip_abduction_quadruped"),
            name = LocalizedText("Fire Hydrant", "Отведение бедра в сторону"),
            muscleGroups = listOf("glutes", "hip_abductors"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Lift to the side without rotating your torso.",
                "Поднимай в сторону, не разворачивая корпус."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A quadruped hip abduction that targets the glute medius and hip stabilizers.",
                    "Отведение бедра на четвереньках для средней ягодичной и стабилизаторов таза."
                ),
                focusEn = listOf("Glutes", "Hip abductors"),
                focusRu = listOf("Ягодицы", "Отводящие бедра"),
                keyCue = LocalizedText(
                    "Open the knee out to the side, keeping it bent at 90 degrees.",
                    "Отводи колено в сторону, сохраняя угол 90°."
                ),
                stepsEn = listOf(
                    "Start on hands and knees with a neutral spine.",
                    "Keep one knee bent and lift it out to the side.",
                    "Raise until your thigh is roughly parallel to the floor.",
                    "Lower slowly and repeat, then switch sides."
                ),
                stepsRu = listOf(
                    "Встань на четвереньки, позвоночник нейтрален.",
                    "Согнутое колено подними в сторону.",
                    "Подними до положения бедра примерно параллельно полу.",
                    "Медленно опусти и повтори, затем смени сторону."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "jump_lunge",
            aliases = setOf("jumping_lunge", "split_jump"),
            name = LocalizedText("Jumping Lunge", "Выпады в прыжке"),
            muscleGroups = listOf("quads", "glutes", "calves"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "A plyometric lunge; land softly and control each rep.",
                "Плиометрический выпад; приземляйся мягко и контролируй каждое повторение."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An explosive jumping lunge that builds single-leg power and conditioning.",
                    "Взрывной выпад в прыжке для мощности одной ноги и выносливости."
                ),
                focusEn = listOf("Quads", "Glutes", "Calves"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Икры"),
                keyCue = LocalizedText(
                    "Jump up and switch legs midair, landing softly into the next lunge.",
                    "Выпрыгивай и меняй ноги в воздухе, мягко приземляясь в следующий выпад."
                ),
                stepsEn = listOf(
                    "Start in a lunge with one foot forward.",
                    "Drive down then explode straight up.",
                    "Switch legs in the air and land in the opposite lunge.",
                    "Absorb the landing softly and repeat."
                ),
                stepsRu = listOf(
                    "Начни в выпаде, одна нога впереди.",
                    "Опустись вниз, затем взорвись строго вверх.",
                    "Смени ноги в воздухе и приземлись в противоположный выпад.",
                    "Мягко погаси приземление и повтори."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "flutter_kick",
            aliases = setOf("flutter_kicks"),
            name = LocalizedText("Flutter Kicks", "Ножницы вертикальные"),
            muscleGroups = listOf("lower_abs", "hip_flexors"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Keep your lower back pressed to the floor the whole time.",
                "Держи поясницу прижатой к полу всё время."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A low-abdominal endurance drill with small alternating leg kicks.",
                    "Упражнение на выносливость низа пресса с мелкими поочерёдными махами ног."
                ),
                focusEn = listOf("Lower abs", "Hip flexors"),
                focusRu = listOf("Низ пресса", "Сгибатели бедра"),
                keyCue = LocalizedText(
                    "Keep legs low and straight, kicking in small quick motions.",
                    "Держи ноги низко и прямо, делай мелкие быстрые махи."
                ),
                stepsEn = listOf(
                    "Lie on your back with legs straight and hands under your hips.",
                    "Lift both heels a few inches off the floor.",
                    "Alternately kick your legs up and down in small motions.",
                    "Keep your lower back glued to the floor throughout."
                ),
                stepsRu = listOf(
                    "Ляг на спину, ноги прямые, ладони под тазом.",
                    "Подними обе пятки на несколько сантиметров от пола.",
                    "Поочерёдно двигай ногами вверх-вниз мелкими махами.",
                    "Держи поясницу прижатой к полу всё время."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "reverse_crunch",
            aliases = setOf("knee_raise_floor"),
            name = LocalizedText("Reverse Crunch", "Обратные скручивания"),
            muscleGroups = listOf("lower_abs"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Curl your pelvis up, not just your knees.",
                "Скручивай таз вверх, а не просто подтягивай колени."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A reverse crunch that emphasizes the lower abdominals through pelvic curl.",
                    "Обратное скручивание с акцентом на низ пресса через подкручивание таза."
                ),
                focusEn = listOf("Lower abs"),
                focusRu = listOf("Низ пресса"),
                keyCue = LocalizedText(
                    "Lift your hips off the floor by curling your pelvis toward your ribs.",
                    "Оторви таз от пола, подкручивая его к рёбрам."
                ),
                stepsEn = listOf(
                    "Lie on your back, knees bent and lifted to tabletop.",
                    "Place your hands flat beside your hips.",
                    "Curl your pelvis up, lifting your hips off the floor.",
                    "Lower slowly without letting your feet touch down."
                ),
                stepsRu = listOf(
                    "Ляг на спину, колени согнуты и подняты в положение стола.",
                    "Положи ладони на пол у бёдер.",
                    "Подкрути таз вверх, отрывая бёдра от пола.",
                    "Медленно опусти, не касаясь стопами пола."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "heel_touch",
            aliases = setOf("oblique_heel_taps", "heel_taps"),
            name = LocalizedText("Heel Touches", "Касания пяток"),
            muscleGroups = listOf("obliques", "abs"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Crunch side to side, reaching for each heel.",
                "Скручивайся из стороны в сторону, тянясь к каждой пятке."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A side-to-side crunch that targets the obliques by reaching for the heels.",
                    "Скручивание из стороны в сторону для косых мышц с касанием пяток."
                ),
                focusEn = listOf("Obliques", "Abs"),
                focusRu = listOf("Косые", "Пресс"),
                keyCue = LocalizedText(
                    "Keep shoulders off the floor and crunch sideways to each heel.",
                    "Держи плечи над полом и скручивайся вбок к каждой пятке."
                ),
                stepsEn = listOf(
                    "Lie on your back, knees bent, feet flat and close to your hips.",
                    "Lift your shoulders slightly off the floor.",
                    "Reach one hand toward the same-side heel by crunching sideways.",
                    "Alternate sides in a steady rhythm."
                ),
                stepsRu = listOf(
                    "Ляг на спину, колени согнуты, стопы близко к бёдрам.",
                    "Слегка приподними плечи от пола.",
                    "Тянись рукой к пятке той же стороны, скручиваясь вбок.",
                    "Чередуй стороны в ровном ритме."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "plank_shoulder_tap",
            aliases = setOf("shoulder_taps", "plank_taps"),
            name = LocalizedText("Plank Shoulder Tap", "Планка с касанием плеч"),
            muscleGroups = listOf("core", "shoulders", "obliques"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Tap alternate shoulders while keeping your hips still.",
                "Касайся плеч поочерёдно, удерживая таз неподвижным."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A high-plank anti-rotation drill tapping alternate shoulders.",
                    "Упражнение на антиротацию в планке с поочерёдным касанием плеч."
                ),
                focusEn = listOf("Core", "Shoulders", "Obliques"),
                focusRu = listOf("Кор", "Плечи", "Косые"),
                keyCue = LocalizedText(
                    "Keep your hips square and don't let them rock side to side.",
                    "Держи таз ровным, не позволяй ему раскачиваться."
                ),
                stepsEn = listOf(
                    "Set up in a high plank with hands under shoulders.",
                    "Widen your feet slightly for stability and brace your core.",
                    "Lift one hand to tap the opposite shoulder without shifting hips.",
                    "Return the hand and alternate sides."
                ),
                stepsRu = listOf(
                    "Прими высокую планку, кисти под плечами.",
                    "Расставь стопы чуть шире для устойчивости, напряги кор.",
                    "Подними руку, коснись противоположного плеча, не смещая таз.",
                    "Верни руку и чередуй стороны."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "bird_dog",
            aliases = setOf("quadruped_reach"),
            name = LocalizedText("Bird Dog", "Птица-собака"),
            muscleGroups = listOf("core", "lower_back", "glutes"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Extend opposite arm and leg while keeping your back flat.",
                "Вытягивай противоположные руку и ногу, держа спину ровной."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A quadruped anti-rotation drill extending opposite arm and leg.",
                    "Упражнение на антиротацию на четвереньках с вытягиванием противоположных руки и ноги."
                ),
                focusEn = listOf("Core", "Lower back", "Glutes"),
                focusRu = listOf("Кор", "Поясница", "Ягодицы"),
                keyCue = LocalizedText(
                    "Reach long through your fingertips and heel without arching your back.",
                    "Тянись кончиками пальцев и пяткой, не прогибая спину."
                ),
                stepsEn = listOf(
                    "Start on hands and knees with a flat back.",
                    "Brace your core to keep your spine still.",
                    "Extend one arm forward and the opposite leg back.",
                    "Return under control and switch to the other pair."
                ),
                stepsRu = listOf(
                    "Встань на четвереньки, спина ровная.",
                    "Напряги кор, чтобы позвоночник не двигался.",
                    "Вытяни одну руку вперёд и противоположную ногу назад.",
                    "Вернись подконтрольно и смени пару."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "plank_up_down",
            aliases = setOf("plank_walkup", "up_down_plank"),
            name = LocalizedText("Up-Down Plank", "Планка вверх-вниз"),
            muscleGroups = listOf("core", "triceps", "shoulders"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Move between forearm and hand plank while keeping hips still.",
                "Переходи из планки на предплечьях в упор на руках, таз неподвижен."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A dynamic plank transitioning between forearms and hands to challenge the core and arms.",
                    "Динамическая планка с переходом между предплечьями и руками для кора и рук."
                ),
                focusEn = listOf("Core", "Triceps", "Shoulders"),
                focusRu = listOf("Кор", "Трицепс", "Плечи"),
                keyCue = LocalizedText(
                    "Keep your hips level as you press up and lower one arm at a time.",
                    "Держи таз ровным, поднимаясь и опускаясь по одной руке."
                ),
                stepsEn = listOf(
                    "Start in a forearm plank with a braced core.",
                    "Press up onto one hand, then the other, into a high plank.",
                    "Lower back to your forearms one arm at a time.",
                    "Keep your hips level throughout, alternating the lead arm."
                ),
                stepsRu = listOf(
                    "Начни в планке на предплечьях с напряжённым кором.",
                    "Поднимись на одну руку, затем на другую, в высокую планку.",
                    "Опустись обратно на предплечья по одной руке.",
                    "Держи таз ровным, чередуя ведущую руку."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "side_plank_hip_dip",
            aliases = setOf("side_plank_dip"),
            name = LocalizedText("Side Plank Hip Dip", "Боковая планка с опусканием таза"),
            muscleGroups = listOf("obliques", "core"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Dip the hip toward the floor and lift back up under control.",
                "Опускай таз к полу и подконтрольно поднимай обратно."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A dynamic side plank that adds hip dips to load the obliques through a range.",
                    "Динамическая боковая планка с опусканием таза для нагрузки косых в амплитуде."
                ),
                focusEn = listOf("Obliques", "Core"),
                focusRu = listOf("Косые", "Кор"),
                keyCue = LocalizedText(
                    "Lower the hip slowly, then drive it high to the top.",
                    "Медленно опускай таз, затем выжимай его высоко вверх."
                ),
                stepsEn = listOf(
                    "Set up in a forearm side plank, body in a straight line.",
                    "Brace your obliques hard.",
                    "Lower your bottom hip toward the floor without touching.",
                    "Drive your hip back up to the top and repeat."
                ),
                stepsRu = listOf(
                    "Прими боковую планку на предплечье, тело в линию.",
                    "Сильно напряги косые.",
                    "Опусти нижний таз к полу, не касаясь его.",
                    "Выжми таз обратно вверх и повтори."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "hollow_rock",
            aliases = setOf("hollow_body_rock"),
            name = LocalizedText("Hollow Rock", "Раскачка в лодочке"),
            muscleGroups = listOf("abs", "hip_flexors"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Master the hollow hold before adding the rocking motion.",
                "Освой удержание лодочки перед добавлением раскачки."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A dynamic hollow-body rock that builds deep core tension and control.",
                    "Динамическая раскачка в лодочке для глубокого напряжения и контроля кора."
                ),
                focusEn = listOf("Abs", "Hip flexors"),
                focusRu = listOf("Пресс", "Сгибатели бедра"),
                keyCue = LocalizedText(
                    "Keep the banana shape and rock from shoulders to hips.",
                    "Сохраняй форму банана и раскачивайся от плеч к бёдрам."
                ),
                stepsEn = listOf(
                    "Lie on your back and lift arms and legs into a hollow shape.",
                    "Press your lower back firmly into the floor.",
                    "Rock forward and back keeping the hollow shape rigid.",
                    "Stay smooth and controlled throughout."
                ),
                stepsRu = listOf(
                    "Ляг на спину, подними руки и ноги в форму лодочки.",
                    "Прижми поясницу к полу.",
                    "Раскачивайся вперёд-назад, сохраняя жёсткую форму лодочки.",
                    "Двигайся плавно и подконтрольно."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "plank_reach",
            aliases = setOf("plank_arm_reach"),
            name = LocalizedText("Plank Arm Reach", "Планка с вытягиванием руки"),
            muscleGroups = listOf("core", "shoulders"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CORE,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Reach one arm forward while holding a stable plank.",
                "Вытягивай одну руку вперёд, удерживая устойчивую планку."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An anti-rotation plank variation reaching one arm forward at a time.",
                    "Вариант планки на антиротацию с поочерёдным вытягиванием руки вперёд."
                ),
                focusEn = listOf("Core", "Shoulders"),
                focusRu = listOf("Кор", "Плечи"),
                keyCue = LocalizedText(
                    "Keep hips square as you extend each arm straight ahead.",
                    "Держи таз ровным, вытягивая каждую руку прямо вперёд."
                ),
                stepsEn = listOf(
                    "Start in a high plank with a wide, stable base.",
                    "Brace your core and squeeze your glutes.",
                    "Reach one arm straight forward without dropping the hip.",
                    "Return and repeat on the other side."
                ),
                stepsRu = listOf(
                    "Начни в высокой планке с широкой устойчивой базой.",
                    "Напряги кор и сожми ягодицы.",
                    "Вытяни одну руку прямо вперёд, не роняя таз.",
                    "Вернись и повтори другой рукой."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "knee_pushup",
            aliases = setOf("kneeling_pushup", "modified_pushup"),
            name = LocalizedText("Kneeling Push-Up", "Отжимания с колен"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "A scaled push-up on the knees to build pressing strength.",
                "Облегчённые отжимания с колен для развития жимовой силы."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A knee-supported push-up that lowers the load while grooving the pattern.",
                    "Отжимания с опорой на колени: снижают нагрузку и отрабатывают технику."
                ),
                focusEn = listOf("Chest", "Triceps", "Front delts"),
                focusRu = listOf("Грудь", "Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Keep a straight line from knees to head and lower your chest fully.",
                    "Держи прямую линию от колен до головы и опускай грудь полностью."
                ),
                stepsEn = listOf(
                    "Kneel and place your hands slightly wider than shoulders.",
                    "Form a straight line from knees to head, core braced.",
                    "Lower your chest toward the floor with elbows at about 45 degrees.",
                    "Press back to full arm extension."
                ),
                stepsRu = listOf(
                    "Встань на колени, кисти чуть шире плеч.",
                    "Держи прямую линию от колен до головы, кор напряжён.",
                    "Опусти грудь к полу, локти примерно под 45°.",
                    "Выжми до полного выпрямления рук."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "pseudo_planche_pushup",
            aliases = setOf("lean_pushup"),
            name = LocalizedText("Pseudo Planche Push-Up", "Отжимания с наклоном вперёд"),
            muscleGroups = listOf("front_delts", "chest", "triceps"),
            difficulty = 4,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Lean your shoulders past your hands to load the delts.",
                "Наклоняй плечи за линию кистей, чтобы нагрузить дельты."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An advanced push-up leaning forward to bias the front delts and build planche strength.",
                    "Продвинутые отжимания с наклоном вперёд для передних дельт и силы планша."
                ),
                focusEn = listOf("Front delts", "Chest", "Triceps"),
                focusRu = listOf("Передние дельты", "Грудь", "Трицепс"),
                keyCue = LocalizedText(
                    "Point your fingers back and keep your shoulders ahead of your hands.",
                    "Пальцы назад, плечи впереди линии кистей."
                ),
                stepsEn = listOf(
                    "Set up in a push-up with hands by your waist, fingers pointing back.",
                    "Lean your shoulders forward past your hands.",
                    "Lower with control keeping the forward lean.",
                    "Press back up maintaining the lean throughout."
                ),
                stepsRu = listOf(
                    "Прими упор лёжа, кисти у пояса, пальцы назад.",
                    "Наклони плечи вперёд за линию кистей.",
                    "Опустись подконтрольно, сохраняя наклон.",
                    "Выжми вверх, удерживая наклон."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "archer_pushup",
            aliases = setOf("side_to_side_pushup"),
            name = LocalizedText("Archer Push-Up", "Отжимания лучника"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 4,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Shift toward one arm to overload it; a step toward one-arm push-ups.",
                "Смещайся к одной руке для перегрузки; шаг к отжиманиям на одной руке."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A wide push-up shifting weight to one working arm while the other stays straight.",
                    "Широкие отжимания со смещением веса на одну рабочую руку, другая прямая."
                ),
                focusEn = listOf("Chest", "Triceps", "Front delts"),
                focusRu = listOf("Грудь", "Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Bend one arm while the other stays straight, sliding out to the side.",
                    "Сгибай одну руку, вторая прямая, отводится в сторону."
                ),
                stepsEn = listOf(
                    "Set up in a wide push-up position, hands far apart.",
                    "Lower toward one hand, bending that elbow.",
                    "Keep the opposite arm straight, extending out to the side.",
                    "Press back to center and alternate sides."
                ),
                stepsRu = listOf(
                    "Прими широкий упор лёжа, кисти далеко друг от друга.",
                    "Опустись к одной руке, сгибая этот локоть.",
                    "Держи противоположную руку прямой, отведённой в сторону.",
                    "Выжми в центр и чередуй стороны."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "clap_pushup",
            aliases = setOf("plyo_pushup", "explosive_pushup"),
            name = LocalizedText("Clap Push-Up", "Отжимания с хлопком"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 4,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "An explosive push-up; only add once standard reps feel easy.",
                "Взрывные отжимания; добавляй, когда обычные даются легко."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A plyometric push-up pushing off the floor hard enough to clap between reps.",
                    "Плиометрические отжимания с отталкиванием от пола и хлопком между повторениями."
                ),
                focusEn = listOf("Chest", "Triceps", "Front delts"),
                focusRu = listOf("Грудь", "Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Explode off the floor and land with soft, bent elbows.",
                    "Взрывно оттолкнись от пола и приземляйся на мягкие согнутые локти."
                ),
                stepsEn = listOf(
                    "Start in a standard push-up position, core braced.",
                    "Lower your chest, then explode up off the floor.",
                    "Clap your hands quickly while airborne.",
                    "Land softly with bent elbows and immediately repeat."
                ),
                stepsRu = listOf(
                    "Начни в обычном упоре лёжа, кор напряжён.",
                    "Опусти грудь, затем взрывно оттолкнись от пола.",
                    "Быстро хлопни в ладоши в воздухе.",
                    "Мягко приземлись на согнутые локти и сразу повтори."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "db_bench_press",
            aliases = setOf("dumbbell_bench_press", "flat_db_press"),
            name = LocalizedText("Dumbbell Bench Press", "Жим гантелей лёжа"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Pick a pair you can press for 8-10 clean reps with stable shoulders.",
                "Возьми пару, с которой сделаешь 8-10 чистых повторений при стабильных плечах."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A dumbbell chest press allowing a deeper stretch and independent arm work.",
                    "Жим гантелей на грудь с большей амплитудой и независимой работой рук."
                ),
                focusEn = listOf("Chest", "Triceps", "Front delts"),
                focusRu = listOf("Грудь", "Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Lower until you feel a stretch, then press the dumbbells together.",
                    "Опускай до ощущения растяжения, затем своди гантели вверху."
                ),
                stepsEn = listOf(
                    "Lie on a bench holding a dumbbell in each hand at chest level.",
                    "Retract your shoulder blades and plant your feet.",
                    "Press the dumbbells up until your arms are extended.",
                    "Lower under control to a deep chest stretch."
                ),
                stepsRu = listOf(
                    "Ляг на скамью с гантелями у груди.",
                    "Сведи лопатки, упрись стопами в пол.",
                    "Выжми гантели вверх до выпрямления рук.",
                    "Подконтрольно опусти до глубокого растяжения груди."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "incline_db_press",
            aliases = setOf("incline_dumbbell_press"),
            name = LocalizedText("Incline Dumbbell Press", "Жим гантелей на наклонной"),
            muscleGroups = listOf("upper_chest", "front_delts", "triceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Set the bench to about 30-45 degrees to hit the upper chest.",
                "Установи скамью под 30-45°, чтобы проработать верх груди."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An incline dumbbell press biasing the upper chest and front delts.",
                    "Жим гантелей на наклонной с акцентом на верх груди и передние дельты."
                ),
                focusEn = listOf("Upper chest", "Front delts", "Triceps"),
                focusRu = listOf("Верх груди", "Передние дельты", "Трицепс"),
                keyCue = LocalizedText(
                    "Press up and slightly back, keeping the dumbbells over the collarbones.",
                    "Жми вверх и чуть назад, держа гантели над ключицами."
                ),
                stepsEn = listOf(
                    "Set an incline bench to 30-45 degrees and sit back with dumbbells.",
                    "Start with the dumbbells at upper-chest level, elbows down.",
                    "Press up until your arms are extended over your collarbones.",
                    "Lower slowly to a stretch at the upper chest."
                ),
                stepsRu = listOf(
                    "Установи наклон 30-45° и откинься со гантелями.",
                    "Начни с гантелей у верха груди, локти вниз.",
                    "Выжми вверх до выпрямления рук над ключицами.",
                    "Медленно опусти до растяжения верха груди."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "machine_chest_press",
            aliases = setOf("chest_press_machine", "seated_chest_press"),
            name = LocalizedText("Machine Chest Press", "Жим от груди в тренажёре"),
            muscleGroups = listOf("chest", "triceps", "front_delts"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Adjust the seat so the handles line up with mid-chest.",
                "Отрегулируй сиденье, чтобы рукояти были на уровне середины груди."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A machine chest press offering a stable, beginner-friendly pressing path.",
                    "Жим от груди в тренажёре: стабильная траектория, удобно для новичков."
                ),
                focusEn = listOf("Chest", "Triceps", "Front delts"),
                focusRu = listOf("Грудь", "Трицепс", "Передние дельты"),
                keyCue = LocalizedText(
                    "Press the handles forward and squeeze your chest at full extension.",
                    "Выжимай рукояти вперёд и сжимай грудь в конечной точке."
                ),
                stepsEn = listOf(
                    "Adjust the seat so handles align with your mid-chest.",
                    "Grip the handles and set your shoulder blades back.",
                    "Press the handles forward until your arms are extended.",
                    "Return slowly to a stretch without clanking the stack."
                ),
                stepsRu = listOf(
                    "Отрегулируй сиденье, рукояти на уровне середины груди.",
                    "Возьмись за рукояти, сведи лопатки.",
                    "Выжми рукояти вперёд до выпрямления рук.",
                    "Медленно вернись до растяжения, не бросая вес."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "svend_press",
            aliases = setOf("plate_press", "squeeze_press"),
            name = LocalizedText("Svend Press", "Жим с плотным сведением"),
            muscleGroups = listOf("inner_chest", "front_delts"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PUSH,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Press two plates or dumbbells together and push straight out.",
                "Сжимай два блина или гантели вместе и выжимай прямо вперёд."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A pressing drill squeezing weights together to emphasize inner-chest contraction.",
                    "Жим со сжатием отягощений для акцента на внутренней части груди."
                ),
                focusEn = listOf("Inner chest", "Front delts"),
                focusRu = listOf("Внутренняя грудь", "Передние дельты"),
                keyCue = LocalizedText(
                    "Crush the weights together the whole time you press out and back.",
                    "Сдавливай отягощения всё время, выжимая вперёд и обратно."
                ),
                stepsEn = listOf(
                    "Stand holding two plates or dumbbells pressed together at your chest.",
                    "Squeeze them together hard to engage the chest.",
                    "Press them straight out until your arms are extended.",
                    "Return to your chest keeping constant squeeze."
                ),
                stepsRu = listOf(
                    "Встань, сжимая два блина или гантели у груди.",
                    "Сильно сдавливай их, включая грудь.",
                    "Выжми прямо вперёд до выпрямления рук.",
                    "Вернись к груди, сохраняя постоянное сжатие."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "pendlay_row",
            aliases = setOf("dead_stop_row"),
            name = LocalizedText("Pendlay Row", "Тяга Пендлея"),
            muscleGroups = listOf("lats", "upper_back", "rear_delts"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Reset the bar on the floor each rep from a flat-back position.",
                "Опускай штангу на пол каждое повторение из положения с ровной спиной."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A strict barbell row from the floor each rep, emphasizing explosive back strength.",
                    "Строгая тяга штанги с пола каждое повторение с акцентом на взрывную силу спины."
                ),
                focusEn = listOf("Lats", "Upper back", "Rear delts"),
                focusRu = listOf("Широчайшие", "Верх спины", "Задние дельты"),
                keyCue = LocalizedText(
                    "Keep your torso parallel and explode the bar to your lower chest.",
                    "Держи корпус параллельно и взрывно тяни штангу к низу груди."
                ),
                stepsEn = listOf(
                    "Hinge over a barbell with your torso near parallel to the floor.",
                    "Grip just outside your knees with a flat back.",
                    "Pull the bar explosively to your lower chest.",
                    "Lower it fully to the floor and reset before the next rep."
                ),
                stepsRu = listOf(
                    "Наклонись над штангой, корпус почти параллелен полу.",
                    "Возьмись чуть шире колен, спина ровная.",
                    "Взрывно потяни штангу к низу груди.",
                    "Опусти полностью на пол и переустановись перед следующим повтором."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "seal_row",
            aliases = setOf("bench_seal_row"),
            name = LocalizedText("Seal Row", "Тяга лёжа на скамье"),
            muscleGroups = listOf("lats", "upper_back", "rear_delts"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Lie chest-down on a high bench to remove all body english.",
                "Ляг грудью на высокую скамью, чтобы исключить читинг корпусом."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A chest-supported prone row that isolates the back with zero momentum.",
                    "Тяга лёжа на груди, изолирует спину без инерции."
                ),
                focusEn = listOf("Lats", "Upper back", "Rear delts"),
                focusRu = listOf("Широчайшие", "Верх спины", "Задние дельты"),
                keyCue = LocalizedText(
                    "Pull the weight to your ribs while staying flat on the bench.",
                    "Тяни вес к рёбрам, оставаясь прижатым к скамье."
                ),
                stepsEn = listOf(
                    "Lie face down on a high bench holding weights hanging below.",
                    "Let your arms fully extend toward the floor.",
                    "Row the weights up to your ribs, driving elbows back.",
                    "Lower slowly without lifting your chest off the bench."
                ),
                stepsRu = listOf(
                    "Ляг лицом вниз на высокую скамью, отягощения свисают вниз.",
                    "Полностью выпрями руки к полу.",
                    "Подтяни вес к рёбрам, направляя локти назад.",
                    "Медленно опусти, не отрывая грудь от скамьи."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "meadows_row",
            aliases = setOf("landmine_row"),
            name = LocalizedText("Landmine Row", "Тяга в наклоне у стойки"),
            muscleGroups = listOf("lats", "upper_back", "rear_delts"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Row one end of a barbell anchored in a corner or landmine.",
                "Тяни один конец штанги, закреплённой в углу или лендмайне."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A single-arm row using a landmine-anchored barbell for a strong lat stretch.",
                    "Одноручная тяга штанги в лендмайне с сильным растяжением широчайших."
                ),
                focusEn = listOf("Lats", "Upper back", "Rear delts"),
                focusRu = listOf("Широчайшие", "Верх спины", "Задние дельты"),
                keyCue = LocalizedText(
                    "Stagger your stance and pull the bar end up along your side.",
                    "Поставь ноги в разножку и тяни конец штанги вдоль бока."
                ),
                stepsEn = listOf(
                    "Anchor one end of a barbell and load the other with plates.",
                    "Stagger your stance and hinge over, gripping near the sleeve.",
                    "Row the loaded end up along your side, elbow high.",
                    "Lower under a full stretch and repeat, then switch sides."
                ),
                stepsRu = listOf(
                    "Закрепи один конец штанги, второй нагрузи блинами.",
                    "Поставь ноги в разножку, наклонись, возьмись у втулки.",
                    "Тяни нагруженный конец вдоль бока, локоть высоко.",
                    "Опусти до полного растяжения и повтори, затем смени сторону."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "renegade_row",
            aliases = setOf("plank_row", "dumbbell_plank_row"),
            name = LocalizedText("Renegade Row", "Тяга в планке"),
            muscleGroups = listOf("lats", "core", "rear_delts"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Row from a plank on dumbbells, resisting the urge to rotate.",
                "Тяни из планки на гантелях, сопротивляясь развороту корпуса."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A plank row on dumbbells combining back work with heavy anti-rotation core demand.",
                    "Тяга из планки на гантелях: работа спины плюс мощная антиротация кора."
                ),
                focusEn = listOf("Lats", "Core", "Rear delts"),
                focusRu = listOf("Широчайшие", "Кор", "Задние дельты"),
                keyCue = LocalizedText(
                    "Widen your feet and keep your hips level as you row each side.",
                    "Расставь стопы шире и держи таз ровным, поднимая каждую руку."
                ),
                stepsEn = listOf(
                    "Get into a plank gripping a dumbbell in each hand, feet wide.",
                    "Brace your core hard to stop your hips from twisting.",
                    "Row one dumbbell to your ribs while balancing on the other.",
                    "Lower it and alternate sides, keeping hips square."
                ),
                stepsRu = listOf(
                    "Прими планку с гантелями в руках, стопы шире.",
                    "Сильно напряги кор, чтобы таз не разворачивался.",
                    "Подтяни одну гантель к рёбрам, опираясь на вторую.",
                    "Опусти и чередуй стороны, держа таз ровно."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "reverse_fly",
            aliases = setOf("rear_delt_fly", "bent_over_fly"),
            name = LocalizedText("Reverse Fly", "Разведение в наклоне"),
            muscleGroups = listOf("rear_delts", "upper_back"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Use light dumbbells and lead with your elbows.",
                "Используй лёгкие гантели и веди движение локтями."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A reverse fly that isolates the rear delts and upper-back for posture and balance.",
                    "Разведение в наклоне для изоляции задних дельт и верха спины, для осанки."
                ),
                focusEn = listOf("Rear delts", "Upper back"),
                focusRu = listOf("Задние дельты", "Верх спины"),
                keyCue = LocalizedText(
                    "Squeeze your shoulder blades and raise the weights out to the sides.",
                    "Сводя лопатки, поднимай отягощения в стороны."
                ),
                stepsEn = listOf(
                    "Hinge forward with light dumbbells hanging below your chest.",
                    "Keep a soft bend in your elbows and a flat back.",
                    "Raise the dumbbells out to your sides until level with your shoulders.",
                    "Lower slowly, keeping tension on the rear delts."
                ),
                stepsRu = listOf(
                    "Наклонись вперёд, лёгкие гантели свисают под грудью.",
                    "Сохрани лёгкий сгиб локтей и ровную спину.",
                    "Разведи гантели в стороны до уровня плеч.",
                    "Медленно опусти, сохраняя напряжение задних дельт."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "straight_arm_pulldown",
            aliases = setOf("straight_arm_pushdown", "lat_pushdown"),
            name = LocalizedText("Straight-Arm Pulldown", "Пуловер на блоке прямыми руками"),
            muscleGroups = listOf("lats", "teres_major"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Keep arms nearly straight and drive the bar down with the lats.",
                "Держи руки почти прямыми и опускай штангу широчайшими."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A straight-arm cable pulldown that isolates the lats without biceps involvement.",
                    "Пуловер на блоке прямыми руками для изоляции широчайших без бицепса."
                ),
                focusEn = listOf("Lats", "Teres major"),
                focusRu = listOf("Широчайшие", "Большая круглая"),
                keyCue = LocalizedText(
                    "Sweep the bar down to your thighs with only your lats.",
                    "Опускай штангу к бёдрам только за счёт широчайших."
                ),
                stepsEn = listOf(
                    "Stand facing a high cable, gripping the bar with straight arms.",
                    "Hinge slightly and brace your core.",
                    "Pull the bar down to your thighs, arms nearly straight.",
                    "Return slowly to a full overhead stretch."
                ),
                stepsRu = listOf(
                    "Встань лицом к верхнему блоку, возьмись за штангу прямыми руками.",
                    "Слегка наклонись и напряги кор.",
                    "Опусти штангу к бёдрам, руки почти прямые.",
                    "Медленно вернись до полного растяжения над головой."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "neutral_grip_pullup",
            aliases = setOf("hammer_pullup", "parallel_grip_pullup"),
            name = LocalizedText("Neutral-Grip Pull-Up", "Подтягивания нейтральным хватом"),
            muscleGroups = listOf("lats", "biceps", "upper_back"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Palms face each other; often the easiest pull-up on the joints.",
                "Ладони смотрят друг на друга; часто самый щадящий для суставов вариант."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A pull-up with palms facing each other, easy on the shoulders and strong on the lats.",
                    "Подтягивания ладонями друг к другу: щадит плечи и хорошо грузит широчайшие."
                ),
                focusEn = listOf("Lats", "Biceps", "Upper back"),
                focusRu = listOf("Широчайшие", "Бицепс", "Верх спины"),
                keyCue = LocalizedText(
                    "Pull your chest to the bar keeping your elbows close to your body.",
                    "Тяни грудь к перекладине, держа локти близко к телу."
                ),
                stepsEn = listOf(
                    "Grip parallel handles with palms facing each other.",
                    "Hang with straight arms and set your shoulders down.",
                    "Pull your chest toward the handles, elbows driving down.",
                    "Lower with control to a full hang."
                ),
                stepsRu = listOf(
                    "Возьмись за параллельные рукояти ладонями друг к другу.",
                    "Повисни на прямых руках, опусти плечи.",
                    "Тяни грудь к рукоятям, локти идут вниз.",
                    "Подконтрольно опустись в полный вис."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "negative_pullup",
            aliases = setOf("eccentric_pullup"),
            name = LocalizedText("Negative Pull-Up", "Негативные подтягивания"),
            muscleGroups = listOf("lats", "biceps", "upper_back"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Jump to the top and lower as slowly as possible to build pull-ups.",
                "Запрыгни в верхнюю точку и опускайся максимально медленно."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An eccentric-only pull-up: jump to the top and lower slowly to build strength.",
                    "Только негативная фаза подтягивания: запрыгни наверх и опускайся медленно."
                ),
                focusEn = listOf("Lats", "Biceps", "Upper back"),
                focusRu = listOf("Широчайшие", "Бицепс", "Верх спины"),
                keyCue = LocalizedText(
                    "Fight gravity the whole way down, taking 3-5 seconds per rep.",
                    "Сопротивляйся весь путь вниз, по 3-5 секунд на повтор."
                ),
                stepsEn = listOf(
                    "Jump or step up so your chin is above the bar.",
                    "Grip the bar and hold the top position briefly.",
                    "Lower yourself as slowly as you can to a full hang.",
                    "Reset at the top and repeat."
                ),
                stepsRu = listOf(
                    "Запрыгни или шагни так, чтобы подбородок был выше перекладины.",
                    "Возьмись за перекладину и задержись наверху.",
                    "Опускайся максимально медленно до полного виса.",
                    "Вернись наверх и повтори."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "preacher_curl",
            aliases = setOf("scott_curl"),
            name = LocalizedText("Preacher Curl", "Сгибания на скамье Скотта"),
            muscleGroups = listOf("biceps", "brachialis"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Rest your arms on the pad to eliminate swinging.",
                "Положи руки на подушку, чтобы исключить раскачку."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A preacher curl on an angled pad that strictly isolates the biceps.",
                    "Сгибания на наклонной подушке для строгой изоляции бицепса."
                ),
                focusEn = listOf("Biceps", "Brachialis"),
                focusRu = listOf("Бицепс", "Плечевая"),
                keyCue = LocalizedText(
                    "Keep the backs of your arms glued to the pad the whole set.",
                    "Держи заднюю часть рук прижатой к подушке весь подход."
                ),
                stepsEn = listOf(
                    "Sit at a preacher bench with the backs of your arms on the pad.",
                    "Grip the weight with your arms extended down the pad.",
                    "Curl the weight up until your biceps are fully contracted.",
                    "Lower slowly without letting your arms lift off the pad."
                ),
                stepsRu = listOf(
                    "Сядь за скамью Скотта, задняя часть рук на подушке.",
                    "Возьми вес, руки вытянуты вдоль подушки.",
                    "Согни руки до полного сокращения бицепса.",
                    "Медленно опусти, не отрывая руки от подушки."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "spider_curl",
            aliases = setOf("prone_incline_curl"),
            name = LocalizedText("Spider Curl", "Сгибания на наклонной вниз"),
            muscleGroups = listOf("biceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Lie chest-down on an incline so your arms hang straight down.",
                "Ляг грудью на наклонную, чтобы руки свисали строго вниз."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A spider curl on an incline bench for constant tension on the biceps peak.",
                    "Сгибания лёжа на наклонной для постоянного напряжения на пике бицепса."
                ),
                focusEn = listOf("Biceps"),
                focusRu = listOf("Бицепс"),
                keyCue = LocalizedText(
                    "Keep your upper arms vertical and curl straight up.",
                    "Держи плечи вертикально и сгибай строго вверх."
                ),
                stepsEn = listOf(
                    "Lie chest-down on an incline bench, arms hanging straight down.",
                    "Hold dumbbells with your palms facing forward.",
                    "Curl the weights up while keeping your upper arms still.",
                    "Lower slowly to a full stretch."
                ),
                stepsRu = listOf(
                    "Ляг грудью на наклонную скамью, руки свисают вниз.",
                    "Держи гантели ладонями вперёд.",
                    "Согни руки, удерживая плечи неподвижными.",
                    "Медленно опусти до полного растяжения."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "zottman_curl",
            aliases = emptySet(),
            name = LocalizedText("Zottman Curl", "Сгибания Зоттмана"),
            muscleGroups = listOf("biceps", "forearms", "brachialis"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Curl up palms-up, then rotate to palms-down on the way down.",
                "Поднимай ладонями вверх, затем разворачивай ладонями вниз при опускании."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A curl that flips the grip at the top to hit both biceps and forearms.",
                    "Сгибание со сменой хвата наверху для проработки бицепса и предплечий."
                ),
                focusEn = listOf("Biceps", "Forearms", "Brachialis"),
                focusRu = listOf("Бицепс", "Предплечья", "Плечевая"),
                keyCue = LocalizedText(
                    "Rotate to palms-down at the top, then lower slowly.",
                    "Разверни ладони вниз наверху, затем медленно опусти."
                ),
                stepsEn = listOf(
                    "Stand with dumbbells at your sides, palms forward.",
                    "Curl them up to your shoulders.",
                    "Rotate your wrists so your palms face down.",
                    "Lower slowly, then rotate back at the bottom."
                ),
                stepsRu = listOf(
                    "Встань с гантелями по бокам, ладони вперёд.",
                    "Согни руки к плечам.",
                    "Разверни запястья ладонями вниз.",
                    "Медленно опусти, затем разверни обратно внизу."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "chin_up_hold",
            aliases = setOf("flexed_arm_hang", "chin_hold"),
            name = LocalizedText("Flexed-Arm Hang", "Вис в верхней точке"),
            muscleGroups = listOf("biceps", "lats", "forearms"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Hold your chin over the bar as long as you can.",
                "Держи подбородок над перекладиной как можно дольше."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An isometric hold at the top of a chin-up to build bicep and grip endurance.",
                    "Изометрическое удержание в верхней точке подтягивания для выносливости бицепса и хвата."
                ),
                focusEn = listOf("Biceps", "Lats", "Forearms"),
                focusRu = listOf("Бицепс", "Широчайшие", "Предплечья"),
                keyCue = LocalizedText(
                    "Keep your chin above the bar and your elbows fully bent.",
                    "Держи подбородок над перекладиной, локти полностью согнуты."
                ),
                stepsEn = listOf(
                    "Jump or pull up to a chin-over-bar position with an underhand grip.",
                    "Squeeze your biceps and lats to hold the position.",
                    "Keep your chest up and elbows fully bent.",
                    "Hold as long as form allows, then lower with control."
                ),
                stepsRu = listOf(
                    "Запрыгни или подтянись подбородком над перекладиной обратным хватом.",
                    "Сожми бицепс и широчайшие, удерживая положение.",
                    "Держи грудь поднятой, локти полностью согнуты.",
                    "Держи столько, сколько позволяет техника, затем опустись."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "overhead_dumbbell_extension",
            aliases = setOf("seated_overhead_extension", "db_overhead_extension"),
            name = LocalizedText("Overhead Dumbbell Extension", "Французский жим гантели над головой"),
            muscleGroups = listOf("triceps"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Keep your elbows tucked and pointing forward as you extend.",
                "Держи локти прижатыми и направленными вперёд при разгибании."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An overhead triceps extension that emphasizes the long head under stretch.",
                    "Разгибание над головой с акцентом на длинную головку трицепса под растяжением."
                ),
                focusEn = listOf("Triceps"),
                focusRu = listOf("Трицепс"),
                keyCue = LocalizedText(
                    "Lower the weight behind your head, then extend without flaring elbows.",
                    "Опускай вес за голову, затем разгибай, не разводя локти."
                ),
                stepsEn = listOf(
                    "Hold one dumbbell overhead with both hands, arms extended.",
                    "Keep your elbows pointing forward and tucked in.",
                    "Lower the weight behind your head by bending the elbows.",
                    "Extend back to the top, squeezing the triceps."
                ),
                stepsRu = listOf(
                    "Держи одну гантель над головой двумя руками, руки прямые.",
                    "Держи локти направленными вперёд и прижатыми.",
                    "Опусти вес за голову, сгибая локти.",
                    "Разогни обратно вверх, сжимая трицепс."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "jm_press",
            aliases = setOf("jm_bench"),
            name = LocalizedText("JM Press", "JM-жим"),
            muscleGroups = listOf("triceps", "chest"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "A hybrid of close-grip bench and skull crusher for the triceps.",
                "Гибрид жима узким хватом и французского жима для трицепса."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A barbell JM press blending a close-grip press and skull crusher to overload the triceps.",
                    "JM-жим штанги — смесь жима узким хватом и французского жима для перегрузки трицепса."
                ),
                focusEn = listOf("Triceps", "Chest"),
                focusRu = listOf("Трицепс", "Грудь"),
                keyCue = LocalizedText(
                    "Lower the bar toward your upper chin, keeping elbows forward.",
                    "Опускай штангу к верху подбородка, локти вперёд."
                ),
                stepsEn = listOf(
                    "Lie on a bench with a close grip on the barbell, arms extended.",
                    "Lower the bar toward your upper neck, elbows tracking forward.",
                    "Stop when your forearms nearly touch your biceps.",
                    "Press back up powerfully to lockout."
                ),
                stepsRu = listOf(
                    "Ляг на скамью, узкий хват на штанге, руки прямые.",
                    "Опусти штангу к верху шеи, локти идут вперёд.",
                    "Останови, когда предплечья почти коснутся бицепсов.",
                    "Мощно выжми обратно до выпрямления."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "close_grip_pushup_feet_elevated",
            aliases = setOf("decline_diamond_pushup"),
            name = LocalizedText("Decline Close-Grip Push-Up", "Узкие отжимания с возвышения"),
            muscleGroups = listOf("triceps", "chest", "front_delts"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.ARMS,
            calibrationKind = ExerciseCalibrationKind.BODYWEIGHT_REPS,
            calibrationHint = LocalizedText(
                "Feet elevated and hands close to overload the triceps.",
                "Стопы на возвышении, руки узко — для перегрузки трицепса."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A decline close-grip push-up that heavily loads the triceps with bodyweight.",
                    "Узкие отжимания с приподнятыми стопами для сильной нагрузки на трицепс."
                ),
                focusEn = listOf("Triceps", "Chest", "Front delts"),
                focusRu = listOf("Трицепс", "Грудь", "Передние дельты"),
                keyCue = LocalizedText(
                    "Hands under your chest, elbows tracking straight back.",
                    "Кисти под грудью, локти строго назад."
                ),
                stepsEn = listOf(
                    "Place your feet on a raised surface, hands close under your chest.",
                    "Form a straight line from feet to head.",
                    "Lower your chest, keeping elbows tight to your ribs.",
                    "Press to full extension, squeezing the triceps."
                ),
                stepsRu = listOf(
                    "Поставь стопы на возвышение, кисти узко под грудью.",
                    "Держи прямую линию от стоп до головы.",
                    "Опусти грудь, локти прижаты к рёбрам.",
                    "Выжми до полного выпрямления, сжимая трицепс."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "kettlebell_goblet_squat",
            aliases = setOf("kb_goblet_squat"),
            name = LocalizedText("Kettlebell Goblet Squat", "Гоблет-присед с гирей"),
            muscleGroups = listOf("quads", "glutes", "core"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Hold the kettlebell by the horns at chest height.",
                "Держи гирю за рога на уровне груди."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A goblet squat with a kettlebell held at the chest for upright, knee-dominant work.",
                    "Гоблет-присед с гирей у груди для вертикального приседа с акцентом на колени."
                ),
                focusEn = listOf("Quads", "Glutes", "Core"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Кор"),
                keyCue = LocalizedText(
                    "Keep your chest tall and elbows inside your knees at the bottom.",
                    "Держи грудь высоко, внизу локти внутри коленей."
                ),
                stepsEn = listOf(
                    "Hold a kettlebell by the horns close to your chest.",
                    "Stand with feet shoulder-width, toes slightly out.",
                    "Squat down between your knees to full depth.",
                    "Drive up through your heels to stand tall."
                ),
                stepsRu = listOf(
                    "Держи гирю за рога у груди.",
                    "Встань, стопы на ширине плеч, носки слегка врозь.",
                    "Присядь между коленями до полной глубины.",
                    "Толкни через пятки и выпрямись."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "kettlebell_clean",
            aliases = setOf("kb_clean"),
            name = LocalizedText("Kettlebell Clean", "Взятие гири на грудь"),
            muscleGroups = listOf("glutes", "hamstrings", "shoulders"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.LOWER_BODY,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Guide the bell up smoothly to the rack without banging your wrist.",
                "Плавно выведи гирю в стойку, не отбивая запястье."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An explosive hinge that racks a kettlebell to the shoulder in one smooth motion.",
                    "Взрывное шарнирное движение, выводящее гирю в стойку на плечо одним движением."
                ),
                focusEn = listOf("Glutes", "Hamstrings", "Shoulders"),
                focusRu = listOf("Ягодицы", "Бицепс бедра", "Плечи"),
                keyCue = LocalizedText(
                    "Drive with your hips and keep the bell close to your body.",
                    "Толкай бёдрами и держи гирю близко к телу."
                ),
                stepsEn = listOf(
                    "Hinge and grip a kettlebell between your legs.",
                    "Explosively extend your hips to pull the bell up.",
                    "Guide it around your wrist into the rack at your shoulder.",
                    "Lower it back down along the same path and repeat."
                ),
                stepsRu = listOf(
                    "Наклонись и возьми гирю между ног.",
                    "Взрывно разогни бёдра, поднимая гирю.",
                    "Проведи её вокруг запястья в стойку на плече.",
                    "Опусти по той же траектории и повтори."
                ),
                defaultRestSeconds = 75
            )
        ),
        ExerciseDefinition(
            id = "kettlebell_snatch",
            aliases = setOf("kb_snatch", "one_arm_snatch"),
            name = LocalizedText("Kettlebell Snatch", "Рывок гири"),
            muscleGroups = listOf("glutes", "shoulders", "hamstrings"),
            difficulty = 4,
            visualFamily = ExerciseVisualFamily.OVERHEAD,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "An advanced ballistic lift; master the clean and swing first.",
                "Продвинутое баллистическое движение; сначала освой взятие и мах."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A ballistic kettlebell lift from the floor to overhead in one motion.",
                    "Баллистический подъём гири с пола над головой одним движением."
                ),
                focusEn = listOf("Glutes", "Shoulders", "Hamstrings"),
                focusRu = listOf("Ягодицы", "Плечи", "Бицепс бедра"),
                keyCue = LocalizedText(
                    "Punch your hand up through the bell as it reaches the top.",
                    "Пробей рукой сквозь гирю, когда она достигает верха."
                ),
                stepsEn = listOf(
                    "Hinge and grip a kettlebell with one hand between your legs.",
                    "Explosively extend your hips to drive the bell upward.",
                    "Punch your hand up as the bell rotates over your wrist to lockout.",
                    "Guide it back down into the next hinge and repeat."
                ),
                stepsRu = listOf(
                    "Наклонись и возьми гирю одной рукой между ног.",
                    "Взрывно разогни бёдра, направляя гирю вверх.",
                    "Пробей рукой вверх, гиря разворачивается на запястье до фиксации.",
                    "Опусти её обратно в следующий наклон и повтори."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "turkish_getup",
            aliases = setOf("get_up", "tgu"),
            name = LocalizedText("Turkish Get-Up", "Турецкий подъём"),
            muscleGroups = listOf("shoulders", "core", "glutes"),
            difficulty = 4,
            visualFamily = ExerciseVisualFamily.OVERHEAD,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Move slowly through each step, keeping the weight locked overhead.",
                "Двигайся медленно по каждому этапу, удерживая вес над головой."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A slow full-body get-up from lying to standing while holding a weight overhead.",
                    "Медленный подъём всем телом из положения лёжа в стойку с весом над головой."
                ),
                focusEn = listOf("Shoulders", "Core", "Glutes"),
                focusRu = listOf("Плечи", "Кор", "Ягодицы"),
                keyCue = LocalizedText(
                    "Keep your eyes on the weight and your arm vertical the whole time.",
                    "Смотри на вес и держи руку вертикально всё время."
                ),
                stepsEn = listOf(
                    "Lie on your back pressing a weight up with one arm.",
                    "Roll to your forearm, then your hand, and lift your hips.",
                    "Sweep your leg through to a kneeling position and stand up.",
                    "Reverse each step precisely to return to the floor."
                ),
                stepsRu = listOf(
                    "Ляг на спину, выжми вес одной рукой вверх.",
                    "Перекатись на предплечье, затем на кисть, подними таз.",
                    "Проведи ногу назад в положение на колене и встань.",
                    "Точно повтори все шаги в обратном порядке."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "kettlebell_high_pull",
            aliases = setOf("kb_high_pull"),
            name = LocalizedText("Kettlebell High Pull", "Высокая тяга гири"),
            muscleGroups = listOf("upper_back", "rear_delts", "glutes"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.PULL,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Drive with the hips and pull the bell to chest height, elbow high.",
                "Толкай бёдрами и тяни гирю к груди, локоть высоко."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A hip-powered high pull that trains the upper back and conditioning at once.",
                    "Высокая тяга за счёт бёдер, тренирует верх спины и выносливость одновременно."
                ),
                focusEn = listOf("Upper back", "Rear delts", "Glutes"),
                focusRu = listOf("Верх спины", "Задние дельты", "Ягодицы"),
                keyCue = LocalizedText(
                    "Let the hips do the work; the arm just guides the bell up.",
                    "Работу делают бёдра; рука лишь направляет гирю вверх."
                ),
                stepsEn = listOf(
                    "Set up like a swing with a kettlebell between your legs.",
                    "Drive your hips to float the bell upward.",
                    "Pull your elbow high and back as the bell reaches chest height.",
                    "Guide it back down into the next hinge."
                ),
                stepsRu = listOf(
                    "Прими стойку как для маха, гиря между ног.",
                    "Толкни бёдрами, поднимая гирю вверх.",
                    "Подтяни локоть высоко и назад, когда гиря на уровне груди.",
                    "Проведи её обратно в следующий наклон."
                ),
                defaultRestSeconds = 60
            )
        ),
        ExerciseDefinition(
            id = "squat_thrust",
            aliases = setOf("no_pushup_burpee", "half_burpee"),
            name = LocalizedText("Squat Thrust", "Выброс ног в упор"),
            muscleGroups = listOf("full_body", "core", "legs"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "A burpee without the push-up or jump; great low-impact conditioning.",
                "Бёрпи без отжимания и прыжка; хорошее кардио с низкой ударной нагрузкой."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A burpee variation that kicks the feet out and back without the push-up or jump.",
                    "Вариант бёрпи с выбросом ног в упор и обратно без отжимания и прыжка."
                ),
                focusEn = listOf("Full body", "Core", "Legs"),
                focusRu = listOf("Всё тело", "Кор", "Ноги"),
                keyCue = LocalizedText(
                    "Jump your feet straight back into a plank, then back in.",
                    "Выбрасывай ноги строго назад в планку, затем возвращай."
                ),
                stepsEn = listOf(
                    "Squat down and place your hands on the floor.",
                    "Jump both feet back into a plank position.",
                    "Jump your feet back toward your hands.",
                    "Stand up and repeat at a steady pace."
                ),
                stepsRu = listOf(
                    "Присядь и поставь руки на пол.",
                    "Выброси обе ноги назад в планку.",
                    "Верни ноги к рукам прыжком.",
                    "Встань и повтори в ровном темпе."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "tuck_jump",
            aliases = setOf("knee_tuck_jump"),
            name = LocalizedText("Tuck Jump", "Прыжок с подтягиванием коленей"),
            muscleGroups = listOf("quads", "calves", "core"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Explode up and pull your knees to your chest; land softly.",
                "Взрывно выпрыгивай и подтягивай колени к груди; приземляйся мягко."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An explosive plyometric jump pulling the knees to the chest for power.",
                    "Взрывной плиометрический прыжок с подтягиванием коленей к груди для мощности."
                ),
                focusEn = listOf("Quads", "Calves", "Core"),
                focusRu = listOf("Квадрицепсы", "Икры", "Кор"),
                keyCue = LocalizedText(
                    "Jump high and snap your knees up, landing with soft knees.",
                    "Прыгай высоко и резко поднимай колени, приземляясь на мягкие колени."
                ),
                stepsEn = listOf(
                    "Stand with feet hip-width and arms ready.",
                    "Dip slightly and explode straight up.",
                    "Pull your knees toward your chest at the top.",
                    "Land softly and immediately repeat."
                ),
                stepsRu = listOf(
                    "Встань, стопы на ширине бёдер, руки наготове.",
                    "Слегка присядь и взорвись строго вверх.",
                    "Подтяни колени к груди в верхней точке.",
                    "Мягко приземлись и сразу повтори."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "bear_crawl",
            aliases = setOf("bear_walk"),
            name = LocalizedText("Bear Crawl", "Медвежья походка"),
            muscleGroups = listOf("core", "shoulders", "quads"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Crawl with knees hovering just off the floor.",
                "Ползи, колени зависают чуть над полом."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A quadruped crawl that builds core stability, shoulder endurance, and conditioning.",
                    "Ползание на четвереньках для стабильности кора, выносливости плеч и кардио."
                ),
                focusEn = listOf("Core", "Shoulders", "Quads"),
                focusRu = listOf("Кор", "Плечи", "Квадрицепсы"),
                keyCue = LocalizedText(
                    "Move opposite hand and foot, keeping your hips low and level.",
                    "Двигай противоположные руку и ногу, держа таз низко и ровно."
                ),
                stepsEn = listOf(
                    "Start on hands and toes with knees hovering off the floor.",
                    "Keep your back flat and hips low.",
                    "Crawl forward moving opposite hand and foot together.",
                    "Keep your core tight and avoid rocking your hips."
                ),
                stepsRu = listOf(
                    "Встань на руки и носки, колени зависают над полом.",
                    "Держи спину ровной, таз низко.",
                    "Ползи вперёд, двигая противоположные руку и ногу вместе.",
                    "Держи кор напряжённым, не раскачивай таз."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "inchworm",
            aliases = setOf("walkout"),
            name = LocalizedText("Inchworm", "Гусеница"),
            muscleGroups = listOf("hamstrings", "core", "shoulders"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Walk your hands out to a plank and back to your feet.",
                "Прошагай руками в планку и обратно к стопам."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A dynamic walkout that mobilizes the hamstrings and builds core control.",
                    "Динамический выход в планку: мобилизует бицепс бедра и развивает контроль кора."
                ),
                focusEn = listOf("Hamstrings", "Core", "Shoulders"),
                focusRu = listOf("Бицепс бедра", "Кор", "Плечи"),
                keyCue = LocalizedText(
                    "Keep your legs fairly straight as you walk your hands out.",
                    "Держи ноги достаточно прямыми, прошагивая руками вперёд."
                ),
                stepsEn = listOf(
                    "Stand tall, then hinge and place your hands on the floor.",
                    "Walk your hands forward into a high plank.",
                    "Hold briefly, then walk your feet toward your hands.",
                    "Stand up and repeat."
                ),
                stepsRu = listOf(
                    "Встань прямо, затем наклонись и поставь руки на пол.",
                    "Прошагай руками вперёд в высокую планку.",
                    "Задержись, затем подшагни стопами к рукам.",
                    "Выпрямись и повтори."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "lateral_shuffle",
            aliases = setOf("side_shuffle", "agility_shuffle"),
            name = LocalizedText("Lateral Shuffle", "Боковые перемещения"),
            muscleGroups = listOf("glutes", "quads", "calves"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Stay low in an athletic stance and shuffle side to side.",
                "Оставайся низко в атлетической стойке и перемещайся из стороны в сторону."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A lateral agility shuffle that trains footwork, hips, and conditioning.",
                    "Боковые перемещения для работы ног, тазобедренных суставов и кардио."
                ),
                focusEn = listOf("Glutes", "Quads", "Calves"),
                focusRu = listOf("Ягодицы", "Квадрицепсы", "Икры"),
                keyCue = LocalizedText(
                    "Stay low, push off the trailing leg, and don't let your feet cross.",
                    "Держись низко, отталкивайся задней ногой, не скрещивай стопы."
                ),
                stepsEn = listOf(
                    "Get into a low athletic stance, knees bent, chest up.",
                    "Push off one leg to shuffle sideways.",
                    "Keep your feet from crossing and stay low.",
                    "Shuffle a set distance, then reverse direction."
                ),
                stepsRu = listOf(
                    "Прими низкую атлетическую стойку, колени согнуты, грудь вверх.",
                    "Оттолкнись одной ногой для перемещения вбок.",
                    "Не скрещивай стопы, оставайся низко.",
                    "Пройди заданное расстояние, затем смени направление."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "butt_kick",
            aliases = setOf("butt_kicks", "heel_flick"),
            name = LocalizedText("Butt Kicks", "Захлёст голени"),
            muscleGroups = listOf("hamstrings", "calves", "cardiovascular"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Jog in place flicking your heels up to your glutes.",
                "Бег на месте с захлёстом пяток к ягодицам."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A running-in-place drill flicking the heels to the glutes to warm up and condition.",
                    "Бег на месте с захлёстом пяток к ягодицам для разминки и кардио."
                ),
                focusEn = listOf("Hamstrings", "Calves", "Cardiovascular"),
                focusRu = listOf("Бицепс бедра", "Икры", "Кардио"),
                keyCue = LocalizedText(
                    "Stay light on your feet and flick your heels quickly.",
                    "Оставайся лёгким на стопах и быстро захлёстывай пятки."
                ),
                stepsEn = listOf(
                    "Stand tall and begin jogging in place.",
                    "Flick each heel up toward your glutes.",
                    "Keep your knees pointing down and stay on the balls of your feet.",
                    "Maintain a quick, light rhythm."
                ),
                stepsRu = listOf(
                    "Встань прямо и начни бег на месте.",
                    "Захлёстывай каждую пятку к ягодице.",
                    "Держи колени вниз и оставайся на подушечках стоп.",
                    "Сохраняй быстрый лёгкий ритм."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "shadow_boxing",
            aliases = setOf("boxing_drill"),
            name = LocalizedText("Shadow Boxing", "Бой с тенью"),
            muscleGroups = listOf("shoulders", "core", "cardiovascular"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_MINUTES,
            calibrationHint = LocalizedText(
                "Throw controlled punch combinations while staying light on your feet.",
                "Наноси контролируемые комбинации ударов, оставаясь лёгким на ногах."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A boxing conditioning drill throwing punch combinations against an imaginary opponent.",
                    "Кардио-бокс с нанесением комбинаций ударов воображаемому сопернику."
                ),
                focusEn = listOf("Shoulders", "Core", "Cardiovascular"),
                focusRu = listOf("Плечи", "Кор", "Кардио"),
                keyCue = LocalizedText(
                    "Keep your guard up, rotate through your hips, and stay mobile.",
                    "Держи руки в защите, вращайся через бёдра и двигайся."
                ),
                stepsEn = listOf(
                    "Stand in a boxing stance with your hands up.",
                    "Throw controlled jab-cross combinations.",
                    "Rotate your torso and pivot your feet with each punch.",
                    "Keep moving and vary your combinations."
                ),
                stepsRu = listOf(
                    "Встань в боксёрскую стойку, руки подняты.",
                    "Наноси контролируемые комбинации джеб-кросс.",
                    "Вращай корпус и разворачивай стопы с каждым ударом.",
                    "Продолжай двигаться и меняй комбинации."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "jumping_jack_squat",
            aliases = setOf("squat_jack"),
            name = LocalizedText("Squat Jack", "Приседания с разножкой"),
            muscleGroups = listOf("quads", "glutes", "cardiovascular"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Jump your feet wide into a squat and back together.",
                "Выпрыгивай стопами в присед и обратно вместе."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A squatting jumping jack that combines lower-body work with conditioning.",
                    "Прыжковая разножка с приседом: сочетает работу ног и кардио."
                ),
                focusEn = listOf("Quads", "Glutes", "Cardiovascular"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Кардио"),
                keyCue = LocalizedText(
                    "Land in a quarter squat each time and keep your chest up.",
                    "Приземляйся в четверть-присед и держи грудь поднятой."
                ),
                stepsEn = listOf(
                    "Stand with feet together, hands ready at your chest.",
                    "Jump your feet out wide and drop into a squat.",
                    "Jump your feet back together and stand tall.",
                    "Repeat at a steady pace."
                ),
                stepsRu = listOf(
                    "Встань, стопы вместе, руки у груди.",
                    "Выпрыгни стопами широко и опустись в присед.",
                    "Верни стопы вместе прыжком и выпрямись.",
                    "Повторяй в ровном темпе."
                ),
                defaultRestSeconds = 40
            )
        ),
        ExerciseDefinition(
            id = "sled_push",
            aliases = setOf("prowler_push"),
            name = LocalizedText("Sled Push", "Толкание саней"),
            muscleGroups = listOf("quads", "glutes", "calves"),
            difficulty = 3,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Load a sled and drive it forward with powerful strides.",
                "Нагрузи сани и толкай их вперёд мощными шагами."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A loaded sled push that builds leg drive, power, and brutal conditioning.",
                    "Толкание нагруженных саней для мощности ног и жёсткого кардио."
                ),
                focusEn = listOf("Quads", "Glutes", "Calves"),
                focusRu = listOf("Квадрицепсы", "Ягодицы", "Икры"),
                keyCue = LocalizedText(
                    "Lean into the sled and drive through each leg fully.",
                    "Наклонись на сани и толкай каждой ногой до конца."
                ),
                stepsEn = listOf(
                    "Grip the sled posts and lean your body forward.",
                    "Keep your arms firm and your core braced.",
                    "Drive forward with powerful alternating strides.",
                    "Maintain the lean and full leg extension each step."
                ),
                stepsRu = listOf(
                    "Возьмись за стойки саней и наклони тело вперёд.",
                    "Держи руки жёсткими, кор напряжён.",
                    "Толкай вперёд мощными поочерёдными шагами.",
                    "Сохраняй наклон и полное разгибание ног на каждом шаге."
                ),
                defaultRestSeconds = 90
            )
        ),
        ExerciseDefinition(
            id = "medicine_ball_slam",
            aliases = setOf("ball_slam", "med_ball_slam"),
            name = LocalizedText("Medicine Ball Slam", "Броски мяча вниз"),
            muscleGroups = listOf("core", "lats", "shoulders"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.CARDIO,
            calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
            calibrationHint = LocalizedText(
                "Raise the ball overhead and slam it down with full force.",
                "Подними мяч над головой и с силой брось вниз."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An explosive full-body slam that builds power and conditioning through the core.",
                    "Взрывной бросок всем телом для мощности и кардио через кор."
                ),
                focusEn = listOf("Core", "Lats", "Shoulders"),
                focusRu = listOf("Кор", "Широчайшие", "Плечи"),
                keyCue = LocalizedText(
                    "Reach tall, then slam the ball down hard using your whole body.",
                    "Вытянись вверх, затем с силой брось мяч вниз всем телом."
                ),
                stepsEn = listOf(
                    "Stand holding a medicine ball, feet shoulder-width.",
                    "Reach the ball overhead, extending fully.",
                    "Slam it down to the floor with maximum force.",
                    "Catch or pick it up and repeat."
                ),
                stepsRu = listOf(
                    "Встань с набивным мячом, стопы на ширине плеч.",
                    "Подними мяч над головой, полностью вытянувшись.",
                    "С максимальной силой брось его в пол.",
                    "Поймай или подними и повтори."
                ),
                defaultRestSeconds = 45
            )
        ),
        ExerciseDefinition(
            id = "downward_dog",
            aliases = setOf("down_dog"),
            name = LocalizedText("Downward Dog", "Поза собаки мордой вниз"),
            muscleGroups = listOf("hamstrings", "calves", "shoulders"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Press your hips up and back into an inverted V, heels reaching down.",
                "Толкай таз вверх и назад в перевёрнутую V, пятки тянутся вниз."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A foundational yoga pose that lengthens the hamstrings, calves, and shoulders.",
                    "Базовая поза йоги, вытягивающая бицепс бедра, икры и плечи."
                ),
                focusEn = listOf("Hamstrings", "Calves", "Shoulders"),
                focusRu = listOf("Бицепс бедра", "Икры", "Плечи"),
                keyCue = LocalizedText(
                    "Lengthen your spine and press your chest toward your thighs.",
                    "Вытяни позвоночник и тяни грудь к бёдрам."
                ),
                stepsEn = listOf(
                    "Start on hands and knees, hands slightly ahead of your shoulders.",
                    "Tuck your toes and lift your hips up and back.",
                    "Straighten your legs as much as comfortable, heels reaching down.",
                    "Hold, breathing steadily and lengthening the spine."
                ),
                stepsRu = listOf(
                    "Встань на четвереньки, кисти чуть впереди плеч.",
                    "Подверни носки и подними таз вверх и назад.",
                    "Выпрями ноги насколько удобно, пятки тянутся вниз.",
                    "Держи, дыши ровно, вытягивая позвоночник."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "cobra_stretch",
            aliases = setOf("cobra_pose", "upward_dog"),
            name = LocalizedText("Cobra Stretch", "Поза кобры"),
            muscleGroups = listOf("abs", "hip_flexors", "chest"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Press your chest up while keeping your hips on the floor.",
                "Поднимай грудь вверх, оставляя таз на полу."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A gentle back extension that opens the chest and stretches the abs and hip flexors.",
                    "Мягкое разгибание спины, раскрывающее грудь и растягивающее пресс и сгибатели бедра."
                ),
                focusEn = listOf("Abs", "Hip flexors", "Chest"),
                focusRu = listOf("Пресс", "Сгибатели бедра", "Грудь"),
                keyCue = LocalizedText(
                    "Lift through your chest, not by pushing hard with your arms.",
                    "Поднимайся грудью, а не за счёт сильного толчка руками."
                ),
                stepsEn = listOf(
                    "Lie face down with hands under your shoulders.",
                    "Press gently to lift your chest off the floor.",
                    "Keep your hips and legs relaxed on the ground.",
                    "Hold, breathing into the stretch, then lower slowly."
                ),
                stepsRu = listOf(
                    "Ляг лицом вниз, ладони под плечами.",
                    "Мягко поднимись, отрывая грудь от пола.",
                    "Держи таз и ноги расслабленными на полу.",
                    "Держи, дыша в растяжение, затем медленно опустись."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "childs_pose",
            aliases = setOf("child_pose", "balasana"),
            name = LocalizedText("Child's Pose", "Поза ребёнка"),
            muscleGroups = listOf("lower_back", "lats", "hips"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Sit back onto your heels and reach your arms forward to relax the back.",
                "Сядь на пятки и вытяни руки вперёд, расслабляя спину."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A restful stretch that decompresses the lower back and opens the hips.",
                    "Восстановительная растяжка, разгружающая поясницу и раскрывающая бёдра."
                ),
                focusEn = listOf("Lower back", "Lats", "Hips"),
                focusRu = listOf("Поясница", "Широчайшие", "Бёдра"),
                keyCue = LocalizedText(
                    "Relax your torso down between your thighs and breathe deeply.",
                    "Расслабь корпус между бёдрами и глубоко дыши."
                ),
                stepsEn = listOf(
                    "Kneel and sit your hips back onto your heels.",
                    "Fold your torso forward and reach your arms out in front.",
                    "Rest your forehead on the floor and relax.",
                    "Breathe deeply and hold, feeling the back lengthen."
                ),
                stepsRu = listOf(
                    "Встань на колени и сядь тазом на пятки.",
                    "Наклони корпус вперёд и вытяни руки перед собой.",
                    "Опусти лоб на пол и расслабься.",
                    "Глубоко дыши и держи, чувствуя вытяжение спины."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "thoracic_rotation",
            aliases = setOf("open_book", "t_spine_rotation"),
            name = LocalizedText("Thoracic Rotation", "Ротация грудного отдела"),
            muscleGroups = listOf("upper_back", "obliques", "chest"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Open your top arm across to the other side, following it with your eyes.",
                "Раскрывай верхнюю руку в другую сторону, следя за ней глазами."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "An open-book rotation that improves upper-back mobility and posture.",
                    "Ротация типа книжка для подвижности грудного отдела и осанки."
                ),
                focusEn = listOf("Upper back", "Obliques", "Chest"),
                focusRu = listOf("Верх спины", "Косые", "Грудь"),
                keyCue = LocalizedText(
                    "Keep your knees stacked and rotate only through your upper back.",
                    "Держи колени сложенными и вращайся только грудным отделом."
                ),
                stepsEn = listOf(
                    "Lie on your side with knees bent and arms stacked in front.",
                    "Keep your knees together and pinned down.",
                    "Open your top arm across your body toward the floor behind you.",
                    "Follow your hand with your eyes, then return and repeat."
                ),
                stepsRu = listOf(
                    "Ляг на бок, колени согнуты, руки сложены впереди.",
                    "Держи колени вместе и прижатыми.",
                    "Раскрой верхнюю руку через тело к полу позади себя.",
                    "Следи за рукой глазами, затем вернись и повтори."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "pigeon_stretch",
            aliases = setOf("pigeon_pose"),
            name = LocalizedText("Pigeon Stretch", "Поза голубя"),
            muscleGroups = listOf("glutes", "hips", "hip_flexors"),
            difficulty = 2,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Bring one shin forward and sink your hips to stretch the glute.",
                "Выведи одну голень вперёд и опусти таз, растягивая ягодицу."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A deep hip-opener that stretches the glutes and external hip rotators.",
                    "Глубокое раскрытие бедра, растягивающее ягодицы и наружные ротаторы бедра."
                ),
                focusEn = listOf("Glutes", "Hips", "Hip flexors"),
                focusRu = listOf("Ягодицы", "Бёдра", "Сгибатели бедра"),
                keyCue = LocalizedText(
                    "Keep your hips square and fold forward over the front shin.",
                    "Держи таз ровно и наклоняйся вперёд над передней голенью."
                ),
                stepsEn = listOf(
                    "From all fours, bring one shin forward across your body.",
                    "Extend the other leg straight back behind you.",
                    "Keep your hips square and lower them toward the floor.",
                    "Fold forward over the front leg and hold, then switch."
                ),
                stepsRu = listOf(
                    "Из положения на четвереньках выведи одну голень вперёд поперёк тела.",
                    "Вытяни другую ногу прямо назад.",
                    "Держи таз ровно и опусти его к полу.",
                    "Наклонись вперёд над передней ногой и держи, затем смени."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "shoulder_dislocate",
            aliases = setOf("band_dislocate", "shoulder_passthrough"),
            name = LocalizedText("Shoulder Pass-Through", "Выкрут плеч"),
            muscleGroups = listOf("shoulders", "chest", "upper_back"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Use a band or towel wide and pass it over your head and behind you.",
                "Возьми резину или полотенце широко и проведи над головой за спину."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A shoulder mobility drill passing a band overhead and behind to open the shoulders.",
                    "Упражнение на подвижность плеч: проведение резины над головой за спину."
                ),
                focusEn = listOf("Shoulders", "Chest", "Upper back"),
                focusRu = listOf("Плечи", "Грудь", "Верх спины"),
                keyCue = LocalizedText(
                    "Keep your arms straight and grip wide enough to move without pain.",
                    "Держи руки прямыми и берись достаточно широко, чтобы не было боли."
                ),
                stepsEn = listOf(
                    "Hold a band or towel with a wide overhand grip in front of you.",
                    "Keep your arms straight throughout.",
                    "Raise it overhead and continue behind your back.",
                    "Return slowly along the same path."
                ),
                stepsRu = listOf(
                    "Возьми резину или полотенце широким верхним хватом перед собой.",
                    "Держи руки прямыми всё время.",
                    "Подними над головой и проведи за спину.",
                    "Медленно вернись по той же траектории."
                ),
                defaultRestSeconds = 30
            )
        ),
        ExerciseDefinition(
            id = "neck_stretch",
            aliases = setOf("neck_mobility", "neck_release"),
            name = LocalizedText("Neck Stretch", "Растяжка шеи"),
            muscleGroups = listOf("neck", "upper_traps"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Gently tilt your head to each side and hold.",
                "Мягко наклоняй голову в каждую сторону и удерживай."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A gentle neck mobility routine to relieve tension in the upper traps.",
                    "Мягкая мобилизация шеи для снятия напряжения в верхних трапециях."
                ),
                focusEn = listOf("Neck", "Upper traps"),
                focusRu = listOf("Шея", "Верхние трапеции"),
                keyCue = LocalizedText(
                    "Move slowly and never force the stretch.",
                    "Двигайся медленно и никогда не форсируй растяжку."
                ),
                stepsEn = listOf(
                    "Sit or stand tall with relaxed shoulders.",
                    "Gently tilt your head toward one shoulder and hold.",
                    "Return to center, then tilt to the other side.",
                    "Add gentle rotations, keeping movements slow."
                ),
                stepsRu = listOf(
                    "Сядь или встань прямо, плечи расслаблены.",
                    "Мягко наклони голову к одному плечу и удержи.",
                    "Вернись в центр, затем наклони в другую сторону.",
                    "Добавь мягкие повороты, двигаясь медленно."
                ),
                defaultRestSeconds = 20
            )
        ),
        ExerciseDefinition(
            id = "ankle_mobility",
            aliases = setOf("ankle_rocks", "knee_to_wall"),
            name = LocalizedText("Ankle Mobility Rock", "Мобилизация голеностопа"),
            muscleGroups = listOf("calves", "ankles"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Rock your knee forward over your toes with the heel down.",
                "Двигай колено вперёд над носком, пятка на полу."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A knee-to-wall drill that improves ankle dorsiflexion for squats and lunges.",
                    "Упражнение колено к стене для улучшения тыльного сгибания голеностопа в приседах."
                ),
                focusEn = listOf("Calves", "Ankles"),
                focusRu = listOf("Икры", "Голеностоп"),
                keyCue = LocalizedText(
                    "Keep your heel flat and drive your knee forward over your toes.",
                    "Держи пятку на полу и двигай колено вперёд над носком."
                ),
                stepsEn = listOf(
                    "Kneel in a half-kneeling stance facing a wall.",
                    "Place your front foot a few inches from the wall.",
                    "Drive your knee forward to touch the wall, heel staying down.",
                    "Rock back and repeat, then switch sides."
                ),
                stepsRu = listOf(
                    "Прими стойку на одном колене лицом к стене.",
                    "Поставь переднюю стопу в нескольких сантиметрах от стены.",
                    "Двигай колено вперёд до касания стены, пятка на полу.",
                    "Откатись назад и повтори, затем смени сторону."
                ),
                defaultRestSeconds = 20
            )
        ),
        ExerciseDefinition(
            id = "hip_circle",
            aliases = setOf("hip_rotations", "standing_hip_circle"),
            name = LocalizedText("Standing Hip Circle", "Круговые движения бёдрами"),
            muscleGroups = listOf("hips", "glutes", "core"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Circle each knee out and around to loosen the hips.",
                "Вращай каждое колено наружу по кругу, разминая бёдра."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A standing hip circle drill that warms up and mobilizes the hip joints.",
                    "Круговые движения бёдрами стоя для разминки и мобилизации тазобедренных суставов."
                ),
                focusEn = listOf("Hips", "Glutes", "Core"),
                focusRu = listOf("Бёдра", "Ягодицы", "Кор"),
                keyCue = LocalizedText(
                    "Lift the knee and draw a big smooth circle with it.",
                    "Подними колено и рисуй им большой плавный круг."
                ),
                stepsEn = listOf(
                    "Stand tall, holding a wall for balance if needed.",
                    "Lift one knee up in front of you.",
                    "Circle the knee out to the side and back, drawing a big circle.",
                    "Repeat for reps, then reverse and switch legs."
                ),
                stepsRu = listOf(
                    "Встань прямо, при необходимости держись за стену.",
                    "Подними одно колено перед собой.",
                    "Веди колено в сторону и назад, рисуя большой круг.",
                    "Повтори, затем измени направление и смени ногу."
                ),
                defaultRestSeconds = 20
            )
        ),
        ExerciseDefinition(
            id = "leg_swing",
            aliases = setOf("dynamic_leg_swing"),
            name = LocalizedText("Leg Swings", "Махи ногами"),
            muscleGroups = listOf("hips", "hamstrings", "glutes"),
            difficulty = 1,
            visualFamily = ExerciseVisualFamily.GENERIC,
            calibrationKind = ExerciseCalibrationKind.DURATION_SECONDS,
            calibrationHint = LocalizedText(
                "Swing one leg forward and back, then side to side.",
                "Махай одной ногой вперёд-назад, затем из стороны в сторону."
            ),
            imagePrompt = "",
            technique = ExerciseTechnique(
                summary = LocalizedText(
                    "A dynamic warm-up swinging the legs to open the hips before training.",
                    "Динамическая разминка махами ногами для раскрытия бёдер перед тренировкой."
                ),
                focusEn = listOf("Hips", "Hamstrings", "Glutes"),
                focusRu = listOf("Бёдра", "Бицепс бедра", "Ягодицы"),
                keyCue = LocalizedText(
                    "Keep your torso tall and let the leg swing freely.",
                    "Держи корпус прямо и позволь ноге свободно махать."
                ),
                stepsEn = listOf(
                    "Stand tall, holding a wall or post for balance.",
                    "Swing one leg forward and back in a controlled arc.",
                    "After a set, switch to side-to-side swings.",
                    "Keep your torso still and repeat on the other leg."
                ),
                stepsRu = listOf(
                    "Встань прямо, держась за стену или опору.",
                    "Махай одной ногой вперёд и назад контролируемой дугой.",
                    "После подхода перейди к махам из стороны в сторону.",
                    "Держи корпус неподвижным и повтори другой ногой."
                ),
                defaultRestSeconds = 20
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
