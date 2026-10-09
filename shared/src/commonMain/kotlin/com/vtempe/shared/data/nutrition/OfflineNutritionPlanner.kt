package com.vtempe.shared.data.nutrition

import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.nutrition.FoodAllergen
import com.vtempe.shared.domain.nutrition.FoodRestrictions
import com.vtempe.shared.domain.nutrition.NoSafeMealPlanException
import com.vtempe.shared.domain.nutrition.violationReason
import kotlin.math.max

/**
 * Builds the week's meal plan on the device when the AI plan is unavailable.
 *
 * Every template carries its allergens explicitly (granola, teriyaki sauce or bulgur hide them
 * from a keyword check), and a meal is used only when neither its allergens nor its text break
 * the user's [FoodRestrictions] (VT-AUD-025). Incompatible meals are replaced from the rest of
 * the library and a plant-based reserve; with nothing compatible at all the planner refuses
 * with [NoSafeMealPlanException] rather than serving a universal menu.
 */
class OfflineNutritionPlanner {

    fun buildPlan(profile: Profile, weekIndex: Int, languageTag: String): NutritionPlan {
        val restrictions = FoodRestrictions.from(profile)
        val kcalTarget = tdeeKcal(profile)
        val macrosDay = macrosFor(profile, kcalTarget)
        val library = weeklyMealTemplates(languageTag)
        val reserve = library.flatten() + plantBasedReserve(languageTag)
        val allowed = { template: MealTemplate -> template.isAllowedFor(restrictions) }
        if (reserve.none(allowed)) {
            throw NoSafeMealPlanException("No offline meal is compatible with the profile's food restrictions")
        }

        val mealsByDay = linkedMapOf<String, List<Meal>>()
        val shopping = linkedSetOf<String>()
        Days.forEachIndexed { index, day ->
            val dayTemplates = pickMeals(library[index % library.size], reserve.rotate(index * MealsPerDay), allowed)
            val meals = buildMealsForDay(dayTemplates, macrosDay, kcalTarget)
            mealsByDay[day] = meals
            meals.forEach { meal ->
                meal.ingredients.forEach { ingredient ->
                    val clean = ingredient.trim()
                    if (clean.isNotEmpty()) shopping += clean
                }
            }
        }
        return NutritionPlan(weekIndex = weekIndex, mealsByDay = mealsByDay, shoppingList = shopping.toList().sorted())
    }

    /** The day's own templates when allowed; gaps are filled from [reserve] without repeats.
     *  Without restrictions this is exactly the day's templates, as before. */
    private fun pickMeals(
        dayTemplates: List<MealTemplate>,
        reserve: List<MealTemplate>,
        allowed: (MealTemplate) -> Boolean,
    ): List<MealTemplate> {
        val picked = dayTemplates.filter(allowed).toMutableList()
        reserve.asSequence()
            .filter { candidate -> allowed(candidate) && picked.none { it.name == candidate.name } }
            .take((MealsPerDay - picked.size).coerceAtLeast(0))
            .forEach { picked += it }
        return picked
    }

    private fun MealTemplate.isAllowedFor(restrictions: FoodRestrictions): Boolean =
        allergens.none { it in restrictions.tags } &&
            (listOf(name) + ingredients).all { restrictions.violationReason(it) == null }

    private fun <T> List<T>.rotate(by: Int): List<T> =
        if (isEmpty()) this else (indices).map { this[(it + by) % size] }

    private data class MealTemplate(
        val name: String,
        val ingredients: List<String>,
        val allergens: Set<FoodAllergen> = emptySet(),
    )

