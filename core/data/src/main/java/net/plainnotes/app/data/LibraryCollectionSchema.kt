package net.plainnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

/** Local-only durable control records. No archive bytes, business identities or keys are changed. */
@Entity(tableName="library_collection")
data class LibraryCollectionEntity(@PrimaryKey val operation_uuid:String,val request_sha256:ByteArray,
    val origin_generation:String,val origin_dataset:String,val origin_epoch:String,val selected_generation:String,
    val members:Long,val state:String,val created_utc:Long)
@Entity(tableName="library_collection_member",primaryKeys=["collection_uuid","library_uuid"])
data class LibraryCollectionMemberEntity(val collection_uuid:String,val library_uuid:String,val operation_uuid:String,val role:String)
@Entity(tableName="library_write_reservation")
data class LibraryWriteReservationEntity(@PrimaryKey val operation_uuid:String,val generation:String,val dataset:String,
    val epoch:String,val request_sha256:ByteArray,val reserved_ciphertext:Long,val state:String,val created_utc:Long)

internal object LibraryCollectionSchema {
    val create=listOf(
        "CREATE TABLE IF NOT EXISTS library_collection(operation_uuid TEXT NOT NULL PRIMARY KEY,request_sha256 BLOB NOT NULL,origin_generation TEXT NOT NULL,origin_dataset TEXT NOT NULL,origin_epoch TEXT NOT NULL,selected_generation TEXT NOT NULL,members INTEGER NOT NULL,state TEXT NOT NULL,created_utc INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS library_collection_member(collection_uuid TEXT NOT NULL,library_uuid TEXT NOT NULL,operation_uuid TEXT NOT NULL,role TEXT NOT NULL,PRIMARY KEY(collection_uuid,library_uuid))",
        "CREATE TABLE IF NOT EXISTS library_write_reservation(operation_uuid TEXT NOT NULL PRIMARY KEY,generation TEXT NOT NULL,dataset TEXT NOT NULL,epoch TEXT NOT NULL,request_sha256 BLOB NOT NULL,reserved_ciphertext INTEGER NOT NULL,state TEXT NOT NULL,created_utc INTEGER NOT NULL)")
    fun migrate(db:SupportSQLiteDatabase) {create.forEach(db::execSQL);install(db)}
    fun install(db:SupportSQLiteDatabase) {
        if(!db.query("SELECT 1 FROM sqlite_master WHERE name='library_collection'").use {it.moveToFirst()})return
        val rules=listOf(
            """CREATE TRIGGER collection_initial BEFORE INSERT ON library_collection WHEN length(NEW.operation_uuid)!=36 OR length(NEW.request_sha256)!=32 OR NEW.members NOT BETWEEN 1 AND 8 OR NEW.state!='PREPARED' OR NEW.created_utc<=0
                BEGIN SELECT RAISE(ABORT,'invalid collection reservation');END""",
            """CREATE TRIGGER collection_transition BEFORE UPDATE ON library_collection WHEN NEW.operation_uuid IS NOT OLD.operation_uuid OR NEW.request_sha256 IS NOT OLD.request_sha256 OR NEW.origin_generation IS NOT OLD.origin_generation OR NEW.origin_dataset IS NOT OLD.origin_dataset OR NEW.origin_epoch IS NOT OLD.origin_epoch OR NEW.selected_generation IS NOT OLD.selected_generation OR NEW.members!=OLD.members OR NEW.created_utc!=OLD.created_utc OR
                (NEW.state!=OLD.state AND NOT ((OLD.state='PREPARED' AND NEW.state IN('READY','ABORTED')) OR (OLD.state='READY' AND NEW.state IN('COMMITTED','ABORTED'))))
                BEGIN SELECT RAISE(ABORT,'immutable collection or invalid transition');END""",
            """CREATE TRIGGER collection_ready BEFORE UPDATE ON library_collection WHEN NEW.state='READY' AND (SELECT COUNT(*) FROM library_collection_member m JOIN library_candidate c ON c.operation_uuid=m.operation_uuid WHERE m.collection_uuid=NEW.operation_uuid AND c.state='READY')!=NEW.members
                BEGIN SELECT RAISE(ABORT,'incomplete collection');END""",
            """CREATE TRIGGER collection_committed BEFORE UPDATE ON library_collection WHEN NEW.state='COMMITTED' AND (NOT EXISTS(SELECT 1 FROM library_authority WHERE active_generation=NEW.selected_generation) OR
                (SELECT COUNT(*) FROM library_collection_member m JOIN library_candidate c ON c.operation_uuid=m.operation_uuid WHERE m.collection_uuid=NEW.operation_uuid AND ((m.role='ACTIVE' AND c.state='ACTIVE' AND c.generation_uuid=NEW.selected_generation) OR (m.role='RETAINED' AND c.state='RETIRED')))!=NEW.members)
                BEGIN SELECT RAISE(ABORT,'collection not atomically active');END""",
            """CREATE TRIGGER collection_member_initial BEFORE INSERT ON library_collection_member WHEN NEW.role NOT IN('ACTIVE','RETAINED') OR length(NEW.library_uuid)!=36 OR
                NOT EXISTS(SELECT 1 FROM library_collection WHERE operation_uuid=NEW.collection_uuid AND state='PREPARED') OR
                NOT EXISTS(SELECT 1 FROM library_candidate WHERE operation_uuid=NEW.operation_uuid AND state='PREPARED') OR EXISTS(SELECT 1 FROM library_collection_member WHERE operation_uuid=NEW.operation_uuid)
                BEGIN SELECT RAISE(ABORT,'invalid collection member');END""",
            """CREATE TRIGGER write_reservation_initial BEFORE INSERT ON library_write_reservation WHEN length(NEW.operation_uuid)!=36 OR length(NEW.request_sha256)!=32 OR NEW.reserved_ciphertext<=0 OR NEW.reserved_ciphertext>268435456 OR NEW.state!='STARTED' OR NEW.created_utc<=0
                BEGIN SELECT RAISE(ABORT,'invalid joint write reservation');END""",
            """CREATE TRIGGER write_reservation_transition BEFORE UPDATE ON library_write_reservation WHEN NEW.operation_uuid IS NOT OLD.operation_uuid OR NEW.generation IS NOT OLD.generation OR NEW.dataset IS NOT OLD.dataset OR NEW.epoch IS NOT OLD.epoch OR NEW.request_sha256 IS NOT OLD.request_sha256 OR NEW.reserved_ciphertext!=OLD.reserved_ciphertext OR NEW.created_utc!=OLD.created_utc OR NOT (OLD.state='STARTED' AND NEW.state='COMMITTED')
                BEGIN SELECT RAISE(ABORT,'immutable joint write reservation');END"""
        )
        (rules+listOf("library_collection","library_collection_member","library_write_reservation").flatMap {table->
            (if(table=="library_collection_member")listOf("UPDATE","DELETE") else listOf("DELETE")).map {op->
                "CREATE TRIGGER ${table}_keep_${op.lowercase()} BEFORE $op ON $table BEGIN SELECT RAISE(ABORT,'retain collection ownership');END"
            }
        }).forEach {sql->db.execSQL("DROP TRIGGER IF EXISTS ${sql.split(' ')[2]}");db.execSQL(sql)}
    }
    fun requireNoWrites(raw:SupportSQLiteDatabase) {
        raw.query("SELECT 1 FROM library_write_reservation WHERE state!='COMMITTED' LIMIT 1").use {check(!it.moveToFirst()) {"Unresolved joint write reservation"}}
    }
}
