package net.plainnotes.app.ui
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
@Composable fun NotesTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = lightColorScheme(), content = content) }