    private fun weeklyMealTemplates(languageTag: String): List<List<MealTemplate>> {
        return if (languageTag.lowercase().startsWith("ru")) {
            listOf(
                listOf(
                    MealTemplate("\u041E\u0432\u0441\u044F\u043D\u043A\u0430\u0020\u0441\u0020\u044F\u0433\u043E\u0434\u0430\u043C\u0438", listOf("\u043E\u0432\u0441\u044F\u043D\u044B\u0435\u0020\u0445\u043B\u043E\u043F\u044C\u044F", "\u044F\u0433\u043E\u0434\u044B", "\u043C\u043E\u043B\u043E\u043A\u043E"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose, FoodAllergen.Gluten)),
                    MealTemplate("\u041A\u0443\u0440\u0438\u043D\u0430\u044F\u0020\u0433\u0440\u0443\u0434\u043A\u0430\u0020\u0441\u0020\u0433\u0440\u0435\u0447\u043A\u043E\u0439", listOf("\u043A\u0443\u0440\u0438\u043D\u0430\u044F\u0020\u0433\u0440\u0443\u0434\u043A\u0430", "\u0433\u0440\u0435\u0447\u043A\u0430", "\u0431\u0440\u043E\u043A\u043A\u043E\u043B\u0438"), setOf(FoodAllergen.Poultry)),
                    MealTemplate("\u041B\u043E\u0441\u043E\u0441\u044C\u0020\u0441\u0020\u0431\u0443\u0440\u044B\u043C\u0020\u0440\u0438\u0441\u043E\u043C", listOf("\u043B\u043E\u0441\u043E\u0441\u044C", "\u0431\u0443\u0440\u044B\u0439\u0020\u0440\u0438\u0441", "\u0441\u043F\u0430\u0440\u0436\u0430"), setOf(FoodAllergen.Fish))
                ),
                listOf(
                    MealTemplate("\u0419\u043E\u0433\u0443\u0440\u0442\u0020\u0441\u0020\u0433\u0440\u0430\u043D\u043E\u043B\u043E\u0439", listOf("\u0433\u0440\u0435\u0447\u0435\u0441\u043A\u0438\u0439\u0020\u0439\u043E\u0433\u0443\u0440\u0442", "\u0433\u0440\u0430\u043D\u043E\u043B\u0430", "\u043C\u0451\u0434"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose, FoodAllergen.Gluten, FoodAllergen.TreeNut, FoodAllergen.Honey)),
                    MealTemplate("\u0413\u043E\u0432\u044F\u0434\u0438\u043D\u0430\u0020\u0441\u0020\u0433\u0440\u0435\u0447\u043A\u043E\u0439", listOf("\u043F\u043E\u0441\u0442\u043D\u0430\u044F\u0020\u0433\u043E\u0432\u044F\u0434\u0438\u043D\u0430", "\u0433\u0440\u0435\u0447\u043A\u0430", "\u0437\u0435\u043B\u0451\u043D\u044B\u0435\u0020\u043E\u0432\u043E\u0449\u0438"), setOf(FoodAllergen.Meat)),
                    MealTemplate("\u0418\u043D\u0434\u0435\u0439\u043A\u0430\u0020\u0441\u0020\u0437\u0430\u043F\u0435\u0447\u0451\u043D\u043D\u044B\u043C\u0020\u043A\u0430\u0440\u0442\u043E\u0444\u0435\u043B\u0435\u043C", listOf("\u0444\u0438\u043B\u0435\u0020\u0438\u043D\u0434\u0435\u0439\u043A\u0438", "\u043A\u0430\u0440\u0442\u043E\u0444\u0435\u043B\u044C", "\u043B\u0438\u0441\u0442\u043E\u0432\u043E\u0439\u0020\u0441\u0430\u043B\u0430\u0442"), setOf(FoodAllergen.Poultry))
                ),
                listOf(
                    MealTemplate("\u041E\u043C\u043B\u0435\u0442\u0020\u0441\u0020\u043E\u0432\u043E\u0449\u0430\u043C\u0438", listOf("\u044F\u0439\u0446\u0430", "\u0441\u043B\u0430\u0434\u043A\u0438\u0439\u0020\u043F\u0435\u0440\u0435\u0446", "\u043F\u043E\u043C\u0438\u0434\u043E\u0440"), setOf(FoodAllergen.Egg)),
                    MealTemplate("\u0422\u0440\u0435\u0441\u043A\u0430\u0020\u0441\u0020\u0431\u0443\u043B\u0433\u0443\u0440\u043E\u043C", listOf("\u0444\u0438\u043B\u0435\u0020\u0442\u0440\u0435\u0441\u043A\u0438", "\u0431\u0443\u043B\u0433\u0443\u0440", "\u0448\u043F\u0438\u043D\u0430\u0442"), setOf(FoodAllergen.Fish, FoodAllergen.Gluten)),
                    MealTemplate("\u0413\u043E\u0432\u044F\u0436\u044C\u0435\u0020\u0440\u0430\u0433\u0443", listOf("\u0433\u043E\u0432\u044F\u0434\u0438\u043D\u0430", "\u043A\u043E\u0440\u043D\u0435\u043F\u043B\u043E\u0434\u044B", "\u0437\u0435\u043B\u0435\u043D\u044C"), setOf(FoodAllergen.Meat))
                ),
                listOf(
                    MealTemplate("\u0424\u0440\u0443\u043A\u0442\u043E\u0432\u0430\u044F\u0020\u0447\u0430\u0448\u0430\u0020\u0441\u0020\u0439\u043E\u0433\u0443\u0440\u0442\u043E\u043C", listOf("\u0439\u043E\u0433\u0443\u0440\u0442", "\u044F\u0431\u043B\u043E\u043A\u043E", "\u043E\u0440\u0435\u0445\u0438"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose, FoodAllergen.TreeNut, FoodAllergen.Peanut)),
                    MealTemplate("\u041A\u0443\u0440\u0438\u0446\u0430\u0020\u0442\u0435\u0440\u0438\u044F\u043A\u0438", listOf("\u043A\u0443\u0440\u0438\u043D\u043E\u0435\u0020\u0444\u0438\u043B\u0435", "\u0441\u043E\u0443\u0441\u0020\u0442\u0435\u0440\u0438\u044F\u043A\u0438", "\u0440\u0438\u0441", "\u043E\u0432\u043E\u0449\u0438"), setOf(FoodAllergen.Poultry, FoodAllergen.Soy, FoodAllergen.Gluten)),
                    MealTemplate("\u0426\u0435\u043B\u044C\u043D\u043E\u0437\u0435\u0440\u043D\u043E\u0432\u0430\u044F\u0020\u043F\u0430\u0441\u0442\u0430", listOf("\u0446\u0435\u043B\u044C\u043D\u043E\u0437\u0435\u0440\u043D\u0430\u044F\u0020\u043F\u0430\u0441\u0442\u0430", "\u0442\u043E\u043C\u0430\u0442\u043D\u044B\u0439\u0020\u0441\u043E\u0443\u0441", "\u0441\u044B\u0440"), setOf(FoodAllergen.Gluten, FoodAllergen.Dairy, FoodAllergen.Lactose))
                ),
                listOf(
                    MealTemplate("\u0421\u043C\u0443\u0437\u0438\u0020\u0441\u043E\u0020\u0448\u043F\u0438\u043D\u0430\u0442\u043E\u043C", listOf("\u0448\u043F\u0438\u043D\u0430\u0442", "\u0431\u0430\u043D\u0430\u043D", "\u0439\u043E\u0433\u0443\u0440\u0442"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose)),
                    MealTemplate("\u0421\u0442\u0435\u0439\u043A\u0020\u0441\u0020\u043E\u0432\u043E\u0449\u0430\u043C\u0438", listOf("\u0441\u0442\u0435\u0439\u043A", "\u0446\u0443\u043A\u0438\u043D\u0438", "\u0431\u043E\u043B\u0433\u0430\u0440\u0441\u043A\u0438\u0439\u0020\u043F\u0435\u0440\u0435\u0446"), setOf(FoodAllergen.Meat)),
                    MealTemplate("\u0422\u043E\u0444\u0443\u002D\u043B\u0430\u043F\u0448\u0430", listOf("\u0442\u043E\u0444\u0443", "\u0440\u0438\u0441\u043E\u0432\u0430\u044F\u0020\u043B\u0430\u043F\u0448\u0430", "\u043E\u0432\u043E\u0449\u0438"), setOf(FoodAllergen.Soy))
                ),
                listOf(
                    MealTemplate("\u0413\u0440\u0430\u043D\u043E\u043B\u0430\u0020\u0441\u0020\u043A\u0435\u0444\u0438\u0440\u043E\u043C", listOf("\u0433\u0440\u0430\u043D\u043E\u043B\u0430", "\u043A\u0435\u0444\u0438\u0440", "\u044F\u0433\u043E\u0434\u044B"), setOf(FoodAllergen.Gluten, FoodAllergen.TreeNut, FoodAllergen.Dairy, FoodAllergen.Lactose)),
                    MealTemplate("\u041F\u0430\u0440\u043E\u0432\u043E\u0439\u0020\u043B\u043E\u0441\u043E\u0441\u044C", listOf("\u043B\u043E\u0441\u043E\u0441\u044C", "\u0442\u044B\u043A\u0432\u0430", "\u0437\u0435\u043B\u0451\u043D\u0430\u044F\u0020\u0444\u0430\u0441\u043E\u043B\u044C"), setOf(FoodAllergen.Fish)),
                    MealTemplate("\u041A\u0443\u0440\u0438\u0446\u0430\u0020\u0441\u0020\u0447\u0435\u0447\u0435\u0432\u0438\u0446\u0435\u0439", listOf("\u043A\u0443\u0440\u0438\u043D\u043E\u0435\u0020\u0444\u0438\u043B\u0435", "\u0447\u0435\u0447\u0435\u0432\u0438\u0446\u0430", "\u043C\u043E\u0440\u043A\u043E\u0432\u044C"), setOf(FoodAllergen.Poultry))
                ),
                listOf(
                    MealTemplate("\u041E\u0432\u0441\u044F\u043D\u044B\u0435\u0020\u043F\u0430\u043D\u043A\u0435\u0439\u043A\u0438", listOf("\u043E\u0432\u0441\u044F\u043D\u043A\u0430", "\u044F\u0439\u0446\u0430", "\u044F\u0433\u043E\u0434\u044B"), setOf(FoodAllergen.Gluten, FoodAllergen.Egg)),
                    MealTemplate("\u0421\u0430\u043B\u0430\u0442\u0020\u0441\u0020\u0442\u0443\u043D\u0446\u043E\u043C", listOf("\u0442\u0443\u043D\u0435\u0446", "\u043B\u0438\u0441\u0442\u044C\u044F\u0020\u0441\u0430\u043B\u0430\u0442\u0430", "\u043E\u043B\u0438\u0432\u043A\u0438"), setOf(FoodAllergen.Fish)),
                    MealTemplate("\u0417\u0430\u043F\u0435\u0447\u0451\u043D\u043D\u0430\u044F\u0020\u0442\u0440\u0435\u0441\u043A\u0430", listOf("\u0442\u0440\u0435\u0441\u043A\u0430", "\u043A\u0430\u0440\u0442\u043E\u0444\u0435\u043B\u044C", "\u0431\u0440\u043E\u043A\u043A\u043E\u043B\u0438"), setOf(FoodAllergen.Fish))
                )
            )
        } else {
            listOf(
                listOf(
                    MealTemplate("Oatmeal with Berries", listOf("oats", "berries", "milk"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose, FoodAllergen.Gluten)),
                    MealTemplate("Chicken Quinoa Bowl", listOf("chicken breast", "quinoa", "broccoli"), setOf(FoodAllergen.Poultry)),
                    MealTemplate("Salmon with Brown Rice", listOf("salmon", "brown rice", "asparagus"), setOf(FoodAllergen.Fish))
                ),
                listOf(
                    MealTemplate("Greek Yogurt Parfait", listOf("greek yogurt", "granola", "honey"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose, FoodAllergen.Gluten, FoodAllergen.TreeNut, FoodAllergen.Honey)),
                    MealTemplate("Beef and Buckwheat", listOf("lean beef", "buckwheat", "green vegetables"), setOf(FoodAllergen.Meat)),
                    MealTemplate("Turkey with Roast Potatoes", listOf("turkey", "roasted potatoes", "mixed greens"), setOf(FoodAllergen.Poultry))
                ),
                listOf(
                    MealTemplate("Veggie Omelette", listOf("eggs", "bell pepper", "tomato"), setOf(FoodAllergen.Egg)),
                    MealTemplate("Cod with Bulgur", listOf("cod", "bulgur", "spinach"), setOf(FoodAllergen.Fish, FoodAllergen.Gluten)),
                    MealTemplate("Hearty Beef Stew", listOf("beef", "root vegetables", "herbs"), setOf(FoodAllergen.Meat))
                ),
                listOf(
                    MealTemplate("Fruit & Yogurt Bowl", listOf("yogurt", "apple", "nuts"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose, FoodAllergen.TreeNut, FoodAllergen.Peanut)),
                    MealTemplate("Teriyaki Chicken", listOf("chicken", "teriyaki sauce", "rice", "vegetables"), setOf(FoodAllergen.Poultry, FoodAllergen.Soy, FoodAllergen.Gluten)),
                    MealTemplate("Wholegrain Pasta", listOf("wholegrain pasta", "tomato sauce", "cheese"), setOf(FoodAllergen.Gluten, FoodAllergen.Dairy, FoodAllergen.Lactose))
                ),
                listOf(
                    MealTemplate("Spinach Smoothie", listOf("spinach", "banana", "yogurt"), setOf(FoodAllergen.Dairy, FoodAllergen.Lactose)),
                    MealTemplate("Sirloin and Veggies", listOf("sirloin", "zucchini", "bell pepper"), setOf(FoodAllergen.Meat)),
                    MealTemplate("Tofu Stir Fry", listOf("tofu", "rice noodles", "vegetables"), setOf(FoodAllergen.Soy))
                ),
                listOf(
                    MealTemplate("Granola with Kefir", listOf("granola", "kefir", "berries"), setOf(FoodAllergen.Gluten, FoodAllergen.TreeNut, FoodAllergen.Dairy, FoodAllergen.Lactose)),
                    MealTemplate("Steamed Salmon", listOf("salmon", "pumpkin", "green beans"), setOf(FoodAllergen.Fish)),
                    MealTemplate("Chicken with Lentils", listOf("chicken fillet", "lentils", "carrot"), setOf(FoodAllergen.Poultry))
                ),
                listOf(
                    MealTemplate("Oat Pancakes", listOf("oats", "eggs", "berries"), setOf(FoodAllergen.Gluten, FoodAllergen.Egg)),
                    MealTemplate("Tuna Salad", listOf("tuna", "lettuce", "olives"), setOf(FoodAllergen.Fish)),
                    MealTemplate("Baked Cod", listOf("cod", "potatoes", "broccoli"), setOf(FoodAllergen.Fish))
                )
            )
        }
    }

