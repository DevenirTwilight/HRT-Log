package net.plainnotes.app.security

/** In-memory only: whether the disguise shell was passed in this process. A new process always starts closed. */
object Session {
    @Volatile var open = false
    @Volatile var generation = 0L
        private set
    /** Older activities cannot lock a newer shell-authenticated session during task replacement. */
    fun begin() { generation++; externalPicker=false; open=true }
    fun lock(owner:Long) { if(owner==generation)open=false }
    /** Set while a system file picker (import / export) is in front, so disguise mode does not lock the app away mid-pick. */
    @Volatile var externalPicker = false
}

/** Launches a system file picker without triggering the disguise mode's lock-on-leave. */
fun <I> androidx.activity.result.ActivityResultLauncher<I>.launchPicker(input: I) { Session.externalPicker = true; launch(input) }
