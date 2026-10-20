package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

/** Exact whole-business equality without building a second full fixture/DOM during a memory test. */
internal object SyntheticRestoreDigest {
    fun read(db:SupportSQLiteDatabase):String {
        check(db.inTransaction())
        val digest=MessageDigest.getInstance("SHA-256");val size=ByteArray(4)
        fun length(n:Int){for(i in 0..3)size[i]=(n ushr (24-i*8)).toByte();digest.update(size)}
        for(table in DOMAIN_TABLES) {
            val primary=db.query("PRAGMA table_info(`$table`)").use {c->buildList {
                while(c.moveToNext())if(c.getInt(5)>0)add(c.getInt(5) to c.getString(1))
            }.sortedBy{it.first}.map{it.second}}
            check(primary.isNotEmpty())
            val count=db.query("SELECT COUNT(*) FROM `$table`").use{it.moveToFirst();it.getInt(0)}
            val name=table.toByteArray(Charsets.UTF_8);length(name.size);digest.update(name);length(count)
            val ordered=object:SupportSQLiteDatabase by db {
                override fun query(sql:String)=db.query(if(sql.startsWith("SELECT ") && sql.endsWith(" FROM $table"))
                    sql+" ORDER BY "+primary.joinToString(","){"`$it`"} else sql)
            }
            RawData.forEachBackupRow(ordered,table,true) {row->
                val bytes=row.toString().toByteArray(Charsets.UTF_8)
                try{length(bytes.size);digest.update(bytes)}finally{bytes.fill(0)}
            }
        }
        return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
}
