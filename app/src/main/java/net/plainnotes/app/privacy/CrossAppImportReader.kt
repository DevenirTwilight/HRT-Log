package net.plainnotes.app.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.plainnotes.app.data.AndroidSqlSource
import net.plainnotes.app.importer.CrossAppInput
import net.plainnotes.app.importer.HrtTracker
import net.plainnotes.app.importer.TmExport
import net.plainnotes.app.importer.TransMemo
import java.io.File
import java.io.InputStream

/** One transient SAF stream. No persistable URI, public cache, source writes or clinical writes.
 * SQLite alone uses the existing private no-backup copy lifecycle, deleted before preview. */
object CrossAppImportReader {
    sealed interface Parsed {
        val identity:CrossAppInput.Identification
        class Tracker(override val identity:CrossAppInput.Identification,val export:HrtTracker.Export):Parsed
        class Memo(val export:TmExport):Parsed {
            override val identity=CrossAppInput.Identification(CrossAppInput.Source.TRANS_MEMO,"8")
        }
    }
    suspend fun read(noBackup:File,open:()->InputStream?):Parsed=withContext(Dispatchers.IO) {
        val owner=currentCoroutineContext()
        (open() ?: throw CrossAppInput.Rejected()).use { original ->
            CrossAppInput.bounded(original,CrossAppInput.MAX_SQLITE_BYTES,owner::ensureActive).buffered().use { input ->
                input.mark(16)
                val prefix=ByteArray(16);var size=0
                while(size<prefix.size) { val n=input.read(prefix,size,prefix.size-size);if(n<0)break;if(n==0)continue;size+=n }
                input.reset()
                when(CrossAppInput.kind(prefix.copyOf(size))) {
                    CrossAppInput.Kind.SQLITE -> TransMemoImportCopies.read(noBackup,{input}) { file ->
                        AndroidSqlSource.openBounded(file,owner::ensureActive).use { Parsed.Memo(TransMemo.read(it)) }
                    }
                    CrossAppInput.Kind.JSON -> {
                        val text=CrossAppInput.readJson(input,owner::ensureActive)
                        withContext(Dispatchers.Default) { owner.ensureActive();val (identity,export)=CrossAppInput.tracker(text);Parsed.Tracker(identity,export) }
                    }
                    CrossAppInput.Kind.UNSUPPORTED -> throw CrossAppInput.Rejected()
                }
            }
        }
    }
}
