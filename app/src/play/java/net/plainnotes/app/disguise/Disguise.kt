package net.plainnotes.app.disguise

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable

/** The Play build ships without disguise mode (docs/PLAN.md M7). */
object Disguise {
    const val AVAILABLE = false
    enum class Shell(val label: Int, val icon: Int)
    fun shell(c: Context): Shell? = null
    fun enabled(c: Context) = false
    fun disable(c: Context) {}
    fun exit(a: Activity) {}
}

@Suppress("UNUSED_PARAMETER") @Composable fun DisguiseSection(onDisabled: () -> Unit) {}
