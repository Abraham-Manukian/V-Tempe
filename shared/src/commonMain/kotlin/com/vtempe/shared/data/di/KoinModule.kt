package com.vtempe.shared.data.di

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.createHttpClient
import com.vtempe.shared.domain.model.*
import com.vtempe.shared.domain.repository.*
import com.vtempe.shared.data.repo.TrainingRepositoryDb
import com.vtempe.shared.data.repo.NetworkAiTrainerRepository
import com.vtempe.shared.data.repo.NetworkChatRepository
import com.vtempe.shared.data.repo.AiResponseCache
import com.vtempe.shared.data.repo.ExerciseCalibrationSettingsRepository
import com.vtempe.shared.data.repo.NutritionRepositoryDb
import com.vtempe.shared.domain.usecase.*
import com.vtempe.shared.data.repo.ProfileSettingsRepository
import com.vtempe.shared.data.repo.ProfileRepositoryDb
import com.vtempe.shared.data.repo.SettingsPreferencesRepository
import com.vtempe.shared.data.repo.WorkoutProgressStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

object DI {
    fun coreModule(apiBaseUrl: String): Module = module {
        single { createHttpClient() }
        single { ApiClient(get(), apiBaseUrl) }

        // Settings storage
        single { Settings() }
        single { AiResponseCache(get()) }
        single { WorkoutProgressStore(get(), get()) }
        single<ExerciseCalibrationRepository> { ExerciseCalibrationSettingsRepository(get()) }

        // Repositories
        single<PreferencesRepository> { SettingsPreferencesRepository(get()) }
        single<ProfileRepository> { ProfileRepositoryDb(get()) }
        single<AiTrainerRepository> { NetworkAiTrainerRepository(get(), get(), get(), get()) }
        single<ChatRepository> { NetworkChatRepository(get(), get(), get(), get()) }
        single<TrainingRepository> {
            TrainingRepositoryDb(
                db = get(),
                ai = get(),
                validateSubscription = get(),
                cache = get(),
                progressStore = get()
            )
        }
        single<NutritionRepository> {
            NutritionRepositoryDb(
                db = get(),
                ai = get(),
                validateSubscription = get(),
                preferences = get(),
                cache = get()
            )
        }
        single<AdviceRepository> { StubAdviceRepository() }
        single<PurchasesRepository> { StubPurchasesRepository() }
        single<SyncRepository> { StubSyncRepository() }

        // Use cases
        factory { GenerateTrainingPlan(get(), get()) }
        factory { LogWorkoutSet(get()) }
        factory { GenerateNutritionPlan(get(), get()) }
        factory { BootstrapCoachData(get(), get(), get(), get(), get(), get()) }
        factory { EnsureCoachData(get(), get(), get(), get(), get(), get()) }
        factory { SyncWithBackend(get()) }
        factory { ValidateSubscription(get()) }
        factory { AskAiTrainer(get(), get(), get(), get(), get(), get(), get()) }
    }
}
// --- Simple placeholder implementations (MVP offline-first scaffolding) ---

class InMemoryProfileRepository : ProfileRepository {
    private var profile: Profile? = null
    override suspend fun getProfile(): Profile? = profile
    override suspend fun upsertProfile(profile: Profile) { this.profile = profile }
    override suspend fun clearAll() { profile = null }
}

