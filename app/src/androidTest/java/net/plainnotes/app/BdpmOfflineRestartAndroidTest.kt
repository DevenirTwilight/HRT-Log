package net.plainnotes.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.plainnotes.app.data.BdpmCatalogue
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host runs this only after disabling emulator networking and force-stopping the target process. */
class BdpmOfflineRestartAndroidTest {
    @Test fun freshOfflineProcessStillReadsInstalledOfficialCatalogue():Unit=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("bdpm_fresh_offline")=="1")
        val context=instrumentation.targetContext
        val permissions=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        val product=BdpmCatalogue(context).search("PROVAMES").entries.single{it.cis=="65421374"}
        assertEquals("PROVAMES 2 mg, comprimé pelliculé",product.name)
        assertTrue(product.packages.any{it.cip13=="3400935479341"})
    }
}
