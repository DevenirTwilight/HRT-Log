package net.plainnotes.app.ui

import net.plainnotes.app.reminder.TestOutcome
import net.plainnotes.app.reminder.ReminderTestStatus
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
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

class Appearance(val mode: ThemeMode, val dynamic: Boolean, val contrast: Contrast = Contrast.STANDARD)

/**
 * UI-02 A2: settings overview plus six pages. Every section below is the same composable as before, only rendered on its
 * page; the slots carry the sections AppShell owns (region, privacy/disguise, data, recycle bin). The reminder checks are
 * computed once here and feed both the overview summary and the reminders page.
 */
@Composable fun SettingsScreen(appearance: Appearance, onAppearance: (Appearance) -> Unit, highReliability: Boolean, onHighReliability: (Boolean) -> Unit,
                               onSync: () -> Unit, onTest: () -> Unit, contentPadding: PaddingValues, wellbeingPrompt: Boolean = true, onWellbeingPrompt: (Boolean) -> Unit = {},
                               page: SettingsPage = SettingsPage.OVERVIEW, onPage: (SettingsPage) -> Unit = {}, summaries: SettingsSummaries = SettingsSummaries(),
                               wellbeingSlot: @Composable ColumnScope.() -> Unit = {}, privacySlot: @Composable ColumnScope.() -> Unit = {},
                               dataSlot: @Composable ColumnScope.() -> Unit = {}, binSlot: @Composable ColumnScope.() -> Unit = {},
                               testStatus: ReminderTestStatus = ReminderTestStatus(), onRefreshTest: () -> Unit = {}) {
    val context = LocalContext.current; val inspection = LocalInspectionMode.current
    val revision = resumeRevision()
    var reliabilityInfo by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onSync() }
    fun open(action: String, packageUri: Boolean = false) = runCatching { context.startActivity(Intent(action).apply { if (packageUri) data = Uri.parse("package:${context.packageName}") }) }
    val exact = remember(revision) { inspection || Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val notifications = remember(revision) { inspection || NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val battery = remember(revision) { inspection || context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val channel = remember(revision) { inspection || context.getSystemService(NotificationManager::class.java).getNotificationChannel("reminders")?.importance != NotificationManager.IMPORTANCE_NONE }
    val problems = listOf(exact, notifications && channel, battery).count { !it }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(Spacing.l).testTag("settings-page:${page.name}"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (page == SettingsPage.OVERVIEW) {
            val themeLabel = stringResource(when (appearance.mode) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark })
            SettingsRow(SettingsPage.REMINDERS, if (problems == 0) stringResource(R.string.settings_all_ok) else stringResource(R.string.settings_n_need_action, problems), problems > 0, onPage)
            SettingsRow(SettingsPage.APPEARANCE, stringResource(R.string.settings_appearance_summary, themeLabel, languageLabel()), false, onPage)
            SettingsRow(SettingsPage.WELLBEING, summaries.wellbeing, false, onPage)
            SettingsRow(SettingsPage.PRIVACY, summaries.privacy, false, onPage)
            SettingsRow(SettingsPage.DATA, summaries.data, false, onPage)
            SettingsRow(SettingsPage.BIN, summaries.bin, false, onPage)
        }
        if (page == SettingsPage.REMINDERS) SectionCard(stringResource(R.string.reliability)) {
            CheckRow(Icons.Outlined.Alarm, stringResource(R.string.check_exact), exact, stringResource(R.string.check_exact_bad)) {
                if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true)
            }
            CheckRow(Icons.Outlined.Notifications, stringResource(R.string.check_notifications), notifications && channel, stringResource(R.string.check_notifications_bad)) {
                if (Build.VERSION.SDK_INT >= 33 && !notifications) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            }
            CheckRow(Icons.Outlined.BatteryChargingFull, stringResource(R.string.check_battery), battery, stringResource(R.string.check_battery_bad)) {
                // This app's own system info page (no new permission); the battery setting is one tap away there.
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
                    .onFailure { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.manufacturer, Build.MANUFACTURER), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.vendor_guidance), style = MaterialTheme.typography.bodySmall)
                }
            }
            FilledTonalButton(onClick = onTest) { Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.test_reminder)) }
            ReminderTestStatusText(testStatus, onRefreshTest)
            SwitchRow(stringResource(R.string.high_reliability), highReliability, stringResource(R.string.high_privacy)) { onHighReliability(it) }
            TextButton(onClick = { reliabilityInfo = !reliabilityInfo }) { Text(stringResource(R.string.reliability_limits_title)) }
            if (reliabilityInfo) Text(stringResource(R.string.reliability_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (page == SettingsPage.APPEARANCE) SectionCard(stringResource(R.string.appearance)) {
            val themes=ThemeMode.entries.map{m->stringResource(when(m){ThemeMode.SYSTEM->R.string.theme_system;ThemeMode.LIGHT->R.string.theme_light;ThemeMode.DARK->R.string.theme_dark})}
            val contrasts=Contrast.entries.map{c->stringResource(when(c){Contrast.STANDARD->R.string.contrast_low;Contrast.MEDIUM->R.string.contrast_medium;Contrast.HIGH->R.string.contrast_high})}
            Text(stringResource(R.string.theme),style=MaterialTheme.typography.labelLarge)
            AdaptiveButtonChoice(themes,ThemeMode.entries.indexOf(appearance.mode),{onAppearance(Appearance(ThemeMode.entries[it],appearance.dynamic,appearance.contrast))},groupLabels=themes+contrasts)
            if(Build.VERSION.SDK_INT>=31)SwitchRow(stringResource(R.string.dynamic_color),appearance.dynamic,stringResource(R.string.dynamic_color_desc)){onAppearance(Appearance(appearance.mode,it,appearance.contrast))}
            Text(stringResource(R.string.contrast),style=MaterialTheme.typography.labelLarge)
            AdaptiveButtonChoice(contrasts,Contrast.entries.indexOf(appearance.contrast),{onAppearance(Appearance(appearance.mode,appearance.dynamic,Contrast.entries[it]))},groupLabels=themes+contrasts)
        }
        if (page == SettingsPage.WELLBEING) {
            SectionCard(stringResource(R.string.wellbeing)) {
                SwitchRow(stringResource(R.string.wb_prompt_setting), wellbeingPrompt, stringResource(R.string.wb_prompt_setting_desc)) { onWellbeingPrompt(it) }
            }
            wellbeingSlot()
        }
        if (page == SettingsPage.PRIVACY) privacySlot()
        if (page == SettingsPage.DATA) dataSlot()
        if (page == SettingsPage.BIN) binSlot()
        if (page == SettingsPage.APPEARANCE) SectionCard(stringResource(R.string.language)) {
            val current = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val options = listOf("" to stringResource(R.string.language_system), "en-US" to "English", "fr-FR" to "Français", "zh-CN" to "简体中文", "zh-TW" to "繁體中文")
            DropdownField(stringResource(R.string.language), options.map { it.first }, options.firstOrNull { it.first == current }?.first ?: "", { t -> options.first { it.first == t }.second }, { tag ->
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) androidx.core.os.LocaleListCompat.getEmptyLocaleList() else androidx.core.os.LocaleListCompat.forLanguageTags(tag))
            })
        }
    }
}

