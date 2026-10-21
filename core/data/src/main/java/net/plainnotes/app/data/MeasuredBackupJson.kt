package net.plainnotes.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.time.Instant

/** Transfer ownership before leaving a suspendable transaction; never retain plaintext after use.
 * A rejected new buffer is wiped too; an alias of the already owned buffer stays owned.
 * This owns bytes only, never keys or passwords.
 */
internal class BackupPlaintextOwner:AutoCloseable {
    private var plaintext:ByteArray?=null
    private var closed=false
    fun capture(bytes:ByteArray) {
        if(closed || plaintext!=null) {
            if(bytes!==plaintext)bytes.fill(0)
            error("Backup plaintext already owned or closed")
        }
        plaintext=bytes
    }
    fun bytes():ByteArray {
        check(!closed)
        return checkNotNull(plaintext)
    }
    override fun close() {
        plaintext?.fill(0);plaintext=null;closed=true
    }
}

/** Bounded plaintext staging, no files or cryptographic changes. Always wipe owned fragments. */
internal class BackupPlainBytes(private val limit:Int,private val chunkSize:Int=64*1024):AutoCloseable {
    private val chunks=ArrayList<ByteArray>()
    private var size=0
    private var closed=false
    init {require(limit in 0..BackupLimits.MAX_JSON_BYTES && chunkSize>0)}
    fun append(input:ByteArray) {
        check(!closed)
        if(input.size>limit-size)throw BackupCodec.TooLarge()
        var offset=0
        while(offset<input.size) {
            val within=size%chunkSize
            if(within==0)chunks.add(ByteArray(minOf(chunkSize,limit-size)))
            val take=minOf(input.size-offset,chunks.last().size-within)
            input.copyInto(chunks.last(),within,offset,offset+take)
            offset+=take;size+=take
        }
    }
    fun text(value:String) {
        val bytes=value.toByteArray(Charsets.UTF_8)
        try{append(bytes)}finally{bytes.fill(0)}
    }
    fun finish():ByteArray {
        check(!closed)
        val output=ByteArray(size)
        var offset=0
        for(chunk in chunks) {
            val take=minOf(chunk.size,size-offset)
            chunk.copyInto(output,offset,0,take);offset+=take
        }
        check(offset==size)
        return output
    }
    override fun close() {
        chunks.forEach{it.fill(0)};chunks.clear();size=0;closed=true
    }
}

/** Exact existing business JSON, emitted one row at a time in one read transaction.
 * Only the P4 internal export uses it. The PNBAK1 cipher and all restore paths remain unchanged.
 */
internal object MeasuredBackupJson {
    fun encode(db:SupportSQLiteDatabase,created:Instant,maxBytes:Int=BackupLimits.MAX_JSON_BYTES):ByteArray {
        check(db.inTransaction())
        return BackupPlainBytes(maxBytes).use {output->
            val header=JSONObject().put("format",BackupCodec.FORMAT_VERSION).put("schema",RecordBackupCompatibility.SCHEMA)
                .put("created",created.toString()).toString()
            check(header.endsWith("}"))
            output.text(header.dropLast(1)+",\"tables\":{")
            DOMAIN_TABLES.forEachIndexed {index,table->
                if(index>0)output.text(",")
                output.text(JSONObject.quote(table)+":[")
                var first=true
                RawData.forEachBackupRow(db,table,true) {row->
                    if(!first)output.text(",")
                    output.text(row.toString());first=false
                }
                output.text("]")
            }
            output.text("}}")
            output.finish()
        }
    }
}
