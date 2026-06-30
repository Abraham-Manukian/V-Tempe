package com.vtempe.server

import com.vtempe.server.features.ai.data.service.split.InjuryFilter
import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
import com.vtempe.server.features.ai.data.service.nutrition.FoodRestrictionValidator
import com.vtempe.server.features.ai.data.service.nutrition.NutritionTargetCalculator
import com.vtempe.server.features.ai.data.service.nutrition.ShoppingListNormalizer
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.SlotType
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.profile.AiRecentWorkout
import org.junit.jupiter.api.Assertions.assertEquals
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
}
