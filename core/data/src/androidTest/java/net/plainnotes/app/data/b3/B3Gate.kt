package net.plainnotes.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.google.crypto.tink.subtle.Hkdf
import net.plainnotes.app.data.B3Frame.F
import net.plainnotes.app.domain.AttachmentSession
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TEST APK ONLY — MED-02B5 / B3-BACKUP-GATE. An executable candidate of the complete encrypted medical backup and a
 * restore into a fresh device namespace, over real SQLCipher databases, real Keystore wrapping keys and real files.
 * NOT a production format: the magic says so, the KDF profile is a test profile, nothing here is reachable from the app,
 * and the production PNBAK1 backup, Room schema and attachment guards are untouched. See reports 55/56.
 */
internal object B3 {
    /** Bootstrap magic; deliberately unusable as a production identifier. */
    val MAGIC = "HRTLOG-B3-TEST-ONLY-NOT-PROD".toByteArray(Charsets.US_ASCII)
    const val PROTOCOL = 1
    /** KDF profile allowlist. Memory in KiB. TEST_FAST is a test-only cost, NOT a proposed production parameter.
     * TEST_HEAVY is known but deliberately larger than the instrumentation budget, to prove resource refusal. */
    data class Profile(val memoryKiB: Int, val iterations: Int)
    const val TEST_FAST = 0x7E01
    const val TEST_HEAVY = 0x7E02
    val PROFILES = mapOf(TEST_FAST to Profile(1024, 1), TEST_HEAVY to Profile(1024 * 1024, 3))
    const val MAX_FRAME = 2 * 1024 * 1024
    const val MAX_FRAMES = 4096
    const val SEGMENT = 64 * 1024
    val BOOTSTRAP_UNSIGNED = MAGIC.size + 2 + 2 + 16 + 16 + 12
    val BOOTSTRAP = BOOTSTRAP_UNSIGNED + 48
    /** Counts real Argon2id invocations, so tests can prove a refusal happened before any KDF work. */
    val kdfCalls = AtomicInteger()

    enum class Reject {
        BOOTSTRAP_TRUNCATED, BAD_MAGIC, UNSUPPORTED_PROTOCOL, UNSUPPORTED_KDF, RESOURCES, WRONG_PASSWORD_OR_HEADER,
        STREAM_AUTH, MALFORMED_FRAME, DUPLICATE_ENTRY, MANIFEST_MISSING, TRAILING_AFTER_MANIFEST, MANIFEST_MISMATCH,
        OBJECT_MISSING, OBJECT_HASH, OBJECT_AUTH, DEK_MISSING, DEK_MALFORMED, GRAPH_INVALID, QUOTA, CAS_STALE,
        INVARIANT, ARCHIVE_MISMATCH, CANCELLED,
    }
    /** Test seam only: stands for the process dying at that point (nothing in-process runs afterwards). */
    class SimulatedProcessDeath : Error("synthetic process death")
    class Rejected(val reason: Reject, detail: String, cause: Throwable? = null) : IOException("$reason: $detail", cause)
    fun reject(reason: Reject, detail: String, cause: Throwable? = null): Nothing = throw Rejected(reason, detail, cause)
    /** Lock, cancel and space switch revoke the capability; every frame boundary checks it. */
    fun alive(c: AttachmentSession.Capability) { try { c.check() } catch (e: IllegalStateException) { reject(Reject.CANCELLED, "capability revoked", e) } }

    /** RED CONTROLS ONLY (instrumentation args). Each disables exactly one guard so the same assertion must fail. */
    @Volatile var unsafeSkipManifestForRedTest = false
    @Volatile var unsafeSkipCasForRedTest = false

    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }
    fun uuidBytes(u: UUID): ByteArray = ByteBuffer.allocate(16).putLong(u.mostSignificantBits).putLong(u.leastSignificantBits).array()
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String) = Hkdf.computeHkdf("HmacSha256", ikm, salt, info.toByteArray(), 32)
    fun streaming(key: ByteArray) = AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT, 0)
    fun objectAad(vault: String, document: String, version: String, obj: String) = "B3-TEST-OBJ|$vault|$document|$version|$obj".toByteArray()
    fun argon2id(password: CharArray, salt: ByteArray, p: Profile): ByteArray {
        kdfCalls.incrementAndGet()
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(p.memoryKiB).withIterations(p.iterations).withParallelism(1).withSalt(salt).build()
        return ByteArray(32).also { Argon2BytesGenerator().apply { init(params) }.generateBytes(password, it) }
    }
    fun fsyncDir(dir: File) { val fd = Os.open(dir.path, OsConstants.O_RDONLY, 0); try { Os.fsync(fd) } finally { Os.close(fd) } }
}

/** Synthetic graph rows. Integer ids are local display ids only; identity is the UUID. */
internal data class B3Record(val uuid: String, val kind: String, val intId: Int, val state: String, val body: String?)
internal data class B3Version(val uuid: String, val document: String, val seq: Int, val state: String, val vault: String, val obj: String,
                              val dek: String, val plainHash: ByteArray, val cipherHash: ByteArray, val cipherBytes: Long, val corrects: String?)
internal data class B3Link(val uuid: String, val document: String, val record: String, val state: String)
internal data class B3Receipt(val operation: String, val dataset: String, val archive: String, val digest: ByteArray, val cleanupPending: Boolean)

/** One synthetic device namespace: SQLCipher database whose key is wrapped by this namespace's own Keystore alias,
 * encrypted objects, staging and journal. Emulator-only, standalone test package only. */
internal class B3Vault private constructor(val context: Context, val name: String, create: Boolean) : Closeable {
    companion object {
        fun create(context: Context, name: String = "synthetic-b3-${UUID.randomUUID()}") = B3Vault(context, name, true)
        fun open(context: Context, name: String) = B3Vault(context, name, false)
        /** Removes a leftover fixture even if its alias or wrap is already gone (earlier killed runs). */
        fun purge(context: Context, name: String) {
            check(name.matches(Regex("synthetic-b3-[a-zA-Z0-9-]+")))
            File(context.noBackupFilesDir, name).deleteRecursively(); runCatching { keys().deleteEntry("$name.kek") }
        }
        private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }
    enum class Seam { AFTER_STAGE, BEFORE_COMMIT, IN_COMMIT, AFTER_COMMIT, CLEANUP }
    val root = File(context.noBackupFilesDir, name)
    val kekAlias = "$name.kek"
    val objects = File(root, "objects")
    private val dbFile = File(root, "vault.db")
    val db: SQLiteDatabase
    /** Object ids pinned by a running export (backup lease). */
    private val leases = HashSet<String>()

