package net.plainnotes.app.domain

/** No domain identity or health data belongs in this device-protected model. */
/** [test] marks the user-requested test reminder: it has no database mapping and survives rebuilds until it fires. */
data class CachedAlarm(val opaqueId: String, val triggerMillis: Long, val mode: String, val consumed: Boolean = false, val test: Boolean = false)
data class AlarmCache(val generation: String, val expiresMillis: Long, val alarms: List<CachedAlarm>) {
    init { require(alarms.map { it.opaqueId }.distinct().size == alarms.size); require(alarms.all { it.triggerMillis <= expiresMillis }) }
    fun consume(id: String, requestGeneration: String): AlarmCache? {
        if(requestGeneration != generation || alarms.none { it.opaqueId==id && !it.consumed }) return null
        return copy(alarms=alarms.map { if(it.opaqueId==id) it.copy(consumed=true) else it })
    }
    fun next(now: Long): CachedAlarm? = if(now>expiresMillis) null else alarms.filterNot { it.consumed }.minByOrNull { it.triggerMillis }
}
