package net.plainnotes.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

/** Local routing evidence only; never exported in PNBAK1 or copied from an archive. */
@Entity(tableName="library_authority")
data class LibraryAuthorityEntity(@PrimaryKey val singleton:Long,val active_generation:String?,val revision:Long)

@Entity(tableName="library_candidate",indices=[Index(value=["generation_uuid"],unique=true)])
data class LibraryCandidateEntity(@PrimaryKey val operation_uuid:String,val generation_uuid:String,
    val dataset_uuid:String,val epoch_uuid:String,val state:String,val archive_sha256:ByteArray,
    val manifest:ByteArray,val structured_sha256:ByteArray,val database_alias:String,
    val reserved_aliases:Long,val reserved_ciphertext:Long,val created_utc:Long,
    val directory_device:Long?,val directory_inode:Long?)

internal object LibraryGenerationSchema {
    val create=listOf(
        "CREATE TABLE IF NOT EXISTS library_authority (singleton INTEGER NOT NULL PRIMARY KEY,active_generation TEXT,revision INTEGER NOT NULL)",
        """CREATE TABLE IF NOT EXISTS library_candidate (operation_uuid TEXT NOT NULL PRIMARY KEY,
            generation_uuid TEXT NOT NULL,dataset_uuid TEXT NOT NULL,epoch_uuid TEXT NOT NULL,state TEXT NOT NULL,
            archive_sha256 BLOB NOT NULL,manifest BLOB NOT NULL,structured_sha256 BLOB NOT NULL,database_alias TEXT NOT NULL,
            reserved_aliases INTEGER NOT NULL,reserved_ciphertext INTEGER NOT NULL,created_utc INTEGER NOT NULL,
            directory_device INTEGER,directory_inode INTEGER)""",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_library_candidate_generation_uuid ON library_candidate(generation_uuid)")
    fun migrate(db:SupportSQLiteDatabase) {create.forEach(db::execSQL);install(db)}
    fun install(db:SupportSQLiteDatabase) {
        if(!db.query("SELECT 1 FROM sqlite_master WHERE name='library_authority'").use {it.moveToFirst()})return
        val rules=listOf(
            """CREATE TRIGGER library_authority_initial BEFORE INSERT ON library_authority
                WHEN NEW.singleton!=1 OR NEW.active_generation IS NOT NULL OR NEW.revision!=0
                BEGIN SELECT RAISE(ABORT,'authority starts with legacy root');END""",
            """CREATE TRIGGER library_authority_switch BEFORE UPDATE ON library_authority
                WHEN NEW.singleton!=OLD.singleton OR NEW.revision!=OLD.revision+1 OR
                NEW.active_generation IS OLD.active_generation OR NOT EXISTS(SELECT 1 FROM library_candidate
                WHERE generation_uuid=NEW.active_generation AND state='READY' AND directory_inode IS NOT NULL)
                BEGIN SELECT RAISE(ABORT,'unverified library switch');END""",
            "CREATE TRIGGER library_authority_keep BEFORE DELETE ON library_authority BEGIN SELECT RAISE(ABORT,'retain authority');END",
            """CREATE TRIGGER library_candidate_initial BEFORE INSERT ON library_candidate WHEN
                length(NEW.operation_uuid)!=36 OR length(NEW.generation_uuid)!=36 OR length(NEW.dataset_uuid)!=36 OR length(NEW.epoch_uuid)!=36 OR
                NEW.state!='PREPARED' OR length(NEW.archive_sha256)!=32 OR length(NEW.structured_sha256)!=32 OR
                NEW.reserved_aliases<1 OR NEW.reserved_aliases>512 OR NEW.reserved_ciphertext<0 OR NEW.reserved_ciphertext>268435456 OR
                NEW.created_utc<=0 OR NEW.directory_inode IS NOT NULL OR length(NEW.manifest)>8388608
                BEGIN SELECT RAISE(ABORT,'invalid restore reservation');END""",
            """CREATE TRIGGER library_candidate_transition BEFORE UPDATE ON library_candidate WHEN
                NEW.operation_uuid IS NOT OLD.operation_uuid OR NEW.generation_uuid IS NOT OLD.generation_uuid OR
                NEW.dataset_uuid IS NOT OLD.dataset_uuid OR NEW.epoch_uuid IS NOT OLD.epoch_uuid OR
                NEW.archive_sha256 IS NOT OLD.archive_sha256 OR NEW.manifest IS NOT OLD.manifest OR
                NEW.structured_sha256 IS NOT OLD.structured_sha256 OR NEW.database_alias IS NOT OLD.database_alias OR
                NEW.reserved_aliases!=OLD.reserved_aliases OR NEW.reserved_ciphertext!=OLD.reserved_ciphertext OR NEW.created_utc!=OLD.created_utc OR
                (OLD.directory_inode IS NOT NULL AND (NEW.directory_inode IS NOT OLD.directory_inode OR NEW.directory_device IS NOT OLD.directory_device)) OR
                (NEW.state!=OLD.state AND NOT ((OLD.state='PREPARED' AND NEW.state IN('READY','ABORTED')) OR
                (OLD.state='READY' AND NEW.state IN('ACTIVE','ABORTED')) OR (OLD.state='ACTIVE' AND NEW.state='RETIRED')))
                BEGIN SELECT RAISE(ABORT,'immutable restore identity or invalid transition');END""",
            "CREATE TRIGGER library_candidate_keep BEFORE DELETE ON library_candidate BEGIN SELECT RAISE(ABORT,'retain restore ownership');END")
        rules.forEach {sql->db.execSQL("DROP TRIGGER IF EXISTS ${sql.split(' ')[2]}");db.execSQL(sql)}
    }
}
