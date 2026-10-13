package com.vtempe.ui.screens.auth

import com.vtempe.shared.domain.account.AccountDataFailure
import kotlin.test.Test
import kotlin.test.assertEquals

/** VT-AUD-076: every failure reason tells the user something different. */
class AccountDataFailureMessageTest {
    @Test
    fun everyReasonHasItsOwnMessage() {
        val messages = AccountDataFailure.entries.map { it.message() }
        assertEquals(messages.size, messages.toSet().size, "two reasons share a message: $messages")
    }
}
