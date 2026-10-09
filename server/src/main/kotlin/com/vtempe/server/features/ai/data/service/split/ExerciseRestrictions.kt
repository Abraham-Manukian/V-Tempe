package com.vtempe.server.features.ai.data.service.split

import com.vtempe.server.features.ai.data.service.InjuryRestrictions
import com.vtempe.server.features.ai.data.service.normalizeExerciseToken
import com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog

/**
 * The single answer to "may this user be given this exercise?" (VT-AUD-014).
 *
 * Before, the skeleton banned movement PATTERNS ([InjuryFilter]) while the validator banned
 * exercise IDS ([InjuryRestrictions]) — so an exercise of a banned pattern that was not on the
 * ID list (bodyweight_squat for a knee injury) slipped through every check, and fallback paths
 * used neither. Every selection, edit and validation path now asks this one object.
 */
internal class ExerciseRestrictions(
    val bannedPatterns: Set<MovementPattern>,
    val bannedIds: Set<String>,
) {
    val isEmpty: Boolean get() = bannedPatterns.isEmpty() && bannedIds.isEmpty()

    /** Banned when its id/alias is banned or ANY of its movement patterns is. */
    fun allows(item: ExerciseCatalogItem): Boolean =
        item.id !in bannedIds &&
            item.aliases.none { normalizeExerciseToken(it) in bannedIds } &&
            item.primaryPattern !in bannedPatterns &&
            item.secondaryPatterns.none { it in bannedPatterns }

    /** Unknown ids are judged by the id ban list only — they never pass the catalog check anyway. */
    fun allows(rawExerciseId: String, catalog: ExerciseCatalog): Boolean {
        val item = catalog.findByIdOrAlias(rawExerciseId) ?: return normalizeExerciseToken(rawExerciseId) !in bannedIds
        return allows(item)
    }

    /** Every catalog id this user must not get — the resolver's hard exclusion seed. */
    fun disallowedIds(catalog: ExerciseCatalog): Set<String> =
        bannedIds + catalog.all().filterNot(::allows).map { it.id }

    companion object {
        val None = ExerciseRestrictions(emptySet(), emptySet())

        fun forInjuries(injuries: List<String>): ExerciseRestrictions {
            if (injuries.isEmpty()) return None
            return ExerciseRestrictions(
                bannedPatterns = InjuryFilter.bannedPatterns(injuries),
                bannedIds = InjuryFilter.bannedExerciseIds(injuries) + InjuryRestrictions.allForbiddenFor(injuries),
            )
        }
    }
}
