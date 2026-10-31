package com.kidsshorts.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {
    @Test fun acceptsReasonablePins() {
        listOf("4821", "907315", "13579024").forEach { assertNull(it, PinPolicy.validate(it)) }
    }

    @Test fun rejectsBadLengthNonDigitsAndTrivialPins() {
        listOf("123", "123456789", "12a4", "", "    ", "0000", "7777777", "1234", "98765", "56789").forEach {
            assertNotNull("pin=$it", PinPolicy.validate(it))
        }
    }

    @Test fun videoIds_areStrictlyValidated() {
        assertTrue(VideoIds.isValid("dQw4w9WgXcQ"))
        assertTrue(VideoIds.isValid("abc_DEF-123"))
        listOf(null, "", "short", "dQw4w9WgXcQ1", "dQw4w9WgXc'", "dQw4w9WgXc\"", "dQw4w9Wg;);", "dQw4w9WgXc ").forEach {
            assertFalse("id=$it", VideoIds.isValid(it))
        }
    }
}
