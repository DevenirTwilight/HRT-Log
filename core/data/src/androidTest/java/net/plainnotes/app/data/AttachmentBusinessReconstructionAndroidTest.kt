package net.plainnotes.app.data

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class AttachmentBusinessReconstructionAndroidTest:AttachmentBusinessReconstructionContract() {
    init {System.loadLibrary("sqlcipher")}
    override fun factory()=SupportOpenHelperFactory(ByteArray(32) {71})
}
