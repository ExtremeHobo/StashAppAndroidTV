package com.github.damontecres.stashapp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresencePinUnlockTest {
    @Test
    fun skipsWhenSamHomeMeghanAwayGuestOff() {
        assertTrue(
            PresencePinUnlock.shouldSkipPin(
                samState = "home",
                meghanState = "Meghan Work",
                guestState = "off",
            ),
        )
        assertTrue(
            PresencePinUnlock.shouldSkipPin(
                samState = "home",
                meghanState = "not_home",
                guestState = "off",
            ),
        )
    }

    @Test
    fun requiresPinWhenMeghanHome() {
        assertFalse(
            PresencePinUnlock.shouldSkipPin(
                samState = "home",
                meghanState = "home",
                guestState = "off",
            ),
        )
    }

    @Test
    fun requiresPinWhenGuestModeOn() {
        assertFalse(
            PresencePinUnlock.shouldSkipPin(
                samState = "home",
                meghanState = "not_home",
                guestState = "on",
            ),
        )
    }

    @Test
    fun requiresPinWhenSamAway() {
        assertFalse(
            PresencePinUnlock.shouldSkipPin(
                samState = "Sam Work",
                meghanState = "not_home",
                guestState = "off",
            ),
        )
    }

    @Test
    fun requiresPinOnNullStates() {
        assertFalse(PresencePinUnlock.shouldSkipPin(null, null, null))
        assertFalse(PresencePinUnlock.shouldSkipPin("home", null, "off"))
    }
}
