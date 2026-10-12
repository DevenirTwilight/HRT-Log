#!/usr/bin/env python3
"""Compare complete previews against the previous delivered source using synthetic history.
Requires Android SDK/full JDK and git history; generated test sources are removed in finally.
Does not change production source, locked kernels, signing, or user data.
"""
import subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASELINE="9a06fc794e82a7ed079ee8011c0fe9ed57f4d46c"
TEST_SOURCE='package net.plainnotes.app.experimental\nimport net.plainnotes.app.conc.experimental.SlAdapterAudit\nimport net.plainnotes.app.pk.*\nimport net.plainnotes.app.pk.experimental.*\nimport org.junit.Test\nimport org.junit.Assert.*\nclass PreviewPerformanceComparisonTest {\n @Test fun identicalCompletePreviewBeforeAndAfter() {\n  val hour=600000.0\n  val audit=SlAdapterAudit((0 until 360).map { DoseEvent("synthetic-$it",Route.SUBLINGUAL,hour-3-it*12.0,1.0,Ester.E2,70.0,DoseExtras(sublingualTier=2.0)) },emptyMap())\n  for(model in listOf(ResearchModel.M1,ResearchModel.M2)) {\n   var start=System.nanoTime()\n   val before=OriginalConcentrationPreview.compute(audit,model,hour)\n   val oldMs=(System.nanoTime()-start)/1e6\n   start=System.nanoTime()\n   val after=ConcentrationPreview.compute(audit,model,hour)\n   val newMs=(System.nanoTime()-start)/1e6\n   assertEquals(before.currentPgMl!!,after.currentPgMl!!,1e-8)\n   assertEquals(before.historyWindowPeak!!.pgMl,after.historyWindowPeak!!.pgMl,1e-8)\n   assertEquals(before.historyWindowPeak!!.hour,after.historyWindowPeak!!.hour,2e-6)\n   var maxError=0.0\n   for(i in before.timeH.indices) {\n    val actual=after.valueAt(before.timeH[i])!!\n    maxError=maxOf(maxError,kotlin.math.abs(before.values[i]-actual))\n    assertEquals(before.values[i],actual,1e-8)\n   }\n   println("COMPLETE_PREVIEW_COMPARISON model=$model doses=360 before_points=${before.timeH.size} after_points=${after.timeH.size} old_ms=$oldMs new_ms=$newMs max_abs_error=$maxError")\n  }\n }\n}\n'

def main():
    folder=ROOT/"app/src/test/java/net/plainnotes/app/performancegenerated"
    if folder.exists():
        raise RuntimeError("Refusing to overwrite existing generated test folder")
    original=subprocess.check_output(["git","show",BASELINE+":app/src/main/java/net/plainnotes/app/experimental/ConcentrationPreview.kt"],cwd=ROOT,text=True)
    folder.mkdir()
    try:
        (folder/"OriginalConcentrationPreview.kt").write_text(original.replace("ConcentrationPreview","OriginalConcentrationPreview"))
        (folder/"PreviewPerformanceComparisonTest.kt").write_text(TEST_SOURCE)
        subprocess.run([str(ROOT/"gradlew"),":app:testFullDebugUnitTest","--tests","*PreviewPerformanceComparisonTest","--max-workers=2"],cwd=ROOT,check=True)
        print("Complete same-host benchmark and per-point parity: app/build/test-results/testFullDebugUnitTest/TEST-net.plainnotes.app.experimental.PreviewPerformanceComparisonTest.xml")
    finally:
        for file in folder.iterdir(): file.unlink()
        folder.rmdir()
if __name__=="__main__":main()
