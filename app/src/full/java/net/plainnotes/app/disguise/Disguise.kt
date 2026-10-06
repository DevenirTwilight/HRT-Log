package net.plainnotes.app.disguise

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import net.plainnotes.app.security.taskIdentity
import android.content.Intent
import android.content.pm.PackageManager
import net.plainnotes.app.MainActivity
import net.plainnotes.app.R
import net.plainnotes.app.disguise.privatenotes.PrivateNotesActivity
import net.plainnotes.app.disguise.privatenotes.PrivateStore
import net.plainnotes.app.reminder.NotificationPrefs
import net.plainnotes.app.security.AppLock
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.UnlockTarget

/** Public tool shells and a separately authenticated private notes area. Authentication never selects a database. */
object Disguise {
    const val AVAILABLE=true
    enum class Shell(val alias:String,val label:Int,val icon:Int) {
        CALCULATOR("net.plainnotes.app.CalculatorLauncher",R.string.shell_calc,R.mipmap.ic_shell_calc),
        NOTES("net.plainnotes.app.NotesLauncher",R.string.shell_notes,R.mipmap.ic_shell_notes)
    }
    private const val NORMAL="net.plainnotes.app.Launcher"
    private const val MAX_MISSES=10
    private const val PAUSE_MILLIS=60_000L
    @Volatile var revision=0L
        private set
    private tailrec fun activity(c:Context):Activity?=when(c){is Activity->c;is ContextWrapper->activity(c.baseContext);else->null}
    private fun code(c:Context)=AppLock(c,"disguise.bin","notes.disguise")
    // Keep the existing credential filename/alias so installed users retain their alternate code.
    private fun privateCode(c:Context)=AppLock(c,"decoy.bin","notes.decoy")
    private fun prefs(c:Context)=c.getSharedPreferences("prefs",Context.MODE_PRIVATE)
    private fun component(c:Context,name:String)=ComponentName(c.packageName,name)
    private fun set(c:Context,name:String,on:Boolean)=c.packageManager.setComponentEnabledSetting(component(c,name),
        if(on)PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP)
    fun shell(c:Context)=Shell.entries.firstOrNull{c.packageManager.getComponentEnabledSetting(component(c,it.alias))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED}
    fun enabled(c:Context)=shell(c)!=null
    fun hasPrivateCode(c:Context)=privateCode(c).enabled

    @Synchronized fun enable(c:Context,shell:Shell,secret:String,alternate:String?) {
        require(AppLock.validPin(secret) && (alternate==null || (AppLock.validPin(alternate) && alternate!=secret)))
        code(c).setPin(secret)
        if(alternate!=null)privateCode(c).setPin(alternate) else privateCode(c).disable()
        activity(c)?.setTaskDescription(taskIdentity(c.getString(shell.label),shell.icon))
        NotificationPrefs(c).disguised=true
        // Old detailed notifications must not remain visible after enabling disguise.
        c.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        Shell.entries.forEach{if(it!=shell)set(c,it.alias,false)}
        set(c,shell.alias,true);set(c,NORMAL,false)
        prefs(c).edit().remove("disguise_misses").remove("disguise_until").apply()
        revision++
    }
    /** Async setup cannot authenticate an activity that left the screen, or a newer unrelated session. */
    @Synchronized fun completeSetup(a:Activity,owner:Long) {
        if(a !is MainActivity)return
        if(owner==Session.generation)shell(a)?.let{a.setTaskDescription(taskIdentity(a.getString(it.label),it.icon))}
        val resumed=(a as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState==androidx.lifecycle.Lifecycle.State.RESUMED
        if(enabled(a) && resumed && owner==Session.generation)Session.protectCurrentPrimary() else Session.lock(owner)
    }
    @Synchronized fun setPrivateCode(c:Context,value:String) {
        require(enabled(c) && AppLock.validPin(value) && !code(c).matches(value))
        privateCode(c).setPin(value);revision++
        if(Session.target==UnlockTarget.PRIVATE)Session.reset()
        // Deliberately keep the shared brute-force delay; changing a code must not reset it.
    }
    /** Caller first confirms that removing the code also erases the ordinary private notes. */
    @Synchronized fun removePrivateCode(c:Context) {
        PrivateStore(c).destroy();privateCode(c).disable();revision++
        if(Session.target==UnlockTarget.PRIVATE)Session.reset()
    }
    @Synchronized fun clearPrivate(c:Context) { PrivateStore(c).destroy() }
    /** Caller confirms deletion; legacy HRT DECOY storage is cleaned separately by the main repository. */
    @Synchronized fun disable(c:Context) {
        PrivateStore(c).destroy()
        set(c,NORMAL,true);Shell.entries.forEach{set(c,it.alias,false)}
        code(c).disable();privateCode(c).disable();NotificationPrefs(c).disguised=false
        prefs(c).edit().remove("disguise_misses").remove("disguise_until").apply()
        revision++;Session.reset()
        activity(c)?.setTaskDescription(taskIdentity(c.getString(R.string.app_name),R.mipmap.ic_launcher))
    }
    /** No visible authentication errors: misses retain ordinary Calculator/Notes behavior and a shared silent delay. */
    @Synchronized fun check(c:Context,input:String,now:Long=System.currentTimeMillis()):UnlockTarget? {
        if(!AppLock.validPin(input) || !enabled(c))return null
        val p=prefs(c);if(now<p.getLong("disguise_until",0))return null
        val result=when { code(c).matches(input)->UnlockTarget.PRIMARY;privateCode(c).matches(input)->UnlockTarget.PRIVATE;else->null }
        val misses=if(result!=null)0 else p.getInt("disguise_misses",0)+1
        p.edit().putInt("disguise_misses",if(misses>=MAX_MISSES)0 else misses)
            .putLong("disguise_until",if(misses>=MAX_MISSES)now+PAUSE_MILLIS else 0).apply()
        return result
    }
    fun shellIntent(c:Context)=Intent().setComponent(component(c,shell(c)?.alias ?: NORMAL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    fun targetIntent(c:Context,target:UnlockTarget)=Intent(c,when(target){UnlockTarget.PRIMARY->MainActivity::class.java;UnlockTarget.PRIVATE->PrivateNotesActivity::class.java})
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    @Synchronized fun enter(a:Activity,target:UnlockTarget,checkedRevision:Long):Boolean {
        if((a as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState!=androidx.lifecycle.Lifecycle.State.RESUMED)return false
        if(!enabled(a) || checkedRevision!=revision || (target==UnlockTarget.PRIVATE && !hasPrivateCode(a)))return false
        Session.begin(target)
        try { a.startActivity(targetIntent(a,target));a.finish() } catch(e:Exception) { Session.reset();throw e }
        return true
    }
    /** Preparation is available only from an already authenticated main screen. No route exists in the opposite direction. */
    fun preparePrivate(a:Activity) {
        require(Session.allows(UnlockTarget.PRIMARY))
        enter(a,UnlockTarget.PRIVATE,revision)
    }
    fun exit(a:Activity) {
        Session.reset()
        // All three screens share one task affinity. CLEAR_TASK replaces its contents; never remove the replacement task.
        a.startActivity(shellIntent(a));a.finish()
    }
}
