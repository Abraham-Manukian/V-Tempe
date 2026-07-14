package com.vtempe.server

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Wave 2 task 2.1's prescribed first step (docs/FIX_PLAN_execution.md): an invariant test,
 * not a refactor. The exercise catalog exists in THREE independent, hand-maintained copies —
 * server's BuiltInExerciseCatalog, client's ExerciseLibrary (technique/detail screen), client's
 * ExerciseBrowseCatalog (browse/library screen) — with no shared source of truth
 * (`:server` has no Gradle dependency on `:shared`, and `:shared` only targets Android, so a
 * real cross-module compile-time check isn't available without a build-topology change that's
 * out of scope for "just add a test"). This test instead reads all three Kotlin source files as
 * text and diffs the declared exercise IDs.
 *
 * The server-vs-ExerciseBrowseCatalog check is now a hard ZERO-DRIFT check (item A1, backfilled
 * 2026-07-14 — both catalogs have exactly the same 188 IDs). The server-vs-ExerciseLibrary
 * check is still a RATCHET: ExerciseLibrary carries full technique/image content per exercise
 * (real content-authoring, deliberately out of scope for this pass — see
 * ARCHITECTURE_SECURITY_BACKLOG.md item A1), so the dangerous direction (server returning an ID
 * the client has no technique/image for — the user sees a bare label) is captured as
 * KNOWN_SERVER_ONLY_VS_LIBRARY_IDS below so CI stays green today, but any NEW id added to the
 * server catalog without also adding it to ExerciseLibrary will fail this test immediately,
 * instead of silently drifting further. Shrinking that set over time (removing entries as
 * they're backfilled with real technique content) is the remaining "unification" work.
 */
class ExerciseCatalogDriftTest {

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("Could not locate repo root (no settings.gradle.kts found above ${System.getProperty("user.dir")})")
        }
        return dir
    }

    private fun idsFrom(file: File, pattern: Regex): Set<String> {
        assertTrue(file.exists(), "Expected catalog file to exist: ${file.absolutePath}")
        return pattern.findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    private fun serverCatalogIds(): Set<String> = idsFrom(
        File(repoRoot(), "server/src/main/kotlin/com/vtempe/server/features/ai/data/catalog/BuiltInExerciseCatalog.kt"),
        Regex("""ExerciseCatalogItem\(\s*"([a-zA-Z0-9_]+)"""")
    )

    private fun exerciseLibraryIds(): Set<String> = idsFrom(
        File(repoRoot(), "shared/src/commonMain/kotlin/com/vtempe/shared/domain/exercise/ExerciseLibrary.kt"),
        Regex("""id\s*=\s*"([a-zA-Z0-9_]+)"""")
    )

    private fun browseCatalogIds(): Set<String> = idsFrom(
        File(repoRoot(), "shared/src/commonMain/kotlin/com/vtempe/shared/domain/exercise/ExerciseBrowseCatalog.kt"),
        Regex("""BrowseExercise\(\s*"([a-zA-Z0-9_]+)"""")
    )

    @Test
    fun `server catalog does not grow further ahead of ExerciseLibrary (technique detail screen)`() {
        val server = serverCatalogIds()
        val library = exerciseLibraryIds()
        assertTrue(server.size > 100, "Sanity check: expected the server catalog parse to find 100+ ids, found ${server.size} — regex likely broken")
        assertTrue(library.size > 50, "Sanity check: expected ExerciseLibrary parse to find 50+ ids, found ${library.size} — regex likely broken")

        val serverOnly = (server - library).sorted()
        val newDrift = serverOnly - KNOWN_SERVER_ONLY_VS_LIBRARY_IDS
        assertTrue(
            newDrift.isEmpty(),
            "Server catalog has NEW exercise IDs with no ExerciseLibrary entry (client shows a bare " +
                "label with no technique/image for these): $newDrift. Either add them to ExerciseLibrary.kt " +
                "or, if intentional, add them to KNOWN_SERVER_ONLY_VS_LIBRARY_IDS in this test with a reason."
        )
    }

    @Test
    fun `server catalog and ExerciseBrowseCatalog (browse screen) have exactly the same exercise IDs`() {
        // Backfilled 2026-07-14 (item A1) — the browse catalog is now a strict mirror of the
        // server's 188 IDs, so this is a hard zero-drift check, not a ratchet: any NEW id added
        // to either catalog without the other must fail immediately.
        val server = serverCatalogIds()
        val browse = browseCatalogIds()
        assertTrue(browse.size > 50, "Sanity check: expected ExerciseBrowseCatalog parse to find 50+ ids, found ${browse.size} — regex likely broken")

        val serverOnly = (server - browse).sorted()
        val browseOnly = (browse - server).sorted()
        assertTrue(
            serverOnly.isEmpty(),
            "Server catalog has exercise IDs missing from ExerciseBrowseCatalog: $serverOnly. Add them to ExerciseBrowseCatalog.kt."
        )
        assertTrue(
            browseOnly.isEmpty(),
            "ExerciseBrowseCatalog has exercise IDs that don't exist in the server catalog (typo?): $browseOnly."
        )
    }

    companion object {
        // Captured 2026-07-13 — the real drift at the time this test was added. Do not add to
        // these sets to silence a new failure without checking whether the client can actually
        // render that exercise; only ADD when you've confirmed the gap is deliberate, and
        // REMOVE entries as they get backfilled into the client catalogs (that's item 2.1's
        // unification work).
        private val KNOWN_SERVER_ONLY_VS_LIBRARY_IDS: Set<String> = setOf(
            "ab_wheel", "arnold_press", "assisted_pullup", "band_pulldown", "band_row",
            "barbell_hack_squat", "battle_rope", "bicycle_crunch", "box_jump", "box_squat",
            "bulgarian_split_squat", "burpee", "cable_crunch", "cable_curl", "cable_fly",
            "cat_cow", "chest_supported_row", "close_grip_bench", "concentration_curl", "crunch",
            "dead_bug", "decline_pushup", "diamond_pushup", "dumbbell_fly", "dumbbell_shoulder_press",
            "elliptical", "face_pull", "foam_rolling", "front_raise", "front_squat", "goblet_squat",
            "good_morning", "hack_squat", "hammer_curl", "handstand_pushup", "hanging_leg_raise",
            "high_knees", "hip_flexor_stretch", "hollow_body", "incline_bench", "incline_curl",
            "incline_pushup", "inverted_row", "jump_rope", "jump_squat", "jumping_jack",
            "kettlebell_swing", "l_sit", "lateral_lunge", "lateral_raise", "leg_curl", "leg_extension",
            "leg_raise", "mountain_climber", "muscle_up", "nordic_curl", "pike_pushup", "pistol_squat",
            "reverse_curl", "reverse_lunge", "romanian_deadlift", "rowing_machine", "russian_twist",
            "side_plank", "single_leg_deadlift", "skater_jump", "skater_lunge", "sprint",
            "stair_climb", "step_up", "stretching", "sumo_deadlift", "sumo_squat", "superman_row",
            "swim", "toes_to_bar", "tricep_kickback", "tricep_pushdown", "upright_row", "v_up",
            "wall_sit", "wide_pullup", "wide_pushup", "world_greatest_stretch"
        )
        // KNOWN_SERVER_ONLY_VS_BROWSE_IDS removed 2026-07-14: item A1 backfilled all 82 of
        // these into ExerciseBrowseCatalog.kt, so the browse-catalog test above is now a hard
        // zero-drift check instead of a ratchet.
    }
}
