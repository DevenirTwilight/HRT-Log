package net.plainnotes.app.data

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class DataLockedException : IllegalStateException("Credential storage unavailable")
class KeyRecoveryRequired(cause: Throwable? = null) : IllegalStateException("Encrypted data requires recovery",cause)

/**
 * Independent encrypted data spaces. DECOY is the empty space opened by the disguise mode's decoy code
 * (full flavor): its own file, wrapped key and Keystore alias, so nothing in it refers to PRIMARY.
 */
enum class Space(val file:String,val wrap:String,val alias:String) {
    PRIMARY("notes.db","key.wrap","notes.wrap"), DECOY("notes_b.db","key_b.wrap","notes_b.wrap")
}

@Singleton open class DatabaseAccess @Inject constructor(@ApplicationContext private val context: Context) {
    private val instances=mutableMapOf<Space,NotesDatabase>()
    /** The space the UI works on. Background reminders always use PRIMARY. Not persisted: a new process starts in PRIMARY. */
    @Volatile var current=Space.PRIMARY
        private set
    @Synchronized fun select(space:Space) {
        if(space==current)return
        if(current!=Space.PRIMARY)instances.remove(current)?.close()
        current=space
    }
    fun get(): NotesDatabase=get(current)
    @Synchronized open fun get(space:Space): NotesDatabase {
        if(!context.getSystemService(UserManager::class.java).isUserUnlocked) throw DataLockedException()
        instances[space]?.let { return it }
        val passphrase=loadPassphrase(space)
        try {
            System.loadLibrary("sqlcipher")
            val db=Room.databaseBuilder(context,NotesDatabase::class.java,space.file)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addCallback(SchemaGuards).build()
            try { db.openHelper.writableDatabase } catch(e:Exception) { db.close(); throw KeyRecoveryRequired(e) }
            instances[space]=db;return db
        } finally { passphrase.fill(0) }
    }
    /** Closes and deletes one space's database, wrapped key and Keystore alias ("delete all data"). */
    @Synchronized fun destroy(space:Space=current) {
        instances.remove(space)?.close()
        listOf("","-wal","-shm","-journal").forEach { context.getDatabasePath(space.file+it).delete() }
        File(context.noBackupFilesDir,space.wrap).delete(); File(context.noBackupFilesDir,space.wrap+".bak").delete()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(space.alias) }
    }
    private fun loadPassphrase(space:Space): ByteArray {
        val atomic=AtomicFile(File(context.noBackupFilesDir,space.wrap))
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val exists=atomic.baseFile.exists() || File(atomic.baseFile.path+".bak").exists()
        if(exists) {
            try {
                val key=store.getKey(space.alias,null) as? SecretKey ?: throw KeyRecoveryRequired()
                val bytes=atomic.readFully();require(bytes.size>=12+16)
                val cipher=Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)))
                return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).also { require(it.size==32) }
            } catch(e:Exception) { throw KeyRecoveryRequired(e) }
        }
        if(context.getDatabasePath(space.file).exists()) throw KeyRecoveryRequired()
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(space.alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(false).build())
        val key=generator.generateKey();val raw=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key)
        val output=atomic.startWrite()
        try { output.write(cipher.iv+cipher.doFinal(raw));atomic.finishWrite(output) }
        catch(e:Exception) { atomic.failWrite(output);raw.fill(0);throw KeyRecoveryRequired(e) }
        return raw
    }
}
