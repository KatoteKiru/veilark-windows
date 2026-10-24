package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.senyasenyavski.veilark.helper.NodeLatency
import uk.senyasenyavski.veilark.helper.SubscriptionRecord
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.TrafficSnapshot
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.model.requiresStopRetry
import uk.senyasenyavski.veilark.model.locksConfiguration
import java.util.Locale

@Composable
internal fun HomeScreen(
  phase: VpnPhase,
  traffic: TrafficSnapshot?,
  elevated: Boolean,
  profile: Profile?,
  selectedEngine: VpnEngine,
  selectedNodeTag: String?,
  nodeLatencies: Map<String, NodeLatency>,
  probing: Boolean,
  refreshing: Boolean,
  availableEngines: Set<VpnEngine>,
  subscriptions: List<SubscriptionRecord>,
  activeSubscriptionId: String?,
  onEngineSelect: (VpnEngine) -> Unit,
  onSelectEndpoint: (String, String) -> Unit,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
  onImport: () -> Unit,
  onRefresh: () -> Unit,
  onProbe: () -> Unit,
  onOpenLogs: () -> Unit,
) {
  val connected = phase is VpnPhase.Connected || phase is VpnPhase.Degraded
  val connecting = phase is VpnPhase.Preparing || phase is VpnPhase.Connecting
  val stopping = phase is VpnPhase.Stopping
  Page(maxWidth = 440.dp, horizontalPadding = 16.dp, verticalPadding = 8.dp) {
    AnimatedVisibility(visible = !elevated && !connected) {
      ElevationNotice(Modifier.padding(bottom = 10.dp))
    }
    val connectionAction = when {
      connected || connecting || phase.requiresStopRetry -> onDisconnect
      stopping -> ({})
      else -> onConnect
    }
    CompactConnectionWorkspace(
      phase = phase,
      traffic = traffic,
      profile = profile,
      selectedEngine = selectedEngine,
      selectedNodeTag = selectedNodeTag,
      nodeLatencies = nodeLatencies,
      probing = probing,
      refreshing = refreshing,
      availableEngines = availableEngines,
      subscriptions = subscriptions,
      activeSubscriptionId = activeSubscriptionId,
      onEngineSelect = onEngineSelect,
      onSelectEndpoint = onSelectEndpoint,
      onAction = connectionAction,
      onImport = onImport,
      onRefresh = onRefresh,
      onProbe = onProbe,
      onOpenLogs = onOpenLogs,
    )
  }
}

