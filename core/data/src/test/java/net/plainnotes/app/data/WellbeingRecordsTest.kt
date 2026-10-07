package net.plainnotes.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class WellbeingRecordsTest {
    private lateinit var db:NotesDatabase
    private lateinit var repo:NotesRepository
    private val day=LocalDate.of(2026,2,10)
    @Before fun open(){val c=ApplicationProvider.getApplicationContext<Context>();db=Room.inMemoryDatabaseBuilder(c,NotesDatabase::class.java).allowMainThreadQueries().addCallback(SchemaGuards).build();repo=NotesRepository(object:DatabaseAccess(c){override fun get(space:Space)=db})}
    @After fun close(){db.close()}
    @Test fun reviewSymptomAndVisibilityRoundTripThroughEncryptedBackup()=runBlocking {
        val items=repo.checkinItems();repo.setScore(day,items.first().id,4)
        repo.setSymptomCheck(day,"JAUNDICE",true,"Synthetic note")
        val id=repo.saveStageReview(StageReviewEntity(date=day.toString(),effects_json="{\"FAT\":\"NOTICED\",\"FAT:note\":\"Synthetic\"}",smoking="NO",systolic=110,diastolic=70,weight_kg=60.0,satisfaction=3))
        repo.setReviewEffect("FAT",false)
        val review=repo.stageReviews().single();assertEquals(id,review.id)
        val archive=repo.exportBackup("synthetic-password".toCharArray())
        repo.restoreBackup(archive,"synthetic-password".toCharArray())
        assertEquals(listOf(review),repo.stageReviews());assertEquals(listOf(ReviewEffectEntity("FAT",false)),repo.reviewEffects())
        assertEquals("Synthetic note",repo.symptomChecks(day,day).single().note)
        assertEquals(4,repo.scores(day,day).single().value)
        repo.deleteStageReview(id);assertTrue(repo.stageReviews().isEmpty())
        repo.setSymptomCheck(day,"JAUNDICE",false);assertTrue(repo.symptomChecks(day,day).isEmpty())
    }
    @Test fun orderingDoesNotChangeScoresAndRejectsMissingItems()=runBlocking {
        val items=repo.checkinItems();repo.setScore(day,items.first().id,2)
        val reverse=items.reversed().map{it.id};repo.reorderCheckinItems(reverse)
        assertEquals(reverse,repo.checkinItems().map{it.id});assertEquals(2,repo.scores(day,day).single().value)
        try{repo.reorderCheckinItems(reverse.dropLast(1));fail()}catch(_:IllegalArgumentException){}
        assertEquals(reverse,repo.checkinItems().map{it.id})
    }
    @Test fun invalidReviewCannotReplaceExistingPersonalRecord()=runBlocking {
        val id=repo.saveStageReview(StageReviewEntity(date=day.toString(),weight_kg=60.0))
        val before=repo.stageReviews()
        listOf(before.single().copy(date="not-a-date"),before.single().copy(weight_kg=Double.NaN),before.single().copy(effects_json="{\"FAT\":\"DIAGNOSIS\"}"),before.single().copy(satisfaction=6)).forEach{v->
            assertEquals(id,v.id);try{repo.saveStageReview(v);fail()}catch(_:Exception){};assertEquals(before,repo.stageReviews())
        }
    }
}
