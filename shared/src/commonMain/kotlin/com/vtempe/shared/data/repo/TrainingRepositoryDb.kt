package com.vtempe.shared.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.vtempe.shared.data.network.dto.TrainingPlanDto
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.exercise.ExerciseLibrary
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.model.WorkoutSummary
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime


class TrainingRepositoryDb(
    private val db: AppDatabase,
    private val ai: com.vtempe.shared.domain.repository.AiTrainerRepository,
    private val cache: AiResponseCache,
    private val progressStore: WorkoutProgressStore
) : TrainingRepository {

    override suspend fun generatePlan(profile: Profile, weekIndex: Int): TrainingPlan {
        // NetworkAiTrainerRepository already handles cache fallback internally.
        // We only need to persist on success or fall through to the offline plan.
        when (val aiPlanResult = ai.generateTrainingPlan(profile, weekIndex)) {
            is DataResult.Success -> {
                persistPlan(aiPlanResult.data)
                return aiPlanResult.data
            }
            is DataResult.Failure -> {
                Napier.w(
                    message = "AI training plan unavailable (${aiPlanResult.reason}), using offline plan",
                    throwable = aiPlanResult.throwable
                )
            }
        }

        seedFallbackExercises()
        val fallbackPlan = buildOfflinePlan(weekIndex)
        persistPlan(fallbackPlan)
        return fallbackPlan
    }

    override suspend fun logSet(workoutId: String, set: WorkoutSet) {
        progressStore.appendExtraSet(workoutId, set)
    }

    override suspend fun savePlan(plan: TrainingPlan) {
        persistPlan(plan)
        cache.storeTraining(TrainingPlanDto.fromDomain(plan))
    }

    override suspend fun hasPlan(weekIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            db.workoutQueries.selectWorkoutsWithSetsByWeek(weekIndex.toLong()).executeAsList().isNotEmpty()
        }

    /** All workouts across all weeks — for history / progress screens. */
    override fun observeWorkouts(): Flow<List<Workout>> =
        db.workoutQueries.selectWorkoutsWithSets()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.toWorkoutDomainList() }

    /** Only the current week's plan — for the active workout screen. */
    override fun observeWorkoutsByWeek(weekIndex: Int): Flow<List<Workout>> =
        db.workoutQueries.selectWorkoutsWithSetsByWeek(weekIndex.toLong())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.toWorkoutDomainList() }

    override suspend fun deleteWeeksFrom(weekIndex: Int) {
        withContext(Dispatchers.IO) {
            // WorkoutSet rows cascade automatically via ON DELETE CASCADE
            db.workoutQueries.deleteWorkoutsFromWeek(weekIndex.toLong())
        }
    }

    override fun observeWorkoutProgress(): Flow<Map<String, WorkoutProgress>> = progressStore.observe()

    override suspend fun saveWorkoutProgress(progress: WorkoutProgress) {
        progressStore.save(progress)
    }

    override suspend fun recentWorkoutSummaries(limit: Int): List<WorkoutSummary> =
        progressStore.recentSummaries(limit)

    private suspend fun persistPlan(plan: TrainingPlan) {
        withContext(Dispatchers.IO) {
            db.workoutQueries.transaction {
                db.workoutQueries.deleteSetsByWeek(plan.weekIndex.toLong())
                db.workoutQueries.deleteWorkoutsByWeek(plan.weekIndex.toLong())
                plan.workouts.forEach { workout ->
                    db.workoutQueries.insertWorkout(workout.id, plan.weekIndex.toLong(), workout.label, workout.date.toString())
                    db.workoutQueries.deleteSetsForWorkout(workout.id)
                    workout.sets.forEach { set ->
                        db.workoutQueries.insertSet(workout.id, set.exerciseId, set.reps.toLong(), set.weightKg, set.rpe)
                    }
                }
            }
        }
    }

    private fun seedFallbackExercises() {
        ExerciseLibrary.all().forEach { exercise ->
            val muscleGroupsJson = exercise.muscleGroups.joinToString(
                prefix = "[\"",
                separator = "\",\"",
                postfix = "\"]"
            )
            db.exerciseQueries.upsertExercise(
                exercise.id,
                exercise.name.en,
                muscleGroupsJson,
                exercise.difficulty.toLong()
            )
        }
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