/** The six settings pages after the overview; titles are reused for the top bar. */
enum class SettingsPage(val title: Int, val icon: ImageVector) {
    OVERVIEW(R.string.settings, Icons.Outlined.Settings),
    REMINDERS(R.string.settings_reminders, Icons.Outlined.NotificationsActive),
    APPEARANCE(R.string.settings_appearance_language, Icons.Outlined.Palette),
    WELLBEING(R.string.wellbeing, Icons.Outlined.FavoriteBorder),
    PRIVACY(R.string.settings_privacy_security, Icons.Outlined.Lock),
    DATA(R.string.settings_data_backup, Icons.Outlined.Storage),
    BIN(R.string.trash_title, Icons.Outlined.Delete),
}

/** One-line overview states that AppShell knows (the reminder one is computed here). */
data class SettingsSummaries(val wellbeing: String = "", val privacy: String = "", val data: String = "", val bin: String = "")

@Composable private fun languageLabel(): String {
    val current = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return when (current) { "en-US" -> "English"; "fr-FR" -> "Français"; "zh-CN" -> "简体中文"; "zh-TW" -> "繁體中文"; else -> stringResource(R.string.language_system) }
}

/** ≥56dp overview row; the row is one button whose spoken name is title and summary together. */
@Composable private fun SettingsRow(page: SettingsPage, summary: String, attention: Boolean, onPage: (SettingsPage) -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = { onPage(page) }, shape = Corners.list, color = c.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("settings-row:${page.name}")) {
        Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(page.icon, null, tint = if (attention) c.tertiary else c.primary)
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text(stringResource(page.title), style = MaterialTheme.typography.titleMedium)
                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium,
                    color = if (attention) c.tertiary else c.onSurfaceVariant, fontWeight = if (attention) FontWeight.SemiBold else null)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
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
    var privacyNotice by rememberSaveable { mutableStateOf(false) }
    if (privacyNotice) PrivacyNoticeDialog { privacyNotice = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(null) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.version, version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SourceIdentitySection(remember { SourceIdentity.current(context) })
        SectionCard(stringResource(R.string.privacy)) {
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { privacyNotice = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("privacy-notice-open"), contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.privacy_notice_open))
            }
        }
        SectionCard(stringResource(R.string.credits)) { Text(stringResource(R.string.credits_body), style = MaterialTheme.typography.bodyMedium) }
        SectionCard(stringResource(R.string.chrysalide_title)) {
            Text(stringResource(R.string.chrysalide_body), style = MaterialTheme.typography.bodyMedium)
            LinkButton(stringResource(R.string.chrysalide_link), "https://www.chrysalide-asso.fr/")
        }
        SectionCard(stringResource(R.string.pk_origin_title)) {
            Text(stringResource(R.string.pk_origin_body), style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(stringResource(R.string.licenses)) {
            Text(stringResource(R.string.licenses_body), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.licenses_bouncycastle_title), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.licenses_bouncycastle_notice), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("licence-bouncycastle"))
        }
    }
}

