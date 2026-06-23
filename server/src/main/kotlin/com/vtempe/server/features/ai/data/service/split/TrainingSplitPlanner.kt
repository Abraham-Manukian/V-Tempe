package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.PatternSlot
import com.vtempe.server.features.ai.domain.model.SlotType
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/** Orchestrates split selection and returns a labeled skeleton per training day. */
internal object TrainingSplitPlanner {

    fun build(
        trainingDays: List<String>,
        focusRaw: String,
        goalRaw: String,
        splitPreferenceRaw: String,
        experienceLevel: Int,
        age: Int,
        sexRaw: String,
        lifestyleRaw: String,
        injuries: List<String>,
        sessionDurationMins: Int,
        weekIndex: Int,
        forceDeload: Boolean = false
    ): List<WorkoutSkeleton> {
        val focus     = SplitParamsFactory.focusFromRaw(focusRaw)
        val goal      = SplitParamsFactory.goalFromRaw(goalRaw)
        val sex       = SplitParamsFactory.sexFromRaw(sexRaw)
        val lifestyle = SplitParamsFactory.lifestyleFromRaw(lifestyleRaw)
        val params    = SplitParamsFactory.create(goal, focus, experienceLevel, age, sex, lifestyle, sessionDurationMins, weekIndex, forceDeload)
        val dayCount  = trainingDays.size.coerceIn(1, 6)
        val pref      = runCatching { SplitPreference.valueOf(splitPreferenceRaw.uppercase()) }
                            .getOrDefault(SplitPreference.AUTO)
        val templates = InjuryFilter.applyTo(chooseTemplates(pref, dayCount, params), injuries)
            .map { skeleton ->
                // Guard: if all patterns were banned by InjuryFilter, fall back to a safe
                // core + mobility session rather than generating an empty workout.
                if (skeleton.slots.isNotEmpty()) skeleton
                else skeleton.copy(
                    slots = listOf(
                        PatternSlot(MovementPattern.CORE,     SlotType.ISOLATION, params.isolationSets, params.isolationRepMin, params.isolationRepMax, params.isolationRpe, params.isolationRestSeconds),
                        PatternSlot(MovementPattern.MOBILITY, SlotType.ISOLATION, params.isolationSets, params.isolationRepMin, params.isolationRepMax, params.isolationRpe, params.isolationRestSeconds)
                    )
                )
            }
        return templates.mapIndexed { i, t ->
            val day = trainingDays.getOrNull(i) ?: "Day ${i + 1}"
            t.copy(label = "$day — ${t.label}")
        }
    }

    fun renderPromptBlock(skeletons: List<WorkoutSkeleton>): String =
        renderPromptBlock(skeletons, emptyList())

    /**
     * Renders the skeleton with pre-resolved exercise IDs.
     * When [resolvedExercises] is populated the AI is told to use exactly those IDs
     * and never substitute — eliminating wrong exercise selection entirely.
     */
    fun renderPromptBlock(
        skeletons: List<WorkoutSkeleton>,
        resolvedExercises: List<List<String?>>
    ): String = buildString {
        val hasResolved = resolvedExercises.isNotEmpty()
        appendLine("MANDATORY WORKOUT SKELETON — follow exactly, no deviations.")
        if (hasResolved) {
            appendLine("exerciseId values are PRE-ASSIGNED below — copy them EXACTLY, do NOT substitute or change.")
        } else {
            appendLine("For each slot: pick one exerciseId token, assign a realistic weightKg.")
        }
        appendLine("IMPORTANT: Each slot = exactly ONE object in the sets array. '3 sets x 8 reps' means 3 sets to perform in the gym — NOT 3 JSON entries. 5 slots = 5 objects in sets[].")
        appendLine()
        appendLine("Loading tier legend:")
        appendLine("  [PRIMARY]   = main compound accent — heaviest, longest rest")
        appendLine("  [SECONDARY] = supporting compound — moderate load/rest")
        appendLine("  [ISOLATION] = accessory — light, short rest, pump-focus")
        appendLine()
        skeletons.forEachIndexed { i, s ->
            appendLine("Session ${i + 1} (${s.label}):")
            s.slots.forEachIndexed { j, slot ->
                val exerciseId = resolvedExercises.getOrNull(i)?.getOrNull(j)
                appendLine("  Slot ${j + 1}: ${if (exerciseId != null) slot.describeWithExercise(exerciseId) else slot.describe()}")
            }
            appendLine()
        }
        appendLine("Sets/reps/rest/RPE above are FINAL — do not override.")
        appendLine("Order: PRIMARY and SECONDARY patterns FIRST (Nunes 2021), ISOLATION LAST.")
        appendLine("IMPORTANT: use the session label exactly as the 'label' field in your JSON.")
    }

    private fun chooseTemplates(
        pref: SplitPreference,
        dayCount: Int,
        params: SplitParams
    ): List<WorkoutSkeleton> = when (pref) {
        SplitPreference.FULL_BODY    -> SplitTemplates.fullBodyABA(params).take(dayCount.coerceIn(1, 3))
        SplitPreference.UPPER_LOWER  -> SplitTemplates.upperLower(params).take(dayCount.coerceIn(1, 4))
        SplitPreference.PPL          -> SplitTemplates.ppl(params, dayCount.coerceIn(3, 6))
        // AUTO defaults to split-based programming — Full Body is only given when
        // the user explicitly selects it. Real-world gym athletes train splits 8/10.
        SplitPreference.AUTO         -> when {
            dayCount <= 2 -> SplitTemplates.fullBodyAB(params)
            dayCount <= 4 -> SplitTemplates.upperLower(params).take(dayCount)
            else          -> SplitTemplates.ppl(params, dayCount)
        }
    }

    private fun PatternSlot.describe(): String {
        val tier = "[${slotType.name}]".padEnd(12)
        return "$tier ${pattern.token} — ${sets}×${repMin}–${repMax} reps — RPE $rpeTarget — ${restSeconds}s rest"
    }

    private fun PatternSlot.describeWithExercise(exerciseId: String): String {
        val tier = "[${slotType.name}]".padEnd(12)
        return "$tier $exerciseId — ${sets}×${repMin}–${repMax} reps — RPE $rpeTarget — ${restSeconds}s rest"
    }
}
