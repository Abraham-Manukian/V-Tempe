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

    private val KNEE_KEYWORDS     = setOf("knee", "колено", "колен", "knees", "kneecap", "patella", "mcl", "acl", "pcl", "meniscus", "patellofemoral")
    private val SHOULDER_KEYWORDS = setOf("shoulder", "плечо", "плеч", "rotator", "cuff", "labrum", "ac joint", "impingement", "тендинопатия", "tendinopathy")
    private val BACK_KEYWORDS     = setOf("back", "spine", "lumbar", "поясниц", "спин", "disc", "disk", "herniat", "грыж", "l4", "l5", "s1", "scoliosis", "сколиоз")
    private val ELBOW_KEYWORDS    = setOf("elbow", "локоть", "локт", "tennis elbow", "golfer", "epicondyl")
    private val WRIST_KEYWORDS    = setOf("wrist", "запястье", "запястн", "carpal")
    private val NECK_KEYWORDS     = setOf("neck", "шея", "шей", "cervical", "цервикальн")
    private val ANKLE_KEYWORDS    = setOf("ankle", "лодыжка", "лодыж", "голеностоп", "achilles", "ахилл")

    fun bannedPatterns(injuries: List<String>): Set<MovementPattern> {
        val banned = mutableSetOf<MovementPattern>()
        for (injury in injuries) {
            val lower = injury.lowercase()
            if (KNEE_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.KNEE_DOMINANT
                banned += MovementPattern.SINGLE_LEG
            }
            if (SHOULDER_KEYWORDS.any { lower.contains(it) }) {
                // Only overhead press is banned — horizontal press is safe at moderate ROM
                // and is first-line exercise therapy for most shoulder conditions.
                banned += MovementPattern.VERTICAL_PUSH
            }
            if (BACK_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.HINGE
            }
            if (ELBOW_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.ARM_FLEXION
                banned += MovementPattern.ARM_EXTENSION
            }
            if (WRIST_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.HORIZONTAL_PUSH
                banned += MovementPattern.ARM_FLEXION
                banned += MovementPattern.ARM_EXTENSION
            }
            if (NECK_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.VERTICAL_PUSH
            }
            if (ANKLE_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.SINGLE_LEG
            }
        }
        return banned
    }

    fun applyTo(
        skeletons: List<WorkoutSkeleton>,
        injuries: List<String>
    ): List<WorkoutSkeleton> {
        val banned = bannedPatterns(injuries)
        if (banned.isEmpty()) return skeletons
        return skeletons.map { s ->
            s.copy(slots = s.slots.filter { it.pattern !in banned })
        }
    }
}
