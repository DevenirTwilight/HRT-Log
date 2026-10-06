package net.plainnotes.app.security

import org.junit.Assert.*
import org.junit.Test

class SessionTest {
    @Test fun oldTaskCannotLockANewAuthenticatedSession() {
        Session.begin();val old=Session.generation
        Session.begin();Session.lock(old)
        assertTrue(Session.open)
        Session.lock(Session.generation)
        assertFalse(Session.open)
    }
}
