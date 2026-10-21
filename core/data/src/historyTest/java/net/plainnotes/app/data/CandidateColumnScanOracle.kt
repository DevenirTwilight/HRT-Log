package net.plainnotes.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.junit.Assert.*
import java.util.UUID

/** Synthetic schema fixture only: compares all original SQL predicates, never bypasses production auditing. */
internal object CandidateColumnScanOracle {
    private fun before(db:SupportSQLiteDatabase) {
        check(db.inTransaction());var total=0L;var textBytes=0L
        for(table in DOMAIN_TABLES) {
            total+=db.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getLong(0)}
            require(total<=BackupLimits.MAX_ROWS)
            val columns=db.query("PRAGMA table_info(`$table`)").use{c->buildList{while(c.moveToNext())add(c.getString(1) to c.getString(2))}}
            for((column,type) in columns) {
                val allowed=when(type){"INTEGER"->"typeof(`$column`)='integer'";"TEXT"->"typeof(`$column`)='text' AND length(`$column`)<=1048576";"REAL"->"typeof(`$column`) IN ('integer','real') AND `$column` BETWEEN -1.7976931348623157e308 AND 1.7976931348623157e308";else->error("Unknown trusted storage type")}
                db.query("SELECT 1 FROM `$table` WHERE `$column` IS NOT NULL AND NOT ($allowed) LIMIT 1").use{require(!it.moveToFirst())}
                if(type=="TEXT") {
                    textBytes+=db.query("SELECT COALESCE(SUM(length(CAST(`$column` AS BLOB))),0) FROM `$table`").use{it.moveToFirst();it.getLong(0)}
                    require(textBytes<=BackupLimits.MAX_JSON_BYTES)
                }
            }
        }
    }


    fun check(context:Context,factory:SupportSQLiteOpenHelper.Factory,limits:Boolean) {
        val name="synthetic-column-oracle-${UUID.randomUUID()}.db"
        val helper=factory.create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:SupportSQLiteDatabase) {for(table in DOMAIN_TABLES)db.execSQL("CREATE TABLE `$table` (i INTEGER,t TEXT,r REAL)")}
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("No migration in this isolated fixture")
            }).build())
        try {
            val db=helper.writableDatabase
            db.beginTransaction()
            try {
                val table=DOMAIN_TABLES.first()
                fun check(expected:Boolean) {
                    assertEquals("frozen checker",expected,runCatching{before(db)}.isSuccess)
                    assertEquals("aggregate checker",expected,runCatching{BackupValidation.validateCandidateStoredColumns(db)}.isSuccess)
                }
                fun clear(){db.execSQL("DELETE FROM `$table`")}
                check(true)
                if(!limits) {
                    db.execSQL("INSERT INTO `$table` VALUES(NULL,NULL,NULL)");check(true);clear()
                    db.execSQL("INSERT INTO `$table` VALUES(?,?,?)",arrayOf(Long.MAX_VALUE,"片µg😀",Double.MAX_VALUE));check(true);clear()
                    for(sql in listOf("('wrong','ok',1)","(1.25,'ok',1)","(1,X'61',1)","(1,'ok','wrong')","(1,'ok',1e999)")) {
                        db.execSQL("INSERT INTO `$table` VALUES$sql");check(false);clear()
                    }
                    db.execSQL("INSERT INTO `$table` VALUES(1,?,1)",arrayOf("a".repeat(1_048_576)));check(true);clear()
                    db.execSQL("INSERT INTO `$table` VALUES(1,?,1)",arrayOf("a".repeat(1_048_576+1)));check(false);clear()
                    db.execSQL("INSERT INTO `$table` VALUES(1,?,1)",arrayOf("片".repeat(1_048_576)));check(true)
                } else {
                    db.execSQL("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<100000) INSERT INTO `$table` SELECT x,NULL,NULL FROM n")
                    check(true);db.execSQL("INSERT INTO `$table` VALUES(100001,NULL,NULL)");check(false);clear()
                    val text="a".repeat(1_048_576)
                    repeat(32){db.execSQL("INSERT INTO `$table` VALUES(1,?,1)",arrayOf(text))};check(true)
                    db.execSQL("INSERT INTO `$table` VALUES(1,'a',1)");check(false)
                }
            } finally{db.endTransaction()}
        } finally{helper.close();context.deleteDatabase(name)}
    }
}
