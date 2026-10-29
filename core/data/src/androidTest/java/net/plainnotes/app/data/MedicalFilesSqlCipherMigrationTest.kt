package net.plainnotes.app.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class MedicalFilesSqlCipherMigrationTest:MedicalFilesMigrationContract() {
    override val assetsContext:Context get()=InstrumentationRegistry.getInstrumentation().context
    override fun factory():SupportOpenHelperFactory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(ByteArray(32) {(it+21).toByte()})
    }
}
