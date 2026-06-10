package com.vtempe.shared.domain.exercise

/**
 * Lightweight client-side catalog of all 107 exercises for the browse/library screen.
 *
 * Mirrors the server's BuiltInExerciseCatalog metadata (id, training modes, muscle group,
 * difficulty) but without resolver logic. Used purely for display: filtering by where the
 * user trains (home / gym / outdoor) and by difficulty.
 *
 * Names are bilingual; full technique steps live in ExerciseLibrary for the subset that has them.
 */

enum class TrainMode { GYM, HOME, OUTDOOR }

enum class MuscleGroup(val en: String, val ru: String) {
    LEGS("Legs", "Ноги"),
    GLUTES("Glutes & Hamstrings", "Ягодицы и бицепс бедра"),
    CHEST("Chest", "Грудь"),
    BACK("Back", "Спина"),
    SHOULDERS("Shoulders", "Плечи"),
    BICEPS("Biceps", "Бицепс"),
    TRICEPS("Triceps", "Трицепс"),
    CORE("Core", "Пресс и кор"),
    CARDIO("Cardio", "Кардио"),
    MOBILITY("Mobility", "Мобильность")
}

data class BrowseExercise(
    val id: String,
    val nameEn: String,
    val nameRu: String,
    val muscle: MuscleGroup,
    val modes: Set<TrainMode>,
    val difficulty: Int  // 1 beginner … 5 elite
) {
    fun name(localeTag: String?): String =
        if (localeTag?.lowercase()?.startsWith("ru") == true) nameRu else nameEn
}

object ExerciseBrowseCatalog {

    private val G = TrainMode.GYM
    private val H = TrainMode.HOME
    private val O = TrainMode.OUTDOOR

