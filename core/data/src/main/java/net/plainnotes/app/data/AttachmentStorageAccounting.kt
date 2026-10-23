package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** Called under the existing writer lock AND current SQL transaction. No key/file deletion. */
internal object AttachmentStorageAccounting {
    private data class Fact(val name:String,val identity:AttachmentOwnedFiles.Identity?,
        val category:AttachmentStorageBudget.Category)
    fun inspect(raw:SupportSQLiteDatabase,directory:AttachmentOwnedFiles.Directory,
        keys:AttachmentDeviceKeys,budget:AttachmentStorageBudget,checkAuthority:()->Unit):AttachmentStorageBudget.Report {
        check(raw.inTransaction()) {"Inventory requires a consistent metadata transaction"}
        val facts=ArrayList<Fact>();val aliases=HashSet<String>()
        raw.query("""SELECT j.object_uuid,j.parent_device,j.parent_inode,j.file_device,j.file_inode,
            j.proof,o.phase,j.stage_alias_uuid,j.active_alias_uuid,
            v.version_uuid,d.active_version_uuid,d.state,s.store_state,j.record_epoch,s.record_epoch
            FROM attachment_journal j JOIN attachment_operation o ON o.store_uuid=j.store_uuid AND o.operation_uuid=j.operation_uuid
            JOIN attachment_store s ON s.store_uuid=j.store_uuid
            LEFT JOIN attachment_version v ON v.store_uuid=j.store_uuid AND v.document_uuid=j.document_uuid AND v.version_uuid=j.version_uuid
            LEFT JOIN attachment_document d ON d.store_uuid=v.store_uuid AND d.document_uuid=v.document_uuid""").use { c ->
            while(c.moveToNext()) {
                checkAuthority()
                if(facts.size>=budget.maximumEntries) throw AttachmentStorageBudget.Rejected(AttachmentStorageBudget.Refusal.ENTRY_BUDGET)
                val name=c.getString(0);check(UUID.fromString(name).toString()==name)
                aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(7)),AttachmentDeviceKeys.Role.STAGING).value
                aliases+=AttachmentDeviceKeys.Alias(UUID.fromString(c.getString(8)),AttachmentDeviceKeys.Role.ACTIVE).value
                val category=when {
                    c.getString(5)=="COMMITTED" && c.getString(6)=="COMMITTED" && !c.isNull(9) ->
                        if(c.getString(9)==c.getString(10) && c.getString(11)=="ACTIVE" && c.getString(12)=="CURRENT" && c.getString(13)==c.getString(14))
                            AttachmentStorageBudget.Category.CURRENT else AttachmentStorageBudget.Category.HISTORY
                    c.getString(5)=="QUARANTINED" || c.getString(6)=="ABORTED" -> AttachmentStorageBudget.Category.QUARANTINED
                    c.getString(5)!="COMMITTED" -> AttachmentStorageBudget.Category.IN_FLIGHT
                    else -> AttachmentStorageBudget.Category.UNKNOWN
                }
                facts+=Fact(name,if(c.isNull(4))null else AttachmentOwnedFiles.Identity(c.getLong(1),c.getLong(2),c.getLong(3),c.getLong(4)),category)
            }
        }
        val reservedAliases=aliases.size
        aliases+=keys.retainedAliases(Math.multiplyExact(budget.maximumEntries,2))
        val inventory=directory.inventory(budget.maximumEntries,checkAuthority)
        val byName=facts.groupBy {it.name};val found=HashSet<Fact>()
        val count=AttachmentStorageBudget.Category.entries.associateWith {longArrayOf(0,0,0)}
        var complete=true
        for(e in inventory.entries) {
            checkAuthority()
            val candidates=byName[e.name].orEmpty()
            val fact=candidates.singleOrNull()?.takeIf {it.identity==e.identity && e.privateRegular}
            if(!e.privateRegular || (candidates.isNotEmpty() && fact==null) || fact?.category==AttachmentStorageBudget.Category.UNKNOWN) complete=false
            val category=fact?.category ?: AttachmentStorageBudget.Category.UNKNOWN
            if(fact!=null) found+=fact
            val sums=count.getValue(category)
            sums[0]=Math.addExact(sums[0],1);sums[1]=Math.addExact(sums[1],e.logicalBytes);sums[2]=Math.addExact(sums[2],e.chargedBytes)
        }
        checkAuthority()
        return AttachmentStorageBudget.Report(count.mapValues {(_,v)->AttachmentStorageBudget.Usage(v[0],v[1],v[2])},
            inventory.overheadBytes,aliases.size,facts.count {it.identity!=null && it !in found}.toLong(),complete,
            inventory.freeBytes,inventory.allocationUnit,inventory.entries.size,reservedAliases,aliases.size-reservedAliases)
    }
}
