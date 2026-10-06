package net.plainnotes.app.disguise

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.plainnotes.app.disguise.privatenotes.PrivateNotesActivity
import net.plainnotes.app.disguise.privatenotes.PrivateStore
import net.plainnotes.app.security.Session
import net.plainnotes.app.security.UnlockTarget
import org.junit.*
import org.junit.Assert.*

/** These phases run in separate instrumentation processes; scripts/run_disguise_restart.py orchestrates the force-stop. */
class ProcessRestartAndroidTest {
    private val instrument get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrument.targetContext
    @Test fun prepare() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("restart_phase")=="prepare")
        Disguise.disable(context);Disguise.enable(context,Disguise.Shell.CALCULATOR,"2468","1357")
        Session.begin(UnlockTarget.PRIVATE)
        PrivateStore(context).save(null,"Travel","Charger, passport")
        ActivityScenario.launch<PrivateNotesActivity>(Intent(context,PrivateNotesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertEquals(UnlockTarget.PRIVATE,Session.target)
    }
    @Test fun verifyFreshProcess() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("restart_phase")=="verify")
        try {
            assertNull("No process can restore an open authorization",Session.target)
            assertTrue(Disguise.enabled(context));assertTrue(Disguise.hasPrivateCode(context))
            ActivityScenario.launch<PrivateNotesActivity>(Intent(context,PrivateNotesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrument.waitForIdleSync()
            instrument.runOnMainSync {
                val resumed=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                assertTrue(resumed.any{it is CalculatorActivity});assertFalse(resumed.any{it is PrivateNotesActivity})
            }
            assertNull(Session.target)
            assertEquals(UnlockTarget.PRIVATE,Disguise.check(context,"1357"))
            Session.begin(UnlockTarget.PRIVATE)
            assertEquals("Charger, passport",PrivateStore(context).list().single().body)
        }finally {
            instrument.runOnMainSync{ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach{it.finish()}}
            Disguise.disable(context);Session.reset()
        }
    }
}
