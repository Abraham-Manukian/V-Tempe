package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.dto.NutritionPlanDto
import com.vtempe.shared.data.nutrition.OfflineNutritionPlanner
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.nutrition.FoodRestrictions
import com.vtempe.shared.domain.nutrition.planViolations
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class NutritionRepositoryDb(
    private val db: AppDatabase,
    private val ai: com.vtempe.shared.domain.repository.AiTrainerRepository,
    private val languagePrefs: LanguagePreferences,
    private val cache: AiResponseCache,
    private val offlinePlanner: OfflineNutritionPlanner = OfflineNutritionPlanner(),
) : NutritionRepository {

    private val planFlow = MutableStateFlow<NutritionPlan?>(null)

    /**
     * The weekIndex currently displayed on screen.
     * Only plans for this week update the observable flow — background
     * prefetch for future weeks writes to DB silently without touching the UI.
     */
    private var activeWeekIndex: Int = -1

    override suspend fun generatePlan(profile: Profile, weekIndex: Int): NutritionPlan {
        // NetworkAiTrainerRepository already handles cache fallback internally.
        // We only need to persist on success or fall through to the offline plan.
        when (val aiPlanResult = ai.generateNutritionPlan(profile, weekIndex)) {
            is DataResult.Success -> {
                // The result may be a cached plan made for an older profile: re-check it against
                // the CURRENT restrictions before showing or storing it.
                val violations = FoodRestrictions.from(profile).planViolations(aiPlanResult.data)
                if (violations.isEmpty()) {
                    persistPlan(aiPlanResult.data)
                    return aiPlanResult.data
                }
                Napier.w("AI nutrition plan conflicts with the profile's food restrictions (${violations.size}), using offline plan")
            }
            is DataResult.Failure -> {
                Napier.w(
                    message = "AI nutrition plan unavailable (${aiPlanResult.reason}), using offline plan",
                    throwable = aiPlanResult.throwable
                )
            }
        }

        // Throws NoSafeMealPlanException when nothing compatible exists — nothing is stored then.
        val fallback = offlinePlanner.buildPlan(profile, weekIndex, languagePrefs.getLanguageTag().orEmpty())
        persistPlan(fallback)
        return fallback
    }

    override suspend fun savePlan(plan: NutritionPlan) {
        persistPlan(plan)
        cache.storeNutrition(NutritionPlanDto.fromDomain(plan))
    }

    override fun observePlan(): Flow<NutritionPlan?> = planFlow.asStateFlow()

    /** Pure DB check — does NOT touch planFlow (safe to call during background prefetch). */
    override suspend fun hasPlan(weekIndex: Int): Boolean {
        if (planFlow.value?.weekIndex == weekIndex) return true
        return withContext(Dispatchers.Default) { loadPlanFromDb(weekIndex) != null }
    }

    /**
     * Sets this week as the active (displayed) week and loads its plan from DB into
     * the observable flow so the UI renders immediately without waiting for the network.
     * Returns true if a cached plan existed.
     */
    override suspend fun setActiveWeek(weekIndex: Int): Boolean {
        activeWeekIndex = weekIndex
        val plan = withContext(Dispatchers.Default) { loadPlanFromDb(weekIndex) }
        planFlow.value = plan
        return plan != null
    }

    /**
     * Registers the active week instantly (no IO) so that [savePlan] knows to push
     * the incoming plan to the flow once the network request finishes.
     * Use before a force-refresh to avoid showing stale cached data.
     */
    override fun registerActiveWeek(weekIndex: Int) {
        activeWeekIndex = weekIndex
    }

    override suspend fun deleteWeeksFrom(weekIndex: Int) {
        withContext(Dispatchers.Default) {
            val q = db.nutritionQueries
            q.transaction {
                q.deleteIngredientsFromWeek(weekIndex.toLong())
                q.deleteMealsFromWeek(weekIndex.toLong())
                q.deleteMealByDayFromWeek(weekIndex.toLong())
                q.deleteShoppingItemsFromWeek(weekIndex.toLong())
                q.deleteDailyTargetsFromWeek(weekIndex.toLong())
                q.deleteOrphanMeals()
                q.deleteOrphanIngredients()
            }
        }
        // If the current active week was nuked, clear the flow too
        if (activeWeekIndex >= weekIndex) planFlow.value = null
    }

    private suspend fun persistPlan(plan: NutritionPlan) {
        withContext(Dispatchers.Default) {
            // The whole week is replaced in ONE transaction (VT-AUD-033): a failure anywhere
            // rolls back to the previous complete week instead of leaving half a new one, and
            // the old week's meals and ingredients are removed, not just unlinked (VT-AUD-034).
            // The flow below is updated only after the commit succeeded.
            val week = plan.weekIndex.toLong()
            db.nutritionQueries.transaction {
                db.nutritionQueries.deleteIngredientsOfWeek(week)
                db.nutritionQueries.deleteMealsOfWeek(week)
                db.nutritionQueries.deleteMealsForWeek(week)
                db.nutritionQueries.deleteShoppingItemsForWeek(week)
                db.nutritionQueries.deleteDailyTargetForWeek(week)
                plan.dailyTarget?.let { target ->
                    db.nutritionQueries.insertDailyTarget(
                        week,
                        target.kcal.toLong(),
                        target.proteinGrams.toLong(),
                        target.fatGrams.toLong(),
                        target.carbsGrams.toLong(),
                    )
                }
                plan.shoppingList.forEachIndexed { position, item ->
                    db.nutritionQueries.insertShoppingItem(week, position.toLong(), item)
                }
                plan.mealsByDay.forEach { (day, meals) ->
                    meals.forEachIndexed { idx, meal ->
                        val mealId = "m_${plan.weekIndex}_${day}_${idx}"
                        db.nutritionQueries.insertMeal(
                            mealId,
                            meal.name,
                            meal.kcal.toLong(),
                            meal.macros.proteinGrams.toLong(),
                            meal.macros.fatGrams.toLong(),
                            meal.macros.carbsGrams.toLong(),
                            meal.recipe,
                        )
                        db.nutritionQueries.deleteIngredientsForMeal(mealId)
                        meal.ingredients.forEach { ingredient ->
                            val clean = ingredient.trim()
                            if (clean.isNotEmpty()) {
                                db.nutritionQueries.insertMealIngredient(mealId, clean)
                            }
                        }
                        db.nutritionQueries.insertMealByDay(plan.weekIndex.toLong(), day, idx.toLong(), mealId)
                    }
                }
                db.nutritionQueries.deleteOrphanMeals()
                db.nutritionQueries.deleteOrphanIngredients()
            }
        }
        // Only update the observable flow for the week currently on screen.
        // Background prefetch for future weeks must NOT touch planFlow — it would
        // switch the UI to next week's data while the user is still viewing this week.
        if (plan.weekIndex == activeWeekIndex) {
            planFlow.value = plan
        }
    }

    private fun loadPlanFromDb(weekIndex: Int): NutritionPlan? {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val mealsByDay = mutableMapOf<String, List<Meal>>()
        var hasMeals = false
        for (day in days) {
            val rows = db.nutritionQueries.selectMealsForDay(weekIndex.toLong(), day).executeAsList()
            if (rows.isEmpty()) continue
            hasMeals = true
            val meals = mutableListOf<Meal>()
            var currentId: String? = null
            var currentIngredients = linkedSetOf<String>()
            var baseMeal: Meal? = null
            fun flushCurrent() {
                baseMeal?.let { meal ->
                    meals += meal.copy(ingredients = currentIngredients.toList())
                }
            }
            rows.forEach { row ->
                if (currentId != row.id) {
                    flushCurrent()
                    currentId = row.id
                    currentIngredients = linkedSetOf()
                    baseMeal = Meal(
                        name = row.name,
                        ingredients = emptyList(),
                        kcal = row.kcal.toInt(),
                        macros = Macros(
                            proteinGrams = row.protein.toInt(),
                            fatGrams = row.fat.toInt(),
                            carbsGrams = row.carbs.toInt(),
                            kcal = row.kcal.toInt()
                        ),
                        recipe = row.recipe,
                    )
                }
                val ingredient = row.ingredient?.trim()
                if (!ingredient.isNullOrEmpty()) {
                    currentIngredients += ingredient
                }
            }
            flushCurrent()
            mealsByDay[day] = meals
        }
        if (!hasMeals) return null
        // The saved canonical list; weeks stored before it existed fall back to their ingredients.
        val shopping = db.nutritionQueries.selectShoppingItemsForWeek(weekIndex.toLong()).executeAsList()
            .ifEmpty { db.nutritionQueries.selectShoppingListForWeek(weekIndex.toLong()).executeAsList() }
        val dailyTarget = db.nutritionQueries.selectDailyTarget(weekIndex.toLong()).executeAsOneOrNull()?.let {
            Macros(proteinGrams = it.protein.toInt(), fatGrams = it.fat.toInt(), carbsGrams = it.carbs.toInt(), kcal = it.kcal.toInt())
        }
        return NutritionPlan(weekIndex, mealsByDay, shopping, dailyTarget)
    }
}
