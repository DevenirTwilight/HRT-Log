package net.plainnotes.app.security

/** Authentication is independent of any database. PRIVATE never grants access to the main application. */
enum class UnlockTarget { PRIMARY, PRIVATE }

/** Process-local authorization. No token, target or picker exemption is restored from an Intent/Bundle/disk. */
object Session {
    @Volatile var target: UnlockTarget? = null
        private set
    @Volatile var generation = 0L
        private set
    @Volatile private var pickerOwner: Long? = null

    @Synchronized fun begin(target: UnlockTarget): Long {
        generation++; pickerOwner=null; this.target=target
        return generation
    }
    /** Enabling disguise from an already unlocked main screen keeps that activity's ownership. */
    @Synchronized fun protectCurrentPrimary() { target=UnlockTarget.PRIMARY; pickerOwner=null }
    fun allows(target: UnlockTarget, owner: Long = generation) = this.target==target && owner==generation
    fun requiresAppPin(disguised: Boolean, pinEnabled: Boolean) = pinEnabled && !(disguised && allows(UnlockTarget.PRIMARY))
    /** Stopping an old activity during task replacement must not revoke the new target. */
    @Synchronized fun lock(owner: Long) { if(owner==generation) reset() }
    @Synchronized fun reset() { target=null; pickerOwner=null }
    @Synchronized fun beginPicker() { if(target!=UnlockTarget.PRIVATE)pickerOwner=generation }
    fun pickerActive(owner: Long) = pickerOwner==owner && generation==owner
    @Synchronized fun consumePicker(owner: Long): Boolean {
        val active=pickerActive(owner)
        if(active)pickerOwner=null
        return active
    }
    @Synchronized fun cancelPicker() { pickerOwner=null }
}

/** Only the main application's system document picker can exempt lock-on-leave; failed launches revoke it. */
fun <I> androidx.activity.result.ActivityResultLauncher<I>.launchPicker(input: I) {
    Session.beginPicker()
    try { launch(input) } catch(e:Exception) { Session.cancelPicker(); throw e }
}
