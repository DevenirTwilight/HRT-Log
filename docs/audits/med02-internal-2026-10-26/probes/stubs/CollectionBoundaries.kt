package net.plainnotes.app.data
// Controlled snapshot/record-count boundary ONLY. Does not certify archive parsing or DB capture.
internal object BackupLimits {const val MAX_JSON_BYTES=32*1024*1024}
internal object MedicalArchiveRecords {
    fun count(bytes:ByteArray,active:()->Unit):Long {active();return 0}
}
internal class AttachmentBackupPreparation {
    class Snapshot(private val value:AttachmentBackupManifest) {
        fun manifest()=value
        fun <T> records(block:(ByteArray)->T):T=block(byteArrayOf(0x41))
        fun <T> member(version:java.util.UUID,block:(java.io.InputStream,ByteArray)->T):T=error("empty synthetic collection")
    }
}
