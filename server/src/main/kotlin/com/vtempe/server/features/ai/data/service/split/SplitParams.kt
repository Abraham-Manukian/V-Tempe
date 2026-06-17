package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.domain.model.SlotType

/**
 * Session-level training parameters — three loading tiers per session.
 *
 * PRIMARY   = main compound(s): heaviest load, longest rest (e.g. squat 4×6-8)
 * SECONDARY = supporting compounds: moderate load/rest  (e.g. row 3×8-12)
 * ISOLATION = accessories: light, short rest            (e.g. curl 2×10-15)
 */
internal data class SplitParams(
    // PRIMARY compound slots
    val primarySets: Int,
    val primaryRepMin: Int,
    val primaryRepMax: Int,
    val primaryRpe: Float,
    val primaryRestSeconds: Int,
    val primarySlotCount: Int,       // how many slots get PRIMARY tier

    // SECONDARY compound slots
    val secondarySets: Int,
    val secondaryRepMin: Int,
    val secondaryRepMax: Int,
    val secondaryRpe: Float,
    val secondaryRestSeconds: Int,

    // ISOLATION slots
    val isolationSets: Int,
    val isolationRepMin: Int,
    val isolationRepMax: Int,
    val isolationRpe: Float,
    val isolationRestSeconds: Int,

    val exercisesPerSession: Int
)
