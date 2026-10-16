package net.plainnotes.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.domain.RuleKind
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ReferenceCatalogTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    @Before fun setup(){val context=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})}
    @After fun close(){db.close()}
    private fun product(name:String="SYNTHETIC Œstrogène A",version:String="user-v1")=UserReferenceProduct(name,listOf("SYNTHETIC estradiol free compound","SYNTHETIC ingredient B, hydrate"),"2 mg / tablet","Comprimé",listOf("Orale"),sourceVersion=version,recordedOn="2026-10-16")
    private suspend fun medication()=repo.saveMedication(MedicationEntity(name="SYNTHETIC actual medication",molecule="OTHER",unit="TABLET",dose_per_intake=1.0,container_capacity=28.0,soon_alert_minutes=0,late_after_minutes=60,site_rotation=false,notifications_on=false,active=true,sort_order=0),null,RuleKind.EVERY_N_DAYS,1,listOf(LocalTime.NOON),emptySet(),Instant.parse("2026-03-28T12:00:00Z"))
    private suspend fun initial(id:Long,operation:String,value:UserReferenceProduct)=repo.associateInitialMedication(id,operation,value,repo.medicationReferenceState(id).previewEpoch)
    @Test fun accentsCaseAndLigaturesAreSearchOnlyAndNeverChangeStoredNames()=runBlocking {
        val id=medication();initial(id,UUID.randomUUID().toString(),product())
        assertEquals(product(),repo.savedReferenceProducts("OESTROGENE","COMPRIME","ORALE").entries.single().product)
        assertEquals(1,repo.savedReferenceProducts("INGREDIENT B, HYDRATE").entries.size)
        assertTrue(repo.savedReferenceProducts("estradiol","gel","").entries.isEmpty())
        assertTrue(repo.savedReferenceProducts("","","vaginale").entries.isEmpty())
        assertEquals("SYNTHETIC Œstrogène A",repo.savedReferenceProducts().entries.single().product.name)
    }
    @Test fun differentCisStrengthsRoutesAndVersionsRemainSeparateSnapshots()=runBlocking {
        val id=medication();val first=initial(id,UUID.randomUUID().toString(),product().copy(cis="00000001",cip="0000000000001"))
        repo.associateMedication(first.scope!!,id,1,UUID.randomUUID().toString(),product(version="user-v2").copy(cis="00000002",strength="1 mg / capsule",form="capsule",routes=listOf("other recorded route")))
        val page=repo.savedReferenceProducts("estradiol")
        assertEquals(2,page.entries.size);assertEquals(2,page.entries.map{it.snapshotUuid}.toSet().size)
        assertEquals(setOf("00000001","00000002"),page.entries.map{it.product.cis}.toSet())
        assertEquals(setOf("user-v1","user-v2"),page.entries.map{it.product.sourceVersion}.toSet())
        assertEquals(1,repo.savedReferenceProducts(form="capsule").entries.size)
        assertEquals("00000001",repo.savedReferenceProducts(strength="2 mg").entries.single().product.cis)
        assertEquals("00000002",repo.savedReferenceProducts(strength="1 MG").entries.single().product.cis)
        assertTrue(repo.savedReferenceProducts(strength="2000 µg").entries.isEmpty())
    }
    @Test fun boundedMatchingResultsStillSearchPastTheFirstTwoHundredStoredSnapshots()=runBlocking {
        val id=medication();val initial=initial(id,UUID.randomUUID().toString(),product("SYNTHETIC entry-000"))
        for(i in 1..200)repo.associateMedication(initial.scope!!,id,i,UUID.randomUUID().toString(),product("SYNTHETIC entry-${i.toString().padStart(3,'0')}","user-$i"))
        val page=repo.savedReferenceProducts();assertEquals(200,page.entries.size);assertTrue(page.limited)
        val hidden=db.openHelper.writableDatabase.query("SELECT uuid,payload_json FROM reference_snapshot ORDER BY uuid DESC LIMIT 1").use {
            assertTrue(it.moveToFirst());SavedReferenceProduct(it.getString(0),ReferencePayload.readProduct(org.json.JSONObject(it.getString(1))))
        }
        assertTrue(page.entries.none {it.snapshotUuid==hidden.snapshotUuid})
        val exact=repo.savedReferenceProducts(hidden.product.name);assertEquals(hidden,exact.entries.single());assertFalse(exact.limited)
    }
    @Test fun loadingEmptyLibraryAndCancelledPreviewDoNotCreateDatasetOrIdentity()=runBlocking {
        val id=medication();assertTrue(repo.savedReferenceProducts().entries.isEmpty());assertNull(repo.medicationReferenceState(id).scope)
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM reference_dataset").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM reference_identity").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
    }
    @Test fun privateSpaceCannotReadReferenceLibraryBeforeDatabaseAccess()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val guarded=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space):NotesDatabase {fail("Wrong-space reference search opened DB");return db}}).pinnedTo(Space.DECOY)
        try{guarded.savedReferenceProducts();fail("Wrong space accepted")}catch(e:ReferenceFailure){assertEquals(ReferenceError.WRONG_SPACE,e.code)}
    }
    @Test fun publicDraftValidationRejectsInvalidDatesAndDoesNotCreateReferenceFacts() {
        assertEquals(product(),validatedUserReferenceProduct(product()))
        listOf(product().copy(recordedOn="2026-02-30"),product().copy(cip="0000000000001"),product().copy(sourceVersion="")).forEach {
            try{validatedUserReferenceProduct(it);fail("Invalid product accepted")}catch(e:ReferenceFailure){assertEquals(ReferenceError.INVALID_SOURCE,e.code)}
        }
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM reference_snapshot").use{it.moveToFirst();assertEquals(0,it.getInt(0))}
    }
}