    init {
        check(name.matches(Regex("synthetic-b3-[a-zA-Z0-9-]+")))
        check(context.packageName == "net.plainnotes.app.data.test") { "Standalone synthetic test APK only" }
        Gate2EmulatorGuard.requireEmulator()
        System.loadLibrary("sqlcipher")
        if (create) {
            check(!root.exists()) { "Fixture exists" }; check(root.mkdirs() && objects.mkdirs())
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(kekAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            }.generateKey()
            val pass = ByteArray(32).also(SecureRandom()::nextBytes)
            try { File(root, "db.wrap").writeBytes(wrap(pass, "B3-TEST-DBKEY|$name".toByteArray())) } finally { pass.fill(0) }
        }
        // Reopening never creates key material: a missing alias or wrap fails closed.
        val pass = unwrap(File(root, "db.wrap").readBytes(), "B3-TEST-DBKEY|$name".toByteArray())
        db = try { SQLiteDatabase.openOrCreateDatabase(dbFile, pass, null, null) } finally { pass.fill(0) }
        db.enableWriteAheadLogging()
        if (create) schema()
    }

    private fun kek() = keys().getKey(kekAlias, null) as? SecretKey ?: throw IllegalStateException("Namespace key unavailable")
    fun wrap(secret: ByteArray, aad: ByteArray, alias: String = kekAlias): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, keys().getKey(alias, null) as SecretKey); updateAAD(aad); iv + doFinal(secret)
    }
    fun unwrap(bytes: ByteArray, aad: ByteArray, alias: String = kekAlias): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        val key = keys().getKey(alias, null) as? SecretKey ?: throw IllegalStateException("Alias $alias unavailable")
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12))); updateAAD(aad); doFinal(bytes.copyOfRange(12, bytes.size))
    }
    fun dekAad(dataset: String, handle: String) = "B3-TEST-DEK|$name|$dataset|$handle".toByteArray()

    private fun schema() = tx {
        listOf(
            "CREATE TABLE authority(id INTEGER PRIMARY KEY CHECK(id=1), current TEXT, revision INTEGER NOT NULL)",
            "INSERT INTO authority VALUES(1,NULL,0)",
            "CREATE TABLE dataset(uuid TEXT PRIMARY KEY, state TEXT NOT NULL CHECK(state IN('CURRENT','RETIRED')), epoch TEXT NOT NULL, vault TEXT NOT NULL, source_dataset TEXT, operation TEXT, retired_by TEXT)",
            "CREATE TABLE record(uuid TEXT PRIMARY KEY, dataset TEXT NOT NULL, kind TEXT NOT NULL, int_id INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN('LIVE','TRASHED','PURGED')), body TEXT, CHECK(state<>'PURGED' OR body IS NULL))",
            "CREATE TABLE version(uuid TEXT PRIMARY KEY, dataset TEXT NOT NULL, document TEXT NOT NULL, seq INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN('CURRENT','RETAINED','TRASHED')), vault TEXT NOT NULL, object TEXT NOT NULL UNIQUE, dek TEXT NOT NULL, plain_hash BLOB NOT NULL, cipher_hash BLOB NOT NULL, cipher_bytes INTEGER NOT NULL, corrects TEXT)",
            "CREATE UNIQUE INDEX one_current_version ON version(dataset,document) WHERE state='CURRENT'",
            "CREATE TABLE dek(handle TEXT NOT NULL, dataset TEXT NOT NULL, wrap BLOB NOT NULL, PRIMARY KEY(handle,dataset))",
            "CREATE TABLE link(uuid TEXT PRIMARY KEY, dataset TEXT NOT NULL, document TEXT NOT NULL, record TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN('LIVE','TRASHED','PURGED','QUARANTINED')))",
            "CREATE TABLE operation(id TEXT PRIMARY KEY, archive TEXT NOT NULL, digest BLOB, phase TEXT NOT NULL, expected TEXT, expected_revision INTEGER NOT NULL, reservation INTEGER NOT NULL, receipt TEXT, files TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE quota(id INTEGER PRIMARY KEY CHECK(id=1), limit_bytes INTEGER NOT NULL)",
            "INSERT INTO quota VALUES(1,${64L * 1024 * 1024})",
            // Staging: encrypted at rest in the same SQLCipher file, invisible to every live query.
            "CREATE TABLE stage_record(op TEXT NOT NULL, uuid TEXT NOT NULL, kind TEXT NOT NULL, int_id INTEGER NOT NULL, state TEXT NOT NULL, body TEXT, PRIMARY KEY(op,uuid))",
            "CREATE TABLE stage_version(op TEXT NOT NULL, uuid TEXT NOT NULL, document TEXT NOT NULL, seq INTEGER NOT NULL, state TEXT NOT NULL, vault TEXT NOT NULL, object TEXT NOT NULL, dek TEXT NOT NULL, plain_hash BLOB NOT NULL, cipher_hash BLOB NOT NULL, cipher_bytes INTEGER NOT NULL, corrects TEXT, PRIMARY KEY(op,uuid))",
            "CREATE TABLE stage_link(op TEXT NOT NULL, uuid TEXT NOT NULL, document TEXT NOT NULL, record TEXT NOT NULL, state TEXT NOT NULL, PRIMARY KEY(op,uuid))",
            "CREATE TABLE stage_dek(op TEXT NOT NULL, handle TEXT NOT NULL, wrap BLOB NOT NULL, PRIMARY KEY(op,handle))",
        ).forEach { db.execSQL(it) }
    }
    fun <T> tx(block: () -> T): T { db.beginTransaction(); try { val r = block(); db.setTransactionSuccessful(); return r } finally { db.endTransaction() } }
    fun scalarString(sql: String, vararg args: String): String? = db.rawQuery(sql, args).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
    fun scalarLong(sql: String, vararg args: String): Long = db.rawQuery(sql, args).use { check(it.moveToFirst()); it.getLong(0) }
    fun current(): String? = scalarString("SELECT current FROM authority WHERE id=1")
    fun revision(): Long = scalarLong("SELECT revision FROM authority WHERE id=1")
    fun phase(op: String): String? = scalarString("SELECT phase FROM operation WHERE id=?", op)
    fun currentCount(): Long = scalarLong("SELECT COUNT(*) FROM dataset WHERE state='CURRENT'")
    fun datasetState(uuid: String): String? = scalarString("SELECT state FROM dataset WHERE uuid=?", uuid)
    fun epoch(uuid: String): String = scalarString("SELECT epoch FROM dataset WHERE uuid=?", uuid)!!
    fun vaultOf(uuid: String): String = scalarString("SELECT vault FROM dataset WHERE uuid=?", uuid)!!
    fun setQuota(bytes: Long) = db.execSQL("UPDATE quota SET limit_bytes=? WHERE id=1", arrayOf(bytes))
    fun reserved(): Long = scalarLong("SELECT COALESCE(SUM(reservation),0) FROM operation WHERE phase IN('PREPARED','STAGED','SYNCED')")
    fun stagedRows(): Long = listOf("stage_record", "stage_version", "stage_link", "stage_dek").sumOf { scalarLong("SELECT COUNT(*) FROM $it") }
    fun records(dataset: String) = db.rawQuery("SELECT uuid,kind,int_id,state,body FROM record WHERE dataset=? ORDER BY uuid", arrayOf(dataset)).use { c ->
        generateSequence { if (c.moveToNext()) B3Record(c.getString(0), c.getString(1), c.getInt(2), c.getString(3), if (c.isNull(4)) null else c.getString(4)) else null }.toList() }
    fun versions(dataset: String) = db.rawQuery("SELECT uuid,document,seq,state,vault,object,dek,plain_hash,cipher_hash,cipher_bytes,corrects FROM version WHERE dataset=? ORDER BY uuid", arrayOf(dataset)).use { c ->
        generateSequence { if (c.moveToNext()) B3Version(c.getString(0), c.getString(1), c.getInt(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6),
            c.getBlob(7), c.getBlob(8), c.getLong(9), if (c.isNull(10)) null else c.getString(10)) else null }.toList() }
    fun links(dataset: String) = db.rawQuery("SELECT uuid,document,record,state FROM link WHERE dataset=? ORDER BY uuid", arrayOf(dataset)).use { c ->
        generateSequence { if (c.moveToNext()) B3Link(c.getString(0), c.getString(1), c.getString(2), c.getString(3)) else null }.toList() }
    fun dekWrap(dataset: String, handle: String): ByteArray? = db.rawQuery("SELECT wrap FROM dek WHERE dataset=? AND handle=?", arrayOf(dataset, handle)).use { if (it.moveToFirst()) it.getBlob(0) else null }
    fun objectFile(vault: String, obj: String) = File(objects, "$vault/$obj.cipher")

    /** A new CURRENT dataset (fresh epoch and logical crypto vault) for seeding a namespace. */
    fun newDataset(): String = tx {
        check(current() == null) { "Seeding needs an empty namespace" }
        val ds = UUID.randomUUID().toString()
        db.execSQL("INSERT INTO dataset(uuid,state,epoch,vault) VALUES(?,'CURRENT',?,?)", arrayOf(ds, UUID.randomUUID().toString(), UUID.randomUUID().toString()))
        db.execSQL("UPDATE authority SET current=?, revision=revision+1 WHERE id=1", arrayOf(ds)); ds
    }
    fun addRecord(dataset: String, kind: String, intId: Int, state: String, body: String?): String = UUID.randomUUID().toString().also {
        db.execSQL("INSERT INTO record VALUES(?,?,?,?,?,?)", arrayOf(it, dataset, kind, intId, state, body)) }
    fun addLink(dataset: String, document: String, record: String, state: String): String = UUID.randomUUID().toString().also {
        db.execSQL("INSERT INTO link VALUES(?,?,?,?,?)", arrayOf(it, dataset, document, record, state)) }
    /** Encrypts [plain] under a fresh per-version DEK (wrapped by this namespace's alias) into an immutable object. */
    fun addVersion(dataset: String, document: String, seq: Int, state: String, plain: ByteArray, corrects: String? = null): B3Version {
        val vault = vaultOf(dataset); val version = UUID.randomUUID().toString(); val obj = UUID.randomUUID().toString(); val handle = UUID.randomUUID().toString()
        val dek = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            val file = objectFile(vault, obj).also { check(it.parentFile!!.mkdirs() || it.parentFile!!.isDirectory) }
            FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { ch ->
                B3.streaming(dek).newEncryptingStream(java.nio.channels.Channels.newOutputStream(ch), B3.objectAad(vault, document, version, obj)).use { it.write(plain) }
            }
            FileChannel.open(file.toPath(), StandardOpenOption.WRITE).use { it.force(true) }; B3.fsyncDir(file.parentFile!!)
            val cipher = file.readBytes()
            val v = B3Version(version, document, seq, state, vault, obj, handle, B3.sha256(plain), B3.sha256(cipher), cipher.size.toLong(), corrects)
            tx {
                db.execSQL("INSERT INTO dek VALUES(?,?,?)", arrayOf(handle, dataset, wrap(dek, dekAad(dataset, handle))))
                db.execSQL("INSERT INTO version VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf(v.uuid, dataset, document, seq, state, vault, obj, handle, v.plainHash, v.cipherHash, v.cipherBytes, corrects))
            }
            return v
        } finally { dek.fill(0) }
    }
    /** Full authenticated decryption of one version through this namespace's own DEK wrap. */
    fun decrypt(dataset: String, v: B3Version): ByteArray {
        val dek = unwrap(dekWrap(dataset, v.dek) ?: error("No DEK"), dekAad(dataset, v.dek))
        try {
            return B3.streaming(dek).newDecryptingStream(FileInputStream(objectFile(v.vault, v.obj)), B3.objectAad(v.vault, v.document, v.uuid, v.obj)).use { it.readBytes() }
        } finally { dek.fill(0) }
    }
    /** Physical cleanup of an object. Refused while a backup lease pins it or while any version row references it. */
    @Synchronized fun gcObject(vault: String, obj: String): Boolean {
        if (obj in leases) return false
        if (scalarLong("SELECT COUNT(*) FROM version WHERE object=?", obj) > 0) return false
        return objectFile(vault, obj).delete()
    }
    @Synchronized internal fun lease(ids: Collection<String>) { leases += ids }
    @Synchronized internal fun release(ids: Collection<String>) { leases -= ids.toSet() }
    /** Second, independent SQLCipher connection to the same file (a concurrent writer). */
    fun secondConnection(): SQLiteDatabase {
        val pass = unwrap(File(root, "db.wrap").readBytes(), "B3-TEST-DBKEY|$name".toByteArray())
        return try { SQLiteDatabase.openOrCreateDatabase(dbFile, pass, null, null).also { it.enableWriteAheadLogging() } } finally { pass.fill(0) }
    }
    override fun close() = db.close()
    /** Removes the fixture and its Keystore alias ("the source phone is gone"). */
    fun destroy() { runCatching { db.close() }; root.deleteRecursively(); runCatching { keys().deleteEntry(kekAlias) } }
    fun aliasExists() = keys().containsAlias(kekAlias)
}