/** Opens a web page in the browser (the app itself has no internet access). */
@Composable private fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, contentPadding = PaddingValues(0.dp)) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label)
    }
}

@Composable fun ComingSoonScreen(icon: ImageVector, title: String, contentPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        EmptyState(icon, title, stringResource(R.string.coming_soon_body))
    }
}

/** One line per observed stage of the scheduled test; the settings check at tap time posts nothing. */
@Composable private fun ReminderTestStatusText(status: ReminderTestStatus, onRefresh: () -> Unit) {
    val time = remember { java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.MEDIUM) }
    fun at(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).format(time)
    @Composable fun outcome(o: TestOutcome) = stringResource(when (o) {
        TestOutcome.SHOWN -> R.string.test_out_shown; TestOutcome.POSTED_NOT_SHOWN -> R.string.test_out_not_shown
        TestOutcome.BLOCKED_PERMISSION -> R.string.test_out_permission; TestOutcome.BLOCKED_APP -> R.string.test_out_app; TestOutcome.BLOCKED_CHANNEL -> R.string.test_out_channel })
    val pending = status.scheduledAt > 0 && status.receivedAt == 0L
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // While a scheduled test is pending, re-read the recorded stages so a delivery shows up without leaving the page.
    LaunchedEffect(status) { while (pending) { kotlinx.coroutines.delay(5_000); now = System.currentTimeMillis(); onRefresh() } }
    val lines = buildList {
        if (status.scheduledAt > 0) {
            val mode = stringResource(when { !status.exact -> R.string.test_mode_inexact; status.mode == "HIGH" -> R.string.test_mode_high; else -> R.string.test_mode_exact })
            add(stringResource(R.string.test_scheduled, at(status.triggerAt), mode))
            add(status.blockedAtSchedule?.let { stringResource(R.string.test_check_blocked, outcome(it)) } ?: stringResource(R.string.test_check_ok))
            val received = status.outcome
            if (received != null) add(stringResource(R.string.test_received, at(status.receivedAt), outcome(received)))
            else if (now > status.triggerAt + 2 * 60_000) add(stringResource(R.string.test_missing))
        }
    }
    if (lines.isNotEmpty()) Text(lines.joinToString("\n"), Modifier.testTag("reminder-test-status"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
