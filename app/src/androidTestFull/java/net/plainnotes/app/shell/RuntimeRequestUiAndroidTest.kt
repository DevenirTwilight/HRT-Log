package net.plainnotes.app.shell

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.NotesViewModel
import net.plainnotes.app.R
import net.plainnotes.app.data.AppointmentEntity
import net.plainnotes.app.debug.CalendarTestActivity
import net.plainnotes.app.runtime.RuntimeLibrarySession
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real Compose shell over its existing synthetic native SQLite debug fixture. Core's separate
 * RuntimeLibraryRequestAndroidTest verifies the full SQLCipher/attachment generation switch.
 */
class RuntimeRequestUiAndroidTest {
    @get:Rule val ui=createAndroidComposeRule<CalendarTestActivity>()
    @Test fun restoreInvalidatesOldPageAndOnlyExplicitNewOwnerCanModifySameInteger() {
        assumeTrue(Build.HARDWARE in setOf("ranchu","goldfish"));System.loadLibrary("sqlcipher")
        ui.waitUntil(20000) {ui.activity.model.librarySession.value is RuntimeLibrarySession.State.Ready}
        val activity=ui.activity;val repository=activity.repository
        val id=runBlocking {repository.saveAppointment(AppointmentEntity(type="ENDO",at_utc=1000,at_zone="UTC",note="SYNTHETIC restored target",remind_minutes_before=0))}
        val archive=runBlocking {repository.exportBackup("SYNTHETIC ordinary backup".toCharArray())}
        val target=runBlocking {repository.appointments().single {it.id==id}}
        runBlocking {repository.saveAppointment(target.copy(note="SYNTHETIC old displayed fact"))}
        lateinit var old:NotesViewModel;lateinit var refresh:Job
        ui.runOnUiThread {old=activity.model;refresh=old.refresh()};runBlocking {refresh.join()}
        // A later shell refresh may cancel this Job. Joining a cancelled read
        // is not proof that the displayed snapshot already contains the target.
        ui.waitUntil(20000) {old.state.value.appointments.any {it.id==id && it.note=="SYNTHETIC old displayed fact"}}
        assertEquals("SYNTHETIC old displayed fact",old.state.value.appointments.single {it.id==id}.note)
        val doseBefore=runBlocking {repository.medications()}.map {it.dose_per_intake}
        runBlocking {repository.restoreBackup(archive,"SYNTHETIC ordinary backup".toCharArray())};archive.fill(0)
        ui.runOnUiThread {refresh=old.refresh()};runBlocking {refresh.join()}
        ui.waitUntil(20000) {old.librarySession.value is RuntimeLibrarySession.State.Unavailable}
        ui.onNodeWithTag("library-request-boundary").assertIsDisplayed()
        lateinit var stale:Job;ui.runOnUiThread {stale=old.deleteAppointment(id)};runBlocking {stale.join()}
        assertEquals("SYNTHETIC restored target",runBlocking {repository.appointments().single {it.id==id}}.note)
        ui.onNodeWithText(activity.getString(R.string.library_scope_reopen)).performClick()
        ui.waitUntil(20000) {activity.model!==old && activity.model.librarySession.value is RuntimeLibrarySession.State.Ready}
        assertNotSame(old,activity.model)
        lateinit var fresh:Job;ui.runOnUiThread {fresh=activity.model.deleteAppointment(id)};runBlocking {fresh.join()}
        assertFalse(runBlocking {repository.appointments()}.any {it.id==id})
        assertEquals(doseBefore,runBlocking {repository.medications()}.map {it.dose_per_intake})
    }
}
