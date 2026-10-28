package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

abstract class BdpmCatalogueContract {
    protected open fun helperFactory():androidx.sqlite.db.SupportSQLiteOpenHelper.Factory?=null
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Test fun genuineBundledProvamesIngredientsIdentifiersAndReopenWithoutUserData()=runBlocking {
        val first=BdpmCatalogue(context).search("pRoVaMeS").entries.single{it.cis=="65421374"}
        assertEquals("PROVAMES 2 mg, comprimé pelliculé",first.name)
        assertEquals("3400935479341",first.packages.single().cip13)
        assertEquals(BdpmComposition("comprimé","01786","ESTRADIOL","2 mg","un comprimé","SA","1"),first.composition.single())
        assertEquals(first,BdpmCatalogue(context).search("3400935479341").entries.single())
        val ingredients=BdpmCatalogue(context).search("ESTRADIOL").entries
        assertTrue(ingredients.size>10);assertTrue(ingredients.any{!it.name.contains("ESTRADIOL",ignoreCase=true)})
        assertEquals(BdpmCatalogue.fold("œstrogène"),BdpmCatalogue.fold("OESTROGENE"))
        assertTrue(BdpmCatalogue(context).search("NO_SYNTHETIC_MATCH_728340").entries.isEmpty())
    }
    @Test fun corruptedPublicCopyIsRepairedWithoutTouchingEncryptedData():Unit=runBlocking {
        val catalogue=BdpmCatalogue(context);val expected=catalogue.search("65421374").entries
        val unrelated=File(context.noBackupFilesDir,"synthetic-user-sentinel").apply{writeText("SYNTHETIC untouched")}
        val expectedHash=JSONObject(context.assets.open("bdpm/manifest.json").bufferedReader().use {it.readText()}).getString("database_sha256")
        val copy=File(context.noBackupFilesDir,"bdpm/$expectedHash.sqlite");assertTrue(copy.isFile);copy.writeText("broken public copy")
        assertEquals(expected,BdpmCatalogue(context).search("65421374").entries)
        assertEquals("SYNTHETIC untouched",unrelated.readText());unrelated.delete()
    }
    @Test fun officialAssociationIsImmutableSeparateFromUserSourcesAndBacksUpSafely()=runBlocking {
        val builder=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards)
        helperFactory()?.let{builder.openHelperFactory(it)}
        val db=builder.build()
        try {
            val repo=NotesRepository(object:DatabaseAccess(context){override fun get(space:Space)=db})
            val med=db.dao().insertMedication(MedicationEntity(name="SYNTHETIC owned medication",molecule="OTHER",unit="MG",dose_per_intake=0.5,container_capacity=37.0,site_rotation=false,notifications_on=false,active=false,sort_order=0))
            val before=db.dao().medication(med)
            assertEquals(12,RecordBackupCompatibility.exportSchema(db.openHelper.writableDatabase))
            val catalogue=BdpmCatalogue(context);val p=catalogue.search("65421374").entries.single();val snapshot=catalogue.snapshot(p,p.packages.single())
            val op=UUID.randomUUID().toString();val preview=repo.medicationReferenceState(med).previewEpoch
            repo.associateInitialMedication(med,op,snapshot,preview)
            val original=repo.medicationReferenceState(med).reference!!
            assertEquals(snapshot,original.product);assertTrue(repo.savedReferenceProducts().entries.isEmpty())
            val raw=db.openHelper.writableDatabase
            ReferenceSchema.validateCandidateReferences(raw)
            db.withTransaction {assertTrue(MeasuredConsumptionStore.auditStagedDataset(raw).isEmpty())}
            assertEquals(13,RecordBackupCompatibility.exportSchema(raw))
            try{RecordBackupCompatibility.readVersion(JSONObject().put("format",1).put("schema",13),12);fail("Old reader accepted official schema")}catch(_:BackupCodec.NewerBackup){}
            assertEquals(0,repo.records().size) // Association did not create a medical record.
            repo.unscheduled(med,java.time.Instant.parse("2026-10-22T10:00:00Z"),0.5)
            val recordedBefore=repo.records()
            val historyBefore=repo.recordReferenceContexts(repo.referenceScope(),recordedBefore.single().id)
            assertEquals(snapshot,historyBefore.single().product)
            val user=UserReferenceProduct("SYNTHETIC manual source",sourceVersion="manual-v1",recordedOn="2026-10-22")
            repo.associateMedication(original.let{repo.referenceScope()},med,1,UUID.randomUUID().toString(),user)
            assertEquals(user,repo.savedReferenceProducts().entries.single().product)
            val password="SYNTHETIC bdpm backup".toCharArray();val archive=repo.exportBackup(password)
            try {
                assertEquals(13,JSONObject(String(BackupCodec.decrypt(archive,password))).getInt("schema"))
                repo.restoreBackup(archive,password)
                assertEquals(user,repo.medicationReferenceState(med).reference!!.product)
                val old=raw.query("SELECT payload_json FROM reference_snapshot WHERE namespace='BDPM_FR'").use{it.moveToFirst();it.getString(0)}
                assertEquals(snapshot,ReferencePayload.readProduct(JSONObject(old)))
                assertEquals(before,db.dao().medication(med))
                assertEquals(0,db.dao().containers().size)
                assertEquals(recordedBefore,db.dao().records())
                assertEquals(historyBefore,repo.recordReferenceContexts(repo.referenceScope(),recordedBefore.single().id))
                ReferenceSchema.validateCandidateReferences(raw)
            }finally{archive.fill(0);password.fill('\u0000')}
        }finally{db.close()}
    }
    @Test fun officialProductionDetailsPreserveSourceDisagreementDatesAndIdentity()=runBlocking {
        val d=requireNotNull(BdpmCatalogue(context).official("65421374","PROVAMES 2 mg, comprimé pelliculé"))
        assertEquals("liste II",d.data.getJSONArray("cpd").getString(0))
        val smr=d.rows("assessments").single {it.getString("kind")=="SMR" && it.getString("dossier")=="CT-21206"}
        assertEquals("Modéré",smr.getString("value"));assertTrue(smr.getString("text").contains("reste important"))
        assertEquals("2025-09-24",smr.getString("date"))
        assertTrue(d.rows("assessments").any {it.getString("kind")=="ASMR" && it.getString("value")=="V"})
        assertEquals("G03CA03",d.rows("mitm").single().getString("atc"))
        assertTrue(d.rows("important").any {it.getString("start")=="2026-06-25"})
        val docs=d.data.getJSONObject("documents");assertTrue(docs.getBoolean("verified"));assertTrue(docs.getBoolean("rcp"));assertTrue(docs.getBoolean("notice"))
        assertEquals("https://base-donnees-publique.medicaments.gouv.fr/medicament/65421374/extrait",docs.getString("url"))
        assertEquals("2026-09-29",d.sourceDate("CIS_CPD_bdpm.txt"));assertNull(d.sourceDate("CIS_InfoImportantes.txt"))
        assertNull(BdpmCatalogue(context).official("00000000"))
        try {BdpmCatalogue(context).official("65421374","SYNTHETIC WRONG PRODUCT");fail("Accepted mismatched identity")}catch(_:IllegalArgumentException) {}
    }
    @Test fun officialCacheRepairAndBrowserLinksCannotAffectPrivateRecords()=runBlocking {
        val catalogue=BdpmCatalogue(context);val d=requireNotNull(catalogue.official("65421374"))
        val copy=File(context.noBackupFilesDir,"bdpm/"+d.provenance.getString("database_sha256")+".sqlite")
        copy.writeText("SYNTHETIC corrupted public copy")
        assertEquals(d.cis,requireNotNull(catalogue.official(d.cis)).cis)
        assertEquals("3400935479341",catalogue.search("65421374").entries.single().packages.single().cip13)
        assertTrue(BdpmOfficialLinks.safe(d.data.getJSONObject("documents").getString("url")))
        listOf("http://ansm.sante.fr/", "https://ansm.sante.fr.evil.invalid/", "https://user@ansm.sante.fr/", "https://ansm.sante.fr:8443/", "javascript:alert(1)","https://ansm.sante.fr/%0aevil", "https://ansm.sante.fr\\@evil.invalid/").forEach {assertFalse(it,BdpmOfficialLinks.safe(it))}
    }
    @Test fun malformedOfficialProvenanceAndForgedCatalogueSelectionFailClosed()=runBlocking {
        val catalogue=BdpmCatalogue(context);val p=catalogue.search("65421374").entries.single();val snapshot=catalogue.snapshot(p,null)
        val payload=ReferencePayload.userProduct(snapshot)
        assertEquals("BDPM_FR",payload.getString("namespace"));assertEquals(2,payload.getInt("format"))
        fun reject(block:()->Unit){try{block();fail("Accepted invalid provenance")}catch(_:IllegalArgumentException){}}
        val legacy=snapshot.copy(bdpm=requireNotNull(snapshot.bdpm).copy(downloadUrl="https://bdpmt.ansm.sante.fr/telechargement"))
        assertEquals(legacy,ReferencePayload.readProduct(ReferencePayload.userProduct(legacy)))
        for(url in listOf("https://evil.invalid/telechargement","https://base-donnees-publique.medicaments.gouv.fr/telechargement?unverified=1","https://base-donnees-publique.medicaments.gouv.fr.evil.invalid/telechargement")) {
            reject{ReferencePayload.userProduct(snapshot.copy(bdpm=requireNotNull(snapshot.bdpm).copy(downloadUrl=url)))}
        }
        reject{validatedUserReferenceProduct(snapshot)}
        reject{ReferencePayload.validateProduct(JSONObject(payload.toString()).put("namespace","USER_ENTERED"))}
        reject{ReferencePayload.validateProduct(JSONObject(payload.toString()).put("verification","UNVERIFIED"))}
        try{catalogue.snapshot(p.copy(name="SYNTHETIC fabricated official name"),null);fail("Accepted altered official row")}catch(_:IllegalArgumentException){}
        val tables=JSONObject().put("reference_snapshot",org.json.JSONArray().put(JSONObject().put("namespace","BDPM_FR")))
        reject{RecordBackupCompatibility.validateReferenceVersion(12,BackupTables.of(tables))}
    }
}
