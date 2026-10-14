package net.plainnotes.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Test-only synthetic wrapping transcript. Does not freeze a production envelope format. */
class AttachmentKeyAndroidTest {
    @Test fun independentKeystoreAliasesCrossScopeRejectRotationAndMissingAliasFailClosed() {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val a="hrt.attachment.primary.synthetic.${UUID.randomUUID()}"
        val b="hrt.attachment.primary.synthetic.${UUID.randomUUID()}"
        val raw=ByteArray(32){7};val aad="synthetic primary scoped wrap".toByteArray()
        fun generate(alias:String):SecretKey {
            val g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
            g.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            return g.generateKey()
        }
        fun wrap(key:SecretKey)=Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,key);updateAAD(aad);iv+doFinal(raw)
        }
        fun unwrap(alias:String,bytes:ByteArray,context:ByteArray=aad):ByteArray {
            val key=store.getKey(alias,null) as? SecretKey ?: throw AttachmentKeyFoundation.MissingKey()
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));updateAAD(context);doFinal(bytes.copyOfRange(12,bytes.size))
            }
        }
        try {
            val keyA=generate(a);val keyB=generate(b);val old=wrap(keyA);val frozen=old.copyOf();val rotated=wrap(keyB)
            assertArrayEquals(raw,unwrap(a,old));assertArrayEquals(raw,unwrap(b,rotated));assertArrayEquals(frozen,old)
            try{unwrap(b,old);fail("Cross alias accepted")}catch(_:javax.crypto.AEADBadTagException){}
            try{unwrap(a,old,"synthetic private space".toByteArray());fail("Cross space AAD accepted")}catch(_:javax.crypto.AEADBadTagException){}
            store.deleteEntry(a)
            try{unwrap(a,old);fail("Missing key regenerated")}catch(_:AttachmentKeyFoundation.MissingKey){}
            assertFalse(store.containsAlias(a));assertArrayEquals(frozen,old)
        } finally {raw.fill(0);store.deleteEntry(a);store.deleteEntry(b)}
    }
}
