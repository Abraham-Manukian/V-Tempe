package com.vtempe.shared.data.training

import com.vtempe.shared.domain.training.MovementPattern
import com.vtempe.shared.domain.training.MovementPattern.CORE
import com.vtempe.shared.domain.training.MovementPattern.HINGE
import com.vtempe.shared.domain.training.MovementPattern.HORIZONTAL_PULL
import com.vtempe.shared.domain.training.MovementPattern.HORIZONTAL_PUSH
import com.vtempe.shared.domain.training.MovementPattern.KNEE_DOMINANT
import com.vtempe.shared.domain.training.MovementPattern.MOBILITY
import com.vtempe.shared.domain.training.MovementPattern.SINGLE_LEG
import com.vtempe.shared.domain.training.MovementPattern.VERTICAL_PULL
import com.vtempe.shared.domain.training.MovementPattern.VERTICAL_PUSH

/** One exercise the offline planner may use, described exactly as in the server catalog. */
data class PoolExercise(
    val id: String,
    val primary: MovementPattern,
    val secondary: Set<MovementPattern> = emptySet(),
    /** Any one of these is enough; empty = no equipment needed. */
    val equipmentAnyOf: Set<String> = emptySet(),
    val difficulty: Int,
    /** Lower is preferred, as in the server resolver. */
    val priority: Int,
)

/** The offline planner's exercises — a subset of the server catalog that the client library also
 *  knows. `testdata/offline-training-pool.tsv` pins this list to the catalog on both sides. */
internal val OfflineExercisePool: List<PoolExercise> = listOf(
    PoolExercise("bodyweight_squat", KNEE_DOMINANT, difficulty = 1, priority = 12),
    PoolExercise("kettlebell_goblet_squat", KNEE_DOMINANT, equipmentAnyOf = setOf("kettlebell"), difficulty = 2, priority = 20),
    PoolExercise("squat", KNEE_DOMINANT, equipmentAnyOf = setOf("barbell", "rack"), difficulty = 3, priority = 10),
    PoolExercise("split_squat", SINGLE_LEG, setOf(KNEE_DOMINANT), difficulty = 2, priority = 18),
    PoolExercise("lunge", SINGLE_LEG, setOf(KNEE_DOMINANT), difficulty = 2, priority = 10),
    PoolExercise("glute_bridge", HINGE, difficulty = 1, priority = 25),
    PoolExercise("bodyweight_good_morning", HINGE, difficulty = 1, priority = 26),
    PoolExercise("hip_thrust", HINGE, difficulty = 2, priority = 20),
    PoolExercise("deadlift", HINGE, equipmentAnyOf = setOf("barbell"), difficulty = 3, priority = 10),
    PoolExercise("knee_pushup", HORIZONTAL_PUSH, difficulty = 1, priority = 25),
    PoolExercise("pushup", HORIZONTAL_PUSH, difficulty = 1, priority = 20),
    PoolExercise("db_bench_press", HORIZONTAL_PUSH, equipmentAnyOf = setOf("dumbbells", "bench"), difficulty = 2, priority = 15),
    PoolExercise("bench", HORIZONTAL_PUSH, equipmentAnyOf = setOf("bench", "barbell", "dumbbells"), difficulty = 2, priority = 10),
    PoolExercise("superman_row", HORIZONTAL_PULL, difficulty = 1, priority = 45),
    PoolExercise("doorway_row", HORIZONTAL_PULL, difficulty = 1, priority = 30),
    PoolExercise("table_row", HORIZONTAL_PULL, difficulty = 2, priority = 28),
    PoolExercise("dumbbell_row", HORIZONTAL_PULL, equipmentAnyOf = setOf("dumbbells"), difficulty = 2, priority = 15),
    PoolExercise("row", HORIZONTAL_PULL, equipmentAnyOf = setOf("barbell", "dumbbells", "bands", "trx"), difficulty = 3, priority = 10),
    PoolExercise("negative_pullup", VERTICAL_PULL, equipmentAnyOf = setOf("pullup_bar"), difficulty = 2, priority = 25),
    PoolExercise("pullup", VERTICAL_PULL, equipmentAnyOf = setOf("pullup_bar", "trx"), difficulty = 3, priority = 10),
    PoolExercise("pike_pushup_elevated", VERTICAL_PUSH, setOf(HORIZONTAL_PUSH), difficulty = 3, priority = 25),
    PoolExercise("ohp", VERTICAL_PUSH, equipmentAnyOf = setOf("barbell", "dumbbells", "bands", "kettlebell"), difficulty = 3, priority = 10),
    PoolExercise("plank", CORE, difficulty = 1, priority = 10),
    PoolExercise("bird_dog", CORE, difficulty = 1, priority = 26),
    PoolExercise("heel_touch", CORE, difficulty = 1, priority = 36),
    PoolExercise("reverse_crunch", CORE, difficulty = 2, priority = 30),
    PoolExercise("cobra_stretch", MOBILITY, difficulty = 1, priority = 32),
    PoolExercise("childs_pose", MOBILITY, difficulty = 1, priority = 34),
    PoolExercise("hip_circle", MOBILITY, difficulty = 1, priority = 42),
    PoolExercise("thoracic_rotation", MOBILITY, setOf(CORE), difficulty = 1, priority = 36),
)