    val all: List<BrowseExercise> = listOf(
        // ── LEGS ────────────────────────────────────────────────────────────
        BrowseExercise("squat", "Back Squat", "Приседания со штангой", MuscleGroup.LEGS, setOf(G), 3),
        BrowseExercise("goblet_squat", "Goblet Squat", "Гоблет-присед", MuscleGroup.LEGS, setOf(G, H), 2),
        BrowseExercise("front_squat", "Front Squat", "Фронтальный присед", MuscleGroup.LEGS, setOf(G), 4),
        BrowseExercise("leg_press", "Leg Press", "Жим ногами", MuscleGroup.LEGS, setOf(G), 1),
        BrowseExercise("sumo_squat", "Sumo Squat", "Присед сумо", MuscleGroup.LEGS, setOf(G, H, O), 2),
        BrowseExercise("wall_sit", "Wall Sit", "Стульчик у стены", MuscleGroup.LEGS, setOf(H, O, G), 1),
        BrowseExercise("box_squat", "Box Squat", "Присед на ящик", MuscleGroup.LEGS, setOf(G), 3),
        BrowseExercise("hack_squat", "Hack Squat (machine)", "Гакк-присед (тренажёр)", MuscleGroup.LEGS, setOf(G), 1),
        BrowseExercise("barbell_hack_squat", "Barbell Hack Squat", "Гакк-присед со штангой", MuscleGroup.LEGS, setOf(G), 3),
        BrowseExercise("leg_extension", "Leg Extension", "Разгибание ног", MuscleGroup.LEGS, setOf(G), 1),
        BrowseExercise("jump_squat", "Jump Squat", "Выпрыгивания из приседа", MuscleGroup.LEGS, setOf(H, O, G), 2),
        BrowseExercise("lunge", "Lunge", "Выпады", MuscleGroup.LEGS, setOf(G, H, O), 2),
        BrowseExercise("reverse_lunge", "Reverse Lunge", "Обратные выпады", MuscleGroup.LEGS, setOf(H, G, O), 2),
        BrowseExercise("bulgarian_split_squat", "Bulgarian Split Squat", "Болгарский присед", MuscleGroup.LEGS, setOf(H, G), 4),
        BrowseExercise("step_up", "Step-Up", "Зашагивания на возвышение", MuscleGroup.LEGS, setOf(H, G, O), 2),
        BrowseExercise("lateral_lunge", "Lateral Lunge", "Боковые выпады", MuscleGroup.LEGS, setOf(H, G, O), 2),
        BrowseExercise("pistol_squat", "Pistol Squat", "Пистолетик", MuscleGroup.LEGS, setOf(H, G, O), 5),
        BrowseExercise("skater_lunge", "Skater Lunge", "Конькобежные выпады", MuscleGroup.LEGS, setOf(H, G, O), 3),

        // ── GLUTES & HAMSTRINGS ─────────────────────────────────────────────
        BrowseExercise("deadlift", "Deadlift", "Становая тяга", MuscleGroup.GLUTES, setOf(G), 3),
        BrowseExercise("romanian_deadlift", "Romanian Deadlift", "Румынская тяга", MuscleGroup.GLUTES, setOf(G, H), 3),
        BrowseExercise("hip_thrust", "Hip Thrust", "Ягодичный мост со штангой", MuscleGroup.GLUTES, setOf(G, H), 2),
        BrowseExercise("glute_bridge", "Glute Bridge", "Ягодичный мостик", MuscleGroup.GLUTES, setOf(H, G, O), 1),
        BrowseExercise("kettlebell_swing", "Kettlebell Swing", "Махи гирей", MuscleGroup.GLUTES, setOf(H, G, O), 3),
        BrowseExercise("single_leg_deadlift", "Single-Leg Deadlift", "Тяга на одной ноге", MuscleGroup.GLUTES, setOf(H, G, O), 4),
        BrowseExercise("sumo_deadlift", "Sumo Deadlift", "Тяга сумо", MuscleGroup.GLUTES, setOf(G), 3),
        BrowseExercise("good_morning", "Good Morning", "Наклоны со штангой", MuscleGroup.GLUTES, setOf(G), 3),
        BrowseExercise("nordic_curl", "Nordic Curl", "Нордические сгибания", MuscleGroup.GLUTES, setOf(H, G, O), 4),
        BrowseExercise("leg_curl", "Leg Curl", "Сгибание ног", MuscleGroup.GLUTES, setOf(G), 1),

        // ── CHEST ───────────────────────────────────────────────────────────
        BrowseExercise("bench", "Bench Press", "Жим лёжа", MuscleGroup.CHEST, setOf(G), 2),
        BrowseExercise("incline_bench", "Incline Press", "Жим на наклонной", MuscleGroup.CHEST, setOf(G), 2),
        BrowseExercise("pushup", "Push-Up", "Отжимания", MuscleGroup.CHEST, setOf(G, H, O), 1),
        BrowseExercise("dip", "Dip", "Отжимания на брусьях", MuscleGroup.CHEST, setOf(G, H, O), 3),
        BrowseExercise("diamond_pushup", "Diamond Push-Up", "Алмазные отжимания", MuscleGroup.CHEST, setOf(H, G, O), 2),
        BrowseExercise("wide_pushup", "Wide Push-Up", "Широкие отжимания", MuscleGroup.CHEST, setOf(H, G, O), 1),
        BrowseExercise("decline_pushup", "Decline Push-Up", "Отжимания с ногами на возвышении", MuscleGroup.CHEST, setOf(H, O, G), 2),
        BrowseExercise("incline_pushup", "Incline Push-Up", "Отжимания с упором выше", MuscleGroup.CHEST, setOf(H, O, G), 1),
        BrowseExercise("pike_pushup", "Pike Push-Up", "Отжимания «домиком»", MuscleGroup.CHEST, setOf(H, O, G), 2),
        BrowseExercise("dumbbell_fly", "Dumbbell Fly", "Разводка гантелей", MuscleGroup.CHEST, setOf(G, H), 2),
        BrowseExercise("cable_fly", "Cable Fly", "Сведение в кроссовере", MuscleGroup.CHEST, setOf(G), 2),

        // ── BACK ────────────────────────────────────────────────────────────
        BrowseExercise("row", "Barbell Row", "Тяга штанги в наклоне", MuscleGroup.BACK, setOf(G, H), 3),
        BrowseExercise("dumbbell_row", "Dumbbell Row", "Тяга гантели", MuscleGroup.BACK, setOf(G, H), 2),
        BrowseExercise("inverted_row", "Inverted Row", "Горизонтальная тяга к перекладине", MuscleGroup.BACK, setOf(H, G, O), 2),
        BrowseExercise("cable_row", "Seated Cable Row", "Тяга в блоке сидя", MuscleGroup.BACK, setOf(G), 1),
        BrowseExercise("band_row", "Band Row", "Тяга резинки", MuscleGroup.BACK, setOf(H, O), 1),
        BrowseExercise("face_pull", "Face Pull", "Тяга к лицу", MuscleGroup.BACK, setOf(G, H), 2),
        BrowseExercise("chest_supported_row", "Chest-Supported Row", "Тяга лёжа на наклонной", MuscleGroup.BACK, setOf(G), 2),
        BrowseExercise("t_bar_row", "T-Bar Row", "Т-тяга", MuscleGroup.BACK, setOf(G), 3),
        BrowseExercise("pullup", "Pull-Up", "Подтягивания", MuscleGroup.BACK, setOf(G, H, O), 3),
        BrowseExercise("chin_up", "Chin-Up", "Подтягивания обратным хватом", MuscleGroup.BACK, setOf(G, H, O), 3),
        BrowseExercise("wide_pullup", "Wide-Grip Pull-Up", "Подтягивания широким хватом", MuscleGroup.BACK, setOf(G, H, O), 4),
        BrowseExercise("lat_pulldown", "Lat Pulldown", "Тяга верхнего блока", MuscleGroup.BACK, setOf(G), 1),
        BrowseExercise("band_pulldown", "Band Pulldown", "Тяга резинки сверху", MuscleGroup.BACK, setOf(H, G), 1),
        BrowseExercise("assisted_pullup", "Assisted Pull-Up", "Подтягивания с резинкой", MuscleGroup.BACK, setOf(G, H), 2),
        BrowseExercise("muscle_up", "Muscle-Up", "Выход силой", MuscleGroup.BACK, setOf(G, O), 5),

        // ── SHOULDERS ───────────────────────────────────────────────────────
        BrowseExercise("ohp", "Overhead Press", "Жим над головой", MuscleGroup.SHOULDERS, setOf(G, H), 3),
        BrowseExercise("dumbbell_shoulder_press", "Dumbbell Shoulder Press", "Жим гантелей сидя", MuscleGroup.SHOULDERS, setOf(G, H), 2),
        BrowseExercise("arnold_press", "Arnold Press", "Жим Арнольда", MuscleGroup.SHOULDERS, setOf(G, H), 3),
        BrowseExercise("lateral_raise", "Lateral Raise", "Махи в стороны", MuscleGroup.SHOULDERS, setOf(G, H), 1),
        BrowseExercise("front_raise", "Front Raise", "Махи перед собой", MuscleGroup.SHOULDERS, setOf(G, H), 1),
        BrowseExercise("handstand_pushup", "Handstand Push-Up", "Отжимания в стойке на руках", MuscleGroup.SHOULDERS, setOf(H, G, O), 5),
        BrowseExercise("upright_row", "Upright Row", "Протяжка", MuscleGroup.SHOULDERS, setOf(G, H), 2),

        // ── BICEPS ──────────────────────────────────────────────────────────
        BrowseExercise("curl", "Biceps Curl", "Сгибания на бицепс", MuscleGroup.BICEPS, setOf(G, H), 1),
        BrowseExercise("hammer_curl", "Hammer Curl", "Молотковые сгибания", MuscleGroup.BICEPS, setOf(G, H), 1),
        BrowseExercise("incline_curl", "Incline Curl", "Сгибания на наклонной", MuscleGroup.BICEPS, setOf(G), 2),
        BrowseExercise("concentration_curl", "Concentration Curl", "Концентрированные сгибания", MuscleGroup.BICEPS, setOf(G, H), 1),
        BrowseExercise("cable_curl", "Cable Curl", "Сгибания в блоке", MuscleGroup.BICEPS, setOf(G), 1),
        BrowseExercise("reverse_curl", "Reverse Curl", "Обратные сгибания", MuscleGroup.BICEPS, setOf(G, H), 2),

        // ── TRICEPS ─────────────────────────────────────────────────────────
        BrowseExercise("tricep_extension", "Triceps Extension", "Разгибание на трицепс", MuscleGroup.TRICEPS, setOf(G, H), 1),
        BrowseExercise("skull_crusher", "Skull Crusher", "Французский жим", MuscleGroup.TRICEPS, setOf(G), 2),
        BrowseExercise("tricep_pushdown", "Triceps Pushdown", "Разгибание в блоке", MuscleGroup.TRICEPS, setOf(G), 1),
        BrowseExercise("close_grip_bench", "Close-Grip Bench", "Жим узким хватом", MuscleGroup.TRICEPS, setOf(G), 3),
        BrowseExercise("tricep_kickback", "Triceps Kickback", "Разгибание в наклоне", MuscleGroup.TRICEPS, setOf(G, H), 1),

        // ── CORE ────────────────────────────────────────────────────────────
        BrowseExercise("plank", "Plank", "Планка", MuscleGroup.CORE, setOf(G, H, O), 1),
        BrowseExercise("side_plank", "Side Plank", "Боковая планка", MuscleGroup.CORE, setOf(H, G, O), 2),
        BrowseExercise("crunch", "Crunch", "Скручивания", MuscleGroup.CORE, setOf(H, G, O), 1),
        BrowseExercise("bicycle_crunch", "Bicycle Crunch", "Велосипед", MuscleGroup.CORE, setOf(H, G, O), 1),
        BrowseExercise("leg_raise", "Leg Raise", "Подъём ног лёжа", MuscleGroup.CORE, setOf(H, G, O), 2),
        BrowseExercise("hanging_leg_raise", "Hanging Leg Raise", "Подъём ног в висе", MuscleGroup.CORE, setOf(H, G, O), 3),
        BrowseExercise("toes_to_bar", "Toes to Bar", "Носки к перекладине", MuscleGroup.CORE, setOf(H, G, O), 4),
        BrowseExercise("mountain_climber", "Mountain Climber", "Скалолаз", MuscleGroup.CORE, setOf(H, G, O), 2),
        BrowseExercise("russian_twist", "Russian Twist", "Русский твист", MuscleGroup.CORE, setOf(H, G, O), 1),
        BrowseExercise("dead_bug", "Dead Bug", "«Мёртвый жук»", MuscleGroup.CORE, setOf(H, G), 2),
        BrowseExercise("hollow_body", "Hollow Body Hold", "Уголок «лодочка»", MuscleGroup.CORE, setOf(H, G, O), 3),
        BrowseExercise("v_up", "V-Up", "Складка V-образная", MuscleGroup.CORE, setOf(H, G, O), 2),
        BrowseExercise("cable_crunch", "Cable Crunch", "Скручивания в блоке", MuscleGroup.CORE, setOf(G), 2),
        BrowseExercise("ab_wheel", "Ab Wheel Rollout", "Ролик для пресса", MuscleGroup.CORE, setOf(H, G), 3),
        BrowseExercise("l_sit", "L-Sit", "Уголок в упоре", MuscleGroup.CORE, setOf(H, G, O), 5),

        // ── CARDIO ──────────────────────────────────────────────────────────
        BrowseExercise("run", "Running", "Бег", MuscleGroup.CARDIO, setOf(O, H), 1),
        BrowseExercise("sprint", "Sprint", "Спринт", MuscleGroup.CARDIO, setOf(O, G), 2),
        BrowseExercise("bike", "Cycling", "Велотренажёр", MuscleGroup.CARDIO, setOf(G, H), 1),
        BrowseExercise("burpee", "Burpee", "Бёрпи", MuscleGroup.CARDIO, setOf(H, G, O), 2),
        BrowseExercise("jumping_jack", "Jumping Jack", "Прыжки «звёздочка»", MuscleGroup.CARDIO, setOf(H, G, O), 1),
        BrowseExercise("high_knees", "High Knees", "Бег с высоким подниманием колен", MuscleGroup.CARDIO, setOf(H, G, O), 1),
        BrowseExercise("jump_rope", "Jump Rope", "Скакалка", MuscleGroup.CARDIO, setOf(H, G, O), 2),
        BrowseExercise("box_jump", "Box Jump", "Запрыгивания на тумбу", MuscleGroup.CARDIO, setOf(H, G, O), 3),
        BrowseExercise("elliptical", "Elliptical", "Эллипс", MuscleGroup.CARDIO, setOf(G), 1),
        BrowseExercise("stair_climb", "Stair Climb", "Степпер / лестница", MuscleGroup.CARDIO, setOf(G, O), 1),
        BrowseExercise("skater_jump", "Skater Jump", "Конькобежные прыжки", MuscleGroup.CARDIO, setOf(H, G, O), 2),
        BrowseExercise("swim", "Swimming", "Плавание", MuscleGroup.CARDIO, setOf(O), 3),
        BrowseExercise("rowing_machine", "Rowing Machine", "Гребной тренажёр", MuscleGroup.CARDIO, setOf(G, H), 2),
        BrowseExercise("battle_rope", "Battle Rope", "Канаты", MuscleGroup.CARDIO, setOf(G), 2),

        // ── MOBILITY ────────────────────────────────────────────────────────
        BrowseExercise("stretching", "Stretching", "Растяжка", MuscleGroup.MOBILITY, setOf(H, G, O), 1),
        BrowseExercise("foam_rolling", "Foam Rolling", "Раскатка на ролике", MuscleGroup.MOBILITY, setOf(H, G), 1),
        BrowseExercise("hip_flexor_stretch", "Hip Flexor Stretch", "Растяжка сгибателей бедра", MuscleGroup.MOBILITY, setOf(H, G, O), 1),
        BrowseExercise("world_greatest_stretch", "World's Greatest Stretch", "Лучшая растяжка", MuscleGroup.MOBILITY, setOf(H, G, O), 2),
        BrowseExercise("cat_cow", "Cat-Cow", "Кошка-корова", MuscleGroup.MOBILITY, setOf(H, G), 1)
    )

    fun byMode(mode: TrainMode?): List<BrowseExercise> =
        if (mode == null) all else all.filter { mode in it.modes }
}
