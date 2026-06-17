package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.PatternSlot
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/** Orchestrates split selection and returns a labeled skeleton per training day. */
internal object TrainingSplitPlanner {

    fun build(
        trainingDays: List<String>,
        focusRaw: String,
        goalRaw: String,
        experienceLevel: Int,
        sessionDurationMins: Int,
        weekIndex: Int
    ): List<WorkoutSkeleton> {
        val focus    = SplitParamsFactory.focusFromRaw(focusRaw)
        val goal     = SplitParamsFactory.goalFromRaw(goalRaw)
        val params   = SplitParamsFactory.create(goal, focus, experienceLevel, sessionDurationMins, weekIndex)
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
        appendLine("Loading tier legend:")
        appendLine("  [PRIMARY]   = main compound accent — heaviest, longest rest")
        appendLine("  [SECONDARY] = supporting compound — moderate load/rest")
        appendLine("  [ISOLATION] = accessory — light, short rest, pump-focus")
        appendLine()
        skeletons.forEachIndexed { i, s ->
            appendLine("Session ${i + 1} (${s.label}):")
            s.slots.forEachIndexed { j, slot ->
                appendLine("  Slot ${j + 1}: ${slot.describe()}")
            }
            appendLine()
        }
        appendLine("Sets/reps/rest/RPE above are FINAL — do not override.")
        appendLine("Order: PRIMARY and SECONDARY patterns FIRST (Nunes 2021), ISOLATION LAST.")
        appendLine("IMPORTANT: use the session label exactly as the 'label' field in your JSON.")
    }

    private fun PatternSlot.describe(): String {
        val tier = "[${slotType.name}]".padEnd(12)
        return "$tier ${pattern.token} — ${sets}×${repMin}–${repMax} reps — RPE $rpeTarget — ${restSeconds}s rest"
    }
}