/** Frame types inside the authenticated body stream. */
internal object B3Frame {
    const val META = 1; const val RECORDS = 2; const val VERSIONS = 3; const val LINKS = 4; const val DEK = 5; const val OBJECT = 6; const val MANIFEST = 9
    data class F(val type: Int, val payload: ByteArray) { fun encoded(): ByteArray = ByteArrayOutputStream().also { DataOutputStream(it).run { writeByte(type); writeInt(payload.size); write(payload) } }.toByteArray() }
    fun bytes(write: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { DataOutputStream(it).write() }.toByteArray()
    fun DataOutputStream.str(s: String?) { writeBoolean(s != null); if (s != null) writeUTF(s) }
    fun DataOutputStream.blob(b: ByteArray) { writeInt(b.size); write(b) }
    fun DataInputStream.str(): String? = if (readBoolean()) readUTF() else null
    fun DataInputStream.blob(max: Int = B3.MAX_FRAME): ByteArray { val n = readInt(); if (n < 0 || n > max) B3.reject(B3.Reject.MALFORMED_FRAME, "blob $n"); return ByteArray(n).also { readFully(it) } }
}

/** What an export captured inside one exclusive read gate: a consistent snapshot of rows plus the immutable objects
 * they reference, which stay pinned by a backup lease until the bundle is closed. */
internal class B3Snapshot(val dataset: String, val epoch: String, val vault: String, val records: List<B3Record>, val versions: List<B3Version>,
                          val links: List<B3Link>, val deks: Map<String, ByteArray>)

internal object B3Exporter {
    /**
     * Writes a TEST_ONLY complete bundle of [source]'s CURRENT dataset to [out]. [duringCapture] runs while the snapshot
     * gate is held; [afterCapture] runs after the gate is released but before any object is read; [beforeManifest] and
     * [afterManifest] let tests tamper frames (a malicious or buggy writer). [capability] models lock / cancel / space
     * switch: once revoked no further frame is written and the partial file is deleted. Success means: final manifest
     * written, stream closed, file fsynced.
     */
    fun export(source: B3Vault, password: CharArray, out: File, capability: AttachmentSession.Capability, profileId: Int = B3.TEST_FAST,
               duringCapture: () -> Unit = {}, afterCapture: () -> Unit = {},
               beforeManifest: (MutableList<B3Frame.F>) -> Unit = {}, afterManifest: (MutableList<B3Frame.F>) -> Unit = {}): ByteArray {
        check(!out.exists())
        val snap = capture(source, duringCapture)
        val pinned = snap.versions.map { it.obj }
        source.lease(pinned)
        try {
            afterCapture()
            B3.alive(capability)
            val frames = frames(source, snap, capability)
            beforeManifest(frames)
            val archive = UUID.randomUUID()
            val manifest = manifest(archive, snap, frames)
            frames += manifest; afterManifest(frames)
            val digest = B3.sha256(manifest.payload)
            write(out, password, archive, profileId, frames, capability)
            return digest
        } catch (e: Throwable) { out.delete(); throw e }
        finally { source.release(pinned); snap.deks.values.forEach { it.fill(0) } }
    }

