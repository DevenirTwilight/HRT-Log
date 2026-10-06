package net.plainnotes.app.security

import org.junit.*
import org.junit.Assert.*

class SessionTest {
    @Before fun reset(){Session.reset()}
    @After fun clear(){Session.reset()}
    @Test fun initialAndResetStateNeverAuthorizeEitherTarget() {
        assertNull(Session.target)
        assertFalse(Session.allows(UnlockTarget.PRIMARY));assertFalse(Session.allows(UnlockTarget.PRIVATE))
        Session.begin(UnlockTarget.PRIMARY);Session.reset()
        assertFalse(Session.allows(UnlockTarget.PRIMARY));assertFalse(Session.allows(UnlockTarget.PRIVATE))
    }
    @Test fun targetsAreMutuallyExclusive() {
        Session.begin(UnlockTarget.PRIVATE)
        assertTrue(Session.allows(UnlockTarget.PRIVATE));assertFalse(Session.allows(UnlockTarget.PRIMARY))
        Session.begin(UnlockTarget.PRIMARY)
        assertTrue(Session.allows(UnlockTarget.PRIMARY));assertFalse(Session.allows(UnlockTarget.PRIVATE))
    }
    @Test fun oldTaskCannotLockOrConsumeANewAuthenticatedSession() {
        val old=Session.begin(UnlockTarget.PRIMARY)
        val current=Session.begin(UnlockTarget.PRIVATE)
        Session.lock(old)
        assertTrue(Session.allows(UnlockTarget.PRIVATE,current));assertFalse(Session.allows(UnlockTarget.PRIVATE,old))
        assertFalse(Session.consumePicker(old))
        Session.lock(current);assertNull(Session.target)
    }
    @Test fun ordinaryLockIsNeverBypassedByAnyDisguiseToken() {
        UnlockTarget.entries.forEach { Session.begin(it);assertTrue(Session.requiresAppPin(false,true)) }
        Session.reset();assertTrue(Session.requiresAppPin(false,true))
        assertFalse(Session.requiresAppPin(false,false))
    }
    @Test fun onlyPrimaryDisguiseAuthenticationSkipsTheSecondPin() {
        Session.begin(UnlockTarget.PRIVATE);assertTrue(Session.requiresAppPin(true,true))
        Session.begin(UnlockTarget.PRIMARY);assertFalse(Session.requiresAppPin(true,true))
        Session.lock(Session.generation);assertTrue(Session.requiresAppPin(true,true))
    }
    @Test fun filePickerExemptionBelongsOnlyToItsSessionAndIsConsumedOnce() {
        val old=Session.begin(UnlockTarget.PRIMARY);Session.beginPicker()
        assertTrue(Session.pickerActive(old));assertTrue(Session.consumePicker(old));assertFalse(Session.consumePicker(old))
        Session.beginPicker();Session.begin(UnlockTarget.PRIMARY);assertFalse(Session.pickerActive(old))
        Session.begin(UnlockTarget.PRIVATE);Session.beginPicker();assertFalse(Session.pickerActive(Session.generation))
    }
    @Test fun normalPickerAndFailedLaunchExemptionDoNotCreateAuthorization() {
        Session.beginPicker();assertTrue(Session.pickerActive(Session.generation));assertNull(Session.target)
        Session.cancelPicker();assertFalse(Session.pickerActive(Session.generation))
    }
    @Test fun enablingDisguiseInPlaceStillLocksWhenTheCurrentActivityLeaves() {
        val owner=Session.generation
        Session.protectCurrentPrimary();assertTrue(Session.allows(UnlockTarget.PRIMARY,owner))
        Session.lock(owner);assertNull(Session.target)
        Session.protectCurrentPrimary();Session.reset();Session.protectCurrentPrimary()
        Session.lock(owner);assertNull(Session.target)
    }
}
