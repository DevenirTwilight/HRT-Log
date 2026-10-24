package net.plainnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

/** Local evidence, never a second authority or part of either portable protocol. */
@Entity(tableName="library_resource_owner")
data class LibraryResourceOwnerEntity(@PrimaryKey val operation_uuid:String,val generation_uuid:String,
    val origin_generation:String,val origin_dataset:String,val origin_epoch:String,val started:Int,
    val released:Int,val release_digest:ByteArray?)
@Entity(tableName="library_resource_inventory")
data class LibraryResourceInventoryEntity(@PrimaryKey val singleton:Long,val revision:Long,
    val operation_uuid:String,val generation:String,val dataset:String,val epoch:String,
    val payload:ByteArray,val sha256:ByteArray,val observed_utc:Long)

internal object LibraryLifecycleSchema {
    private val ddl=listOf(
        "CREATE TABLE IF NOT EXISTS library_resource_owner (operation_uuid TEXT NOT NULL PRIMARY KEY,generation_uuid TEXT NOT NULL,origin_generation TEXT NOT NULL,origin_dataset TEXT NOT NULL,origin_epoch TEXT NOT NULL,started INTEGER NOT NULL,released INTEGER NOT NULL,release_digest BLOB)",
        "CREATE TABLE IF NOT EXISTS library_resource_inventory (singleton INTEGER NOT NULL PRIMARY KEY,revision INTEGER NOT NULL,operation_uuid TEXT NOT NULL,generation TEXT NOT NULL,dataset TEXT NOT NULL,epoch TEXT NOT NULL,payload BLOB NOT NULL,sha256 BLOB NOT NULL,observed_utc INTEGER NOT NULL)")
    fun migrate(db:SupportSQLiteDatabase) {ddl.forEach(db::execSQL);install(db)}
    fun install(db:SupportSQLiteDatabase) {
        if(!db.query("SELECT 1 FROM sqlite_master WHERE name='library_resource_owner'").use {it.moveToFirst()})return
        val rules=listOf(
            """CREATE TRIGGER library_owner_initial BEFORE INSERT ON library_resource_owner WHEN NEW.started!=0 OR NEW.released!=0 OR NEW.release_digest IS NOT NULL OR length(NEW.origin_dataset)!=36 OR length(NEW.origin_epoch)!=36 OR NOT EXISTS(SELECT 1 FROM library_candidate c WHERE c.operation_uuid=NEW.operation_uuid AND c.generation_uuid=NEW.generation_uuid AND c.state='PREPARED') BEGIN SELECT RAISE(ABORT,'invalid resource owner');END""",
            """CREATE TRIGGER library_owner_transition BEFORE UPDATE ON library_resource_owner WHEN NEW.operation_uuid IS NOT OLD.operation_uuid OR NEW.generation_uuid IS NOT OLD.generation_uuid OR NEW.origin_generation IS NOT OLD.origin_generation OR NEW.origin_dataset IS NOT OLD.origin_dataset OR NEW.origin_epoch IS NOT OLD.origin_epoch OR NEW.started NOT IN(0,1) OR NEW.started<OLD.started OR NEW.released NOT IN(0,1) OR NEW.released<OLD.released OR (OLD.released=1 AND (NEW.started!=OLD.started OR NEW.release_digest IS NOT OLD.release_digest)) OR (NEW.released=1 AND (NEW.started!=0 OR NEW.release_digest IS NULL OR length(NEW.release_digest)!=32 OR NOT EXISTS(SELECT 1 FROM library_candidate c WHERE c.operation_uuid=NEW.operation_uuid AND c.generation_uuid=NEW.generation_uuid AND c.state='ABORTED' AND c.directory_inode IS NULL))) BEGIN SELECT RAISE(ABORT,'invalid logical resource release');END""",
            "CREATE TRIGGER library_owner_keep BEFORE DELETE ON library_resource_owner BEGIN SELECT RAISE(ABORT,'retain owner evidence');END",
            """CREATE TRIGGER library_inventory_initial BEFORE INSERT ON library_resource_inventory WHEN NEW.singleton!=1 OR NEW.revision!=1 OR length(NEW.payload)>1048576 OR length(NEW.sha256)!=32 BEGIN SELECT RAISE(ABORT,'invalid inventory');END""",
            """CREATE TRIGGER library_inventory_revision BEFORE UPDATE ON library_resource_inventory WHEN NEW.singleton!=OLD.singleton OR NEW.revision!=OLD.revision+1 OR length(NEW.payload)>1048576 OR length(NEW.sha256)!=32 BEGIN SELECT RAISE(ABORT,'invalid inventory revision');END""",
            "CREATE TRIGGER library_inventory_keep BEFORE DELETE ON library_resource_inventory BEGIN SELECT RAISE(ABORT,'retain unknown observations');END")
        rules.forEach {db.execSQL("DROP TRIGGER IF EXISTS ${it.split(' ')[2]}");db.execSQL(it)}
    }
    fun released(db:SupportSQLiteDatabase,operation:String)=db.query("SELECT released FROM library_resource_owner WHERE operation_uuid=?",arrayOf(operation)).use {it.moveToFirst() && it.getInt(0)==1}
}