class LocalTrainingRepository : TrainingRepository {
    private val workouts = MutableStateFlow<List<Workout>>(emptyList())
    private val progress = MutableStateFlow<Map<String, WorkoutProgress>>(emptyMap())
    override suspend fun generatePlan(profile: Profile, weekIndex: Int): TrainingPlan {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val w = List(3) { day ->
            Workout(
                id = "w_${'$'}weekIndex_${'$'}day",
                date = today,
                sets = listOf(
                    WorkoutSet(exerciseId = "squat", reps = 8, weightKg = 40.0, rpe = 7.5),
                    WorkoutSet(exerciseId = "bench", reps = 10, weightKg = 30.0, rpe = 7.0)
                )
            )
        }
        workouts.value = w
        return TrainingPlan(weekIndex = weekIndex, workouts = w)
    }
    override suspend fun logSet(workoutId: String, set: WorkoutSet) {
        val current = progress.value[workoutId] ?: WorkoutProgress(workoutId = workoutId)
        val updated = current.copy(
            extraSets = current.extraSets + ExtraWorkoutSet(
                exerciseId = set.exerciseId,
                reps = set.reps,
                weightKg = set.weightKg,
                rpe = set.rpe
            ),
            updatedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            submitted = false
        )
        progress.value = progress.value + (workoutId to updated)
    }
    override suspend fun savePlan(plan: TrainingPlan) {
        workouts.value = plan.workouts
    }
    override fun observeWorkouts(): Flow<List<Workout>> = workouts.asStateFlow()
    override fun observeWorkoutProgress(): Flow<Map<String, WorkoutProgress>> = progress.asStateFlow()
    override suspend fun saveWorkoutProgress(progress: WorkoutProgress) {
        this.progress.value = this.progress.value + (progress.workoutId to progress)
    }
    override suspend fun recentWorkoutSummaries(limit: Int): List<WorkoutSummary> = workouts.value
        .mapNotNull { workout ->
            val workoutProgress = progress.value[workout.id] ?: return@mapNotNull null
            val completedItems = workoutProgress.performedSets.count { it.completed }
            if (completedItems == 0 && workoutProgress.extraSets.isEmpty() && workoutProgress.notes.isBlank()) {
                return@mapNotNull null
            }
            WorkoutSummary(
                workoutId = workout.id,
                date = workout.date.toString(),
                completionRate = if (workout.sets.isEmpty()) 0.0 else completedItems.toDouble() / workout.sets.size.toDouble(),
                completedItems = completedItems,
                plannedItems = workout.sets.size,
                totalVolumeKg = 0.0,
                averageRpe = null,
                notes = workoutProgress.notes
            )
        }
        .sortedByDescending { it.date }
        .take(limit)

    override suspend fun hasPlan(weekIndex: Int): Boolean = workouts.value.isNotEmpty()
}

