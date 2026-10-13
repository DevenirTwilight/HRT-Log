package net.plainnotes.app

import android.net.Uri
import net.plainnotes.app.privacy.RestoreProtection
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class RestoreProtectionTest {
    private val source=Uri.parse("content://synthetic/source")
    private val target=Uri.parse("content://synthetic/current-output")
    @Test fun selectingSourceOnlyOffersAChoice(){val f=RestoreProtection();f.select(source);assertEquals(RestoreProtection.Stage.Choice(source),f.stage);assertNull(f.takeRestore(source))}
    @Test fun cancellingSourceOrChoiceResetsToIdle(){val f=RestoreProtection();f.select(null);assertEquals(RestoreProtection.Stage.Idle,f.stage);f.select(source);f.cancel();assertEquals(RestoreProtection.Stage.Idle,f.stage);assertNull(f.takeRestore(source))}
    @Test fun cancelledBackupPickerCannotAdvanceToRestore(){val f=RestoreProtection();f.select(source);f.chooseBackup();f.destination(null);assertEquals(RestoreProtection.Stage.Choice(source,RestoreProtection.Notice.CANCELLED),f.stage);assertNull("Cancelled backup must never authorize restore",f.takeRestore(source))}
    @Test fun sourceCannotBecomeTheBackupOutput(){val f=RestoreProtection();f.select(source);f.chooseBackup();f.destination(source);assertEquals(RestoreProtection.Stage.Choice(source,RestoreProtection.Notice.SAME_TARGET),f.stage);assertFalse(f.beginBackup(source,source));assertNull(f.takeRestore(source))}
    @Test fun successfulBackupPreservesSourceAndRequiresAnExplicitContinue(){val f=RestoreProtection();f.select(source);f.chooseBackup();f.destination(target);assertTrue(f.beginBackup(source,target));f.backupFinished(true);assertEquals(RestoreProtection.Stage.Choice(source,RestoreProtection.Notice.SAVED),f.stage);assertNull("Saving current data must not authorize restore",f.takeRestore(source));f.continueRestore();assertEquals(source,f.takeRestore(source));assertEquals(RestoreProtection.Stage.Idle,f.stage)}
    @Test fun failedBackupNeverAuthorizesRestore(){val f=RestoreProtection();f.select(source);f.chooseBackup();f.destination(target);f.beginBackup(source,target);f.backupFinished(false);assertEquals(RestoreProtection.Stage.Choice(source,RestoreProtection.Notice.FAILED),f.stage);assertNull(f.takeRestore(source))}
    @Test fun staleBackupAndRestoreCallbacksCannotUseAnotherSource(){val f=RestoreProtection();f.select(source);f.chooseBackup();f.destination(target);assertFalse(f.beginBackup(target,source));f.cancel();f.backupFinished(true);assertEquals(RestoreProtection.Stage.Idle,f.stage);f.select(source);f.continueRestore();assertNull(f.takeRestore(target));assertEquals(source,f.takeRestore(source));assertNull(f.takeRestore(source))}
    @Test fun cancelPasswordReturnsToChoiceWithoutASecret(){val f=RestoreProtection();f.select(source);f.chooseBackup();f.destination(target);f.cancelPassword();assertEquals(RestoreProtection.Stage.Choice(source),f.stage);f.continueRestore();f.cancelPassword();assertEquals(RestoreProtection.Stage.Choice(source),f.stage);assertNull(f.takeRestore(source))}
    @Test fun latePickerResultAfterRecreationIsIgnored(){val old=RestoreProtection();old.select(source);old.chooseBackup();val recreated=RestoreProtection();recreated.destination(target);recreated.backupFinished(true);assertEquals(RestoreProtection.Stage.Idle,recreated.stage);assertNull(recreated.takeRestore(source))}
}
