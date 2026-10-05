package net.plainnotes.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.plainnotes.app.R

/** Full-screen PIN pad. [verify] returns true when the PIN is right; [waitMillis] reports a lockout delay. */
@Composable fun LockScreen(verify: (String) -> Boolean, waitMillis: () -> Long, biometric: (() -> Unit)?, title: String = stringResource(R.string.lock_title)) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(waitMillis()) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait = waitMillis() } }
    LaunchedEffect(Unit) { biometric?.invoke() }
    fun submit() { if (verify(pin)) return; error = true; pin = ""; wait = waitMillis() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(when { wait > 0 -> stringResource(R.string.lock_wait, ((wait + 999) / 1000).toInt()); error -> stringResource(R.string.lock_wrong); else -> " " },
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { k ->
                        when (k) {
                            "bio" -> Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { if (biometric != null) IconButton(onClick = biometric) { Icon(Icons.Outlined.Fingerprint, stringResource(R.string.lock_biometric), Modifier.size(32.dp)) } }
                            "del" -> Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { IconButton(onClick = { pin = pin.dropLast(1) }) { Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.remove)) } }
                            else -> FilledTonalButton(onClick = { if (wait == 0L && pin.length < 12) { pin += k; error = false } }, Modifier.size(72.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
                                Text(k, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = ::submit, enabled = pin.length >= 4 && wait == 0L, modifier = Modifier.width(200.dp)) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