    /** Exclusive gate: no writer can commit between the first and last row read. */
    private fun capture(v: B3Vault, duringCapture: () -> Unit): B3Snapshot {
        v.db.beginTransaction()
        try {
            val ds = v.current() ?: error("No CURRENT dataset")
            val records = v.records(ds); duringCapture()
            val versions = v.versions(ds); val links = v.links(ds)
            val deks = versions.associate { it.dek to v.unwrap(v.dekWrap(ds, it.dek)!!, v.dekAad(ds, it.dek)) }
            val snap = B3Snapshot(ds, v.epoch(ds), v.vaultOf(ds), records, versions, links, deks)
            v.db.setTransactionSuccessful(); return snap
        } finally { v.db.endTransaction() }
    }

    private fun frames(v: B3Vault, s: B3Snapshot, capability: AttachmentSession.Capability): MutableList<B3Frame.F> = with(B3Frame) {
        val out = mutableListOf<F>()
        out += F(META, bytes { writeUTF(s.dataset); writeUTF(s.epoch); writeUTF(s.vault) })
        out += F(RECORDS, bytes { writeInt(s.records.size); s.records.forEach { r -> writeUTF(r.uuid); writeUTF(r.kind); writeInt(r.intId); writeUTF(r.state); str(r.body) } })
        out += F(VERSIONS, bytes { writeInt(s.versions.size); s.versions.forEach { x -> writeUTF(x.uuid); writeUTF(x.document); writeInt(x.seq); writeUTF(x.state); writeUTF(x.vault); writeUTF(x.obj); writeUTF(x.dek); blob(x.plainHash); blob(x.cipherHash); writeLong(x.cipherBytes); str(x.corrects) } })
        out += F(LINKS, bytes { writeInt(s.links.size); s.links.forEach { l -> writeUTF(l.uuid); writeUTF(l.document); writeUTF(l.record); writeUTF(l.state) } })
        s.deks.forEach { (handle, dek) -> out += F(DEK, bytes { writeUTF(handle); blob(dek) }) }
        s.versions.forEach { x -> B3.alive(capability)
            val cipher = FileInputStream(v.objectFile(x.vault, x.obj)).use { it.readBytes() }
            if (!MessageDigest.isEqual(B3.sha256(cipher), x.cipherHash)) error("Source object changed under lease")
            out += F(OBJECT, bytes { writeUTF(x.uuid); writeUTF(x.obj); blob(cipher) })
        }
        out
    }

    fun manifest(archive: UUID, s: B3Snapshot, frames: List<B3Frame.F>): B3Frame.F = with(B3Frame) {
        val transcript = MessageDigest.getInstance("SHA-256").also { d -> frames.forEach { d.update(it.encoded()) } }.digest()
        val objects = frames.filter { it.type == OBJECT }.map { DataInputStream(ByteArrayInputStream(it.payload)).run { val ver = readUTF(); readUTF(); ver to B3.sha256(blob()) } }
        F(MANIFEST, bytes {
            writeUTF(archive.toString()); writeUTF(s.dataset); writeInt(frames.size)
            writeInt(frames.count { it.type == DEK }); writeInt(objects.size); objects.forEach { (ver, h) -> writeUTF(ver); blob(h) }
            blob(transcript)
        })
    }

