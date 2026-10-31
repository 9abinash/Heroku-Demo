package com.kidsshorts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kidsshorts.security.PinManager
import com.kidsshorts.security.PinManager.VerifyResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device/emulator: exercises the real Android Keystore. */
@RunWith(AndroidJUnit4::class)
class PinManagerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var pins: PinManager
    private var now = 1_000_000L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pins = PinManager(context) { now }
        pins.wipe()
    }

    @After fun tearDown() = pins.wipe()

    @Test fun createAndVerify_neverStoresPinOrRecoveryCodeInPlaintext() {
        assertFalse(pins.isPinSet())
        val recovery = pins.createPin("4821")
        assertTrue(pins.isPinSet())
        assertEquals(VerifyResult.Success, pins.verifyPin("4821"))
        assertTrue(pins.verifyPin("0000") is VerifyResult.Wrong)

        val stored = context.getSharedPreferences("parent_auth", Context.MODE_PRIVATE).all.values.joinToString { it.toString() }
        assertFalse(stored.contains("4821"))
        assertFalse(stored.contains(recovery))
        assertFalse(stored.contains(recovery.replace("-", "")))
    }

    @Test fun repeatedFailures_lockOut_andLockSurvivesCorrectPin_untilTimePasses() {
        pins.createPin("4821")
        repeat(4) { assertTrue(pins.verifyPin("1111") is VerifyResult.Wrong) }
        assertTrue(pins.verifyPin("1111") is VerifyResult.Locked)           // 5th failure locks
        assertTrue(pins.verifyPin("4821") is VerifyResult.Locked)           // even the right PIN is refused while locked
        now += 31_000
        assertEquals(VerifyResult.Success, pins.verifyPin("4821"))
    }

    @Test fun recoveryCode_resetsPin_andIsSingleUse() {
        val first = pins.createPin("4821")
        val second = pins.resetPinWithRecovery(first, "7391")
        assertNotNull(second)
        assertTrue(pins.verifyPin("4821") is VerifyResult.Wrong)
        assertEquals(VerifyResult.Success, pins.verifyPin("7391"))
        assertNull(pins.resetPinWithRecovery(first, "2468"))                // old code no longer works
        assertNotNull(pins.resetPinWithRecovery(second!!, "2468"))
    }

    @Test fun wrongRecoveryCode_countsTowardsTheSameLockout() {
        pins.createPin("4821")
        repeat(5) { pins.verifyRecoveryCode("AAAA-AAAA-AAAA-AAAA") }
        assertTrue(pins.lockoutRemainingMs() > 0)
        assertTrue(pins.verifyPin("4821") is VerifyResult.Locked)
    }

    @Test fun wipe_removesCredential() {
        pins.createPin("4821")
        pins.wipe()
        assertFalse(pins.isPinSet())
        assertEquals(VerifyResult.NotSet, pins.verifyPin("4821"))
    }
}
