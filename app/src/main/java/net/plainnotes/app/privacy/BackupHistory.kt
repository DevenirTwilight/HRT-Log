package net.plainnotes.app.privacy

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Main-space operation metadata only: never a URI, password, or assertion that a backup is recoverable. */
class BackupHistory(private val prefs:SharedPreferences,private val clock:Clock=Clock.systemUTC()) {
    data class State(val lastExport:Instant?,val reminderEnabled:Boolean,val days:Int)
    internal data class Previous(val time:Instant?,val generation:Long)
    companion object {
        const val LAST="b2_backup_export_completed_utc"
        private const val ENABLED="b2_backup_reminder_enabled"
        private const val DAYS="b2_backup_reminder_days"
        val intervals=listOf(7,14,30)
        private val lock=Any()
        private var generation=0L
    }
    private fun read()=State(if(prefs.contains(LAST))Instant.ofEpochMilli(prefs.getLong(LAST,0)) else null,
        prefs.getBoolean(ENABLED,false),prefs.getInt(DAYS,14).takeIf{it in intervals} ?: 14)
    private val mutable=MutableStateFlow(read())
    val state=mutable.asStateFlow()
    fun now():Instant=clock.instant()
    fun refresh()=synchronized(lock){mutable.value=read()}
    fun configure(enabled:Boolean,days:Int)=synchronized(lock){
        require(days in intervals)
        prefs.edit().putBoolean(ENABLED,enabled).putInt(DAYS,days).apply();mutable.value=read()
    }
    fun due(now:Instant=now()):Boolean {
        val s=state.value
        return s.reminderEnabled && (s.lastExport==null || (!now.isBefore(s.lastExport) && Duration.between(s.lastExport,now).toDays()>=s.days))
    }
    internal fun previous()=synchronized(lock){Previous(read().lastExport,generation)}
    internal fun record(previous:Previous)=synchronized(lock){
        if(previous.generation!=generation)throw IOException("Export superseded by clear")
        if(!prefs.edit().putLong(LAST,clock.millis()).commit())throw IOException("Export metadata unavailable")
        mutable.value=read()
    }
    internal fun rollback(previous:Previous)=synchronized(lock){
        if(previous.generation==generation) {
            val edit=prefs.edit();if(previous.time==null)edit.remove(LAST) else edit.putLong(LAST,previous.time.toEpochMilli())
            edit.commit();mutable.value=read()
        }
    }
    fun clear()=synchronized(lock){
        generation++ // a cancelled older export cannot resurrect metadata after a wipe
        val ok=prefs.edit().remove(LAST).remove(ENABLED).remove(DAYS).commit()
        mutable.value=read()
        if(!ok)throw IOException("Backup metadata clear unavailable")
    }
}
