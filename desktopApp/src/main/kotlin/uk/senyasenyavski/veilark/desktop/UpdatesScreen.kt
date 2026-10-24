package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.senyasenyavski.veilark.update.AppUpdate
import uk.senyasenyavski.veilark.update.UpdateClient

@Composable
internal fun UpdateBanner(
  state: DesktopUpdateState,
  onOpen: () -> Unit,
) {
  val language = LocalUiLanguage.current
  val visible = state is DesktopUpdateState.Available ||
    state is DesktopUpdateState.Downloading ||
    state is DesktopUpdateState.Ready
  AnimatedVisibility(visible = visible) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.Update, null, Modifier.size(19.dp))
          Text(
            when (state) {
              is DesktopUpdateState.Available -> language.text("Доступно обновление ${state.update.versionName}", "Update ${state.update.versionName} available")
              is DesktopUpdateState.Downloading -> language.text("Загружаем ${state.update.versionName}", "Downloading ${state.update.versionName}")
              is DesktopUpdateState.Ready -> language.text("Обновление ${state.update.versionName} готово к перезапуску", "Update ${state.update.versionName} is ready to restart")
              else -> ""
            },
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelLarge,
          )
          TextButton(onClick = onOpen) { Text(language.text("Открыть", "Open")) }
        }
        // Progress lives only on the Updates screen. The banner is a notice,
        // not a second determinate bar.
      }
    }
  }
}

@Composable
internal fun UpdatesScreen(
  state: DesktopUpdateState,
  onCheck: () -> Unit,
  onDownload: (AppUpdate) -> Unit,
  onCancelDownload: () -> Unit,
  onInstall: (DesktopUpdateState.Ready) -> Unit,
) {
  val language = LocalUiLanguage.current
  val update = when (state) {
    is DesktopUpdateState.Available -> state.update
    is DesktopUpdateState.Downloading -> state.update
    is DesktopUpdateState.Ready -> state.update
    is DesktopUpdateState.Installing -> state.update
    is DesktopUpdateState.Failed -> state.update
    else -> null
  }
  Page(maxWidth = 580.dp) {
    PageHeader(
      language.text("Обновления", "Updates"),
      language.text("Проверка работает в фоне и не прерывается при навигации", "Checks continue in the background while you navigate"),
    )
    Spacer(Modifier.height(10.dp))
    CardSection(Modifier.fillMaxWidth()) {
      Column {
        ListItem(
          headlineContent = {
            Text(
              when (state) {
                is DesktopUpdateState.Available -> language.text("Доступна версия ${state.update.versionName}", "Version ${state.update.versionName} available")
                is DesktopUpdateState.Downloading -> language.text("Загрузка ${state.update.versionName}", "Downloading ${state.update.versionName}")
                is DesktopUpdateState.Ready -> language.text("Версия ${state.update.versionName} готова", "Version ${state.update.versionName} is ready")
                is DesktopUpdateState.Installing -> language.text("Подготовка перезапуска", "Preparing restart")
                else -> "Veilark Windows ${UpdateClient.CURRENT_VERSION_NAME}"
              },
              fontWeight = FontWeight.Medium,
            )
          },
          supportingContent = {
            Text(
              when (state) {
                DesktopUpdateState.Checking -> language.text("Проверяем подписанный OTA-канал…", "Checking the signed OTA channel…")
                DesktopUpdateState.Current -> language.text("Установлена актуальная версия", "The latest version is installed")
                is DesktopUpdateState.Available -> language.text("Можно загрузить в фоне, продолжая пользоваться приложением", "Download in the background while using the app")
                is DesktopUpdateState.Downloading -> language.text("${(state.progress * 100).toInt()}% · подпись и SHA-256 будут проверены", "${(state.progress * 100).toInt()}% · signature and SHA-256 will be verified")
                is DesktopUpdateState.Ready -> language.text("VPN отключится только после подтверждения перезапуска", "VPN will disconnect only after you confirm restart")
                is DesktopUpdateState.Installing -> language.text("Veilark завершится и автоматически откроется после установки", "Veilark will close and reopen automatically after installation")
                is DesktopUpdateState.Failed -> updateErrorText(state.error, language)
              },
            )
          },
          leadingContent = {
            Icon(
              Icons.Rounded.Update,
              null,
              tint = if (state !is DesktopUpdateState.Failed) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.error
              },
            )
          },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        Box(
          modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
          contentAlignment = Alignment.CenterEnd,
        ) {
          UpdatePrimaryAction(
            state = state,
            onCheck = onCheck,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onInstall = onInstall,
          )
        }
        AnimatedVisibility(visible = state is DesktopUpdateState.Downloading) {
          LinearProgressIndicator(
            progress = {
              (state as? DesktopUpdateState.Downloading)?.progress?.coerceIn(0f, 1f) ?: 0f
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    Text(
      if (update != null) {
        language.text("Что изменится в ${update.versionName}", "What's new in ${update.versionName}")
      } else {
        language.text("Что входит в ${UpdateClient.CURRENT_VERSION_NAME}", "What's included in ${UpdateClient.CURRENT_VERSION_NAME}")
      },
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    CardSection(Modifier.fillMaxWidth()) {
      Column {
        ListItem(
          headlineContent = {
            Text(
              if (update != null) language.text("Описание обновления", "Update details")
              else language.text("Журнал изменений", "Changelog"),
              fontWeight = FontWeight.Medium,
            )
          },
          supportingContent = {
            Text(language.text(
              "Установленная версия — встроенный changelog; новая — подписанный OTA-манифест",
              "Installed release notes are built in; new notes come from the signed OTA manifest",
            ))
          },
          leadingContent = {
            Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
          },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        Text(
          update?.notes?.ifBlank { language.text("Описание версии не указано", "No release notes provided") }
            ?: language.text(UpdateClient.CURRENT_RELEASE_NOTES, UpdateClient.CURRENT_RELEASE_NOTES_EN),
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
internal fun UpdatePrimaryAction(
  state: DesktopUpdateState,
  onCheck: () -> Unit,
  onDownload: (AppUpdate) -> Unit,
  onCancelDownload: () -> Unit,
  onInstall: (DesktopUpdateState.Ready) -> Unit,
) {
  val language = LocalUiLanguage.current
  when (state) {
    is DesktopUpdateState.Available -> Button(
      onClick = { onDownload(state.update) },
      shape = RoundedCornerShape(12.dp),
    ) { Text(language.text("Загрузить", "Download")) }
    is DesktopUpdateState.Downloading -> OutlinedButton(
      onClick = onCancelDownload,
      shape = RoundedCornerShape(12.dp),
    ) { Text(language.text("Отменить", "Cancel")) }
    is DesktopUpdateState.Ready -> Button(
      onClick = { onInstall(state) },
      shape = RoundedCornerShape(12.dp),
    ) { Text(language.text("Установить и перезапустить", "Install and restart")) }
    is DesktopUpdateState.Installing -> CircularProgressIndicator(
      Modifier.size(24.dp),
      strokeWidth = 2.dp,
    )
    else -> OutlinedButton(
      onClick = onCheck,
      enabled = state !is DesktopUpdateState.Checking,
      shape = RoundedCornerShape(12.dp),
    ) {
      if (state is DesktopUpdateState.Checking) {
        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
      } else {
        Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
      }
      Text(language.text("Проверить", "Check"), Modifier.padding(start = 7.dp))
    }
  }
}
