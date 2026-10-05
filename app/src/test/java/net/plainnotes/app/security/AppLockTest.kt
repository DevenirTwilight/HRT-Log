package net.plainnotes.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    @Test fun pinRules() {
        assertTrue(AppLock.validPin("0000")); assertTrue(AppLock.validPin("123456789012"))
        listOf("", "123", "1234567890123", "12a4", "12 34", "١٢٣٤x").forEach { assertFalse(it, AppLock.validPin(it)) }
    }

    @Test fun hashIsSaltedAndStable() {
        val a = ByteArray(16) { 1 }; val b = ByteArray(16) { 2 }
        assertArrayEquals(AppLock.hash("2468", a), AppLock.hash("2468", a))
        assertFalse(AppLock.hash("2468", a).contentEquals(AppLock.hash("2468", b)))
        assertFalse(AppLock.hash("2468", a).contentEquals(AppLock.hash("2469", a)))
    }
}
