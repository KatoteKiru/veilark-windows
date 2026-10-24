package uk.senyasenyavski.veilark.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.senyasenyavski.veilark.helper.VeilarkPaths
import uk.senyasenyavski.veilark.diagnostics.HealthChecker
import uk.senyasenyavski.veilark.diagnostics.HealthResult
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files

@Composable
internal fun DiagnosticsScreen() {
  val language = LocalUiLanguage.current
  val scope = rememberCoroutineScope()
  val checker = remember { HealthChecker() }
  var running by remember { mutableStateOf(false) }
  var results by remember { mutableStateOf<Map<String, HealthResult>>(emptyMap()) }

  fun runChecks() {
    if (running) return
    running = true
    scope.launch {
      results = checker.checkAll().associateBy { it.target.id }
      running = false
    }
  }

  Page(maxWidth = 520.dp) {
    PageHeader(
      language.text("Диагностика", "Diagnostics"),
      language.text("Доступность внешних сервисов", "External service availability"),
      action = {
        Button(
          onClick = ::runChecks,
          enabled = !running,
          shape = RoundedCornerShape(12.dp),
        ) {
          if (running) {
            CircularProgressIndicator(
              modifier = Modifier.size(17.dp),
              strokeWidth = 2.dp,
              color = LocalContentColor.current,
            )
          } else {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
          }
          Text(
            if (running) language.text("Проверяем…", "Checking…")
            else language.text("Проверить", "Check"),
            Modifier.padding(start = 8.dp),
          )
        }
      },
    )
    Spacer(Modifier.height(10.dp))
    CardSection(Modifier.fillMaxWidth()) {
      Column {
        HealthChecker.defaultTargets
          .forEachIndexed { index, target ->
            val result = results[target.id]
            ListItem(
              headlineContent = { Text(target.name) },
              supportingContent = {
                Text(
                  when {
                    running && result == null -> language.text("Выполняется HTTPS-запрос", "Running HTTPS request")
                    result?.safeError != null -> result.safeError.orEmpty()
                    result?.statusCode != null ->
                      language.text(
                        "HTTP ${result.statusCode} · ${result.latencyMillis} мс",
                        "HTTP ${result.statusCode} · ${result.latencyMillis} ms",
                      )
                    else -> language.text("Проверка ещё не запускалась", "Not checked yet")
                  },
                )
              },
              leadingContent = {
                Box(
                  Modifier.size(10.dp).background(
                    when {
                      result?.reachable == true -> MaterialTheme.colorScheme.primary
                      result != null -> MaterialTheme.colorScheme.error
                      else -> MaterialTheme.colorScheme.outline
                    },
                    CircleShape,
                  ),
                )
              },
              trailingContent = {
                Text(
                  when {
                    result?.reachable == true -> language.text("Доступен", "Available")
                    result != null -> language.text("Недоступен", "Unavailable")
                    running -> language.text("Проверяется", "Checking")
                    else -> language.text("Не проверено", "Not checked")
                  },
                  style = MaterialTheme.typography.labelLarge,
                  color = when {
                    result?.reachable == true -> MaterialTheme.colorScheme.primary
                    result != null -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                  },
                )
              },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (index < HealthChecker.defaultTargets.lastIndex) {
              HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
          }
      }
    }
  }
}

@Composable
internal fun LogsScreen() {
  val language = LocalUiLanguage.current
  var log by remember { mutableStateOf(readLog()) }
  val verticalScroll = rememberScrollState(initial = Int.MAX_VALUE)
  val horizontalScroll = rememberScrollState()
  val visibleLog = log.let { current ->
    when {
      current == null -> language.text("Не удалось прочитать журнал", "Could not read the log")
      current.isBlank() -> language.text("Ошибок и сетевых событий пока нет", "No errors or network events yet")
      else -> current
    }
  }
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
      PageHeader(
        language.text("Технический журнал", "Technical log"),
        language.text("События ядра без ключей доступа", "Core events with credentials removed"),
        action = {
          Row {
            IconButton(
              onClick = {
                Toolkit.getDefaultToolkit().systemClipboard
                  .setContents(StringSelection(log.orEmpty()), null)
              },
            ) {
              Icon(
                Icons.Rounded.ContentPaste,
                language.text("Копировать журнал", "Copy log"),
              )
            }
            IconButton(
              onClick = { log = readLog() },
            ) {
              Icon(
                Icons.Rounded.Refresh,
                language.text("Обновить журнал", "Refresh log"),
              )
            }
          }
        },
      )
      Spacer(Modifier.height(8.dp))
      Surface(
        modifier = Modifier.fillMaxWidth().weight(1f),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Description,
              null,
              modifier = Modifier.size(19.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
              Text(language.text("Последние события", "Latest events"), style = MaterialTheme.typography.labelLarge)
              Text(
                VeilarkPaths.logFile.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              language.text("${visibleLog.lineSequence().count()} строк", "${visibleLog.lineSequence().count()} lines"),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          HorizontalDivider()
          SelectionContainer {
            Box(
              modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
                .padding(10.dp),
            ) {
              Text(
                visibleLog,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
              )
            }
          }
        }
      }
    }
  }
}

/** The last journal lines, or `null` when the file cannot be read. */
private fun readLog(): String? = runCatching {
  if (!Files.exists(VeilarkPaths.logFile)) return@runCatching ""
  // Malformed bytes are replaced; readAllLines would reject the whole journal.
  String(Files.readAllBytes(VeilarkPaths.logFile), Charsets.UTF_8)
    .lines()
    .dropLastWhile(String::isEmpty)
    .takeLast(250)
    .joinToString(System.lineSeparator())
}.getOrNull()