    private fun write(out: File, password: CharArray, archive: UUID, profileId: Int, frames: List<B3Frame.F>, capability: AttachmentSession.Capability) {
        val profile = B3.PROFILES.getValue(profileId)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes); val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val master = ByteArray(32).also(SecureRandom()::nextBytes)
        val unsigned = ByteBuffer.allocate(B3.BOOTSTRAP_UNSIGNED).put(B3.MAGIC).putShort(B3.PROTOCOL.toShort()).putShort(profileId.toShort())
            .put(salt).put(B3.uuidBytes(archive)).put(nonce).array()
        val kek = B3.argon2id(password, salt, profile)
        try {
            val wrapped = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, nonce)); updateAAD("B3-TEST-WRAP".toByteArray() + unsigned); doFinal(master) }
            val bootstrap = unsigned + wrapped
            val body = B3.hkdf(master, B3.uuidBytes(archive), "B3-TEST-BODY")
            FileChannel.open(out.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { ch ->
                val os = java.nio.channels.Channels.newOutputStream(ch)
                os.write(bootstrap)
                B3.streaming(body).newEncryptingStream(object : FilterOutputStream(os) {
                    override fun write(b: ByteArray, off: Int, len: Int) = os.write(b, off, len); override fun close() = flush() }, B3.sha256(bootstrap)).use { s ->
                    frames.forEach { f -> B3.alive(capability); s.write(f.encoded()) }
                }
                ch.force(true)
            }
            B3.fsyncDir(out.parentFile!!); body.fill(0)
        } finally { kek.fill(0); master.fill(0) }
    }
}

internal object B3Restorer {
    private class Parsed(val meta: Triple<String, String, String>, val records: List<B3Record>, val versions: List<B3Version>, val links: List<B3Link>,
                         val deks: Map<String, ByteArray>, val objects: Map<String, File>, val manifestDigest: ByteArray, val archive: String)

    /**
     * Restores the bundle into [dest] as its new single CURRENT dataset, replacing (never merging with) what is there.
     * Order: invariants → bootstrap and KDF allowlist → resources → password → quota reservation → stage and
     * authenticate every frame and object → fsync → publish owned files → one SQL transaction with CAS on the expected
     * CURRENT and revision → receipt → cleanup. Any rejection before the commit leaves the old CURRENT, its rows and files
     * untouched and aborts only what this operation owns. [operation] makes a retry after a lost ACK idempotent.
     */
    fun restore(dest: B3Vault, bundle: File, password: CharArray, operation: String = UUID.randomUUID().toString(),
                capability: AttachmentSession.Capability, seam: (B3Vault.Seam) -> Unit = {}): B3Receipt {
        // Retry of a known operation: never a second dataset or a second reservation.
        dest.db.rawQuery("SELECT archive,digest,phase,receipt FROM operation WHERE id=?", arrayOf(operation)).use { c ->
            if (c.moveToFirst()) {
                val phase = c.getString(2)
                if (phase !in setOf("COMMITTED", "COMMITTED_CLEANUP_PENDING", "COMPLETE")) B3.reject(B3.Reject.INVARIANT, "operation $operation is $phase; reconcile first")
                val archive = readArchive(bundle)
                if (archive != c.getString(0)) B3.reject(B3.Reject.ARCHIVE_MISMATCH, "operation bound to another archive")
                val pending = phase != "COMPLETE" && !cleanup(dest, operation)
                return B3Receipt(operation, c.getString(3), archive, c.getBlob(1), pending)
            }
        }
        if (dest.currentCount() > 1) B3.reject(B3.Reject.INVARIANT, "two CURRENT datasets")
        val expected = dest.current(); val expectedRevision = dest.revision()
        if (expected != null && dest.datasetState(expected) != "CURRENT") B3.reject(B3.Reject.INVARIANT, "authority points at a non-CURRENT dataset")

        val (bootstrap, profile) = bootstrap(bundle)
        val budget = Runtime.getRuntime().maxMemory() / 4
        if (profile.memoryKiB.toLong() * 1024 > budget) B3.reject(B3.Reject.RESOURCES, "KDF needs ${profile.memoryKiB} KiB, budget ${budget / 1024} KiB")
        val master = unwrapMaster(bootstrap, password, profile)
        val archive = UUID(ByteBuffer.wrap(bootstrap, B3.MAGIC.size + 4 + 16, 16).long, ByteBuffer.wrap(bootstrap, B3.MAGIC.size + 4 + 24, 8).long).toString()

        val reservation = bundle.length()
        val used = dest.scalarLong("SELECT COALESCE(SUM(cipher_bytes),0) FROM version") + dest.reserved()
        val limit = dest.scalarLong("SELECT limit_bytes FROM quota WHERE id=1")
        if (used + reservation > limit) { master.fill(0); B3.reject(B3.Reject.QUOTA, "need $reservation, used $used of $limit") }
        dest.db.execSQL("INSERT INTO operation(id,archive,phase,expected,expected_revision,reservation) VALUES(?,?,'PREPARED',?,?,?)",
            arrayOf(operation, archive, expected, expectedRevision, reservation))
        val staging = File(dest.root, "staging/$operation").also { check(it.mkdirs()) }
        var parsed: Parsed? = null
        try {
            parsed = parse(dest, operation, bundle, bootstrap, master, staging, capability)
            authenticate(dest, operation, parsed, staging, capability)
            dest.db.execSQL("UPDATE operation SET phase='STAGED', digest=? WHERE id=?", arrayOf(parsed.manifestDigest, operation))
            seam(B3Vault.Seam.AFTER_STAGE)
            publish(dest, operation, parsed)
            seam(B3Vault.Seam.BEFORE_COMMIT)
            B3.alive(capability)
            commit(dest, operation, parsed, expected, expectedRevision, seam)
        } catch (e: B3.SimulatedProcessDeath) { throw e }
        catch (e: B3.Rejected) { abort(dest, operation); throw e }
        catch (e: Throwable) { if (dest.phase(operation) != "COMMITTED") abort(dest, operation); throw e }
        finally { master.fill(0); parsed?.deks?.values?.forEach { it.fill(0) } }
        seam(B3Vault.Seam.AFTER_COMMIT)
        val pending = !cleanup(dest, operation, seam)
        return B3Receipt(operation, dest.scalarString("SELECT receipt FROM operation WHERE id=?", operation)!!, archive, parsed!!.manifestDigest, pending)
    }

    private fun readArchive(bundle: File): String { val b = bootstrap(bundle).first
        return UUID(ByteBuffer.wrap(b, B3.MAGIC.size + 4 + 16, 8).long, ByteBuffer.wrap(b, B3.MAGIC.size + 4 + 24, 8).long).toString() }

