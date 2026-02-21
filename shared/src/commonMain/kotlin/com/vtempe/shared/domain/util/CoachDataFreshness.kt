package com.vtempe.shared.domain.util

object CoachDataFreshness {
    const val SCHEMA_VERSION: Int = 5
    private const val STALE_AFTER_DAYS: Long = 7
    const val STALE_AFTER_MILLIS: Long = STALE_AFTER_DAYS * 24L * 60L * 60L * 1000L
}

