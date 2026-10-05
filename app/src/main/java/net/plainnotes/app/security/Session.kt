package net.plainnotes.app.security

/** In-memory only: whether the disguise shell was passed in this process. A new process always starts closed. */
object Session {
    @Volatile var open = false
}
