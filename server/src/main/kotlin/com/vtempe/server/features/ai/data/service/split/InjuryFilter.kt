package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/**
 * Maps free-text injury notes to banned MovementPatterns and filters them out of
 * the skeleton. Better than relying on the AI to honour injury text in a prompt —
 * the AI can hallucinate safe exercises for injured joints.
 *
 * Clinical basis (PMC11061926, PMC3635671, PMID40165544):
 * - Shoulder: overhead press is high-risk; horizontal press at moderate ROM is generally
 *   recommended as part of exercise therapy — VERTICAL_PUSH banned, HORIZONTAL_PUSH kept.
 * - Knee: squat/lunge loading is broadly supported for knee OA and patellofemoral pain,
 *   but we keep the ban as a conservative default for acute/post-op cases.
 * - Back: hip hinge under load is the primary concern; rowing is generally safe.
 * - Neck: overhead press loads the cervical spine — ban VERTICAL_PUSH.
 * - Ankle: single-leg stance requires ankle stability — ban SINGLE_LEG.
 */
internal object InjuryFilter {

    /**
     * Body-part keywords -> banned MovementPatterns, one list so [bannedPatterns] and [keywords]
     * can never disagree. Shoulder bans only overhead press: horizontal press is safe at moderate
     * ROM and is first-line exercise therapy for most shoulder conditions.
     */
    private val PATTERN_RULES: List<Pair<Set<String>, Set<MovementPattern>>> = listOf(
        setOf("knee", "колено", "колен", "knees", "kneecap", "patella", "mcl", "acl", "pcl", "meniscus", "patellofemoral") to
            setOf(MovementPattern.KNEE_DOMINANT, MovementPattern.SINGLE_LEG),
        setOf("shoulder", "плечо", "плеч", "rotator", "cuff", "labrum", "ac joint", "impingement", "тендинопатия", "tendinopathy") to
            setOf(MovementPattern.VERTICAL_PUSH),
        setOf("back", "spine", "lumbar", "поясниц", "спин", "disc", "disk", "herniat", "грыж", "l4", "l5", "s1", "scoliosis", "сколиоз") to
            setOf(MovementPattern.HINGE),
        setOf("elbow", "локоть", "локт", "tennis elbow", "golfer", "epicondyl") to
            setOf(MovementPattern.ARM_FLEXION, MovementPattern.ARM_EXTENSION),
        setOf("wrist", "запястье", "запястн", "carpal") to
            setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.ARM_FLEXION, MovementPattern.ARM_EXTENSION),
        setOf("neck", "шея", "шей", "cervical", "цервикальн") to setOf(MovementPattern.VERTICAL_PUSH),
        setOf("ankle", "лодыжка", "лодыж", "голеностоп", "achilles", "ахилл") to setOf(MovementPattern.SINGLE_LEG),
    )

    /**
     * Body-part keywords above ban whole MovementPatterns. Some injury notes instead name a
     * specific exercise/equipment the user can't use (e.g. "нельзя брусья") — those don't map
     * to a MovementPattern ban (dip is HORIZONTAL_PUSH/ARM_EXTENSION, patterns that are safe in
     * general) so they need their own explicit exerciseId exclusion list.
     */
    private val EXERCISE_ID_BAN_KEYWORDS: List<Pair<Set<String>, Set<String>>> = listOf(
        setOf("брусья", "брусьях", "dip bar", "dip bars", "parallel bar", "parallel bars") to
            setOf("dip", "bench_dip"),
        setOf("широким хватом", "wide grip", "wide-grip", "wide pullup", "wide pull up", "wide pull-up") to
            setOf("wide_pullup"),
    )

    /** Every keyword these rules react to — the contract test checks each one against
     *  testdata/injury-keyword-rules.tsv, so a keyword added here alone fails the build. */
    val keywords: Set<String>
        get() = (PATTERN_RULES.flatMap { it.first } + EXERCISE_ID_BAN_KEYWORDS.flatMap { it.first }).toSet()

    /** ExerciseIds the user explicitly can't do, parsed from free-text injury notes. */
    fun bannedExerciseIds(injuries: List<String>): Set<String> {
        val banned = mutableSetOf<String>()
        for (injury in injuries) {
            val lower = injury.lowercase()
            for ((keywords, ids) in EXERCISE_ID_BAN_KEYWORDS) {
                if (keywords.any { lower.contains(it) }) banned += ids
            }
        }
        return banned
    }

    fun bannedPatterns(injuries: List<String>): Set<MovementPattern> {
        val banned = mutableSetOf<MovementPattern>()
        for (injury in injuries) {
            val lower = injury.lowercase()
            for ((keywords, patterns) in PATTERN_RULES) {
                if (keywords.any { lower.contains(it) }) banned += patterns
            }
        }
        return banned
    }

    /**
     * Returns a SAFE alternative movement pattern for a banned [pattern] given the set of
     * [banned] patterns for this user, or null if no safe substitute exists (slot is dropped).
     *
     * Rationale (keeps training volume instead of leaving empty slots):
     * - KNEE_DOMINANT / SINGLE_LEG (knee/ankle) → HINGE if hinge is safe (posterior chain,
     *   low knee shear), else HORIZONTAL_PULL, else CORE.
     * - HINGE (lower-back/hernia) → HORIZONTAL_PULL (supported row) then CORE — never another
     *   spinal-loading hinge.
     * - VERTICAL_PUSH (shoulder/neck) → HORIZONTAL_PUSH (first-line shoulder therapy) then CORE.
     * - ARM_FLEXION / ARM_EXTENSION (elbow/wrist) → CORE.
     */
    fun safeAlternativeFor(pattern: MovementPattern, banned: Set<MovementPattern>): MovementPattern? {
        val candidates: List<MovementPattern> = when (pattern) {
            MovementPattern.KNEE_DOMINANT,
            MovementPattern.SINGLE_LEG ->
                listOf(MovementPattern.HINGE, MovementPattern.HORIZONTAL_PULL, MovementPattern.CORE)
            MovementPattern.HINGE ->
                listOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.CORE)
            MovementPattern.VERTICAL_PUSH ->
                listOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.CORE)
            MovementPattern.ARM_FLEXION,
            MovementPattern.ARM_EXTENSION ->
                listOf(MovementPattern.CORE, MovementPattern.MOBILITY)
            MovementPattern.HORIZONTAL_PUSH ->
                listOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.CORE)
            else ->
                listOf(MovementPattern.CORE, MovementPattern.MOBILITY)
        }
        return candidates.firstOrNull { it !in banned }
    }

    fun applyTo(
        skeletons: List<WorkoutSkeleton>,
        injuries: List<String>
    ): List<WorkoutSkeleton> {
        val banned = bannedPatterns(injuries)
        if (banned.isEmpty()) return skeletons
        return skeletons.map { s ->
            // Replace banned patterns with a safe alternative (preserving the slot's loading tier
            // and set/rep scheme) rather than deleting the slot and shrinking the workout.
            // CORE and MOBILITY are never duplicated within a session — if a replacement would
            // collide with an existing pattern in the same day, drop the slot instead.
            val usedPatterns = s.slots.map { it.pattern }.filter { it !in banned }.toMutableSet()
            val rebuilt = s.slots.mapNotNull { slot ->
                if (slot.pattern !in banned) {
                    slot
                } else {
                    val alt = safeAlternativeFor(slot.pattern, banned)
                    when {
                        alt == null -> null
                        alt in usedPatterns && (alt == MovementPattern.CORE || alt == MovementPattern.MOBILITY) -> null
                        else -> {
                            usedPatterns += alt
                            slot.copy(pattern = alt)
                        }
                    }
                }
            }
            s.copy(slots = rebuilt)
        }
    }
}
