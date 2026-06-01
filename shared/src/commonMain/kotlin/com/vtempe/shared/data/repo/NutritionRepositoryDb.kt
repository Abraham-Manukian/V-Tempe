package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.dto.NutritionPlanDto
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max

class NutritionRepositoryDb(
    private val db: AppDatabase,
    private val ai: com.vtempe.shared.domain.repository.AiTrainerRepository,
    private val languagePrefs: LanguagePreferences,
    private val cache: AiResponseCache
) : NutritionRepository {

    private val planFlow = MutableStateFlow<NutritionPlan?>(null)

    /**
     * The weekIndex currently displayed on screen.
     * Only plans for this week update the observable flow — background
     * prefetch for future weeks writes to DB silently without touching the UI.
     */
    @Volatile private var activeWeekIndex: Int = -1

    override suspend fun generatePlan(profile: Profile, weekIndex: Int): NutritionPlan {
        // NetworkAiTrainerRepository already handles cache fallback internally.
        // We only need to persist on success or fall through to the offline plan.
        when (val aiPlanResult = ai.generateNutritionPlan(profile, weekIndex)) {
            is DataResult.Success -> {
                persistPlan(aiPlanResult.data)
                return aiPlanResult.data
            }
            is DataResult.Failure -> {
                Napier.w(
                    message = "AI nutrition plan unavailable (${aiPlanResult.reason}), using offline plan",
                    throwable = aiPlanResult.throwable
                )
            }
        }

        val fallback = buildOfflinePlan(profile, weekIndex)
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
        return withContext(Dispatchers.IO) { loadPlanFromDb(weekIndex) != null }
    }

    /**
     * Sets this week as the active (displayed) week and loads its plan from DB into
     * the observable flow so the UI renders immediately without waiting for the network.
     * Returns true if a cached plan existed.
     */
    override suspend fun setActiveWeek(weekIndex: Int): Boolean {
        activeWeekIndex = weekIndex
        val plan = withContext(Dispatchers.IO) { loadPlanFromDb(weekIndex) }
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
        withContext(Dispatchers.IO) {
            db.nutritionQueries.deleteMealsFromWeek(weekIndex.toLong())
        }
        // If the current active week was nuked, clear the flow too
        if (activeWeekIndex >= weekIndex) planFlow.value = null
    }

    private suspend fun persistPlan(plan: NutritionPlan) {
        withContext(Dispatchers.IO) {
            // Note: only DB writes here — flow update is below, outside IO context
            db.nutritionQueries.deleteMealsForWeek(plan.weekIndex.toLong())
            plan.mealsByDay.forEach { (day, meals) ->
                meals.forEachIndexed { idx, meal ->
                    val mealId = "m_${plan.weekIndex}_${day}_${idx}"
                    db.nutritionQueries.insertMeal(
                        mealId,
                        meal.name,
                        meal.kcal.toLong(),
                        meal.macros.proteinGrams.toLong(),
                        meal.macros.fatGrams.toLong(),
                        meal.macros.carbsGrams.toLong()
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
                        )
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
        val shopping = db.nutritionQueries.selectShoppingListForWeek(weekIndex.toLong()).executeAsList()
        return NutritionPlan(weekIndex, mealsByDay, shopping)
    }

    private fun buildOfflinePlan(profile: Profile, weekIndex: Int): NutritionPlan {
        val kcalTarget = tdeeKcal(profile)
        val macrosDay = macrosFor(profile, kcalTarget)
        val languageTag = languagePrefs.getLanguageTag()?.lowercase() ?: ""
        val templates = weeklyMealTemplates(languageTag)
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val mealsByDay = linkedMapOf<String, List<Meal>>()
        val shopping = linkedSetOf<String>()
        days.forEachIndexed { index, day ->
            val dayTemplates = templates[index % templates.size]
            val meals = buildMealsForDay(dayTemplates, macrosDay, kcalTarget)
            mealsByDay[day] = meals
            meals.forEach { meal ->
                meal.ingredients.forEach { ingredient ->
                    val clean = ingredient.trim()
                    if (clean.isNotEmpty()) {
                        shopping += clean
                    }
                }
            }
        }
        return NutritionPlan(
            weekIndex = weekIndex,
            mealsByDay = mealsByDay,
            shoppingList = shopping.toList().sorted()
        )
    }

    private data class MealTemplate(val name: String, val ingredients: List<String>)

    private fun weeklyMealTemplates(languageCode: String): List<List<MealTemplate>> {
        val lang = languageCode.lowercase()
        return if (lang.startsWith("ru")) {
            listOf(
                listOf(
                    MealTemplate("\u041E\u0432\u0441\u044F\u043D\u043A\u0430\u0020\u0441\u0020\u044F\u0433\u043E\u0434\u0430\u043C\u0438", listOf("\u043E\u0432\u0441\u044F\u043D\u044B\u0435\u0020\u0445\u043B\u043E\u043F\u044C\u044F", "\u044F\u0433\u043E\u0434\u044B", "\u043C\u043E\u043B\u043E\u043A\u043E")),
                    MealTemplate("\u041A\u0443\u0440\u0438\u043D\u0430\u044F\u0020\u0433\u0440\u0443\u0434\u043A\u0430\u0020\u0441\u0020\u0433\u0440\u0435\u0447\u043A\u043E\u0439", listOf("\u043A\u0443\u0440\u0438\u043D\u0430\u044F\u0020\u0433\u0440\u0443\u0434\u043A\u0430", "\u0433\u0440\u0435\u0447\u043A\u0430", "\u0431\u0440\u043E\u043A\u043A\u043E\u043B\u0438")),
                    MealTemplate("\u041B\u043E\u0441\u043E\u0441\u044C\u0020\u0441\u0020\u0431\u0443\u0440\u044B\u043C\u0020\u0440\u0438\u0441\u043E\u043C", listOf("\u043B\u043E\u0441\u043E\u0441\u044C", "\u0431\u0443\u0440\u044B\u0439\u0020\u0440\u0438\u0441", "\u0441\u043F\u0430\u0440\u0436\u0430"))
                ),
                listOf(
                    MealTemplate("\u0419\u043E\u0433\u0443\u0440\u0442\u0020\u0441\u0020\u0433\u0440\u0430\u043D\u043E\u043B\u043E\u0439", listOf("\u0433\u0440\u0435\u0447\u0435\u0441\u043A\u0438\u0439\u0020\u0439\u043E\u0433\u0443\u0440\u0442", "\u0433\u0440\u0430\u043D\u043E\u043B\u0430", "\u043C\u0451\u0434")),
                    MealTemplate("\u0413\u043E\u0432\u044F\u0434\u0438\u043D\u0430\u0020\u0441\u0020\u0433\u0440\u0435\u0447\u043A\u043E\u0439", listOf("\u043F\u043E\u0441\u0442\u043D\u0430\u044F\u0020\u0433\u043E\u0432\u044F\u0434\u0438\u043D\u0430", "\u0433\u0440\u0435\u0447\u043A\u0430", "\u0437\u0435\u043B\u0451\u043D\u044B\u0435\u0020\u043E\u0432\u043E\u0449\u0438")),
                    MealTemplate("\u0418\u043D\u0434\u0435\u0439\u043A\u0430\u0020\u0441\u0020\u0437\u0430\u043F\u0435\u0447\u0451\u043D\u043D\u044B\u043C\u0020\u043A\u0430\u0440\u0442\u043E\u0444\u0435\u043B\u0435\u043C", listOf("\u0444\u0438\u043B\u0435\u0020\u0438\u043D\u0434\u0435\u0439\u043A\u0438", "\u043A\u0430\u0440\u0442\u043E\u0444\u0435\u043B\u044C", "\u043B\u0438\u0441\u0442\u043E\u0432\u043E\u0439\u0020\u0441\u0430\u043B\u0430\u0442"))
                ),
                listOf(
                    MealTemplate("\u041E\u043C\u043B\u0435\u0442\u0020\u0441\u0020\u043E\u0432\u043E\u0449\u0430\u043C\u0438", listOf("\u044F\u0439\u0446\u0430", "\u0441\u043B\u0430\u0434\u043A\u0438\u0439\u0020\u043F\u0435\u0440\u0435\u0446", "\u043F\u043E\u043C\u0438\u0434\u043E\u0440")),
                    MealTemplate("\u0422\u0440\u0435\u0441\u043A\u0430\u0020\u0441\u0020\u0431\u0443\u043B\u0433\u0443\u0440\u043E\u043C", listOf("\u0444\u0438\u043B\u0435\u0020\u0442\u0440\u0435\u0441\u043A\u0438", "\u0431\u0443\u043B\u0433\u0443\u0440", "\u0448\u043F\u0438\u043D\u0430\u0442")),
                    MealTemplate("\u0413\u043E\u0432\u044F\u0436\u044C\u0435\u0020\u0440\u0430\u0433\u0443", listOf("\u0433\u043E\u0432\u044F\u0434\u0438\u043D\u0430", "\u043A\u043E\u0440\u043D\u0435\u043F\u043B\u043E\u0434\u044B", "\u0437\u0435\u043B\u0435\u043D\u044C"))
                ),
                listOf(
                    MealTemplate("\u0424\u0440\u0443\u043A\u0442\u043E\u0432\u0430\u044F\u0020\u0447\u0430\u0448\u0430\u0020\u0441\u0020\u0439\u043E\u0433\u0443\u0440\u0442\u043E\u043C", listOf("\u0439\u043E\u0433\u0443\u0440\u0442", "\u044F\u0431\u043B\u043E\u043A\u043E", "\u043E\u0440\u0435\u0445\u0438")),
                    MealTemplate("\u041A\u0443\u0440\u0438\u0446\u0430\u0020\u0442\u0435\u0440\u0438\u044F\u043A\u0438", listOf("\u043A\u0443\u0440\u0438\u043D\u043E\u0435\u0020\u0444\u0438\u043B\u0435", "\u0441\u043E\u0443\u0441\u0020\u0442\u0435\u0440\u0438\u044F\u043A\u0438", "\u0440\u0438\u0441", "\u043E\u0432\u043E\u0449\u0438")),
                    MealTemplate("\u0426\u0435\u043B\u044C\u043D\u043E\u0437\u0435\u0440\u043D\u043E\u0432\u0430\u044F\u0020\u043F\u0430\u0441\u0442\u0430", listOf("\u0446\u0435\u043B\u044C\u043D\u043E\u0437\u0435\u0440\u043D\u0430\u044F\u0020\u043F\u0430\u0441\u0442\u0430", "\u0442\u043E\u043C\u0430\u0442\u043D\u044B\u0439\u0020\u0441\u043E\u0443\u0441", "\u0441\u044B\u0440"))
                ),
                listOf(
                    MealTemplate("\u0421\u043C\u0443\u0437\u0438\u0020\u0441\u043E\u0020\u0448\u043F\u0438\u043D\u0430\u0442\u043E\u043C", listOf("\u0448\u043F\u0438\u043D\u0430\u0442", "\u0431\u0430\u043D\u0430\u043D", "\u0439\u043E\u0433\u0443\u0440\u0442")),
                    MealTemplate("\u0421\u0442\u0435\u0439\u043A\u0020\u0441\u0020\u043E\u0432\u043E\u0449\u0430\u043C\u0438", listOf("\u0441\u0442\u0435\u0439\u043A", "\u0446\u0443\u043A\u0438\u043D\u0438", "\u0431\u043E\u043B\u0433\u0430\u0440\u0441\u043A\u0438\u0439\u0020\u043F\u0435\u0440\u0435\u0446")),
                    MealTemplate("\u0422\u043E\u0444\u0443\u002D\u043B\u0430\u043F\u0448\u0430", listOf("\u0442\u043E\u0444\u0443", "\u0440\u0438\u0441\u043E\u0432\u0430\u044F\u0020\u043B\u0430\u043F\u0448\u0430", "\u043E\u0432\u043E\u0449\u0438"))
                ),
                listOf(
                    MealTemplate("\u0413\u0440\u0430\u043D\u043E\u043B\u0430\u0020\u0441\u0020\u043A\u0435\u0444\u0438\u0440\u043E\u043C", listOf("\u0433\u0440\u0430\u043D\u043E\u043B\u0430", "\u043A\u0435\u0444\u0438\u0440", "\u044F\u0433\u043E\u0434\u044B")),
                    MealTemplate("\u041F\u0430\u0440\u043E\u0432\u043E\u0439\u0020\u043B\u043E\u0441\u043E\u0441\u044C", listOf("\u043B\u043E\u0441\u043E\u0441\u044C", "\u0442\u044B\u043A\u0432\u0430", "\u0437\u0435\u043B\u0451\u043D\u0430\u044F\u0020\u0444\u0430\u0441\u043E\u043B\u044C")),
                    MealTemplate("\u041A\u0443\u0440\u0438\u0446\u0430\u0020\u0441\u0020\u0447\u0435\u0447\u0435\u0432\u0438\u0446\u0435\u0439", listOf("\u043A\u0443\u0440\u0438\u043D\u043E\u0435\u0020\u0444\u0438\u043B\u0435", "\u0447\u0435\u0447\u0435\u0432\u0438\u0446\u0430", "\u043C\u043E\u0440\u043A\u043E\u0432\u044C"))
                ),
                listOf(
                    MealTemplate("\u041E\u0432\u0441\u044F\u043D\u044B\u0435\u0020\u043F\u0430\u043D\u043A\u0435\u0439\u043A\u0438", listOf("\u043E\u0432\u0441\u044F\u043D\u043A\u0430", "\u044F\u0439\u0446\u0430", "\u044F\u0433\u043E\u0434\u044B")),
                    MealTemplate("\u0421\u0430\u043B\u0430\u0442\u0020\u0441\u0020\u0442\u0443\u043D\u0446\u043E\u043C", listOf("\u0442\u0443\u043D\u0435\u0446", "\u043B\u0438\u0441\u0442\u044C\u044F\u0020\u0441\u0430\u043B\u0430\u0442\u0430", "\u043E\u043B\u0438\u0432\u043A\u0438")),
                    MealTemplate("\u0417\u0430\u043F\u0435\u0447\u0451\u043D\u043D\u0430\u044F\u0020\u0442\u0440\u0435\u0441\u043A\u0430", listOf("\u0442\u0440\u0435\u0441\u043A\u0430", "\u043A\u0430\u0440\u0442\u043E\u0444\u0435\u043B\u044C", "\u0431\u0440\u043E\u043A\u043A\u043E\u043B\u0438"))
                )
            )
        } else {
            listOf(
                listOf(
                    MealTemplate("Oatmeal with Berries", listOf("oats", "berries", "milk")),
                    MealTemplate("Chicken Quinoa Bowl", listOf("chicken breast", "quinoa", "broccoli")),
                    MealTemplate("Salmon with Brown Rice", listOf("salmon", "brown rice", "asparagus"))
                ),
                listOf(
                    MealTemplate("Greek Yogurt Parfait", listOf("greek yogurt", "granola", "honey")),
                    MealTemplate("Beef and Buckwheat", listOf("lean beef", "buckwheat", "green vegetables")),
                    MealTemplate("Turkey with Roast Potatoes", listOf("turkey", "roasted potatoes", "mixed greens"))
                ),
                listOf(
                    MealTemplate("Veggie Omelette", listOf("eggs", "bell pepper", "tomato")),
                    MealTemplate("Cod with Bulgur", listOf("cod", "bulgur", "spinach")),
                    MealTemplate("Hearty Beef Stew", listOf("beef", "root vegetables", "herbs"))
                ),
                listOf(
                    MealTemplate("Fruit & Yogurt Bowl", listOf("yogurt", "apple", "nuts")),
                    MealTemplate("Teriyaki Chicken", listOf("chicken", "teriyaki sauce", "rice", "vegetables")),
                    MealTemplate("Wholegrain Pasta", listOf("wholegrain pasta", "tomato sauce", "cheese"))
                ),
                listOf(
                    MealTemplate("Spinach Smoothie", listOf("spinach", "banana", "yogurt")),
                    MealTemplate("Sirloin and Veggies", listOf("sirloin", "zucchini", "bell pepper")),
                    MealTemplate("Tofu Stir Fry", listOf("tofu", "rice noodles", "vegetables"))
                ),
                listOf(
                    MealTemplate("Granola with Kefir", listOf("granola", "kefir", "berries")),
                    MealTemplate("Steamed Salmon", listOf("salmon", "pumpkin", "green beans")),
                    MealTemplate("Chicken with Lentils", listOf("chicken fillet", "lentils", "carrot"))
                ),
                listOf(
                    MealTemplate("Oat Pancakes", listOf("oats", "eggs", "berries")),
                    MealTemplate("Tuna Salad", listOf("tuna", "lettuce", "olives")),
                    MealTemplate("Baked Cod", listOf("cod", "potatoes", "broccoli"))
                )
            )
        }
    }

    private fun buildMealsForDay(templates: List<MealTemplate>, macrosDay: Macros, kcalTarget: Int): List<Meal> {
        val count = templates.size.coerceAtLeast(1)
        val calories = distributeTotal(kcalTarget, count)
        val proteins = distributeTotal(macrosDay.proteinGrams, count)
        val fats = distributeTotal(macrosDay.fatGrams, count)
        val carbs = distributeTotal(macrosDay.carbsGrams, count)
        return templates.mapIndexed { index, template ->
            Meal(
                name = template.name,
                ingredients = template.ingredients,
                kcal = calories[index],
                macros = Macros(
                    proteinGrams = proteins[index],
                    fatGrams = fats[index],
                    carbsGrams = carbs[index],
                    kcal = calories[index]
                )
            )
        }
    }

    private fun distributeTotal(total: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        if (total <= 0) return List(count) { 0 }
        val base = total / count
        val remainder = total % count
        return List(count) { index -> base + if (index < remainder) 1 else 0 }
    }

    private fun tdeeKcal(p: Profile): Int {
        val s = if (p.sex == Sex.MALE) 5 else -161
        val bmr = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age + s
        val activity = 1.4
        val goalAdj = when (p.goal) {
            Goal.LOSE_FAT -> 0.85
            Goal.GAIN_MUSCLE -> 1.1
            Goal.MAINTAIN -> 1.0
        }
        return (bmr * activity * goalAdj).toInt()
    }

    private fun macrosFor(p: Profile, kcal: Int): Macros {
        val protein = (1.8 * p.weightKg).toInt()
        val fat = max(0.8 * p.weightKg, 40.0).toInt()
        val proteinKcal = protein * 4
        val fatKcal = fat * 9
        val carbsKcal = (kcal - proteinKcal - fatKcal).coerceAtLeast(0)
        val carbs = carbsKcal / 4
        return Macros(proteinGrams = protein, fatGrams = fat, carbsGrams = carbs, kcal = kcal)
    }
}

