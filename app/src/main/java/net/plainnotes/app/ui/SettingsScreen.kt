package net.plainnotes.app.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.plainnotes.app.R

/** Increments whenever the screen resumes, so system-setting checks refresh after returning from Settings. */
@Composable fun resumeRevision(): Int {
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    return revision
}

class Appearance(val mode: ThemeMode, val dynamic: Boolean)

@Composable fun SettingsScreen(appearance: Appearance, onAppearance: (Appearance) -> Unit, highReliability: Boolean, onHighReliability: (Boolean) -> Unit,
                               onSync: () -> Unit, onTest: () -> Unit, contentPadding: PaddingValues, wellbeingPrompt: Boolean = true, onWellbeingPrompt: (Boolean) -> Unit = {},
                               extra: @Composable ColumnScope.() -> Unit = {}) {
    val context = LocalContext.current; val inspection = LocalInspectionMode.current
    val revision = resumeRevision()
    var reliabilityInfo by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onSync() }
    fun open(action: String, packageUri: Boolean = false) = runCatching { context.startActivity(Intent(action).apply { if (packageUri) data = Uri.parse("package:${context.packageName}") }) }
    val exact = remember(revision) { inspection || Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val notifications = remember(revision) { inspection || NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val battery = remember(revision) { inspection || context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val channel = remember(revision) { inspection || context.getSystemService(NotificationManager::class.java).getNotificationChannel("reminders")?.importance != NotificationManager.IMPORTANCE_NONE }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(stringResource(R.string.reliability)) {
            CheckRow(Icons.Outlined.Alarm, stringResource(R.string.check_exact), exact, stringResource(R.string.check_exact_bad)) {
                if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true)
            }
            CheckRow(Icons.Outlined.Notifications, stringResource(R.string.check_notifications), notifications && channel, stringResource(R.string.check_notifications_bad)) {
                if (Build.VERSION.SDK_INT >= 33 && !notifications) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            }
            CheckRow(Icons.Outlined.BatteryChargingFull, stringResource(R.string.check_battery), battery, stringResource(R.string.check_battery_bad)) { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.manufacturer, Build.MANUFACTURER), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.vendor_guidance), style = MaterialTheme.typography.bodySmall)
                }
            }
            FilledTonalButton(onClick = onTest) { Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.test_reminder)) }
            SwitchRow(stringResource(R.string.high_reliability), highReliability, stringResource(R.string.high_privacy)) { onHighReliability(it) }
            TextButton(onClick = { reliabilityInfo = !reliabilityInfo }) { Text(stringResource(R.string.reliability_limits_title)) }
            if (reliabilityInfo) Text(stringResource(R.string.reliability_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.appearance)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(appearance.mode == m, { onAppearance(Appearance(m, appearance.dynamic)) }, SegmentedButtonDefaults.itemShape(i, 3)) {
                        Text(stringResource(when (m) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }))
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) SwitchRow(stringResource(R.string.dynamic_color), appearance.dynamic, stringResource(R.string.dynamic_color_desc)) { onAppearance(Appearance(appearance.mode, it)) }
        }
        SectionCard(stringResource(R.string.wellbeing)) {
            SwitchRow(stringResource(R.string.wb_prompt_setting), wellbeingPrompt, stringResource(R.string.wb_prompt_setting_desc)) { onWellbeingPrompt(it) }
        }
        extra()
        SectionCard(stringResource(R.string.language)) {
            Text(stringResource(R.string.language_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { open(Settings.ACTION_APP_LOCALE_SETTINGS, true) }) { Text(stringResource(R.string.language_open)) }
        }
    }
}

@Composable private fun CheckRow(icon: ImageVector, label: String, ok: Boolean, bad: String, onFix: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (ok) c.primary else c.error)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(if (ok) stringResource(R.string.check_ok) else bad, style = MaterialTheme.typography.bodySmall, color = if (ok) c.onSurfaceVariant else c.error)
        }
        if (!ok) TextButton(onClick = onFix) { Text(stringResource(R.string.fix)) }
        else Icon(Icons.Outlined.CheckCircle, null, tint = c.primary)
    }
}

@Composable fun AboutScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(null) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.privacy)) { Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium) }
        SectionCard(stringResource(R.string.credits)) { Text(stringResource(R.string.credits_body), style = MaterialTheme.typography.bodyMedium) }
        SectionCard(stringResource(R.string.licenses)) {
            Text(stringResource(R.string.licenses_body), style = MaterialTheme.typography.bodySmall)
            Text(UPSTREAM_MIT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun ComingSoonScreen(icon: ImageVector, title: String, contentPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        EmptyState(icon, title, stringResource(R.string.coming_soon_body))
    }
}

private const val UPSTREAM_MIT = """Transmtf-HRT-Tracker — MIT License

Copyright (c) 2025 Transmtf Team

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""
