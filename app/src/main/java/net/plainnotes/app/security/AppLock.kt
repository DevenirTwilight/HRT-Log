package net.plainnotes.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App-level access control only (the database key is independent, docs/PLAN.md 2.4).
 * The PIN is stored as an Argon2id hash, inside a file encrypted with a non-exportable Keystore key,
 * so it cannot be brute-forced offline from a copied data directory. Repeated failures add a growing delay.
 */
class AppLock(private val context: Context, private val file: String = "lock.bin", private val alias: String = "notes.lock") {
    private val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val store get() = AtomicFile(File(context.noBackupFilesDir, file))

    val enabled get() = store.baseFile.exists()
    var biometric: Boolean
        get() = prefs.getBoolean("lock_biometric", false)
        set(v) { prefs.edit().putBoolean("lock_biometric", v).apply() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }

    companion object {
        fun hash(pin: String, salt: ByteArray): ByteArray {
            val gen = Argon2BytesGenerator()
            gen.init(Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withSalt(salt).withMemoryAsKB(19 * 1024).withIterations(2).withParallelism(1).build())
            return ByteArray(32).also { gen.generateBytes(pin.toCharArray(), it) }
        }
        fun validPin(pin: String) = pin.length in 4..12 && pin.all { it.isDigit() }
    }

    fun setPin(pin: String) {
        require(validPin(pin))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val plain = salt + hash(pin, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        val out = store.startWrite()
        try { out.write(c.iv + c.doFinal(plain)); store.finishWrite(out) } catch (e: Exception) { store.failWrite(out); throw e }
        prefs.edit().putInt("lock_failures", 0).putLong("lock_until", 0).apply()
    }

    fun disable() { store.delete(); biometric = false; runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) } }

    /** Milliseconds until another attempt is allowed (0 = now). */
    fun waitMillis(now: Long = System.currentTimeMillis()) = (prefs.getLong("lock_until", 0) - now).coerceAtLeast(0)

    fun verify(pin: String, now: Long = System.currentTimeMillis()): Boolean {
        if (waitMillis(now) > 0 || !enabled) return false
        val ok = runCatching {
            val bytes = store.readFully()
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val plain = c.doFinal(bytes.copyOfRange(12, bytes.size))
            MessageDigest.isEqual(hash(pin, plain.copyOfRange(0, 16)), plain.copyOfRange(16, plain.size))
        }.getOrDefault(false)
        val failures = if (ok) 0 else prefs.getInt("lock_failures", 0) + 1
        // 5 free attempts, then 30 s, 60 s, 120 s … capped at 15 min.
        val delay = if (failures < 5) 0L else minOf(15 * 60_000L, 30_000L shl (failures - 5).coerceAtMost(10))
        prefs.edit().putInt("lock_failures", failures).putLong("lock_until", if (delay > 0) now + delay else 0).apply()
        return ok
    }
}