class LocalNutritionRepository(
    private val preferences: PreferencesRepository
) : NutritionRepository {
    private val planFlow = MutableStateFlow<NutritionPlan?>(null)

    override suspend fun generatePlan(profile: Profile, weekIndex: Int): NutritionPlan {
        val kcalTarget = tdeeKcal(profile)
        val macros = macrosFor(profile, kcalTarget)
        val languageTag = preferences.getLanguageTag()?.lowercase() ?: ""
        val templates = weeklyMealTemplates(languageTag)
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val mealsByDay = mutableMapOf<String, List<Meal>>()
        val shopping = mutableSetOf<String>()

        days.forEachIndexed { index, day ->
            val meals = buildMealsForDay(templates[index % templates.size], macros, kcalTarget)
            mealsByDay[day] = meals
            meals.flatMap { it.ingredients }.map { it.trim() }.filter { it.isNotEmpty() }.forEach { shopping += it }
        }
        val plan = NutritionPlan(weekIndex, mealsByDay, shopping.toList().sorted())
        planFlow.value = plan
        return plan
    }

    override suspend fun savePlan(plan: NutritionPlan) {
        planFlow.value = plan
    }

    override fun observePlan(): Flow<NutritionPlan?> = planFlow.asStateFlow()
    override suspend fun hasPlan(weekIndex: Int): Boolean = planFlow.value?.weekIndex == weekIndex

    private data class MealTemplate(val name: String, val ingredients: List<String>)

    private fun weeklyMealTemplates(languageCode: String): List<List<MealTemplate>> {
        val lang = languageCode.lowercase()
        return if (lang.startsWith("ru")) {
            listOf(
                listOf(
                    MealTemplate("Овсянка с ягодами", listOf("овсянка", "ягоды", "молоко")),
                    MealTemplate("Куриная грудка с киноа", listOf("куриная грудка", "киноа", "брокколи")),
                    MealTemplate("Лосось с рисом", listOf("лосось", "коричневый рис", "спаржа"))
                ),
                listOf(
                    MealTemplate("Творог с орехами", listOf("творог", "грецкие орехи", "мёд")),
                    MealTemplate("Говядина с гречкой", listOf("говядина", "гречка", "зелёные овощи")),
                    MealTemplate("Индейка с картофелем", listOf("индейка", "запечённый картофель", "салат"))
                ),
                listOf(
                    MealTemplate("Омлет с овощами", listOf("яйца", "перец", "помидоры")),
                    MealTemplate("Треска с булгуром", listOf("треска", "булгур", "шпинат")),
                    MealTemplate("Тушёная говядина", listOf("говядина", "овощное рагу", "зелень"))
                ),
                listOf(
                    MealTemplate("Греческий йогурт с фруктами", listOf("йогурт", "яблоко", "орехи")),
                    MealTemplate("Курица терияки", listOf("курица", "соус терияки", "рис", "овощи")),
                    MealTemplate("Паста из цельнозерновой муки", listOf("цельнозерновая паста", "томатный соус", "сыр"))
                ),
                listOf(
                    MealTemplate("Смузи со шпинатом", listOf("шпинат", "банан", "йогурт")),
                    MealTemplate("Стейк с овощами", listOf("говяжий стейк", "кабачок", "перец")),
                    MealTemplate("Тофу с рисовой лапшой", listOf("тофу", "рисовая лапша", "овощи"))
                ),
                listOf(
                    MealTemplate("Гранола с кефиром", listOf("гранола", "кефир", "ягоды")),
                    MealTemplate("Лосось на пару", listOf("лосось", "тыква", "зелёная фасоль")),
                    MealTemplate("Курица с чечевицей", listOf("курица", "чечевица", "морковь"))
                ),
                listOf(
                    MealTemplate("Панкейки из овсянки", listOf("овсянка", "яйца", "ягоды")),
                    MealTemplate("Салат с тунцом", listOf("тунец", "листовый салат", "оливки")),
                    MealTemplate("Запечённая треска", listOf("треска", "картофель", "брокколи"))
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

class StubAdviceRepository : AdviceRepository {
    private val adviceMap = MutableStateFlow<Map<String, Advice>>(emptyMap())

    override suspend fun getAdvice(profile: Profile, context: Map<String, Any?>): Advice {
        val topic = (context["topic"] as? String)?.lowercase() ?: "general"
        val current = adviceMap.value[topic]
        if (current != null) return current
        val defaults = defaultAdvice(topic)
        adviceMap.value = adviceMap.value + (topic to defaults)
        return defaults
    }

    override suspend fun saveAdvice(topic: String, advice: Advice) {
        val key = topic.lowercase()
        adviceMap.value = adviceMap.value + (key to advice)
    }

    override fun observeAdvice(topic: String): Flow<Advice> {
        val key = topic.lowercase()
        return adviceMap.map { it[key] ?: defaultAdvice(key) }
    }

    override suspend fun hasAdvice(topic: String): Boolean =
        adviceMap.value.containsKey(topic.lowercase())

    private fun defaultAdvice(topic: String): Advice = when (topic) {
        "sleep" -> Advice(
            messages = listOf(
                "Sleep 7-9 hours when possible.",
                "Keep a consistent bedtime routine.",
                "Limit caffeine six hours before bed."
            )
        )
        else -> Advice(messages = listOf("Stay hydrated", "Warm up properly"))
    }
}
class StubPurchasesRepository : PurchasesRepository {
    override suspend fun isSubscriptionActive(): Boolean = false
}

class StubSyncRepository : SyncRepository {
    override suspend fun syncAll(): Boolean = true
}





