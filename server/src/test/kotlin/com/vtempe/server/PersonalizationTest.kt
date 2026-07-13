package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.StubLLMClient
import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import com.vtempe.server.features.ai.data.llm.pipeline.PipelineConfig
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.AiQualityErrorPolicy
import com.vtempe.server.features.ai.data.service.AiService
import com.vtempe.server.features.ai.data.service.clampRepsForUnit
import com.vtempe.server.features.ai.data.service.fallbackNutrition
import com.vtempe.server.features.ai.data.service.fallbackTraining
import com.vtempe.server.features.ai.data.service.normalizeTrainingPlan
import com.vtempe.server.features.ai.data.service.sanitizeTemplateMealsForRestrictions
import com.vtempe.server.features.ai.data.service.split.InjuryFilter
import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
import com.vtempe.server.features.ai.data.service.nutrition.FoodRestrictionValidator
import com.vtempe.server.features.ai.data.service.nutrition.NutritionTargetCalculator
import com.vtempe.server.features.ai.data.service.nutrition.ShoppingListNormalizer
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.SlotType
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.profile.AiRecentWorkout
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonalizationTest {

    private fun profile(
        sex: String = "MALE",
        age: Int = 30,
        heightCm: Int = 180,
        weightKg: Double = 80.0,
        goal: String = "MAINTAIN",
        experienceLevel: Int = 3,
        lifestyleActivity: String = "SEDENTARY",
        dietaryPreferences: List<String> = emptyList(),
        allergies: List<String> = emptyList(),
        injuries: List<String> = emptyList(),
        weeklySchedule: Map<String, Boolean> = mapOf("Mon" to true, "Wed" to true, "Fri" to true),
        recentWorkouts: List<AiRecentWorkout> = emptyList(),
        trainingFocus: String = "GENERAL",
        splitPreference: String = "AUTO"
    ) = AiProfile(
        age = age,
        sex = sex,
        heightCm = heightCm,
        weightKg = weightKg,
        goal = goal,
        experienceLevel = experienceLevel,
        dietaryPreferences = dietaryPreferences,
        allergies = allergies,
        injuries = injuries,
        weeklySchedule = weeklySchedule,
        lifestyleActivity = lifestyleActivity,
        recentWorkouts = recentWorkouts,
        trainingFocus = trainingFocus,
        splitPreference = splitPreference
    )

    // ── BUG 1: NutritionTargetCalculator ───────────────────────────────────────

    @Test
    fun `TDEE and macros for maintenance male`() {
        // Mifflin male: 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5 = 1780 BMR.
        val t = NutritionTargetCalculator.targetsFor(
            profile(goal = "MAINTAIN", weeklySchedule = mapOf("Mon" to true, "Wed" to true, "Fri" to true))
        )
        // Sedentary base 1.20 + 3 training days bonus 0.15 = 1.35 → TDEE ~2403.
        assertTrue(t.kcal in 2300..2500, "kcal=${t.kcal} expected ~2403")
        // protein 1.7 g/kg * 80 = 136
        assertEquals(136, t.proteinG)
        assertTrue(t.fatG >= 40)
        assertTrue(t.carbsG > 0)
    }

    @Test
    fun `fat loss lowers calories vs maintenance`() {
        val maintain = NutritionTargetCalculator.targetsFor(profile(goal = "MAINTAIN"))
        val lose = NutritionTargetCalculator.targetsFor(profile(goal = "LOSE_FAT"))
        assertTrue(lose.kcal < maintain.kcal, "lose=${lose.kcal} maintain=${maintain.kcal}")
    }

    @Test
    fun `muscle gain raises calories vs maintenance`() {
        val maintain = NutritionTargetCalculator.targetsFor(profile(goal = "MAINTAIN"))
        val gain = NutritionTargetCalculator.targetsFor(profile(goal = "GAIN_MUSCLE"))
        assertTrue(gain.kcal > maintain.kcal, "gain=${gain.kcal} maintain=${maintain.kcal}")
    }

    // ── BUG 2: FoodRestrictionValidator ────────────────────────────────────────

    @Test
    fun `vegan parses to no meat fish dairy eggs`() {
        val r = FoodRestrictionValidator.parse(profile(dietaryPreferences = listOf("vegan")))
        assertTrue(r.noMeat)
        assertTrue(r.noPoultry)
        assertTrue(r.noFish)
        assertTrue(r.noDairy)
        assertTrue(r.noEggs)
        assertTrue(r.noHoney, "honey is an animal product — vegans must exclude it too")
    }

    @Test
    fun `vegetarian forbids meat and fish but allows dairy and eggs`() {
        val r = FoodRestrictionValidator.parse(profile(dietaryPreferences = listOf("vegetarian")))
        assertTrue(r.noMeat)
        assertTrue(r.noFish)
        assertTrue(!r.noDairy)
        assertTrue(!r.noEggs)
    }

    @Test
    fun `fish allergy triggers fish restriction`() {
        val r = FoodRestrictionValidator.parse(profile(allergies = listOf("аллергия на рыбу")))
        assertTrue(r.noFish, "fish allergy should set noFish")
    }

    @Test
    fun `validatePlan flags fish meal for vegetarian`() {
        val plan = AiNutritionResponse(
            weekIndex = 0,
            mealsByDay = mapOf(
                "Mon" to listOf(
                    AiMeal(name = "Лосось с рисом", ingredients = listOf("филе лосося", "рис"))
                )
            )
        )
        val violations = FoodRestrictionValidator.validatePlan(plan, profile(dietaryPreferences = listOf("vegetarian")))
        assertTrue(violations.isNotEmpty(), "expected fish violation, got $violations")
        assertTrue(violations.joinToString().contains("fish"), "expected 'fish' in $violations")
    }

    @Test
    fun `validatePlan passes compliant vegan meal`() {
        val plan = AiNutritionResponse(
            weekIndex = 0,
            mealsByDay = mapOf(
                "Mon" to listOf(
                    AiMeal(name = "Тофу с овощами", ingredients = listOf("тофу", "брокколи", "рис"))
                )
            )
        )
        val violations = FoodRestrictionValidator.validatePlan(plan, profile(dietaryPreferences = listOf("vegan")))
        assertTrue(violations.isEmpty(), "expected no violations, got $violations")
    }

    // ── BUG 3: ShoppingListNormalizer ──────────────────────────────────────────

    @Test
    fun `dedup sums same ingredient across grammatical cases`() {
        val out = ShoppingListNormalizer.normalize(listOf("100г риса", "Рис 200г"))
        assertEquals(1, out.size, "expected single rice line, got $out")
        val line = out.first()
        assertTrue(line.contains("300"), "expected 300 in $line")
        assertTrue(line.contains("г"), "expected unit г in $line")
    }

    @Test
    fun `pieces are summed and labelled`() {
        val out = ShoppingListNormalizer.normalize(listOf("банан 2 шт", "Бананы 3 шт"))
        assertEquals(1, out.size, "expected single banana line, got $out")
        assertTrue(out.first().contains("5"), "expected 5 in ${out.first()}")
        assertTrue(out.first().contains("шт"), "expected piece unit in ${out.first()}")
    }

    @Test
    fun `spices are grouped after foods`() {
        val out = ShoppingListNormalizer.normalize(listOf("200г курицы", "соль", "перец"))
        // Food line present plus spice lines; spices come after foods.
        val saltIdx = out.indexOfFirst { it.contains("Соль", ignoreCase = true) }
        val chickenIdx = out.indexOfFirst { it.contains("Кур", ignoreCase = true) }
        assertTrue(chickenIdx >= 0 && saltIdx >= 0, "both lines expected, got $out")
        assertTrue(saltIdx > chickenIdx, "spices should follow foods, got $out")
    }

    // ── BUG 5/8: TrainingSplitPlanner split labels ─────────────────────────────

    @Test
    fun `bro split 3 days yields chest back legs labels`() {
        val skeletons = TrainingSplitPlanner.build(
            trainingDays = listOf("Mon", "Wed", "Fri"),
            focusRaw = "HYPERTROPHY",
            goalRaw = "GAIN_MUSCLE",
            splitPreferenceRaw = "BRO_SPLIT",
            experienceLevel = 3,
            age = 30,
            sexRaw = "MALE",
            lifestyleRaw = "SEDENTARY",
            injuries = emptyList(),
            sessionDurationMins = 60,
            weekIndex = 0
        )
        assertEquals(3, skeletons.size)
        assertTrue(skeletons[0].label.contains("Chest + Triceps"), skeletons[0].label)
        assertTrue(skeletons[1].label.contains("Back + Biceps"), skeletons[1].label)
        assertTrue(skeletons[2].label.contains("Legs + Shoulders"), skeletons[2].label)
    }

    @Test
    fun `no leg day repeats the same leg pattern in compound and isolation`() {
        val skeletons = TrainingSplitPlanner.build(
            trainingDays = listOf("Mon", "Tue", "Wed", "Thu"),
            focusRaw = "HYPERTROPHY",
            goalRaw = "GAIN_MUSCLE",
            splitPreferenceRaw = "UPPER_LOWER",
            experienceLevel = 3,
            age = 30,
            sexRaw = "MALE",
            lifestyleRaw = "SEDENTARY",
            injuries = emptyList(),
            sessionDurationMins = 60,
            weekIndex = 0
        )
        skeletons.forEach { s ->
            val legPatterns = setOf(
                MovementPattern.KNEE_DOMINANT, MovementPattern.SINGLE_LEG, MovementPattern.HINGE
            )
            val compoundLegs = s.slots
                .filter { it.slotType != SlotType.ISOLATION && it.pattern in legPatterns }
                .map { it.pattern }.toSet()
            val isolationLegs = s.slots
                .filter { it.slotType == SlotType.ISOLATION && it.pattern in legPatterns }
                .map { it.pattern }.toSet()
            val collision = compoundLegs.intersect(isolationLegs)
            assertTrue(collision.isEmpty(), "leg pattern collision in ${s.label}: $collision")
        }
    }

    // ── BUG 6: InjuryFilter replacement ────────────────────────────────────────

    @Test
    fun `knee injury removes knee dominant and single leg patterns`() {
        val banned = InjuryFilter.bannedPatterns(listOf("knee pain"))
        assertTrue(MovementPattern.KNEE_DOMINANT in banned)
        assertTrue(MovementPattern.SINGLE_LEG in banned)
    }

    @Test
    fun `back injury bans heavy hinge`() {
        val banned = InjuryFilter.bannedPatterns(listOf("lower back herniation"))
        assertTrue(MovementPattern.HINGE in banned)
    }

    @Test
    fun `injury filter replaces banned slots instead of leaving empty workouts`() {
        val skeletons = TrainingSplitPlanner.build(
            trainingDays = listOf("Mon", "Tue", "Wed", "Thu"),
            focusRaw = "HYPERTROPHY",
            goalRaw = "GAIN_MUSCLE",
            splitPreferenceRaw = "UPPER_LOWER",
            experienceLevel = 3,
            age = 30,
            sexRaw = "MALE",
            lifestyleRaw = "SEDENTARY",
            injuries = listOf("knee pain"),
            sessionDurationMins = 60,
            weekIndex = 0
        )
        // No banned pattern should survive, and no session should be empty.
        skeletons.forEach { s ->
            assertTrue(s.slots.isNotEmpty(), "session ${s.label} became empty")
            assertTrue(s.slots.none { it.pattern == MovementPattern.KNEE_DOMINANT }, "knee pattern survived in ${s.label}")
            assertTrue(s.slots.none { it.pattern == MovementPattern.SINGLE_LEG }, "single-leg survived in ${s.label}")
        }
        // The lower-body day in particular must keep meaningful volume (not collapse to 0-1 slots).
        val lowerDays = skeletons.filter { it.label.contains("Lower") }
        assertTrue(lowerDays.all { it.slots.size >= 2 }, "lower day lost too much volume: ${lowerDays.map { it.slots.size }}")
    }

    @Test
    fun `safeAlternativeFor maps knee dominant to a non-banned pattern`() {
        val banned = InjuryFilter.bannedPatterns(listOf("knee pain"))
        val alt = InjuryFilter.safeAlternativeFor(MovementPattern.KNEE_DOMINANT, banned)
        assertTrue(alt != null && alt !in banned, "expected safe alt, got $alt")
    }

    // ── BUG 7: first-session RPE cap ───────────────────────────────────────────

    @Test
    fun `no history beginner gets RPE at or below 7`() {
        val skeletons = TrainingSplitPlanner.build(
            trainingDays = listOf("Mon", "Wed", "Fri"),
            focusRaw = "STRENGTH", // strength preset would otherwise push primary RPE to 8.5
            goalRaw = "GAIN_MUSCLE",
            splitPreferenceRaw = "AUTO",
            experienceLevel = 2,
            age = 30,
            sexRaw = "MALE",
            lifestyleRaw = "SEDENTARY",
            injuries = emptyList(),
            sessionDurationMins = 60,
            weekIndex = 0,
            hasHistory = false
        )
        val maxRpe = skeletons.flatMap { it.slots }.maxOf { it.rpeTarget }
        assertTrue(maxRpe <= 7.0f, "first-session beginner RPE should be ≤7.0, got $maxRpe")
    }

    @Test
    fun `history beginner is not RPE capped to first session value`() {
        val withHistory = TrainingSplitPlanner.build(
            trainingDays = listOf("Mon", "Wed", "Fri"),
            focusRaw = "STRENGTH",
            goalRaw = "GAIN_MUSCLE",
            splitPreferenceRaw = "AUTO",
            experienceLevel = 2,
            age = 30,
            sexRaw = "MALE",
            lifestyleRaw = "SEDENTARY",
            injuries = emptyList(),
            sessionDurationMins = 60,
            weekIndex = 0,
            hasHistory = true
        )
        val maxRpe = withHistory.flatMap { it.slots }.maxOf { it.rpeTarget }
        assertTrue(maxRpe > 7.0f, "with history, RPE should not be force-capped at 7.0, got $maxRpe")
    }

    // ── BUG: calorie target not enforced ───────────────────────────────────────

    @Test
    fun `severe calorie deviation is critical not just a warning`() {
        val errors = listOf("Mon calorie sum severe deviation: 1683 kcal vs target 2457 kcal (more than 20% off — increase portion sizes/add a meal to reach the target if under, reduce portions if over)")
        val critical = AiQualityErrorPolicy.criticalErrors(errors)
        assertTrue(critical.isNotEmpty(), "severe calorie deviation must be critical (force a retry), got critical=$critical")
    }

    @Test
    fun `mild calorie deviation stays a non-critical warning`() {
        val errors = listOf("Mon calorie sum 2000 kcal outside ±10% of target 2200 kcal")
        val critical = AiQualityErrorPolicy.criticalErrors(errors)
        val warnings = AiQualityErrorPolicy.warningErrors(errors)
        assertTrue(critical.isEmpty(), "mild (±10-20%) calorie deviation should stay non-critical, got critical=$critical")
        assertTrue(warnings.isNotEmpty(), "mild calorie deviation should still surface as a warning")
    }

    // ── BUG: deload signal didn't reach the AI-written RPE ─────────────────────

    @Test
    fun `forced deload clamps AI-written RPE down to the deload ceiling`() {
        val profile = profile(
            experienceLevel = 4,
            trainingFocus = "STRENGTH",
            splitPreference = "AUTO",
            weeklySchedule = mapOf("Mon" to true, "Tue" to true, "Wed" to true, "Fri" to true, "Sat" to true),
            recentWorkouts = listOf(
                AiRecentWorkout(date = "2026-06-20", completionRate = 0.65, completedItems = 13, plannedItems = 20, totalVolumeKg = 4200.0, averageRpe = 8.9),
                AiRecentWorkout(date = "2026-06-27", completionRate = 0.68, completedItems = 14, plannedItems = 20, totalVolumeKg = 4300.0, averageRpe = 9.1)
            )
        )
        // AI ignores the deload guidance in the prompt and writes its usual high RPE anyway —
        // this is exactly what production logs showed (flat RPE 8.5 across a 5-day week despite
        // a clear 2-week deload signal).
        val aiPlan = AiTrainingResponse(
            weekIndex = 0,
            workouts = listOf(
                AiWorkout(id = "w1", label = "Push", date = "2026-06-29", sets = listOf(
                    AiSet(exerciseId = "bench", reps = 6, weightKg = 55.0, rpe = 8.5)
                ))
            )
        )
        val normalized = normalizeTrainingPlan(aiPlan, profile)
        val actualRpe = normalized.workouts.first().sets.first().rpe
        assertTrue(
            actualRpe != null && actualRpe <= 6.5,
            "forced deload should clamp RPE down to ~6.5, but AI's 8.5 passed through unclamped (got $actualRpe)"
        )
    }

    @Test
    fun `without a deload signal AI RPE is not clamped down`() {
        val profile = profile(
            experienceLevel = 4,
            trainingFocus = "STRENGTH",
            splitPreference = "AUTO",
            weeklySchedule = mapOf("Mon" to true, "Wed" to true, "Fri" to true),
            recentWorkouts = listOf(
                AiRecentWorkout(date = "2026-06-27", completionRate = 0.95, completedItems = 19, plannedItems = 20, totalVolumeKg = 4300.0, averageRpe = 7.2)
            )
        )
        val aiPlan = AiTrainingResponse(
            weekIndex = 0,
            workouts = listOf(
                AiWorkout(id = "w1", label = "Push", date = "2026-06-29", sets = listOf(
                    AiSet(exerciseId = "bench", reps = 6, weightKg = 55.0, rpe = 8.5)
                ))
            )
        )
        val normalized = normalizeTrainingPlan(aiPlan, profile)
        val actualRpe = normalized.workouts.first().sets.first().rpe
        assertEquals(8.5, actualRpe, "with good recent performance (no deload signal), AI's RPE should pass through unclamped")
    }

    // ── BUG: fallback template silently violated vegan restrictions (honey) ────

    @Test
    fun `fallback template strips honey for a vegan profile`() {
        // Reproduces the exact production bug: the hardcoded fallback template (used when the
        // LLM exhausts its retry budget) included honey in two meals, and honey had no
        // FoodRestrictionTag at all — so a vegan profile's fallback plan silently violated
        // their diet with no error, no warning, nothing.
        val meals = listOf(
            AiMeal(
                name = "Овсянка с ягодами",
                ingredients = listOf("овсяные хлопья", "молоко", "ягоды", "мёд"),
                kcal = 420,
                macros = Macros(35, 12, 55, 420)
            ),
            AiMeal(
                name = "Греческий йогурт с орехами",
                ingredients = listOf("греческий йогурт", "грецкие орехи", "мёд"),
                kcal = 420,
                macros = Macros(25, 18, 35, 420)
            )
        )
        val veganProfile = profile(dietaryPreferences = listOf("vegan"))
        val sanitized = sanitizeTemplateMealsForRestrictions(meals, veganProfile, java.util.Locale("ru"))

        val allText = sanitized.joinToString(" ") { it.name + " " + it.ingredients.joinToString(" ") }
        assertTrue(!allText.contains("мёд"), "sanitized fallback template must not contain honey for a vegan, got: $allText")
        assertTrue(!allText.contains("молок"), "sanitized fallback template must not contain dairy for a vegan, got: $allText")
        assertTrue(!allText.contains("йогурт"), "sanitized fallback template must not contain yogurt for a vegan, got: $allText")
    }

    // ── BUG: fallbackNutrition() ignored the computed calorie target ───────────

    @Test
    fun `fallback nutrition scales to the computed calorie target instead of a flat number`() {
        val bulkProfile = profile(goal = "GAIN_MUSCLE", weightKg = 52.0, heightCm = 190, age = 18, experienceLevel = 1)
        val cutProfile = profile(goal = "LOSE_FAT", weightKg = 104.0, heightCm = 178, age = 34)
        val bulkPlan = fallbackNutrition(AiNutritionRequest(bulkProfile, weekIndex = 0, locale = "ru"))
        val cutPlan = fallbackNutrition(AiNutritionRequest(cutProfile, weekIndex = 0, locale = "ru"))

        val bulkMonKcal = bulkPlan.mealsByDay.getValue("Mon").sumOf { it.kcal }
        val cutMonKcal = cutPlan.mealsByDay.getValue("Mon").sumOf { it.kcal }
        assertTrue(
            bulkMonKcal > cutMonKcal,
            "bulk fallback ($bulkMonKcal) should exceed cut fallback ($cutMonKcal) — both were a flat 1968 before the fix"
        )

        val target = NutritionTargetCalculator.targetsFor(bulkProfile).kcal
        for (day in listOf("Mon", "Wed", "Sun")) {
            val dayKcal = bulkPlan.mealsByDay.getValue(day).sumOf { it.kcal }
            assertTrue(
                kotlin.math.abs(dayKcal - target) < target * 0.15,
                "$day kcal=$dayKcal should land within 15% of target=$target (Sunday used to be a flat 1408 regardless of target)"
            )
        }
    }

    // ── BUG: fallbackTraining() ignored weeklySchedule and hardcoded 3 days ────

    @Test
    fun `fallback training generates one workout per scheduled day, not a hardcoded 3`() {
        val sixDayProfile = profile(
            splitPreference = "PPL",
            weeklySchedule = mapOf(
                "Mon" to true, "Tue" to true, "Wed" to true,
                "Thu" to true, "Fri" to true, "Sat" to true
            )
        )
        val plan = fallbackTraining(AiTrainingRequest(sixDayProfile, weekIndex = 0, locale = "ru"))
        assertEquals(6, plan.workouts.size, "expected 6 workouts for a 6-day schedule, got ${plan.workouts.size}")
    }

    // ── BUG: free-text exercise exclusions ("нельзя брусья") were never enforced ─

    @Test
    fun `InjuryFilter parses free-text exercise-specific bans`() {
        val banned = InjuryFilter.bannedExerciseIds(
            listOf("болит плечо, нельзя подтягивания широким хватом, нельзя брусья")
        )
        assertTrue("dip" in banned, "banned=$banned should include dip (брусья)")
        assertTrue("bench_dip" in banned, "banned=$banned should include bench_dip (брусья)")
        assertTrue("wide_pullup" in banned, "banned=$banned should include wide_pullup (широким хватом)")
    }

    @Test
    fun `dip is excluded from a generated plan when the profile says no dip bars`() {
        val profileNoDipBars = profile(
            experienceLevel = 3,
            splitPreference = "AUTO",
            weeklySchedule = mapOf("Mon" to true, "Wed" to true, "Fri" to true),
            injuries = listOf("болит плечо, нельзя брусья")
        ).copy(equipment = listOf("pullup_bar"), trainingMode = "OUTDOOR")
        val plan = fallbackTraining(AiTrainingRequest(profileNoDipBars, weekIndex = 0, locale = "ru"))
        val allExerciseIds = plan.workouts.flatMap { it.sets }.map { it.exerciseId }
        assertTrue("dip" !in allExerciseIds, "dip must not appear when 'нельзя брусья' is in injuries, got: $allExerciseIds")
        assertTrue("bench_dip" !in allExerciseIds, "bench_dip must not appear when 'нельзя брусья' is in injuries, got: $allExerciseIds")
    }

    // ── BUG: deload didn't stop the same heavy squat+deadlift repeating 2x/week ─

    @Test
    fun `deload week does not repeat the identical leg-day compounds twice`() {
        val deloadProfile = profile(
            experienceLevel = 4,
            trainingFocus = "STRENGTH",
            splitPreference = "AUTO",
            weeklySchedule = mapOf("Mon" to true, "Tue" to true, "Wed" to true, "Fri" to true, "Sat" to true),
            recentWorkouts = listOf(
                AiRecentWorkout(date = "2026-06-20", completionRate = 0.6, completedItems = 12, plannedItems = 20, totalVolumeKg = 3200.0, averageRpe = 9.2),
                AiRecentWorkout(date = "2026-06-27", completionRate = 0.58, completedItems = 11, plannedItems = 20, totalVolumeKg = 3100.0, averageRpe = 9.4)
            )
        )
        val plan = fallbackTraining(AiTrainingRequest(deloadProfile, weekIndex = 0, locale = "ru"))
        val legDays = plan.workouts.filter { it.label == "Legs" }
        assertEquals(2, legDays.size, "expected two Legs sessions in a 5-day PPL week")
        val ids1 = legDays[0].sets.map { it.exerciseId }.toSet()
        val ids2 = legDays[1].sets.map { it.exerciseId }.toSet()
        assertTrue(ids1 != ids2, "deload's two Leg days should not use the identical exercise set, got $ids1 both times")
    }

    @Test
    fun `without a deload signal both leg days may repeat the same compounds`() {
        val normalProfile = profile(
            experienceLevel = 4,
            trainingFocus = "STRENGTH",
            splitPreference = "AUTO",
            weeklySchedule = mapOf("Mon" to true, "Tue" to true, "Wed" to true, "Fri" to true, "Sat" to true)
        )
        val plan = fallbackTraining(AiTrainingRequest(normalProfile, weekIndex = 0, locale = "ru"))
        val legDays = plan.workouts.filter { it.label == "Legs" }
        assertEquals(2, legDays.size)
        assertEquals(
            legDays[0].sets.map { it.exerciseId }.toSet(),
            legDays[1].sets.map { it.exerciseId }.toSet(),
            "outside deload, hitting legs 2x/week with the same compounds is intentional (Grgic 2018)"
        )
    }

    // ── BUG: AI wrote nonsensical numbers for duration-based exercises ─────────

    @Test
    fun `clampRepsForUnit clamps seconds and minutes exercises into a sane range`() {
        assertEquals(20, clampRepsForUnit("plank", 3), "plank=3 should clamp up to the seconds floor")
        assertEquals(60, clampRepsForUnit("plank", 500), "plank=500 should clamp down to the seconds ceiling")
        assertEquals(30, clampRepsForUnit("plank", 30), "plank=30 is already sane and should pass through")
        assertEquals(5, clampRepsForUnit("run", 1), "run=1 should clamp up to the minutes floor")
        assertEquals(8, clampRepsForUnit("bench", 8), "non-duration exercises must not be touched")
    }

    // ── BUG: bands-only equipment still got a fabricated literal weightKg ──────

    @Test
    fun `bands-only equipment nulls out weightKg even for normally-weighted exercises`() {
        val bandsProfile = profile(
            splitPreference = "PPL",
            weeklySchedule = mapOf("Mon" to true, "Tue" to true, "Wed" to true)
        ).copy(equipment = listOf("bands", "mat"), trainingMode = "HOME")
        val plan = fallbackTraining(AiTrainingRequest(bandsProfile, weekIndex = 0, locale = "ru"))
        val allSets = plan.workouts.flatMap { it.sets }
        assertTrue(allSets.isNotEmpty(), "expected at least some sets in the bands-only plan")
        val weighted = allSets.filter { it.weightKg != null }
        assertTrue(
            weighted.isEmpty(),
            "bands-only equipment should never produce a literal weightKg, got: ${weighted.map { it.exerciseId to it.weightKg }}"
        )
    }

    @Test
    fun `real gym equipment still keeps a literal weightKg`() {
        val gymProfile = profile(weeklySchedule = mapOf("Mon" to true))
            .copy(equipment = listOf("barbell", "dumbbells", "bench"), trainingMode = "GYM")
        val aiPlan = AiTrainingResponse(
            weekIndex = 0,
            workouts = listOf(
                AiWorkout(id = "w1", label = "Full Body A", date = "2026-07-06", sets = listOf(
                    AiSet(exerciseId = "bench", reps = 6, weightKg = 60.0, rpe = 7.0)
                ))
            )
        )
        val normalized = normalizeTrainingPlan(aiPlan, gymProfile)
        val firstSet = normalized.workouts.first().sets.first()
        assertEquals(60.0, firstSet.weightKg, "real gym equipment should keep the AI's literal weight, got ${firstSet.weightKg}")
    }

    // ── cacheKey: SHA-256 collision-resistance (was 32-bit String.hashCode()) ─────

    private fun testAiService(): AiService {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val pipeline = LlmPipeline(
            config = PipelineConfig(),
            extractor = ResponseExtractor(),
            sanitizer = JsonSanitizer(),
            decoder = Decoder(json),
            feedback = FeedbackComposer(),
            rawStore = LlmRawStore(enabled = false),
            tracker = LlmErrorTracker()
        )
        val exerciseCatalog = BuiltInExerciseCatalog()
        return AiService(
            paidLlmClient = StubLLMClient("{}"),
            freeLlmClient = StubLLMClient("{}"),
            llmRepairer = LlmRepairer(pipeline),
            exerciseCatalog = exerciseCatalog,
            trainingPlanResolver = DefaultTrainingPlanResolver(exerciseCatalog)
        )
    }

    @Test
    fun `cacheKey does not collide for different profiles`() {
        val service = testAiService()
        val a = service.cacheKey(profile(weightKg = 70.0), weekIndex = 0, localeTag = "en-US")
        val b = service.cacheKey(profile(weightKg = 90.0), weekIndex = 0, localeTag = "en-US")
        assertNotEquals(a, b, "different profiles must never share a cache key")
    }

    @Test
    fun `cacheKey does not collide on a known String_hashCode collision pair`() {
        // "Aa" and "BB" are a textbook java.lang.String#hashCode() collision (both hash to
        // 2112) — swapping one for the other anywhere inside a longer string leaves the
        // WHOLE string's polynomial hashCode unchanged too. This is exactly the failure mode
        // the old `fingerprint.hashCode()` cache key had: two different profiles (different
        // injuries here) would have produced the identical cache key and one user could get
        // served another user's cached plan. SHA-256 must not reproduce that collision.
        val service = testAiService()
        val a = service.cacheKey(profile(injuries = listOf("Aa")), weekIndex = 0, localeTag = "en-US")
        val b = service.cacheKey(profile(injuries = listOf("BB")), weekIndex = 0, localeTag = "en-US")
        assertNotEquals(a, b, "profiles differing only by a known hashCode-colliding substring must not share a cache key")
    }

    @Test
    fun `cacheKey is stable for the same profile`() {
        val service = testAiService()
        val p = profile(weightKg = 75.0)
        val a = service.cacheKey(p, weekIndex = 2, localeTag = "ru-RU")
        val b = service.cacheKey(p, weekIndex = 2, localeTag = "ru-RU")
        assertEquals(a, b, "identical inputs must produce the identical cache key")
    }
}
