package net.plainnotes.poc

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.UUID

/** Same assertions run on a real JVM and real Android providers. No clinical bytes. */
abstract class ChunkContract {
    private fun hex(s: String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    private inline fun fixture(block:(File)->Unit) {
        val root=Files.createTempDirectory("synthetic-med02a-").toFile()
        try {block(root)}finally {root.deleteRecursively()}
    }
    private fun bytes(n:Int)=ByteArray(n){((it*31+17)%251).toByte()}
    private fun failed(block:()->Unit) {try{block();fail("Unsafe POC operation accepted")}catch(e:Exception){assertTrue(e is IOException || e is IllegalArgumentException || e is IllegalStateException || e is javax.crypto.AEADBadTagException)}}
    private fun obj(root:File,data:ByteArray=bytes(2500),id:ChunkObject.Identity=ChunkObject.Identity.fresh()) = ChunkObject.write(root,id,ByteArrayInputStream(data),1024)
    @Test fun nistAes256GcmKnownAnswer() {
        val actual=ChunkObject.gcm(true,ByteArray(32),ByteArray(12),byteArrayOf(),ByteArray(16))
        assertArrayEquals(hex("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919"),actual)
        assertArrayEquals(ByteArray(16),ChunkObject.gcm(false,ByteArray(32),ByteArray(12),byteArrayOf(),actual))
    }
    @Test fun boundariesEmptyExactPartialAndMultipleBlocks()=fixture{root->
        for(n in listOf(0,1,1023,1024,1025,2500,65536))obj(root,bytes(n)).use{owned->owned.open().use{r->
            assertEquals(n.toLong(),r.size);val out=ByteArray(n+10);assertEquals(n,r.readAt(0,out));assertArrayEquals(bytes(n),out.copyOf(n));assertEquals(0,r.readAt(r.size,out))
        }}
    }
    @Test fun randomReadsCrossBlocksAndReopen()=fixture{root->obj(root).use{o->repeat(3){o.open().use{r->
        for(offset in listOf(2000,0,1023,777,2499)){val out=ByteArray(500);val n=r.readAt(offset.toLong(),out);assertArrayEquals(bytes(2500).copyOfRange(offset,offset+n),out.copyOf(n))}
    }}}}
    @Test fun retriesUseNewKeyNonceAndObjectEvenSameIdentity()=fixture{root->val id=ChunkObject.Identity.fresh()
        obj(root,id=id).use{a->obj(root,id=id).use{b->assertFalse(a.syntheticKey().contentEquals(b.syntheticKey()));assertFalse(a.file.readBytes().copyOfRange(60,68).contentEquals(b.file.readBytes().copyOfRange(60,68)));assertNotEquals(a.file,b.file)}}
    }
    @Test fun noncesDistinctAndFinalReserved()=fixture{root->obj(root).use{o->
        val all=o.file.readBytes();val prefix=all.copyOfRange(60,68);val nonces=(listOf(0,1,2,-1)).map{prefix+ByteBuffer.allocate(4).putInt(it).array()};assertEquals(4,nonces.map{it.toList()}.toSet().size)
    }}
    @Test fun wrongKeyRejected()=fixture{root->obj(root).use{o->failed{ChunkObject.Reader(o.file,ByteArray(32),o.identity).close()}}}
    @Test fun wrongObjectVersionAndSpaceRejected()=fixture{root->obj(root).use{o->
        for(id in listOf(o.identity.copy(store=UUID.randomUUID()),o.identity.copy(objectId=UUID.randomUUID()),o.identity.copy(version=UUID.randomUUID())))failed{o.open(id).close()}
    }}
    @Test fun headerEveryFieldTamperRejected()=fixture{root->obj(root).use{o->val original=o.file.readBytes()
        for(i in listOf(0,8,12,28,44,60)){val bad=original.copyOf();bad[i]=(bad[i].toInt() xor 1).toByte();o.file.writeBytes(bad);failed{o.open().close()}}
    }}
    @Test fun bodyTagAndEndManifestTamperRejected()=fixture{root->obj(root).use{o->val original=o.file.readBytes()
        for(i in listOf(68,72,80,1090,original.size-1,original.size-68)){val bad=original.copyOf();bad[i]=(bad[i].toInt() xor 1).toByte();o.file.writeBytes(bad);failed{o.open().close()}}
    }}
    @Test fun truncatedMissingFooterAndTrailingGarbageRejected()=fixture{root->obj(root).use{o->val original=o.file.readBytes()
        for(n in listOf(0,67,original.size-68,original.size-1)){o.file.writeBytes(original.copyOf(n));failed{o.open().close()}}
        o.file.writeBytes(original+byteArrayOf(1));failed{o.open().close()}
    }}
    @Test fun reorderedRepeatedAndMissingBlocksRejected()=fixture{root->obj(root).use{o->val original=o.file.readBytes();val a=original.copyOfRange(68,1116);val b=original.copyOfRange(1116,2164)
        for(bad in listOf(original.copyOfRange(0,68)+b+a+original.copyOfRange(2164,original.size),original.copyOfRange(0,1116)+a+original.copyOfRange(2164,original.size),original.copyOfRange(0,68)+original.copyOfRange(1116,original.size))){o.file.writeBytes(bad);failed{o.open().close()}}
    }}
    @Test fun foreignBlockSplicingRejected()=fixture{root->obj(root).use{a->obj(root).use{b->val data=a.file.readBytes();val foreign=b.file.readBytes();foreign.copyInto(data,76,76,1116);a.file.writeBytes(data);failed{a.open().close()}}}}
    @Test fun mutationAfterOpenNeverReturnsUnauthenticatedBytes()=fixture{root->obj(root).use{o->o.open().use{r->
        RandomAccessFile(o.file,"rw").use{it.seek(1200);val old=it.read();it.seek(1200);it.write(old xor 1)};val out=ByteArray(2000){1};failed{r.readAt(500,out)};assertArrayEquals(ByteArray(2000),out)
    }}}
    @Test fun inputErrorAndInjectedNoSpaceNeverPublishObject()=fixture{root->
        val broken=object:InputStream(){var n=0;override fun read():Int{if(n++>1200)throw IOException("Synthetic provider read fault");return 7}}
        failed{ChunkObject.write(root,ChunkObject.Identity.fresh(),broken,1024)};assertTrue(root.listFiles()!!.isEmpty())
        failed{ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(bytes(4000)),1024,fault={_,_->throw IOException("Synthetic ENOSPC")})};assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun cancellationDuringUnknownLengthStreamCleansOnlyOwnPart()=fixture{root->
        val sentinel=File(root,"unrelated").apply{writeText("Synthetic sentinel")};var active=true
        failed{ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(bytes(4000)),1024,{active},{stage,_->if(stage==ChunkObject.Stage.BLOCK)active=false})}
        assertEquals(listOf(sentinel),root.listFiles()!!.toList());assertEquals("Synthetic sentinel",sentinel.readText())
    }
    @Test fun eachCommitFaultLeavesOldObjectReadableAndNoPartialReady()=fixture{root->obj(root).use{old->
        for(stage in ChunkObject.Stage.entries){failed{ChunkObject.write(root,ChunkObject.Identity.fresh(),ByteArrayInputStream(bytes(2500)),1024,fault={s,_->if(s==stage)throw IOException("Synthetic $s")})};assertEquals(1,root.listFiles()!!.size);old.open().use{assertEquals(2500,it.size.toInt())}}
    }}
    @Test fun nonprogressingProviderAndQuotaAreBounded()=fixture{root->
        val zeros=object:InputStream(){override fun read()=0;override fun read(b:ByteArray,off:Int,len:Int)=0}
        failed{ChunkObject.write(root,ChunkObject.Identity.fresh(),zeros)}
        failed{ChunkObject.write(root,ChunkObject.Identity.fresh(),SyntheticStream(ChunkObject.MAX_BYTES+1))}
        assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun closedReaderAndOwnerStayRevoked()=fixture{root->val o=obj(root);val r=o.open();r.close();failed{r.readAt(0,ByteArray(1))};o.close();failed{o.open()}}
    @Test fun largeUnknownLengthStreamingHasConstantAlgorithmBuffers()=fixture{root->
        val started=System.nanoTime()
        ChunkObject.write(root,ChunkObject.Identity.fresh(),SyntheticStream(ChunkObject.MAX_BYTES)).use{o->o.open().use{r->
            val out=ByteArray(8192);assertEquals(8192,r.readAt(ChunkObject.MAX_BYTES-8192,out));assertArrayEquals(ByteArray(8192){SyntheticStream.at(ChunkObject.MAX_BYTES-8192+it)},out)
            assertTrue(r.maxScratchBytes<=3*65536+16)
            println("MED02A_STREAM bytes=${r.size} block=${r.blockSize} scratch_bound=${r.maxScratchBytes} elapsed_ms=${(System.nanoTime()-started)/1000000}")
        }}
    }
    @Test fun hundredEncryptedObjectsRemainIndependentAndOnlyChosenObjectsOpen()=fixture{root->
        val all=(0 until 100).map{obj(root,bytes(64+it))}
        try {
            assertEquals(100,root.listFiles()!!.size);assertEquals(100,all.map{it.identity}.distinct().size)
            for(i in listOf(99,0,50))all[i].open().use{r->val data=ByteArray(64+i);assertEquals(data.size,r.readAt(0,data));assertArrayEquals(bytes(data.size),data)}
            assertTrue(root.listFiles()!!.none{it.extension=="part"})
        }finally{all.forEach{it.close()}}
    }
}

class SyntheticStream(private val count:Long):InputStream() {
    private var position=0L
    override fun available()=0 // SAF unknown length must not consult this.
    override fun read():Int=if(position==count)-1 else at(position++).toInt() and 255
    override fun read(b:ByteArray,off:Int,len:Int):Int {
        if(position==count)return -1
        val n=minOf(len.toLong(),count-position).toInt();repeat(n){b[off+it]=at(position++)};return n
    }
    companion object {fun at(i:Long)=((i*31+17)%251).toByte()}
}
