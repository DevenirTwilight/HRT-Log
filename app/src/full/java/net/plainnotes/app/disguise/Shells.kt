package net.plainnotes.app.disguise

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.plainnotes.app.MainActivity
import net.plainnotes.app.R
import net.plainnotes.app.data.NotesRepository
import net.plainnotes.app.security.Session
import javax.inject.Inject

/** Generic look on purpose: system dynamic colours where available, Material baseline otherwise. */
@Composable fun ShellTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme(); val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(scheme, content = content)
}

/** Opens the real app (or the decoy space) when [input] is a code; otherwise does nothing visible. */
private fun ComponentActivity.attempt(repo: NotesRepository, input: String, onMiss: () -> Unit = {}) {
    if (!net.plainnotes.app.security.AppLock.validPin(input)) { onMiss(); return }
    lifecycleScope.launch {
        val space = withContext(Dispatchers.Default) { Disguise.check(this@attempt, input) }
        if (space == null) { onMiss(); return@launch }
        repo.select(space); Session.open = true
        startActivity(Intent(this@attempt, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}

@AndroidEntryPoint class CalculatorActivity : ComponentActivity() {
    @Inject lateinit var repo: NotesRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(); super.onCreate(savedInstanceState)
        setContent { ShellTheme { CalculatorScreen { attempt(repo, it) } } }
    }
}

@AndroidEntryPoint class NotesShellActivity : ComponentActivity() {
    @Inject lateinit var repo: NotesRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(); super.onCreate(savedInstanceState)
        val store = getSharedPreferences("shell_notes", Context.MODE_PRIVATE)
        setContent { ShellTheme { NotesShellScreen(store.getString("text", "") ?: "", { store.edit().putString("text", it).apply() }) { q, miss -> attempt(repo, q.trim(), miss) } } }
    }
}

private val KEYS = listOf(listOf("C", "(", ")", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("%", "0", ".", "="))

@Composable fun CalculatorScreen(onEquals: (String) -> Unit) {
    var expr by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val preview = remember(expr) { Calc.result(expr)?.takeIf { it != expr } }
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                Text(expr.ifEmpty { "0" }, fontSize = if (expr.length > 12) 36.sp else 56.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
                Text(if (error) stringResource(R.string.shell_error) else preview ?: "", style = MaterialTheme.typography.headlineSmall, color = if (error) cs.error else cs.onSurfaceVariant, modifier = Modifier.heightIn(min = 36.dp))
                IconButton(onClick = { expr = expr.dropLast(1); error = false }) { Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.shell_delete)) }
            }
            Spacer(Modifier.height(8.dp))
            KEYS.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        val operator = k in listOf("÷", "×", "−", "+", "(", ")", "%")
                        val (bg, fg) = when { k == "=" -> cs.primary to cs.onPrimary; k == "C" -> cs.tertiaryContainer to cs.onTertiaryContainer
                            operator -> cs.secondaryContainer to cs.onSecondaryContainer; else -> cs.surfaceContainerHigh to cs.onSurface }
                        Surface(onClick = {
                            error = false
                            when (k) {
                                "C" -> expr = ""
                                "=" -> if (expr.isNotEmpty()) { val r = Calc.result(expr); onEquals(expr); if (r == null) error = true else expr = r }
                                else -> if (expr.length < 64) expr += k
                            }
                        }, shape = RoundedCornerShape(28.dp), color = bg, contentColor = fg, modifier = Modifier.weight(1f).height(72.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(k, fontSize = 28.sp) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NotesShellScreen(initial: String, onSave: (String) -> Unit, onSearch: (String, () -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var noResults by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = {
            if (searching) TextField(query, { query = it; noResults = false }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.shell_search)) }, singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch(query) { noResults = true } }))
            else Text(stringResource(R.string.shell_notes))
        }, actions = {
            IconButton(onClick = { searching = !searching; query = ""; noResults = false }) {
                Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, stringResource(R.string.shell_search))
            }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (noResults) Text(stringResource(R.string.shell_no_results), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextField(text, { text = it; onSave(it) }, Modifier.fillMaxSize(), placeholder = { Text(stringResource(R.string.shell_note_hint)) },
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface, unfocusedIndicatorColor = MaterialTheme.colorScheme.surface))
        }
    }
}
