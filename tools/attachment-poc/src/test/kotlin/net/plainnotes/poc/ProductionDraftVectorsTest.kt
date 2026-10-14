package net.plainnotes.poc

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Test-only interoperability transcripts; no production format/writer or security certification. */
class ProductionDraftVectorsTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hmac(key: ByteArray, value: ByteArray) = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(value)
    }
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, n: Int = 32): ByteArray {
        val prk = hmac(salt, ikm); var previous = byteArrayOf(); var out = byteArrayOf()
        for (i in 1..((n + 31) / 32)) { previous = hmac(prk, previous + info + i.toByte()); out += previous }
        return out.copyOf(n)
    }
    private fun vectors() = JSONObject(File("../attachment-design-validation/draft_vectors.json").readText())
    @Test fun rfc5869AppendixA1() {
        val ikm = ByteArray(22) { 0x0b }; val salt = hex("000102030405060708090a0b0c")
        assertArrayEquals(hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"), hmac(salt, ikm))
        assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), hkdf(ikm, salt, hex("f0f1f2f3f4f5f6f7f8f9"), 42))
    }
    @Test fun rfc9106Section53Argon2idKnownAnswerNotMobileCostBenchmark() {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(32).withIterations(3).withParallelism(4)
            .withSalt(ByteArray(16) { 2 }).withSecret(ByteArray(8) { 3 }).withAdditional(ByteArray(12) { 4 }).build()
        val out = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(ByteArray(32) { 1 }, out)
        assertArrayEquals(hex("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"), out)
    }
    @Test fun pinnedPublicWycheproofPositiveAndNegativeVectors() {
        val cases = vectors().getJSONArray("wycheproof")
        assertEquals(6, cases.length())
        for (i in 0 until cases.length()) {
            val t = cases.getJSONObject(i); val sealed = hex(t.getString("ct") + t.getString("tag"))
            if (t.getString("result") == "valid") {
                assertArrayEquals(sealed, ChunkObject.gcm(true, hex(t.getString("key")), hex(t.getString("iv")), hex(t.getString("aad")), hex(t.getString("msg"))))
                assertArrayEquals(hex(t.getString("msg")), ChunkObject.gcm(false, hex(t.getString("key")), hex(t.getString("iv")), hex(t.getString("aad")), sealed))
            } else {
                try { ChunkObject.gcm(false, hex(t.getString("key")), hex(t.getString("iv")), hex(t.getString("aad")), sealed); fail("Invalid public tcId ${t.getInt("tcId")} accepted") }
                catch (expected: javax.crypto.AEADBadTagException) { }
            }
        }
    }
    @Test fun pythonJcaDraftTranscriptInteroperabilityAndContextTamper() {
        val cases = vectors().getJSONArray("drafts"); val label = "HRT-MED-DRAFT0\u0000".toByteArray(Charsets.US_ASCII)
        for (i in 0 until cases.length()) {
            val t = cases.getJSONObject(i); val header = hex(t.getString("header")); assertEquals(140, header.size)
            val hash = MessageDigest.getInstance("SHA-256").digest(header)
            assertArrayEquals(hex(t.getString("header_hash")), hash)
            val keys = (1..3).map { role -> hkdf(hex(t.getString("dek")), hex(t.getString("salt")), label + role.toByte() + hash) }
            for ((index, role) in listOf("header", "body", "footer").withIndex()) assertArrayEquals(hex(t.getJSONObject("keys").getString(role)), keys[index])
            assertFalse(keys[0].contentEquals(keys[1])); assertFalse(keys[1].contentEquals(keys[2]))
            val tag = ChunkObject.gcm(true, keys[0], ByteArray(12), label + 1.toByte() + header, byteArrayOf())
            assertArrayEquals(hex(t.getString("header_tag")), tag)
            val frames = t.getJSONArray("frames"); val digest = MessageDigest.getInstance("SHA-256"); var total = 0L
            for (j in 0 until frames.length()) {
                val f = frames.getJSONObject(j); val plain = hex(f.getString("plaintext")); total += plain.size; digest.update(plain)
                val nonce = ByteBuffer.allocate(12).putLong(0).putInt(j).array()
                val aad = label + 2.toByte() + hash + tag + ByteBuffer.allocate(8).putInt(j).putInt(plain.size).array()
                assertArrayEquals(hex(f.getString("nonce")), nonce); assertArrayEquals(hex(f.getString("aad")), aad)
                val sealed = ChunkObject.gcm(true, keys[1], nonce, aad, plain)
                assertArrayEquals(hex(f.getString("sealed")), sealed)
                assertArrayEquals(plain, ChunkObject.gcm(false, keys[1], nonce, aad, sealed))
                val altered = aad.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
                try { ChunkObject.gcm(false, keys[1], nonce, altered, sealed); fail("Changed draft context accepted") }
                catch (expected: javax.crypto.AEADBadTagException) { }
            }
            assertEquals(t.getLong("size"), total)
            val footer = ByteBuffer.allocate(44).putLong(total).putInt(frames.length()).put(digest.digest()).array()
            assertArrayEquals(hex(t.getString("footer_plain")), footer)
            assertArrayEquals(hex(t.getString("footer_sealed")), ChunkObject.gcm(true, keys[2], ByteArray(12), label + 3.toByte() + hash + tag, footer))
        }
    }
}
