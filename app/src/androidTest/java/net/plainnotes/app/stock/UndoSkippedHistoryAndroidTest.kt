package net.plainnotes.app.stock
import net.plainnotes.app.history.UndoSkippedUiContract
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
@RunWith(Parameterized::class)
class UndoSkippedHistoryAndroidTest(language:String):UndoSkippedUiContract(language) {
    companion object { @JvmStatic @Parameterized.Parameters(name="{0}/320dp/2x") fun languages()=listOf("en","zh-CN","zh-TW","fr").map{arrayOf<Any>(it)} }
}
