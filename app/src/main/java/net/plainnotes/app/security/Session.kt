package net.plainnotes.app.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Authentication is independent of any database. PRIVATE never grants access to the main application. */
enum class UnlockTarget { PRIMARY, PRIVATE }

/** Process-local authorization. No token, target or picker exemption is restored from an Intent/Bundle/disk. */
object Session {
    data class Authorization(val target:UnlockTarget?=null,val generation:Long=0L)
    private val mutable=MutableStateFlow(Authorization())
    val authorization=mutable.asStateFlow()
    val target get()=mutable.value.target
    val generation get()=mutable.value.generation
    @Volatile private var pickerOwner: Long? = null

    @Synchronized fun begin(target: UnlockTarget): Long {
        pickerOwner=null; mutable.value=Authorization(target,generation+1)
        return generation
    }
    /** Enabling disguise from an already unlocked main screen keeps that activity's ownership. */
    @Synchronized fun protectCurrentPrimary() { pickerOwner=null;mutable.value=Authorization(UnlockTarget.PRIMARY,generation) }
    fun allows(target: UnlockTarget, owner: Long = generation) = mutable.value.let{it.target==target && owner==it.generation}
    fun requiresAppPin(disguised: Boolean, pinEnabled: Boolean) = pinEnabled && !(disguised && allows(UnlockTarget.PRIMARY))
    /** Stopping an old activity during task replacement must not revoke the new target. */
    @Synchronized fun lock(owner: Long) { if(owner==generation) reset() }
    @Synchronized fun reset() { pickerOwner=null;mutable.value=Authorization(generation=generation) }
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
