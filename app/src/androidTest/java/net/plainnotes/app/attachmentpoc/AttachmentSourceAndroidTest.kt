package net.plainnotes.app.attachmentpoc

import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.platform.app.InstrumentationRegistry
import net.plainnotes.poc.ChunkObject
import net.plainnotes.poc.SyntheticStream
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class AttachmentSourceAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get()=context.contentResolver
    private fun uri(kind:String)=Uri.parse("content://net.plainnotes.app.test.synthetic-attachments/$kind")
    private inline fun fixture(block:(File)->Unit) {
        SyntheticEmulatorGuard.requireEmulator()
        val root=Files.createTempDirectory(context.filesDir.toPath(),"synthetic-med02a-source-").toFile()
        try{block(root)}finally{root.deleteRecursively()}
    }
    @Test fun realContentResolverUnknownSizePipeEncryptsToCompleteObject()=fixture{root->
        resolver.query(uri("unknown"),null,null,null,null)!!.use{c->assertTrue(c.moveToFirst());assertTrue(c.isNull(c.getColumnIndexOrThrow(OpenableColumns.SIZE)))}
        resolver.openFileDescriptor(uri("unknown"),"r")!!.use{assertEquals(-1L,it.statSize)}
        resolver.openInputStream(uri("unknown"))!!.use{source->ChunkObject.write(root,ChunkObject.Identity.fresh(),source,1024).use{o->o.open().use{r->
            assertEquals(8192L,r.size);val data=ByteArray(8192);r.readAt(0,data);assertArrayEquals(ByteArray(8192){SyntheticStream.at(it.toLong())},data)
        }}}
        assertTrue(root.listFiles()!!.none{it.extension=="part"})
    }
    @Test fun actualReliablePipeProviderErrorRollsBackImport()=fixture{root->
        resolver.openInputStream(uri("failure"))!!.use{source->
            assertThrows(IOException::class.java){ChunkObject.write(root,ChunkObject.Identity.fresh(),source,1024)}
        }
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun providerMimeCannotOverrideActualFileMagic()=fixture{root->
        assertEquals("image/png",resolver.getType(uri("mime")))
        resolver.openInputStream(uri("mime"))!!.use{source->ChunkObject.write(root,ChunkObject.Identity.fresh(),source).use{o->
            assertThrows(IllegalStateException::class.java){ImagePoc.decode(o,resolver.getType(uri("mime"))!!)}
        }}
    }
}
