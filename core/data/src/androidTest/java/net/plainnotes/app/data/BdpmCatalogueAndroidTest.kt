package net.plainnotes.app.data
/** Public SQLite catalogue plus actual SQLCipher user snapshot/backup contract. */
class BdpmCatalogueAndroidTest:BdpmCatalogueContract() {
    override fun helperFactory():androidx.sqlite.db.SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return net.zetetic.database.sqlcipher.SupportOpenHelperFactory(ByteArray(32){(it+43).toByte()})
    }
}
