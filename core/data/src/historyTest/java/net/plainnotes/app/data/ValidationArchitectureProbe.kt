package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** TEST_ONLY architecture hypotheses. No object here is a production validation authority. */
internal object ValidationArchitectureProbe {
    data class Stamp(val changes:Long,val dataVersion:Long,val schemaVersion:Long,val tempSchemaVersion:Long,val epoch:String)
    fun stamp(db:SupportSQLiteDatabase):Stamp {
        check(db.inTransaction())
        fun number(sql:String)=db.query(sql).use{check(it.moveToFirst());it.getLong(0)}
        return Stamp(number("SELECT total_changes()"),number("PRAGMA data_version"),number("PRAGMA schema_version"),
            number("PRAGMA temp.schema_version"),ReferenceStore.previewEpoch(db))
    }
    /** Hypothesis A: a real full audit plus engine stamps and handle/epoch binding suffices.
     * Deliberately commit-unaware; the tests must decide whether it is sound, not ship it.
     */
    class CounterWitness(val owner:SupportSQLiteDatabase,val stamp:Stamp,val projections:List<AuditedStockProjection>) {
        fun matches(db:SupportSQLiteDatabase)=db===owner && db.isOpen && stamp==stamp(db)
    }
    fun auditAndCapture(db:SupportSQLiteDatabase):CounterWitness {
        val projections=MeasuredConsumptionStore.auditStagedDataset(db)
        return CounterWitness(db,stamp(db),projections)
    }
    data class Change(val table:String,val row:Long,val verb:String)
    /** Hypothesis B substrate: transactional connection-local marker and bounded all-domain journal.
     * TEMP only, no schema/backup changes; SQL metadata is NOT claimed tamper-proof.
     */
    class Journal(private val db:SupportSQLiteDatabase):AutoCloseable {
        private val tables=(DOMAIN_TABLES+"reference_runtime").distinct()
        private val triggers=ArrayList<String>()
        init {
            check(db.inTransaction())
            db.execSQL("CREATE TEMP TABLE p4_probe_seal(id INTEGER PRIMARY KEY,token TEXT NOT NULL,overflow INTEGER NOT NULL)")
            db.execSQL("INSERT INTO p4_probe_seal VALUES(1,'',0)")
            db.execSQL("CREATE TEMP TABLE p4_probe_journal(table_name TEXT NOT NULL,row_handle INTEGER NOT NULL,verb TEXT NOT NULL)")
            for((index,table) in tables.withIndex())for(verb in listOf("INSERT","UPDATE","DELETE")) {
                val trigger="p4_probe_${index}_$verb";triggers+=trigger
                val row=if(verb=="DELETE")"OLD.rowid" else "NEW.rowid"
                db.execSQL("CREATE TEMP TRIGGER `$trigger` AFTER $verb ON main.`$table` BEGIN "+
                    "INSERT INTO p4_probe_journal SELECT '$table',$row,'$verb' WHERE (SELECT count(*) FROM p4_probe_journal)<1024; "+
                    "UPDATE p4_probe_seal SET overflow=1 WHERE id=1 AND changes()=0; END")
            }
        }
        data class Seal(val token:String,val witness:CounterWitness)
        fun sealAfterFullAudit():Seal {
            val projections=MeasuredConsumptionStore.auditStagedDataset(db)
            val token=UUID.randomUUID().toString()
            db.execSQL("UPDATE p4_probe_seal SET token=?,overflow=0 WHERE id=1",arrayOf(token))
            db.execSQL("DELETE FROM p4_probe_journal")
            return Seal(token,CounterWitness(db,stamp(db),projections))
        }
        fun unchanged(seal:Seal):Boolean {
            if(!seal.witness.matches(db))return false
            return db.query("SELECT token,overflow FROM p4_probe_seal WHERE id=1").use {
                it.moveToFirst() && it.getString(0)==seal.token && it.getInt(1)==0
            }
        }
        fun markerMatches(seal:Seal)=db.query("SELECT token FROM p4_probe_seal WHERE id=1").use{it.moveToFirst() && it.getString(0)==seal.token}
        fun overflowed()=db.query("SELECT overflow FROM p4_probe_seal WHERE id=1").use{check(it.moveToFirst());it.getInt(0)!=0}
        fun changes():List<Change> = db.query("SELECT table_name,row_handle,verb FROM p4_probe_journal ORDER BY rowid").use {c->
            buildList{while(c.moveToNext())add(Change(c.getString(0),c.getLong(1),c.getString(2)))}
        }
        /** Pure schema adjacency proposal; intentionally does not invent application-level invariants. */
        fun foreignKeyClosure(seeds:Set<String>):Set<String> {
            require(seeds.all{it in tables})
            val adjacency=tables.associateWith{mutableSetOf<String>()}
            for(table in tables)db.query("PRAGMA foreign_key_list(`$table`)").use{c->while(c.moveToNext()) {
                val parent=c.getString(2)
                if(parent in tables){adjacency.getValue(table).add(parent);adjacency.getValue(parent).add(table)}
            }}
            val result=seeds.toMutableSet();val pending=java.util.ArrayDeque(seeds)
            while(pending.isNotEmpty()) {
                val table=pending.removeFirst()
                for(neighbor in adjacency.getValue(table))if(result.add(neighbor))pending.add(neighbor)
            }
            return result
        }
        override fun close() {
            triggers.forEach{db.execSQL("DROP TRIGGER IF EXISTS temp.`$it`")}
            db.execSQL("DROP TABLE IF EXISTS temp.p4_probe_journal")
            db.execSQL("DROP TABLE IF EXISTS temp.p4_probe_seal")
        }
    }
}
