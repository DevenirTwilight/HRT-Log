package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class ReferenceAuditStreamingAndroidTest:ReferenceAuditStreamingContract() {
    override fun factory(key:ByteArray):SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(key)
    }
}
