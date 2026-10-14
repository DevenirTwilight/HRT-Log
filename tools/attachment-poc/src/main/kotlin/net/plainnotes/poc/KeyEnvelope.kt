package net.plainnotes.poc

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Synthetic envelopes, never PNBAK1. Fixed-cost standard PBKDF2 is a POC, not a production KDF selection. */
object KeyEnvelope {
    private val random=SecureRandom()
    fun wrap(key:SecretKey,context:ByteArray,value:ByteArray):ByteArray {
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key)
        val nonce=c.iv;require(nonce.size==12);c.updateAAD(context)
        return nonce+c.doFinal(value)
    }
    fun unwrap(key:SecretKey,context:ByteArray,envelope:ByteArray):ByteArray {
        require(envelope.size in 28..4096)
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,envelope.copyOf(12)));c.updateAAD(context)
        return c.doFinal(envelope,12,envelope.size-12)
    }
    private fun derive(password:CharArray,salt:ByteArray):SecretKeySpec {
        require(salt.size==16 && password.isNotEmpty())
        val spec=PBEKeySpec(password,salt,100000,256)
        val bytes=try{SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded}finally{spec.clearPassword()}
        return try{SecretKeySpec(bytes,"AES")}finally{bytes.fill(0)}
    }
    fun portable(password:CharArray,context:ByteArray,value:ByteArray):ByteArray {
        val salt=ByteArray(16).also(random::nextBytes);return salt+wrap(derive(password,salt),"POC-PORTABLE-1".toByteArray()+context,value)
    }
    fun restore(password:CharArray,context:ByteArray,envelope:ByteArray):ByteArray {
        require(envelope.size in 44..4112)
        return unwrap(derive(password,envelope.copyOf(16)),"POC-PORTABLE-1".toByteArray()+context,envelope.copyOfRange(16,envelope.size))
    }
}
