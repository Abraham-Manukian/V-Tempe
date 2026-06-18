package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.WorkoutSkeleton

/**
 * Maps free-text injury notes to banned MovementPatterns and filters them out of
 * the skeleton. Better than relying on the AI to honour injury text in a prompt —
 * the AI can hallucinate safe exercises for injured joints.
 */
internal object InjuryFilter {

    private val KNEE_KEYWORDS     = setOf("knee", "колено", "колен", "knees", "kneecap", "patella", "mcl", "acl", "pcl", "meniscus")
    private val SHOULDER_KEYWORDS = setOf("shoulder", "плечо", "плеч", "rotator", "cuff", "labrum", "ac joint")
    private val BACK_KEYWORDS     = setOf("back", "spine", "lumbar", "поясниц", "спин", "disc", "disk", "herniat", "грыж", "l4", "l5", "s1")
    private val ELBOW_KEYWORDS    = setOf("elbow", "локоть", "локт", "tennis elbow", "golfer", "epicondyl")
    private val WRIST_KEYWORDS    = setOf("wrist", "запястье", "запястн", "carpal")

    fun bannedPatterns(injuries: List<String>): Set<MovementPattern> {
        val banned = mutableSetOf<MovementPattern>()
        for (injury in injuries) {
            val lower = injury.lowercase()
            if (KNEE_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.KNEE_DOMINANT
                banned += MovementPattern.SINGLE_LEG
            }
            if (SHOULDER_KEYWORDS.any { lower.contains(it) }) {
                banned += MovementPattern.VERTICAL_PUSH
                banned += MovementPattern.HORIZONTAL_PUSH
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
