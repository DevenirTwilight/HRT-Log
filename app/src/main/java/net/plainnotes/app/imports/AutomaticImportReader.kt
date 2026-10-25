package net.plainnotes.app.imports

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.plainnotes.app.data.AndroidSqlSource
import net.plainnotes.app.importer.ImportFormatDetector
import net.plainnotes.app.importer.SqlSource
import net.plainnotes.app.privacy.TransMemoImportCopies
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** One provider open and one immutable input. Parsed exports are materialized before the
 * existing private SQLite copy is removed. No URI/handle escapes into preview or confirmation. */
internal object AutomaticImportReader {
    const val MAXIMUM_BYTES=32*1024*1024
    private const val MAXIMUM_ROWS=100000L
    suspend fun read(directory:File,open:()->InputStream?,permission:()->Unit,
        recognized:(ImportFormatDetector.Source)->Unit):ImportFormatDetector.Parsed=withContext(Dispatchers.IO) {
        val coroutine=currentCoroutineContext()
        fun active() {coroutine.ensureActive();permission()}
        active()
        val raw=open() ?: throw IOException("Input unavailable")
        val bounded=object:FilterInputStream(raw) {
            var count=0L
            override fun read():Int {val byte=ByteArray(1);return if(read(byte,0,1)<0)-1 else byte[0].toInt() and 255}
            override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                if(length==0)return 0
                active()
                val n=`in`.read(bytes,offset,minOf(length.toLong(),MAXIMUM_BYTES-count+1).toInt())
                active()
                if(n==0)throw IOException("Source made no progress")
                if(n>0) {count+=n;if(count>MAXIMUM_BYTES)throw ImportFormatDetector.TooLarge()}
                return n
            }
        }
        PushbackInputStream(bounded,64).use {input->
            val prefix=ByteArray(64);var used=0
            while(used<prefix.size) {val n=input.read(prefix,used,prefix.size-used);if(n<0)break;used+=n}
            input.unread(prefix,0,used)
            when(ImportFormatDetector.header(prefix.copyOf(used))) {
                ImportFormatDetector.Container.FEATHERLINE->throw ImportFormatDetector.Unsupported(ImportFormatDetector.Source.FEATHERLINE)
                ImportFormatDetector.Container.HRT_LOG->throw ImportFormatDetector.Unsupported(ImportFormatDetector.Source.HRT_LOG)
                ImportFormatDetector.Container.UNKNOWN->throw ImportFormatDetector.Unknown()
                ImportFormatDetector.Container.JSON->{
                    val bytes=ByteArrayOutputStream();val buffer=ByteArray(64*1024)
                    try {
                        while(true) {val n=input.read(buffer);if(n<0)break;bytes.write(buffer,0,n)}
                        val owned=bytes.toByteArray()
                        try {
                            val text=try {Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(owned)).toString()}
                            catch(_:java.nio.charset.CharacterCodingException){throw ImportFormatDetector.Damaged()}
                            active();ImportFormatDetector.json(text) {active();recognized(it)}.also {active()}
                        }finally {owned.fill(0)}
                    }finally {buffer.fill(0)}
                }
                ImportFormatDetector.Container.SQLITE->TransMemoImportCopies.read(directory,{
                    // The outer use owns the provider. The existing copy mechanism owns only
                    // this borrowed view, so exceptions/cancellation still close the source once.
                    object:FilterInputStream(input) {override fun close()=Unit}
                }) {file->
                    active()
                    try {SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use {db->
                        val delegate=AndroidSqlSource(db);var remaining=MAXIMUM_ROWS
                        val source=object:SqlSource by delegate {
                            override fun columns(table:String):List<String>? {
                                active()
                                val ordinary=db.rawQuery("SELECT type,sql FROM sqlite_master WHERE name=?",arrayOf(table)).use {c->
                                    c.moveToFirst() && c.getString(0)=="table" && c.getString(1)?.trimStart()?.startsWith("CREATE VIRTUAL",ignoreCase=true)==false
                                }
                                return if(ordinary)delegate.columns(table) else null
                            }
                            override fun rows(table:String):List<Map<String,Any?>> {
                                active()
                                val count=db.rawQuery("SELECT COUNT(*) FROM `$table`",null).use {it.moveToFirst();it.getLong(0)}
                                if(count<0 || count>remaining)throw ImportFormatDetector.TooLarge()
                                remaining-=count
                                return delegate.rows(table).also {active();check(it.size.toLong()==count)}
                            }
                        }
                        ImportFormatDetector.sqlite(source) {active();recognized(it)}.also {active()}
                    }}catch(_:android.database.sqlite.SQLiteException){throw ImportFormatDetector.Damaged()}
                }
            }
        }
    }
}