@Composable
internal fun CompactConnectionWorkspace(
  phase: VpnPhase,
  traffic: TrafficSnapshot?,
  profile: Profile?,
  selectedEngine: VpnEngine,
  selectedNodeTag: String?,
  nodeLatencies: Map<String, NodeLatency>,
  probing: Boolean,
  refreshing: Boolean,
  availableEngines: Set<VpnEngine>,
  subscriptions: List<SubscriptionRecord>,
  activeSubscriptionId: String?,
  onEngineSelect: (VpnEngine) -> Unit,
  onSelectEndpoint: (String, String) -> Unit,
  onAction: () -> Unit,
  onImport: () -> Unit,
  onRefresh: () -> Unit,
  onProbe: () -> Unit,
  onOpenLogs: () -> Unit,
) {
  val language = LocalUiLanguage.current
  val healthy = phase is VpnPhase.Connected
  val degraded = phase is VpnPhase.Degraded
  val connected = healthy || degraded
  val busy = phase is VpnPhase.Preparing ||
    phase is VpnPhase.Connecting ||
    phase is VpnPhase.Stopping
  val configurationLocked = phase.locksConfiguration
  val failed = phase is VpnPhase.Error
  val statusMessage = concisePhaseMessage(phase, profile, traffic, language)
  val statusSummary = when (phase) {
    is VpnPhase.Degraded -> profile?.name ?: language.text(
      "VPN-ядро запущено · требуется проверка",
      "VPN core is running · verification required",
    )
    is VpnPhase.Error -> if (phase.stopRequired) language.text(
      "Ядро ещё не остановлено · повторите остановку", "Core has not stopped · retry stopping",
    ) else language.text("Подключение не установлено", "Connection failed")
    else -> statusMessage
  }
  val actionLabel = when {
    phase.requiresStopRetry -> language.text("Повторить остановку", "Retry stop")
    profile == null -> language.text("Добавить подписку", "Add subscription")
    phase is VpnPhase.Stopping -> language.text("Остановка", "Stopping")
    phase is VpnPhase.Preparing || phase is VpnPhase.Connecting -> language.text("Отменить", "Cancel")
    connected -> language.text("Отключить", "Disconnect")
    else -> language.text("Подключить", "Connect")
  }

  val statusTitle = when (phase) {
    VpnPhase.Idle -> language.text("VPN выключен", "VPN is off")
    VpnPhase.NeedsElevation -> language.text("Нужны права Windows", "Windows admin required")
    VpnPhase.Preparing -> language.text("Подготовка", "Preparing")
    VpnPhase.Connecting -> language.text("Подключение", "Connecting")
    is VpnPhase.Connected -> language.text("Соединение защищено", "Connection protected")
    is VpnPhase.Degraded -> language.text("Соединение нестабильно", "Connection unstable")
    VpnPhase.Stopping -> language.text("Остановка", "Stopping")
    is VpnPhase.Error -> if (phase.stopRequired) language.text("Не удалось остановить", "Could not stop")
      else language.text("Не удалось подключиться", "Could not connect")
  }

  Column(
    modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      shape = MaterialTheme.shapes.extraLarge,
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        ConnectionMark(
          busy = busy,
          connected = connected,
          healthy = healthy,
          degraded = degraded,
          failed = failed,
          stateLabel = statusTitle,
        )
        ConnectionStatus(
          title = statusTitle,
          summary = statusSummary,
          connected = connected,
          phase = phase,
          traffic = traffic,
          modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
        )
        ConnectionActionButton(
          label = actionLabel,
          connected = connected,
          busy = busy,
          enabled = phase !is VpnPhase.Stopping,
          onClick = if (profile == null && !phase.requiresStopRetry) onImport else onAction,
          modifier = Modifier.padding(top = 18.dp).fillMaxWidth().height(52.dp),
        )
      }
    }
    EngineSelector(
      selectedEngine = selectedEngine,
      availableEngines = availableEngines,
      enabled = !configurationLocked,
      onEngineSelect = onEngineSelect,
      modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
    )

    if (profile == null) {
      Text(
        language.text(
          "Нет профиля ${selectedEngine.displayName}",
          "No ${selectedEngine.displayName} profile",
        ),
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      EndpointPicker(
        subscriptions = subscriptions,
        engine = selectedEngine,
        activeSubscriptionId = activeSubscriptionId,
        selectedNodeTag = selectedNodeTag,
        latencies = nodeLatencies,
        enabled = !configurationLocked,
        onSelect = onSelectEndpoint,
        modifier = Modifier.padding(top = 8.dp).widthIn(max = 400.dp).fillMaxWidth(),
      )
    }

    if (profile != null) Row(
      modifier = Modifier.padding(top = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      TextButton(onClick = onImport, enabled = !configurationLocked) {
        Icon(Icons.Rounded.Add, null, Modifier.size(17.dp))
        Text(language.text("Добавить", "Add"), Modifier.padding(start = 5.dp))
      }
      TextButton(
        onClick = onRefresh,
        enabled = !configurationLocked && !refreshing && profile.sourceUrl != null,
      ) {
        if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Sync, null, Modifier.size(17.dp))
        Text(language.text("Обновить", "Refresh"), Modifier.padding(start = 5.dp))
      }
      TextButton(onClick = onProbe, enabled = !configurationLocked && !probing) {
        if (probing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Speed, null, Modifier.size(17.dp))
        Text(language.text("Пинг", "Ping"), Modifier.padding(start = 5.dp))
      }
    }

    AnimatedVisibility(
      visible = degraded || failed,
      enter = fadeIn(tween(160)),
      exit = fadeOut(tween(120)),
    ) {
      PhaseProblemStrip(
        phase = phase,
        message = statusMessage,
        onOpenLogs = onOpenLogs,
        modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun ConnectionStatus(
  title: String,
  summary: String,
  connected: Boolean,
  phase: VpnPhase,
  traffic: TrafficSnapshot?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AnimatedContent(
      targetState = title,
      transitionSpec = {
        (fadeIn(tween(200)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 3 })
          .togetherWith(
            fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 4 },
          )
      },
    ) { currentTitle ->
      Text(
        currentTitle,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
    Text(
      summary,
      modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
    if (connected) {
      CompactTrafficEvidence(
        phase = phase,
        traffic = traffic,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun ConnectionMark(
  busy: Boolean,
  connected: Boolean,
  healthy: Boolean,
  degraded: Boolean,
  failed: Boolean,
  stateLabel: String,
  modifier: Modifier = Modifier,
) {
  val fill by animateColorAsState(
    targetValue = when {
      healthy -> MaterialTheme.colorScheme.onSurface
      degraded -> MaterialTheme.colorScheme.surfaceContainerHighest
      failed -> MaterialTheme.colorScheme.surfaceContainerHighest
      else -> MaterialTheme.colorScheme.surfaceContainerLowest
    },
    animationSpec = VeilarkColorMotion,
  )
  val glyph by animateColorAsState(
    targetValue = when {
      healthy -> MaterialTheme.colorScheme.surface
      degraded -> MaterialTheme.colorScheme.onSurface
      failed -> MaterialTheme.colorScheme.onSurface
      else -> MaterialTheme.colorScheme.onSurface
    },
    animationSpec = VeilarkColorMotion,
  )
  val scale by animateFloatAsState(
    targetValue = if (busy) 0.98f else 1f,
    animationSpec = VeilarkSpring,
  )
  Box(
    modifier = modifier
      .size(112.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .semantics { stateDescription = stateLabel },
    contentAlignment = Alignment.Center,
  ) {
    if (busy) {
      CircularProgressIndicator(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        strokeWidth = 3.5.dp,
      )
    }
    Surface(
      modifier = Modifier.size(if (busy) 96.dp else 104.dp),
      color = fill,
      shape = CircleShape,
    ) {
      Box(contentAlignment = Alignment.Center) {
        BrandGlyph(
          color = glyph,
          modifier = Modifier.size(64.dp),
        )
      }
    }
  }
}

@Composable
private fun EngineSelector(
  selectedEngine: VpnEngine,
  availableEngines: Set<VpnEngine>,
  enabled: Boolean,
  onEngineSelect: (VpnEngine) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(20.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(3.dp).selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      VpnEngine.entries.forEach { engine ->
        EngineChoice(
          engine = engine,
          selected = selectedEngine == engine,
          configured = engine in availableEngines,
          enabled = enabled,
          modifier = Modifier.weight(1f),
          onSelect = onEngineSelect,
        )
      }
    }
  }
}

private fun concisePhaseMessage(
  phase: VpnPhase,
  profile: Profile?,
  traffic: TrafficSnapshot?,
  language: UiLanguage,
): String = when (phase) {
  VpnPhase.Idle -> if (profile == null) {
    language.text("Добавьте подписку для выбранного ядра", "Add a subscription for the selected core")
  } else {
    language.text("Готово к подключению", "Ready to connect")
  }
  VpnPhase.NeedsElevation -> language.text("Windows покажет запрос UAC при подключении", "Windows will show a UAC prompt when connecting")
  VpnPhase.Preparing -> language.text("Проверяем конфигурацию и подготавливаем TUN", "Validating configuration and preparing TUN")
  VpnPhase.Connecting -> language.text("Ждём подтверждения VPN-ядра", "Waiting for the VPN core")
  is VpnPhase.Connected -> traffic?.adapter ?: profile?.name.orEmpty()
  is VpnPhase.Degraded -> phaseProblemText(
    phase = phase,
    fallback = language.text(
      "Туннель запущен, но проверка интернета не завершена. Попробуйте другой узел.",
      "The tunnel is running, but the internet check did not finish. Try another server.",
    ),
    certificateFallback = language.text(
      "Туннель запущен, но защищённая проверка интернета не прошла. Попробуйте другой узел.",
      "The tunnel is running, but the secure check failed. Try another server.",
    ),
    language = language,
  )
  VpnPhase.Stopping -> language.text("Завершаем ядро и удаляем TUN-адаптер", "Stopping the core and removing the TUN adapter")
  is VpnPhase.Error -> phaseProblemText(
    phase = phase,
    fallback = language.text(
      "Не удалось подключиться. Повторите попытку или выберите другой узел.",
      "Could not connect. Try again or choose another server.",
    ),
    certificateFallback = language.text(
      "Не удалось проверить защищённое соединение. Повторите попытку или выберите другой узел.",
      "Could not verify the secure connection. Try again or choose another server.",
    ),
    language = language,
  )
}

@Composable
private fun PhaseProblemStrip(
  phase: VpnPhase,
  message: String,
  onOpenLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalUiLanguage.current
  val failed = phase is VpnPhase.Error
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = MaterialTheme.shapes.small,
  ) {
    Row(
      Modifier.padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        if (failed) Icons.Rounded.Description else Icons.Rounded.Speed,
        null,
        modifier = Modifier.size(18.dp),
        tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
      )
      Column(Modifier.weight(1f).padding(start = 10.dp)) {
        Text(
          message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (phase is VpnPhase.Error && phase.code.isNotBlank()) {
          Text(
            language.text("Код: ${phase.code}", "Code: ${phase.code}"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
      TextButton(onClick = onOpenLogs, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(language.text("Подробнее", "Details"))
      }
    }
  }
}

@Composable
private fun ConnectionActionButton(
  label: String,
  connected: Boolean,
  busy: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = if (connected || busy) {
      ButtonDefaults.filledTonalButtonColors()
    } else {
      ButtonDefaults.buttonColors()
    },
  ) {
    AnimatedContent(
      targetState = label,
      transitionSpec = { fadeIn(tween(160)).togetherWith(fadeOut(tween(120))) },
      label = "connection-action-label",
    ) { currentLabel ->
      Text(currentLabel, style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun CompactTrafficEvidence(
  phase: VpnPhase,
  traffic: TrafficSnapshot?,
  modifier: Modifier = Modifier,
) {
  val language = LocalUiLanguage.current
  val since = (phase as? VpnPhase.Connected)?.sinceEpochMillis
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(since) {
    while (since != null) {
      now = System.currentTimeMillis()
      delay(1_000)
    }
  }
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      "${traffic?.let { formatBytes(it.bytesIn, language) } ?: "—"} / " +
        (traffic?.let { formatBytes(it.bytesOut, language) } ?: "—"),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      buildString {
        append(language.text("получено / отправлено", "received / sent"))
        if (since != null) append(" · ${formatDuration(now - since)}")
      },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

/** Keeps the UAC requirement visible without consuming the compact home screen. */
@Composable
private fun ElevationNotice(modifier: Modifier = Modifier) {
  val language = LocalUiLanguage.current
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.small,
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Rounded.AdminPanelSettings, null, Modifier.size(19.dp))
      Text(
        language.text(
          "При подключении Windows запросит права администратора",
          "Windows will request administrator access when connecting",
        ),
        modifier = Modifier.padding(start = 11.dp),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun formatBytes(value: Long, language: UiLanguage): String {
  if (value < 1_024) return "$value ${language.text("Б", "B")}"
  val units = language.text("КБ МБ ГБ ТБ", "KB MB GB TB").split(' ')
  var amount = value.toDouble() / 1_024
  var unit = 0
  while (amount >= 1_024 && unit < units.lastIndex) {
    amount /= 1_024
    unit++
  }
  return if (amount >= 100) {
    "${amount.toInt()} ${units[unit]}"
  } else {
    String.format(Locale.ROOT, "%.1f %s", amount, units[unit])
  }
}

private fun formatDuration(millis: Long): String {
  val total = (millis / 1_000).coerceAtLeast(0)
  val hours = total / 3_600
  val minutes = (total % 3_600) / 60
  val seconds = total % 60
  return if (hours > 0) {
    String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
  }
}

@Composable
private fun EngineChoice(
  engine: VpnEngine,
  selected: Boolean,
  configured: Boolean,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  onSelect: (VpnEngine) -> Unit,
) {
  val language = LocalUiLanguage.current
  val containerColor by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      Color.Transparent
    },
    animationSpec = VeilarkColorMotion,
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
    animationSpec = VeilarkColorMotion,
  )
  Surface(
    modifier = modifier
      .heightIn(min = 48.dp)
      .selectable(
        selected = selected,
        enabled = enabled,
        role = Role.RadioButton,
        onClick = { onSelect(engine) },
      ),
    color = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(18.dp),
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        if (engine == VpnEngine.SingBox) Icons.Rounded.Hub else VeilarkMark,
        null,
        Modifier.size(19.dp),
        tint = if (engine == VpnEngine.TrustTunnel) MaterialTheme.colorScheme.onSurface else LocalContentColor.current,
      )
      Column(Modifier.padding(start = 9.dp)) {
        Text(
          engine.displayName,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
          maxLines = 1,
        )
        if (!configured) {
          Text(
            language.text("Добавить профиль", "Add profile"),
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = .72f),
          )
        }
      }
    }
  }
}