    /** Fixed-size bootstrap; magic, protocol and KDF profile are checked before any key derivation is attempted. */
    private fun bootstrap(bundle: File): Pair<ByteArray, B3.Profile> {
        val b = ByteArray(B3.BOOTSTRAP)
        FileChannel.open(bundle.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { ch ->
            if (ch.size() < B3.BOOTSTRAP) B3.reject(B3.Reject.BOOTSTRAP_TRUNCATED, "${ch.size()} bytes")
            val buf = ByteBuffer.wrap(b); while (buf.hasRemaining()) if (ch.read(buf) < 0) B3.reject(B3.Reject.BOOTSTRAP_TRUNCATED, "short read")
        }
        if (!MessageDigest.isEqual(b.copyOfRange(0, B3.MAGIC.size), B3.MAGIC)) B3.reject(B3.Reject.BAD_MAGIC, "not a B3 test bundle")
        val bb = ByteBuffer.wrap(b, B3.MAGIC.size, 4)
        val protocol = bb.short.toInt() and 0xffff; val profileId = bb.short.toInt() and 0xffff
        if (protocol != B3.PROTOCOL) B3.reject(B3.Reject.UNSUPPORTED_PROTOCOL, "protocol $protocol")
        val profile = B3.PROFILES[profileId] ?: B3.reject(B3.Reject.UNSUPPORTED_KDF, "profile 0x${profileId.toString(16)}")
        return b to profile
    }

    private fun unwrapMaster(bootstrap: ByteArray, password: CharArray, profile: B3.Profile): ByteArray {
        val unsigned = bootstrap.copyOfRange(0, B3.BOOTSTRAP_UNSIGNED)
        val salt = bootstrap.copyOfRange(B3.MAGIC.size + 4, B3.MAGIC.size + 20); val nonce = bootstrap.copyOfRange(B3.BOOTSTRAP_UNSIGNED - 12, B3.BOOTSTRAP_UNSIGNED)
        val kek = B3.argon2id(password, salt, profile)
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, nonce)); updateAAD("B3-TEST-WRAP".toByteArray() + unsigned)
                doFinal(bootstrap.copyOfRange(B3.BOOTSTRAP_UNSIGNED, B3.BOOTSTRAP)) }
        } catch (e: AEADBadTagException) { B3.reject(B3.Reject.WRONG_PASSWORD_OR_HEADER, "master key did not authenticate", e) }
        finally { kek.fill(0) }
    }

    private fun journal(dest: B3Vault, operation: String, file: File) =
        dest.db.execSQL("UPDATE operation SET files=files||? WHERE id=?", arrayOf(file.path + "\n", operation))

    /** Streams and authenticates the body; strict frame grammar, limits and EOF. Objects go to owned staging files. */
    private fun parse(dest: B3Vault, op: String, bundle: File, bootstrap: ByteArray, master: ByteArray, staging: File, capability: AttachmentSession.Capability): Parsed = with(B3Frame) {
        val body = B3.hkdf(master, bootstrap.copyOfRange(B3.MAGIC.size + 20, B3.MAGIC.size + 36), "B3-TEST-BODY")
        var meta: Triple<String, String, String>? = null; var records: List<B3Record>? = null; var versions: List<B3Version>? = null; var links: List<B3Link>? = null
        val deks = LinkedHashMap<String, ByteArray>(); val objects = LinkedHashMap<String, File>(); val hashes = HashMap<String, ByteArray>()
        val transcript = MessageDigest.getInstance("SHA-256"); var count = 0; var manifest: ByteArray? = null
        try {
            FileInputStream(bundle).use { fin ->
                fin.channel.position(B3.BOOTSTRAP.toLong())
                val input = DataInputStream(BufferedInputStream(B3.streaming(body).newDecryptingStream(fin, B3.sha256(bootstrap))))
                while (true) {
                    B3.alive(capability)
                    val type = input.read()
                    if (type < 0) break
                    if (manifest != null) B3.reject(B3.Reject.TRAILING_AFTER_MANIFEST, "frame type $type after manifest")
                    if (++count > B3.MAX_FRAMES) B3.reject(B3.Reject.MALFORMED_FRAME, "too many frames")
                    val len = input.readInt(); if (len < 0 || len > B3.MAX_FRAME) B3.reject(B3.Reject.MALFORMED_FRAME, "frame length $len")
                    val payload = ByteArray(len).also { input.readFully(it) }
                    val f = F(type, payload)
                    val d = DataInputStream(ByteArrayInputStream(payload))
                    when (type) {
                        MANIFEST -> { manifest = payload; continue }
                        META -> { if (meta != null) B3.reject(B3.Reject.DUPLICATE_ENTRY, "META"); meta = Triple(d.readUTF(), d.readUTF(), d.readUTF()) }
                        RECORDS -> { if (records != null) B3.reject(B3.Reject.DUPLICATE_ENTRY, "RECORDS"); records = List(d.readInt()) { B3Record(d.readUTF(), d.readUTF(), d.readInt(), d.readUTF(), d.str()) } }
                        VERSIONS -> { if (versions != null) B3.reject(B3.Reject.DUPLICATE_ENTRY, "VERSIONS")
                            versions = List(d.readInt()) { B3Version(d.readUTF(), d.readUTF(), d.readInt(), d.readUTF(), d.readUTF(), d.readUTF(), d.readUTF(), d.blob(), d.blob(), d.readLong(), d.str()) } }
                        LINKS -> { if (links != null) B3.reject(B3.Reject.DUPLICATE_ENTRY, "LINKS"); links = List(d.readInt()) { B3Link(d.readUTF(), d.readUTF(), d.readUTF(), d.readUTF()) } }
                        DEK -> { val h = d.readUTF(); val k = d.blob(64); if (h in deks) B3.reject(B3.Reject.DUPLICATE_ENTRY, "DEK $h"); deks[h] = k }
                        OBJECT -> { val ver = d.readUTF(); val obj = d.readUTF(); val cipher = d.blob()
                            if (ver in objects) B3.reject(B3.Reject.DUPLICATE_ENTRY, "OBJECT $ver")
                            val file = File(staging, "$obj.cipher"); journal(dest, op, file)
                            FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { it.write(ByteBuffer.wrap(cipher)) }
                            objects[ver] = file; hashes[ver] = B3.sha256(cipher) }
                        else -> B3.reject(B3.Reject.MALFORMED_FRAME, "unknown frame type $type")
                    }
                    transcript.update(f.encoded())
                }
            }
        } catch (e: B3.Rejected) { throw e }
        catch (e: EOFException) { B3.reject(B3.Reject.MALFORMED_FRAME, "frame cut inside the authenticated body", e) }
        catch (e: IOException) { B3.reject(B3.Reject.STREAM_AUTH, "body did not authenticate (truncation, trailing bytes or tampering)", e) }
        finally { body.fill(0) }
        val m = manifest ?: B3.reject(B3.Reject.MANIFEST_MISSING, "stream ended without a final manifest")
        val md = DataInputStream(ByteArrayInputStream(m))
        val archive = md.readUTF(); val sourceDataset = md.readUTF(); val frameCount = md.readInt(); val dekCount = md.readInt()
        val objectHashes = List(md.readInt()) { md.readUTF() to md.blob() }; val expectedTranscript = md.blob()
        if (!B3.unsafeSkipManifestForRedTest) {
            if (!MessageDigest.isEqual(expectedTranscript, transcript.digest())) B3.reject(B3.Reject.MANIFEST_MISMATCH, "frame transcript differs (reorder, removal or substitution)")
            if (frameCount != count - 1 || dekCount != deks.size || objectHashes.size != objects.size) B3.reject(B3.Reject.MANIFEST_MISMATCH, "counts differ")
            objectHashes.forEach { (ver, h) -> if (!MessageDigest.isEqual(h, hashes[ver] ?: B3.reject(B3.Reject.OBJECT_MISSING, "manifest object $ver absent"))) B3.reject(B3.Reject.MANIFEST_MISMATCH, "object $ver") }
            if (meta?.first != sourceDataset) B3.reject(B3.Reject.MANIFEST_MISMATCH, "source dataset")
        }
        return Parsed(meta ?: B3.reject(B3.Reject.MALFORMED_FRAME, "META"), records ?: B3.reject(B3.Reject.MALFORMED_FRAME, "RECORDS"),
            versions ?: B3.reject(B3.Reject.MALFORMED_FRAME, "VERSIONS"), links ?: B3.reject(B3.Reject.MALFORMED_FRAME, "LINKS"), deks, objects, B3.sha256(m), archive)
    }

    /** Every object, key and graph edge must verify before anything is published. Results go to encrypted staging rows. */
    private fun authenticate(dest: B3Vault, op: String, p: Parsed, staging: File, capability: AttachmentSession.Capability) {
        val vault = p.meta.third
        p.versions.forEach { v -> B3.alive(capability)
            val file = p.objects[v.uuid] ?: B3.reject(B3.Reject.OBJECT_MISSING, "version ${v.uuid} (${v.state}) has no object")
            if (v.vault != vault) B3.reject(B3.Reject.GRAPH_INVALID, "version ${v.uuid} in foreign crypto vault")
            val cipher = file.readBytes()
            if (cipher.size.toLong() != v.cipherBytes || !MessageDigest.isEqual(B3.sha256(cipher), v.cipherHash)) B3.reject(B3.Reject.OBJECT_HASH, "ciphertext of ${v.uuid}")
            val dek = p.deks[v.dek] ?: B3.reject(B3.Reject.DEK_MISSING, "no portable key for ${v.uuid}")
            if (dek.size != 32) B3.reject(B3.Reject.DEK_MALFORMED, "key ${v.dek} is ${dek.size} bytes")
            val plain = try { B3.streaming(dek).newDecryptingStream(ByteArrayInputStream(cipher), B3.objectAad(v.vault, v.document, v.uuid, v.obj)).use { it.readBytes() } }
                catch (e: IOException) { B3.reject(B3.Reject.OBJECT_AUTH, "object ${v.uuid} does not authenticate under its own identity", e) }
                catch (e: java.security.GeneralSecurityException) { B3.reject(B3.Reject.OBJECT_AUTH, "object ${v.uuid}", e) }
            try { if (!MessageDigest.isEqual(B3.sha256(plain), v.plainHash)) B3.reject(B3.Reject.OBJECT_HASH, "plaintext of ${v.uuid}") } finally { plain.fill(0) }
        }
        if (p.objects.keys != p.versions.map { it.uuid }.toSet()) B3.reject(B3.Reject.GRAPH_INVALID, "objects without a version row")
        val records = p.records.associateBy { it.uuid }; val byDoc = p.versions.groupBy { it.document }
        byDoc.forEach { (doc, vs) -> if (vs.count { it.state == "CURRENT" } > 1) B3.reject(B3.Reject.GRAPH_INVALID, "document $doc has two CURRENT versions")
            vs.forEach { v -> if (v.corrects != null && vs.none { it.uuid == v.corrects }) B3.reject(B3.Reject.GRAPH_INVALID, "correction target missing") } }
        p.records.forEach { if (it.state == "PURGED" && it.body != null) B3.reject(B3.Reject.GRAPH_INVALID, "purged record keeps content") }
        p.links.forEach { l ->
            val r = records[l.record] ?: B3.reject(B3.Reject.GRAPH_INVALID, "link ${l.uuid} to unknown record")
            val vs = byDoc[l.document] ?: B3.reject(B3.Reject.GRAPH_INVALID, "link ${l.uuid} to unknown document")
            val ok = when (l.state) {
                "LIVE" -> r.state == "LIVE" && vs.any { it.state == "CURRENT" }
                "TRASHED" -> r.state == "TRASHED" || vs.all { it.state == "TRASHED" }
                "PURGED" -> r.state == "PURGED"
                "QUARANTINED" -> true
                else -> false
            }
            if (!ok) B3.reject(B3.Reject.GRAPH_INVALID, "link ${l.uuid} ${l.state} inconsistent with record ${r.state}")
        }
        // Same crypto vault / version identity already present here: refuse rather than rebind or overwrite.
        p.versions.forEach { v -> if (dest.scalarLong("SELECT COUNT(*) FROM version WHERE uuid=? OR object=?", v.uuid, v.obj) > 0) B3.reject(B3.Reject.INVARIANT, "version ${v.uuid} already exists here") }
        // Stage graph rows (SQLCipher, encrypted at rest) and re-wrap every DEK under THIS namespace's alias only.
        dest.tx {
            p.records.forEach { r -> dest.db.execSQL("INSERT INTO stage_record VALUES(?,?,?,?,?,?)", arrayOf(op, r.uuid, r.kind, r.intId, r.state, r.body)) }
            p.versions.forEach { v -> dest.db.execSQL("INSERT INTO stage_version VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf(op, v.uuid, v.document, v.seq, v.state, v.vault, v.obj, v.dek, v.plainHash, v.cipherHash, v.cipherBytes, v.corrects)) }
            p.links.forEach { l -> dest.db.execSQL("INSERT INTO stage_link VALUES(?,?,?,?,?)", arrayOf(op, l.uuid, l.document, l.record, l.state)) }
            p.deks.forEach { (h, k) -> dest.db.execSQL("INSERT INTO stage_dek VALUES(?,?,?)", arrayOf(op, h, dest.wrap(k, dest.dekAad("op:$op", h)))) }
        }
        p.objects.values.forEach { f -> FileChannel.open(f.toPath(), StandardOpenOption.WRITE).use { it.force(true) } }
        B3.fsyncDir(staging)
    }

    /** Moves owned, verified objects to their final names (never replacing). Not referenced by SQL until the commit. */
    private fun publish(dest: B3Vault, op: String, p: Parsed) {
        val dir = File(dest.objects, p.meta.third).also { check(it.mkdirs() || it.isDirectory) }
        p.versions.forEach { v ->
            val target = File(dir, "${v.obj}.cipher")
            // rename(2) would replace silently; an existing name is someone else's object. (Single owned writer: no race.)
            if (target.exists()) B3.reject(B3.Reject.INVARIANT, "object name ${v.obj} already taken")
            journal(dest, op, target)
            Files.move(p.objects.getValue(v.uuid).toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }
        B3.fsyncDir(dir)
        dest.db.execSQL("UPDATE operation SET phase='SYNCED' WHERE id=?", arrayOf(op))
    }

    /** The single authority switch. CAS on the CURRENT pointer and revision read at the start. */
    private fun commit(dest: B3Vault, op: String, p: Parsed, expected: String?, expectedRevision: Long, seam: (B3Vault.Seam) -> Unit) = dest.tx {
        if (!B3.unsafeSkipCasForRedTest && (dest.current() != expected || dest.revision() != expectedRevision))
            B3.reject(B3.Reject.CAS_STALE, "CURRENT changed since the restore started")
        val fresh = UUID.randomUUID().toString()
        if (expected != null) {
            // Old dataset: RETIRED, kept intact as a traceable fallback; its LIVE links can never point into the new dataset.
            dest.db.execSQL("UPDATE dataset SET state='RETIRED', retired_by=? WHERE uuid=?", arrayOf(op, expected))
            dest.db.execSQL("UPDATE link SET state='QUARANTINED' WHERE dataset=? AND state='LIVE'", arrayOf(expected))
        }
        dest.db.execSQL("INSERT INTO dataset(uuid,state,epoch,vault,source_dataset,operation) VALUES(?,'CURRENT',?,?,?,?)",
            arrayOf(fresh, UUID.randomUUID().toString(), p.meta.third, p.meta.first, op))
        dest.db.execSQL("INSERT INTO record SELECT uuid,?,kind,int_id,state,body FROM stage_record WHERE op=?", arrayOf(fresh, op))
        dest.db.execSQL("INSERT INTO version SELECT uuid,?,document,seq,state,vault,object,dek,plain_hash,cipher_hash,cipher_bytes,corrects FROM stage_version WHERE op=?", arrayOf(fresh, op))
        dest.db.execSQL("INSERT INTO link SELECT uuid,?,document,record,state FROM stage_link WHERE op=?", arrayOf(fresh, op))
        // Final per-dataset wraps (distinct role/AAD from the operation-scoped staging wrap).
        dest.db.rawQuery("SELECT handle,wrap FROM stage_dek WHERE op=?", arrayOf(op)).use { c -> while (c.moveToNext()) {
            val k = dest.unwrap(c.getBlob(1), dest.dekAad("op:$op", c.getString(0)))
            try { dest.db.execSQL("INSERT INTO dek VALUES(?,?,?)", arrayOf(c.getString(0), fresh, dest.wrap(k, dest.dekAad(fresh, c.getString(0))))) } finally { k.fill(0) }
        } }
        dest.db.execSQL("UPDATE authority SET current=?, revision=revision+1 WHERE id=1", arrayOf(fresh))
        dest.db.execSQL("UPDATE operation SET phase='COMMITTED', receipt=? WHERE id=?", arrayOf(fresh, op))
        seam(B3Vault.Seam.IN_COMMIT)
    }

    /** Removes staging rows and the staging directory. A failure keeps the journal for retry; never claims cleanup done. */
    fun cleanup(dest: B3Vault, op: String, seam: (B3Vault.Seam) -> Unit = {}): Boolean = try {
        seam(B3Vault.Seam.CLEANUP)
        dest.tx { listOf("stage_record", "stage_version", "stage_link", "stage_dek").forEach { dest.db.execSQL("DELETE FROM $it WHERE op=?", arrayOf(op)) }
            dest.db.execSQL("UPDATE operation SET phase='COMPLETE' WHERE id=?", arrayOf(op)) }
        File(dest.root, "staging/$op").deleteRecursively(); true
    } catch (e: IOException) { dest.db.execSQL("UPDATE operation SET phase='COMMITTED_CLEANUP_PENDING' WHERE id=?", arrayOf(op)); false }

    /** Undoes only what [op] owns: journaled files that no committed version references, staging rows, the reservation. */
    fun abort(dest: B3Vault, op: String) {
        val files = dest.scalarString("SELECT files FROM operation WHERE id=?", op).orEmpty().lines().filter { it.isNotBlank() }
        files.forEach { path -> val f = File(path)
            val referenced = dest.scalarLong("SELECT COUNT(*) FROM version WHERE object=?", f.name.removeSuffix(".cipher")) > 0
            if (!referenced && f.canonicalPath.startsWith(dest.root.canonicalPath + File.separator)) f.delete() }
        File(dest.root, "staging/$op").deleteRecursively()
        dest.tx { listOf("stage_record", "stage_version", "stage_link", "stage_dek").forEach { dest.db.execSQL("DELETE FROM $it WHERE op=?", arrayOf(op)) }
            dest.db.execSQL("UPDATE operation SET phase='ABORTED' WHERE id=?", arrayOf(op)) }
    }

    /** After a process death: operations that never committed are aborted; committed ones finish cleanup. */
    fun reconcile(dest: B3Vault): Map<String, String> {
        val ops = dest.db.rawQuery("SELECT id,phase FROM operation WHERE phase NOT IN('ABORTED','COMPLETE')", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) to c.getString(1) else null }.toList() }
        return ops.associate { (id, phase) ->
            id to when (phase) {
                "COMMITTED", "COMMITTED_CLEANUP_PENDING" -> if (cleanup(dest, id)) "COMPLETE" else "COMMITTED_CLEANUP_PENDING"
                else -> { abort(dest, id); "ABORTED" }
            }
        }
    }
}
