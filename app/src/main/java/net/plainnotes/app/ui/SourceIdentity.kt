package net.plainnotes.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import net.plainnotes.app.BuildConfig
import net.plainnotes.app.R

/**
 * Audit B1: the commit an installed build was made from, so a phone's app can be traced to its source.
 * The commit time is when the commit was made, not when the APK was built. Nothing here touches health data.
 */
data class SourceIdentity(val sha: String, val commitTimeUtc: String, val origin: String, val dirty: Boolean, val versionName: String, val versionCode: Long) {
    /** Plain key=value lines, not localized. Deliberately omits the app name so the clipboard does not reveal it when the app is disguised. */
    fun copyText() = listOf("version_name=$versionName", "version_code=$versionCode", "source_sha=$sha", "source_commit_time_utc=$commitTimeUtc",
        "source_origin=$origin", "source_dirty=$dirty").joinToString("\n")

    /** "2026-10-12 13:37:51": a space instead of T/Z so narrow screens wrap between date and time; the label says UTC. */
    fun displayTime() = commitTimeUtc.removeSuffix("Z").replace('T', ' ')

    companion object {
        fun current(context: Context): SourceIdentity {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            return SourceIdentity(BuildConfig.SOURCE_SHA, BuildConfig.SOURCE_COMMIT_TIME_UTC, BuildConfig.SOURCE_ORIGIN, BuildConfig.SOURCE_DIRTY,
                info?.versionName ?: "", info?.let(PackageInfoCompat::getLongVersionCode) ?: 0L)
        }
    }
}

@Composable fun SourceIdentitySection(identity: SourceIdentity) {
    val context = LocalContext.current
    var copied by remember(identity) { mutableStateOf(false) }
    val unknown = stringResource(R.string.about_source_unknown)
    SectionCard(stringResource(R.string.about_source_title), Modifier.testTag("about-source")) {
        Field(stringResource(R.string.about_source_commit), if (identity.sha == "unknown") unknown else identity.sha, "about-source-sha", monospace = true)
        Field(stringResource(R.string.about_source_time), if (identity.commitTimeUtc == "unknown") unknown else identity.displayTime(), "about-source-time", monospace = true)
        Field(stringResource(R.string.about_source_origin), stringResource(when (identity.origin) {
            "git" -> R.string.about_source_origin_git
            "declared-unverified" -> R.string.about_source_origin_declared
            else -> R.string.about_source_origin_unknown
        }), "about-source-origin")
        if (identity.dirty) Text(stringResource(R.string.about_source_dirty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("about-source-dirty"))
        OutlinedButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.about_source_title), identity.copyText()))
            copied = true
        }, modifier = Modifier.testTag("about-source-copy")) {
            Icon(if (copied) Icons.Outlined.Done else Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(stringResource(if (copied) R.string.about_source_copied else R.string.about_source_copy))
        }
    }
}

@Composable private fun Field(label: String, value: String, tag: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Selectable so the full value can also be copied by hand; long values wrap instead of being cut off.
        SelectionContainer {
            Text(value, style = if (monospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(tag))
        }
    }
}
