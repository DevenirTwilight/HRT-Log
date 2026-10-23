package net.plainnotes.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Default-OFF device wrapping adapter. No database key, PIN derivation or portable recovery.
 * Explicit provisioning is separate from existing-key operations. A later durable coordinator
 * must reserve usage/rotation budgets before wrapping; this primitive is not that coordinator.
 */
internal class AttachmentDeviceKeys {
    enum class Role { STAGING, ACTIVE }
    data class Binding(val content: AttachmentObjectContext, val localDataset: UUID,
        val physicalGeneration: UUID, val operation: UUID, val wrapEpoch: Long, val role: Role) {
        init { require(wrapEpoch > 0) }
        fun encode(): ByteArray = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.write("notes.attachment.device\u0000".toByteArray(Charsets.US_ASCII)); out.writeShort(1)
            out.writeByte(if (role == Role.STAGING) 1 else 2); out.writeLong(wrapEpoch)
            listOf(localDataset, physicalGeneration, operation).forEach { out.writeLong(it.mostSignificantBits); out.writeLong(it.leastSignificantBits) }
            out.write(content.encode())
        } }.toByteArray()
    }
    data class Alias(val identity: UUID, val role: Role) {
        // Neutral, purpose-separated; contains no document, name, health data or database namespace.
        val value: String get() = "notes.obj.v1.${if (role == Role.STAGING) "s" else "a"}.$identity"
    }
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    /** Never replaces an existing alias. Caller must record provisioning/rotation in a later journal. */
    fun provisionNew(alias: Alias) = synchronized(provisioning) {
        check(!store().containsAlias(alias.value)) { "Device alias already exists" }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias.value, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
        Unit
    }
    private fun existing(alias: Alias): SecretKey =
        (store().getKey(alias.value, null) as? SecretKey) ?: throw AttachmentKeyFoundation.MissingKey()

    /** Raw owned DEK is erased even if alias lookup, scope or encryption fails. */
    fun wrapExisting(alias: Alias, binding: Binding, ownedDek: ByteArray): ByteArray {
        try {
            require(alias.role == binding.role && ownedDek.size == 32)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, existing(alias)); cipher.updateAAD(binding.encode())
            val iv = cipher.iv
            check(iv.size == 12) { "Unsupported device IV" }
            return iv + cipher.doFinal(ownedDek)
        } finally { ownedDek.fill(0) }
    }
    /** No provisioning path is reachable here. An absent or invalidated alias requires recovery. */
    fun unwrapExisting(alias: Alias, binding: Binding, envelope: ByteArray): ByteArray {
        require(alias.role == binding.role && envelope.size == 60)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, existing(alias), GCMParameterSpec(128, envelope, 0, 12))
        cipher.updateAAD(binding.encode())
        return cipher.doFinal(envelope, 12, 48).also { if (it.size != 32) { it.fill(0); throw AttachmentKeyFoundation.MissingKey() } }
    }
    companion object { private val provisioning = Any() }
}
