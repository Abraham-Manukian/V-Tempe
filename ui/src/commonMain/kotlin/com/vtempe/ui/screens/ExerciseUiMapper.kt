package com.vtempe.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.intl.Locale
import com.vtempe.shared.domain.exercise.ExerciseCalibrationKind
import com.vtempe.shared.domain.exercise.ExerciseDefinition
import com.vtempe.shared.domain.exercise.ExerciseLibrary
import com.vtempe.shared.domain.exercise.ExerciseTechnique
import com.vtempe.shared.domain.exercise.ExerciseVisualFamily
import com.vtempe.shared.domain.exercise.LocalizedText
import com.vtempe.shared.domain.model.CoachTrainerIds
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal data class ExerciseGuideData(
    val illustration: DrawableResource,
    val icon: ImageVector,
    val description: String,
    val focus: List<String>,
    val cue: String,
    val steps: List<String>,
    val defaultRestSeconds: Int
)

@Composable
internal fun exerciseLabel(exerciseId: String): String =
    ExerciseLibrary.findByIdOrAlias(exerciseId)
        ?.name
        ?.resolve(Locale.current.language)
        ?: exerciseId.replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

@Composable
internal fun exerciseGuide(
    exerciseId: String,
    coachTrainerId: String = CoachTrainerIds.DEFAULT
): ExerciseGuideData {
    val localeTag = Locale.current.language
    val definition = ExerciseLibrary.findByIdOrAlias(exerciseId) ?: genericExerciseDefinition()
    val technique = definition.technique
    val fallbackIllustration = illustrationFor(definition.visualFamily)
    return ExerciseGuideData(
        illustration = coachExerciseIllustration(coachTrainerId, definition.id, fallbackIllustration),
        icon = iconFor(definition.visualFamily, definition.id),
        description = technique.summary.resolve(localeTag),
        focus = technique.focus(localeTag),
        cue = technique.keyCue.resolve(localeTag),
        steps = technique.steps(localeTag),
        defaultRestSeconds = technique.defaultRestSeconds
    )
}

internal fun illustrationFor(family: ExerciseVisualFamily): DrawableResource =
    when (family) {
        ExerciseVisualFamily.LOWER_BODY -> Res.drawable.workout_illustration_lower_body
        ExerciseVisualFamily.PUSH -> Res.drawable.workout_illustration_push
        ExerciseVisualFamily.PULL -> Res.drawable.workout_illustration_pull
        ExerciseVisualFamily.OVERHEAD -> Res.drawable.workout_illustration_overhead
        ExerciseVisualFamily.ARMS -> Res.drawable.workout_illustration_arms
        ExerciseVisualFamily.CORE -> Res.drawable.workout_illustration_core
        ExerciseVisualFamily.CARDIO -> Res.drawable.workout_illustration_cardio
        ExerciseVisualFamily.GENERIC -> Res.drawable.workout_illustration_generic
    }

internal fun iconFor(family: ExerciseVisualFamily, exerciseId: String): ImageVector =
    when (family) {
        ExerciseVisualFamily.LOWER_BODY -> Icons.Filled.DirectionsRun
        ExerciseVisualFamily.CORE -> Icons.Filled.SelfImprovement
        ExerciseVisualFamily.CARDIO ->
            if (exerciseId.trim().lowercase() == "bike") Icons.Filled.DirectionsBike
            else Icons.Filled.DirectionsRun
        else -> Icons.Filled.FitnessCenter
    }

internal fun genericExerciseDefinition(): ExerciseDefinition = ExerciseDefinition(
    id = "generic",
    name = LocalizedText("Exercise", "Упражнение"),
    muscleGroups = emptyList(),
    difficulty = 1,
    visualFamily = ExerciseVisualFamily.GENERIC,
    calibrationKind = ExerciseCalibrationKind.WEIGHT_AND_REPS,
    calibrationHint = LocalizedText(
        "Pick a load you can control with clean technique.",
        "Подбери нагрузку, которую можешь контролировать с чистой техникой."
    ),
    imagePrompt = "",
    technique = ExerciseTechnique(
        summary = LocalizedText(
            "Controlled strength work with focus on repeat quality and technique stability.",
            "Контролируемая силовая работа с акцентом на качество повторений и устойчивую технику."
        ),
        focusEn = listOf("Strength"),
        focusRu = listOf("Сила"),
        keyCue = LocalizedText(
            "Move smoothly and stop the set before technique breaks down.",
            "Двигайся плавно и заканчивай подход до того, как техника начнет ломаться."
        ),
        stepsEn = listOf(
            "Set a stable start position.",
            "Control the main phase of every rep.",
            "Finish balanced and ready for the next repetition."
        ),
        stepsRu = listOf(
            "Займи устойчивый старт.",
            "Контролируй основную фазу каждого повтора.",
            "Заканчивай в балансе и готовности к следующему повтору."
        ),
        defaultRestSeconds = 75
    )
)

@Composable
internal fun plannedSetSummary(set: WorkoutSet): String {
    val weightStr = set.weightKg?.let {
        stringResource(Res.string.workout_weight_display).kmpFormat(it)
    } ?: stringResource(Res.string.workout_weight_bodyweight)
    val rpeStr = set.rpe?.let {
        stringResource(Res.string.workout_rpe_suffix).kmpFormat(it.toEditableDecimal())
    }.orEmpty()
    return stringResource(Res.string.workout_set_summary).kmpFormat(set.reps, weightStr) + rpeStr
}

@Composable
internal fun buildPerformedSummary(performed: PerformedSet): String {
    val repsLabel = stringResource(Res.string.workout_reps_unit)
    return buildList {
        performed.actualReps?.let { add("$it $repsLabel") }
        performed.actualWeightKg?.let {
            add(stringResource(Res.string.workout_weight_display).kmpFormat(it))
        }
        performed.actualRpe?.let { add("RPE ${it.toEditableDecimal()}") }
    }.joinToString(" · ").ifEmpty { "—" }
}

internal fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}

internal fun Double.toEditableDecimal(): String =
    if (this % 1.0 == 0.0) roundToInt().toString() else toString()
