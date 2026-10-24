package uk.senyasenyavski.veilark.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path as NioPath

@Composable
internal fun ImportDialog(
  importing: Boolean,
  onDismiss: () -> Unit,
  onImport: (String) -> Unit,
  initialValue: String = "",
) {
  val language = LocalUiLanguage.current
  var value by remember(initialValue) { mutableStateOf(initialValue) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(language.text("Добавить профиль", "Add profile")) },
    text = {
      Column {
        Text(
          language.text(
            "Вставьте HTTPS-подписку, sing-box JSON, TrustTunnel TOML или URI-ссылку.",
            "Paste an HTTPS subscription, sing-box JSON, TrustTunnel TOML or URI link.",
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          enabled = !importing,
          minLines = 3,
          maxLines = 8,
          shape = RoundedCornerShape(16.dp),
          placeholder = {
            Text(language.text("Ссылка или конфигурация", "Link or configuration"))
          },
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onImport(value) },
        enabled = value.isNotBlank() && !importing,
        shape = RoundedCornerShape(14.dp),
      ) {
        if (importing) {
          CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Text(language.text("Импортировать", "Import"))
        }
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDismiss,
        enabled = !importing,
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(language.text("Отмена", "Cancel"))
      }
    },
  )
}

internal fun chooseProfileFile(language: UiLanguage): NioPath? {
  val extensions = listOf("json", "txt", "yaml", "yml", "toml")
  val dialog = FileDialog(null as Frame?, language.text("Выберите профиль", "Choose a profile"), FileDialog.LOAD).apply {
    // The Windows file dialog ignores FilenameFilter and only honours a
    // wildcard pattern in the initial file name.
    file = extensions.joinToString(";") { "*.$it" }
    setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
    isVisible = true
  }
  return dialog.file?.let { NioPath.of(dialog.directory, it) }
}
