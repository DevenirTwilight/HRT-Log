package net.plainnotes.app
/** Real NotesApp entry and SQLCipher; same assertion body as four-locale local tests. */
class BdpmReferenceFlowAndroidTest:BdpmReferenceFlowContract() {
    override fun helperFactory():androidx.sqlite.db.SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return net.zetetic.database.sqlcipher.SupportOpenHelperFactory(ByteArray(32){(it+81).toByte()})
    }
}
