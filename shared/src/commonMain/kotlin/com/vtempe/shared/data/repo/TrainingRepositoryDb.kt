package com.vtempe.shared.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.vtempe.shared.data.network.dto.TrainingPlanDto
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class TrainingRepositoryDb(
    private val db: AppDatabase,
    private val ai: com.vtempe.shared.domain.repository.AiTrainerRepository,
    private val validateSubscription: com.vtempe.shared.domain.usecase.ValidateSubscription,
    private val cache: AiResponseCache
) : TrainingRepository {

    override suspend fun generatePlan(profile: Profile, weekIndex: Int): TrainingPlan {
        when (val aiPlanResult = ai.generateTrainingPlan(profile, weekIndex)) {
            is DataResult.Success -> {
                persistPlan(aiPlanResult.data)
                cache.storeTraining(TrainingPlanDto.fromDomain(aiPlanResult.data))
                return aiPlanResult.data
            }
            is DataResult.Failure -> {
                cache.lastTraining()?.let { cached ->
                    Napier.w("Using cached training plan after AI failure ${aiPlanResult.reason}", aiPlanResult.throwable)
                    val domain = cached.toDomain()
                    persistPlan(domain)
                    return domain
                }
                Napier.w(
                    message = "AI training plan generation failed: ${aiPlanResult.reason} ${aiPlanResult.message.orEmpty()}",
                    throwable = aiPlanResult.throwable
                )
            }
        }

        if (validateSubscription()) {
            Napier.i("Falling back to offline training plan due to missing AI response")
        }

        seedFallbackExercises()
        val fallbackPlan = buildOfflinePlan(weekIndex)
        persistPlan(fallbackPlan)
        cache.storeTraining(TrainingPlanDto.fromDomain(fallbackPlan))
        return fallbackPlan
    }

    override suspend fun logSet(workoutId: String, set: WorkoutSet) {
        db.workoutQueries.insertSet(workoutId, set.exerciseId, set.reps.toLong(), set.weightKg, set.rpe)
    }

    override suspend fun savePlan(plan: TrainingPlan) {
        persistPlan(plan)
        cache.storeTraining(TrainingPlanDto.fromDomain(plan))
    }

    override suspend fun hasPlan(weekIndex: Int): Boolean =
        db.workoutQueries.selectWorkoutsWithSetsByWeek(weekIndex.toLong()).executeAsList().isNotEmpty()

    override fun observeWorkouts(): Flow<List<Workout>> =
        db.workoutQueries.selectWorkoutsWithSets()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                val grouped = rows.groupBy { it.id }
                grouped.map { (id, list) ->
                    val dateStr = list.firstOrNull()?.date
                        ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                    val date = LocalDate.parse(dateStr)
                    val sets = list
                        .filter { it.exerciseId != null }
                        .map { r ->
                            WorkoutSet(
                                exerciseId = r.exerciseId!!,
                                reps = r.reps!!.toInt(),
                                weightKg = r.weightKg,
                                rpe = r.rpe
                            )
                        }
                    Workout(id = id, date = date, sets = sets)
                }
            }

    private fun persistPlan(plan: TrainingPlan) {
        db.workoutQueries.transaction {
            db.workoutQueries.deleteSetsByWeek(plan.weekIndex.toLong())
            db.workoutQueries.deleteWorkoutsByWeek(plan.weekIndex.toLong())
            plan.workouts.forEach { workout ->
                db.workoutQueries.insertWorkout(workout.id, plan.weekIndex.toLong(), workout.date.toString())
                db.workoutQueries.deleteSetsForWorkout(workout.id)
                workout.sets.forEach { set ->
                    db.workoutQueries.insertSet(workout.id, set.exerciseId, set.reps.toLong(), set.weightKg, set.rpe)
                }
            }
        }
    }

    private fun seedFallbackExercises() {
        db.exerciseQueries.upsertExercise("squat", "\u041F\u0440\u0438\u0441\u0435\u0434\u0430\u043D\u0438\u044F\u0020\u0441\u043E\u0020\u0448\u0442\u0430\u043D\u0433\u043E\u0439", "[\"legs\"]", 2L)
        db.exerciseQueries.upsertExercise("bench", "\u0416\u0438\u043C\u0020\u043B\u0451\u0436\u0430", "[\"chest\"]", 2L)
        db.exerciseQueries.upsertExercise("deadlift", "\u0421\u0442\u0430\u043D\u043E\u0432\u0430\u044F\u0020\u0442\u044F\u0433\u0430", "[\"back\",\"legs\"]", 3L)
        db.exerciseQueries.upsertExercise("ohp", "\u0416\u0438\u043C\u0020\u0441\u0442\u043E\u044F", "[\"shoulders\"]", 2L)
        db.exerciseQueries.upsertExercise("row", "\u0422\u044F\u0433\u0430\u0020\u0432\u0020\u043D\u0430\u043A\u043B\u043E\u043D\u0435", "[\"back\"]", 2L)
        db.exerciseQueries.upsertExercise("pullup", "\u041F\u043E\u0434\u0442\u044F\u0433\u0438\u0432\u0430\u043D\u0438\u044F", "[\"back\"]", 2L)
        db.exerciseQueries.upsertExercise("lunge", "\u0412\u044B\u043F\u0430\u0434\u044B", "[\"legs\"]", 1L)
    }

    private fun buildOfflinePlan(weekIndex: Int): TrainingPlan {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val workouts = List(3) { day ->
            val id = "w_${weekIndex}_$day"
            val date = today.plus(DatePeriod(days = day))
            val sets = when (day) {
                0 -> listOf(
                    WorkoutSet("squat", 8, 40.0, 7.0),
                    WorkoutSet("bench", 10, 30.0, 7.0),
                    WorkoutSet("row", 10, 30.0, 7.0)
                )
                1 -> listOf(
                    WorkoutSet("deadlift", 5, 60.0, 7.5),
                    WorkoutSet("ohp", 8, 20.0, 7.0),
                    WorkoutSet("lunge", 10, 20.0, 7.0)
                )
                else -> listOf(
                    WorkoutSet("squat", 6, 45.0, 7.5),
                    WorkoutSet("bench", 8, 32.5, 7.0),
                    WorkoutSet("pullup", 6, null, 7.5)
                )
            }
            Workout(id = id, date = date, sets = sets)
        }
        return TrainingPlan(weekIndex, workouts)
    }
}

