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
        forceDeload: Boolean = false,
        hasHistory: Boolean = true
    ): List<WorkoutSkeleton> {
        val focus     = SplitParamsFactory.focusFromRaw(focusRaw)
        val goal      = SplitParamsFactory.goalFromRaw(goalRaw)
        val sex       = SplitParamsFactory.sexFromRaw(sexRaw)
        val lifestyle = SplitParamsFactory.lifestyleFromRaw(lifestyleRaw)
        val params    = SplitParamsFactory.create(goal, focus, experienceLevel, age, sex, lifestyle, sessionDurationMins, weekIndex, forceDeload, hasHistory)
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
            .map(::dedupeIsolationAgainstCompounds)
        return templates.mapIndexed { i, t ->
            val day = trainingDays.getOrNull(i) ?: "Day ${i + 1}"
            t.copy(label = "$day — ${t.label}")
        }
    }

    /**
     * Prevents a lower-body ISOLATION slot from duplicating a compound leg pattern already
     * present in the same session (e.g. SINGLE_LEG isolation next to a KNEE_DOMINANT /
     * SINGLE_LEG compound — both resolve to lunges → two lunges in one workout). Such a
     * colliding isolation is remapped to CORE (or MOBILITY if CORE is taken). Multi-angle
     * compound repeats (e.g. HORIZONTAL_PUSH ×3 on a chest day) are intentional and untouched.
     */
    private fun dedupeIsolationAgainstCompounds(skeleton: WorkoutSkeleton): WorkoutSkeleton {
        val legPatterns = setOf(
            MovementPattern.KNEE_DOMINANT,
            MovementPattern.SINGLE_LEG,
            MovementPattern.HINGE
        )
        val compoundLegPatterns = skeleton.slots
            .filter { it.slotType != SlotType.ISOLATION && it.pattern in legPatterns }
            .map { it.pattern }
            .toSet()
        if (compoundLegPatterns.isEmpty()) return skeleton

        val present = skeleton.slots.map { it.pattern }.toMutableSet()
        val rebuilt = skeleton.slots.map { slot ->
            if (slot.slotType == SlotType.ISOLATION && slot.pattern in compoundLegPatterns) {
                val replacement = listOf(MovementPattern.CORE, MovementPattern.MOBILITY)
                    .firstOrNull { it !in present }
                if (replacement != null) {
                    present += replacement
                    slot.copy(pattern = replacement)
                } else slot
            } else slot
        }
        return skeleton.copy(slots = rebuilt)
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
        SplitPreference.BRO_SPLIT    -> SplitTemplates.broSplit(params, dayCount.coerceIn(3, 6))
        // AUTO: muscle-group splits from 3 days up; full body only for ≤2 days.
        // 3 days → bro split (Chest+Tri / Back+Bi / Legs+Shoulders) — classic & intuitive.
        // 4 days → upper/lower — best frequency-to-recovery ratio at 4 sessions.
        // 5–6 days → PPL — highest frequency, suits intermediate+.
        SplitPreference.AUTO         -> when {
            dayCount <= 2 -> SplitTemplates.fullBodyAB(params)
            dayCount == 3 -> SplitTemplates.broSplit(params, 3)
            dayCount == 4 -> SplitTemplates.upperLower(params)
            else          -> SplitTemplates.ppl(params, dayCount.coerceIn(5, 6))
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
