package net.plainnotes.app.attachments

import android.content.Context
import android.content.SharedPreferences
import net.plainnotes.app.data.LibraryRequestScope
import net.plainnotes.app.data.MedicalAttachmentOperations
import java.security.MessageDigest
import java.util.UUID

/** Neutral status-query identities only. Never stores URI/title/bytes/password/unlock grants.
 * Scope and typed target are frozen in the key; another dataset cannot adopt the operation.
 * Synchronous commit precedes dispatch. A corrupt checkpoint refuses new operations.
 */
internal class AttachmentReconciliationStore(context:Context,scope:LibraryRequestScope,target:MedicalAttachmentOperations.Target?) {
    private val ownerEpoch=synchronized(monitor) {wipeEpoch}
    private val prefs=context.getSharedPreferences("attachment-reconciliation-v1",Context.MODE_PRIVATE)
    private val key=MessageDigest.getInstance("SHA-256").digest(
        (scope.encoded()+(target?.let {listOf(it.kind.name,it.id.toString())+(if(it.kind==MedicalAttachmentOperations.Kind.LAB_PANEL)listOf(requireNotNull(it.expectedUuid).toString()) else emptyList())} ?: listOf("FILE_LIBRARY"))).joinToString("\u0000").toByteArray()
    ).joinToString("") {"%02x".format(it.toInt() and 255)}
    fun pending():Pair<UUID,UUID>?=synchronized(monitor) {
        if(!prefs.contains(key))return@synchronized null
        val raw=requireNotNull(prefs.getString(key,null));require(raw.length==73)
        val parts=raw.split('|');require(parts.size==2)
        val ids=parts.map {UUID.fromString(it).also {id->require(id.toString()==it)}}
        ids[0] to ids[1]
    }
    fun retain(operation:UUID,document:UUID)=synchronized(monitor) {
        check(ownerEpoch==wipeEpoch) {"Reconciliation owner ended by data wipe"}
        val pair=operation to document
        check(pending().let {it==null || it==pair}) {"Reconcile the existing attachment operation"}
        check(prefs.edit().putString(key,"$operation|$document").commit()) {"Cannot retain reconciliation identity"}
    }
    fun resolved(operation:UUID,document:UUID)=synchronized(monitor) {
        check(pending()==(operation to document)) {"Wrong reconciliation identity"}
        check(prefs.edit().remove(key).commit()) {"Cannot acknowledge reconciliation"}
    }
    companion object {
        private val monitor=Any()
        private var wipeEpoch=0L
        /** Serialize with retain so a delayed old request cannot recreate wiped identities. */
        fun clearAll(prefs:SharedPreferences)=synchronized(monitor) {
            check(prefs.edit().clear().commit()) {"Cannot clear reconciliation identities"}
            wipeEpoch++
            check(prefs.all.isEmpty()) {"Reconciliation identities remain"}
        }
    }
}
