package net.plainnotes.app.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

/** Synthetic membership/format attacks. No DB, URI, real medical content or activation. */
class MedicalCollectionIndexTest {
    private fun id(n:Long)=UUID(0,n)
    private fun library(n:Long,active:Boolean=false)=MedicalCollectionIndex.Library(id(n),
        if(active)MedicalCollectionIndex.Role.ACTIVE else MedicalCollectionIndex.Role.RETAINED,
        id(100+n),id(200+n),id(300+n),100,"a".repeat(64),200,"b".repeat(64),1,0,emptyList())
    private fun index()=MedicalCollectionIndex(id(99),id(1),listOf(library(1,true),library(2)))
    private fun reject(block:()->Unit) {try {block();fail("Invalid collection accepted")}catch(_:Exception){}}
    @Test fun preservesEmptyRetainedLibraryAndScopedSameIdsWithoutMerging() {
        val i=index();val decoded=MedicalCollectionIndex.decode(i.encode())
        assertEquals(i,decoded);assertEquals(2,decoded.libraries.size)
        assertEquals(MedicalCollectionIndex.Role.RETAINED,decoded.libraries[1].role)
        // A copied source version in a different library is not a duplicate within one library.
        val member=MedicalCollectionIndex.Member(id(700),id(701),56,"c".repeat(64))
        val copied=i.copy(libraries=i.libraries.map {it.copy(members=listOf(member))})
        assertEquals(copied,MedicalCollectionIndex.decode(copied.encode()))
    }
    @Test fun missingActiveDuplicateLibraryReorderedExtraOrTruncatedBytesRefuse() {
        val i=index();val wire=i.encode()
        reject {i.copy(active=id(999)).encode()}
        reject {i.copy(libraries=listOf(i.libraries[0],i.libraries[0])).encode()}
        reject {i.copy(libraries=i.libraries.reversed()).encode()}
        reject {i.copy(libraries=i.libraries.map {it.copy(role=MedicalCollectionIndex.Role.RETAINED)}).encode()}
        reject {MedicalCollectionIndex.decode(wire+byteArrayOf(0))}
        for(n in listOf(0,3,37,wire.size-1))reject {MedicalCollectionIndex.decode(wire.copyOf(n))}
        val wrongCount=wire.copyOf();ByteBuffer.wrap(wrongCount).putInt(36,Int.MAX_VALUE)
        reject {MedicalCollectionIndex.decode(wrongCount)}
        val role=wire.copyOf();role[56]=3;reject {MedicalCollectionIndex.decode(role)}
    }
    @Test fun aggregateLimitsCannotBeMultipliedByLibraryCount() {
        val i=index()
        reject {i.copy(libraries=i.libraries.map {it.copy(recordBytes=17L*1024*1024)}).encode()}
        reject {i.copy(libraries=i.libraries.map {it.copy(manifestBytes=5L*1024*1024)}).encode()}
        reject {i.copy(libraries=i.libraries.map {it.copy(businessRows=50001)}).encode()}
        val many=List(501) {n->MedicalCollectionIndex.Member(id(1000+n.toLong()),id(3000+n.toLong()),56,"c".repeat(64))}
        reject {i.copy(libraries=i.libraries.map {it.copy(members=many)}).encode()}
        reject {i.copy(libraries=List(9) {library(it+1L,it==0)}).encode()}
        reject {i.copy(libraries=listOf(library(1,true).copy(recordBytes=Long.MAX_VALUE))).encode()}
    }
    @Test fun duplicateObjectOrVersionUnknownHashNegativeLengthAndPayloadSubstitutionRefuse() {
        val i=index();val member=MedicalCollectionIndex.Member(id(700),id(701),56,"c".repeat(64))
        for(members in listOf(listOf(member,member),listOf(member,member.copy(version=id(702))),listOf(member.copy(bytes=-1)),listOf(member.copy(sha256="C".repeat(64)))))
            reject {i.copy(libraries=listOf(library(1,true).copy(members=members))).encode()}
        reject {i.verifyLibrary(id(1),ByteArray(100),ByteArray(200))}
    }
    @Test fun v2HeaderIsExplicitAndV1ReaderStillRejectsItBeforeKdf() {
        val v1=MedicalArchiveCrypto.fresh(1000);val v2=MedicalArchiveCrypto.freshCollection(1000)
        assertEquals("PNMEDBK1",String(v1.bytes(),0,8,Charsets.US_ASCII))
        assertEquals("PNMEDBK2",String(v2.bytes(),0,8,Charsets.US_ASCII))
        assertArrayEquals(v1.bytes(),MedicalArchiveCrypto.parse(v1.bytes(),v1.ciphertextBytes+80).bytes())
        assertEquals(v2.archive,MedicalArchiveCrypto.parseCollection(v2.bytes(),v2.ciphertextBytes+80).archive)
        reject {MedicalArchiveCrypto.parse(v2.bytes(),v2.ciphertextBytes+80)}
        reject {MedicalArchiveCrypto.parseCollection(v1.bytes(),v1.ciphertextBytes+80)}
        reject {MedicalArchiveCrypto.parseCollection(v2.bytes(),v2.ciphertextBytes+81)}
    }
    @Test fun wrappingBindsLibraryAndProtocolAndNeverReusesAnAttempt() {
        val header=MedicalArchiveCrypto.freshCollection(1000);val key=ByteArray(32) {3};val dek=ByteArray(32) {7}
        val context=AttachmentObjectContext(id(1),id(2),id(3),id(4),id(5))
        val attempt=MedicalArchiveCrypto.CollectionWrapAttempt(header,key,listOf(id(10) to context,id(11) to context))
        val wrapped=attempt.wrap(id(10),context,dek)
        assertArrayEquals(dek,MedicalArchiveCrypto.unwrap(header,key,context,wrapped,id(10)))
        reject {MedicalArchiveCrypto.unwrap(header,key,context,wrapped,id(11))}
        reject {MedicalArchiveCrypto.unwrap(header,key,context,wrapped)}
        reject {attempt.wrap(id(10),context,dek)}
        val other=attempt.wrap(id(11),context,dek)
        assertArrayEquals(dek,MedicalArchiveCrypto.unwrap(header,key,context,other,id(11)))
        reject {MedicalArchiveCrypto.WrapAttempt(header,key,listOf(context))}
        reject {MedicalArchiveCrypto.CollectionWrapAttempt(header,key,listOf(id(10) to context,id(10) to context))}
        key.fill(0);dek.fill(0)
    }
}