    /** Meals free of every tracked allergen and animal product — the last resort for users
     *  whose restrictions exclude most of the weekly library. */
    private fun plantBasedReserve(languageTag: String): List<MealTemplate> =
        if (languageTag.lowercase().startsWith("ru")) {
            listOf(
                MealTemplate("Гречка с запечёнными овощами", listOf("гречка", "кабачок", "болгарский перец", "оливковое масло")),
                MealTemplate("Чечевичное рагу с овощами", listOf("чечевица", "морковь", "томаты", "лук")),
                MealTemplate("Салат с нутом", listOf("нут", "огурец", "томаты", "оливковое масло")),
                MealTemplate("Рис с фасолью и овощами", listOf("рис", "фасоль", "кукуруза", "болгарский перец")),
                MealTemplate("Запечённый картофель с брокколи", listOf("картофель", "брокколи", "морковь", "оливковое масло")),
                MealTemplate("Фруктовый салат с семенами тыквы", listOf("яблоко", "банан", "семена тыквы")),
                MealTemplate("Пшённая каша с фруктами", listOf("пшено", "яблоко", "банан")),
            )
        } else {
            listOf(
                MealTemplate("Buckwheat with Roasted Vegetables", listOf("buckwheat", "zucchini", "bell pepper", "olive oil")),
                MealTemplate("Lentil and Vegetable Stew", listOf("lentils", "carrot", "tomatoes", "onion")),
                MealTemplate("Chickpea Salad", listOf("chickpeas", "cucumber", "tomatoes", "olive oil")),
                MealTemplate("Rice with Beans and Vegetables", listOf("rice", "black beans", "corn", "bell pepper")),
                MealTemplate("Baked Potatoes with Broccoli", listOf("potatoes", "broccoli", "carrot", "olive oil")),
                MealTemplate("Fruit Salad with Pumpkin Seeds", listOf("apple", "banana", "pumpkin seeds")),
                MealTemplate("Millet Porridge with Fruit", listOf("millet", "apple", "banana")),
            )
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

    // Mirrors the richer formula in server's CoachBundlePromptBuilder.computeTargetNutrition —
    // same Mifflin-St Jeor BMR + lifestyle/training-day activity factor + goal adjustment +
    // evidence-based macro ranges (Morton 2018, ISSN 2017), so a user who falls back to this
    // offline plan (no network) gets the same calorie/macro target as the online path would
    // compute, not a silently different number from a flat activity=1.4 shortcut.
    // Recent-weight-trend adjustment is server-only (needs the AI request's recentWeights
    // history, not available in this offline repo layer).
    private fun tdeeKcal(p: Profile): Int {
        val s = if (p.sex == Sex.MALE) 5.0 else -161.0
        val bmr = 10.0 * p.weightKg + 6.25 * p.heightCm - 5.0 * p.age + s

        val lifestyleBase = when (p.lifestyleActivity) {
            LifestyleActivity.LIGHT -> 1.30
            LifestyleActivity.ACTIVE -> 1.45
            LifestyleActivity.VERY_ACTIVE -> 1.60
            LifestyleActivity.SEDENTARY -> 1.20
        }
        val trainingDays = p.weeklySchedule.count { it.value }
        val trainingBonus = when {
            trainingDays == 0 -> 0.00
            trainingDays <= 2 -> 0.10
            trainingDays <= 4 -> 0.15
            else -> 0.20
        }
        val activityFactor = (lifestyleBase + trainingBonus).coerceAtMost(1.90)
        val tdee = bmr * activityFactor

        val calorieFactor = when (p.goal) {
            Goal.LOSE_FAT -> 0.85
            Goal.GAIN_MUSCLE -> 1.10
            Goal.MAINTAIN -> 1.00
        }
        return (tdee * calorieFactor).coerceAtLeast(bmr).toInt()
    }

    private fun macrosFor(p: Profile, kcal: Int): Macros {
        val proteinMultiplier = when (p.goal) {
            Goal.GAIN_MUSCLE -> 1.8
            Goal.LOSE_FAT -> 1.6
            Goal.MAINTAIN -> 1.7
        }
        val protein = (p.weightKg * proteinMultiplier).toInt()
            .coerceIn((p.weightKg * 1.4).toInt(), (p.weightKg * 2.2).toInt())
        val fat = max(0.9 * p.weightKg, 40.0).toInt()
        val proteinKcal = protein * 4
        val fatKcal = fat * 9
        val carbsKcal = (kcal - proteinKcal - fatKcal).coerceAtLeast(0)
        val carbs = carbsKcal / 4
        return Macros(proteinGrams = protein, fatGrams = fat, carbsGrams = carbs, kcal = kcal)
    }

    private companion object {
        val Days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        const val MealsPerDay = 3
    }
}
