package com.vtempe.ui.screens.profile

import androidx.compose.runtime.saveable.SaverScope
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.SplitPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** VT-AUD-065: an unsaved profile draft survives Activity recreation. */
class EditProfileDraftSaverTest {
    private val scope = SaverScope { true }

    @Test
    fun aDraftRoundTripsUnchanged() {
        val draft = EditProfileState(
            age = "31", heightCm = "182", weightKg = "81,5", goal = Goal.GAIN_MUSCLE,
            splitPreference = SplitPreference.PPL, allergies = "peanut, milk", injuries = "knee",
        )
        val saved = with(EditProfileDraftSaver) { scope.save(draft) }!!
        assertEquals(draft, EditProfileDraftSaver.restore(saved))
    }

    @Test
    fun anUnreadableSavedDraftIsDropped() {
        assertNull(EditProfileDraftSaver.restore("not json"))
    }
}
