package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.PatternSlot
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/** Orchestrates split selection and returns a labeled skeleton per training day. */
internal object TrainingSplitPlanner {

    fun build(
        trainingDays: List<String>,
        focusRaw: String,
        experienceLevel: Int,
        sessionDurationMins: Int,
        weekIndex: Int
    ): List<WorkoutSkeleton> {
        val focus = SplitParamsFactory.focusFromRaw(focusRaw)
        val params = SplitParamsFactory.create(focus, experienceLevel, sessionDurationMins, weekIndex)
        val dayCount = trainingDays.size.coerceIn(1, 6)
        val templates = when {
            dayCount <= 2 -> SplitTemplates.fullBodyAB(params)
            dayCount == 3 -> SplitTemplates.fullBodyABA(params)
            dayCount == 4 -> SplitTemplates.upperLower(params)
            else          -> SplitTemplates.ppl(params, dayCount)
        }
        return templates.mapIndexed { i, t ->
            val day = trainingDays.getOrNull(i) ?: "Day ${i + 1}"
            t.copy(label = "$day — ${t.label}")
        }
    }

    fun renderPromptBlock(skeletons: List<WorkoutSkeleton>): String = buildString {
        appendLine("MANDATORY WORKOUT SKELETON — follow exactly, no deviations.")
        appendLine("For each slot: pick one exerciseId token, assign a realistic weightKg.")
        appendLine()
        skeletons.forEachIndexed { i, s ->
            appendLine("Session ${i + 1} (${s.label}):")
            s.slots.forEachIndexed { j, slot ->
                appendLine("  Slot ${j + 1}: ${slot.describe()}")
            }
        }
        appendLine()
        appendLine("Sets/reps/rest above are FINAL — do not override.")
        appendLine("Order: compound patterns FIRST (Nunes 2021), isolations LAST.")
    }

    private fun PatternSlot.describe() =
        "${pattern.token} — ${sets}×${repMin}–${repMax} reps — RPE $rpeTarget — ${restSeconds}s rest"
}
