package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.veilark.profile.ImportDeepLink
import com.example.veilark.profile.ProfileSelection
import uk.senyasenyavski.veilark.helper.VeilarkPaths
import uk.senyasenyavski.veilark.helper.ActiveTunnelConflict
import uk.senyasenyavski.veilark.helper.ElevationManager
import uk.senyasenyavski.veilark.helper.GeoRoutingPreflight
import uk.senyasenyavski.veilark.helper.ProfileStore
import uk.senyasenyavski.veilark.helper.ProfileLoadResult
import uk.senyasenyavski.veilark.helper.ProfileLifecycle
import uk.senyasenyavski.veilark.helper.ProfileRemovalResult
import uk.senyasenyavski.veilark.helper.SingleInstanceGate
import uk.senyasenyavski.veilark.helper.UrlProtocolRegistration
import uk.senyasenyavski.veilark.helper.NodeLatency
import uk.senyasenyavski.veilark.helper.NodeLatencyProbe
import uk.senyasenyavski.veilark.helper.StoredProfiles
import uk.senyasenyavski.veilark.helper.LegacyBuiltInTrustMigration
import uk.senyasenyavski.veilark.helper.SubscriptionCatalog
import uk.senyasenyavski.veilark.helper.SubscriptionOrigin
import uk.senyasenyavski.veilark.helper.SubscriptionRecord
import uk.senyasenyavski.veilark.helper.SafeLog
import uk.senyasenyavski.veilark.helper.WindowsVpnSession
import uk.senyasenyavski.veilark.helper.ConnectionPreparation
import uk.senyasenyavski.veilark.diagnostics.HealthChecker
import uk.senyasenyavski.veilark.diagnostics.HealthResult
import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings
import uk.senyasenyavski.veilark.model.TrafficSnapshot
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.model.requiresStopRetry
import uk.senyasenyavski.veilark.model.locksConfiguration
import uk.senyasenyavski.veilark.profile.ProfileConfiguration
import uk.senyasenyavski.veilark.profile.NodeCountry
import uk.senyasenyavski.veilark.profile.NodePresentation
import uk.senyasenyavski.veilark.update.AppUpdate
import uk.senyasenyavski.veilark.update.UpdateClient
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.nio.file.Path as NioPath
import javax.swing.TransferHandler
import kotlin.system.exitProcess

private class DesktopActions {
  var preparing by mutableStateOf(false)
  var connect: () -> Unit = {}
  var disconnect: () -> Unit = {}
  var importFile: (NioPath) -> Unit = {}
  var hasProfile: Boolean = false
}

private val InitialWindowWidth = 520.dp
private val InitialWindowHeight = 700.dp
private val MinimumWindowWidth = 460.dp
private val MinimumWindowHeight = 480.dp

fun main(args: Array<String>) {
  try {
    runVeilark(args)
  } catch (error: Throwable) {
    runCatching { SafeLog.writeThrowable("Ошибка запуска Veilark", error) }
    javax.swing.JOptionPane.showMessageDialog(null,
      "Veilark could not start. See the technical journal in %LOCALAPPDATA%\\Veilark\\logs.\n" +
        "Не удалось запустить Veilark. Подробности записаны в технический журнал.",
      "Veilark", javax.swing.JOptionPane.ERROR_MESSAGE)
    exitProcess(1)
  }
}

private fun runVeilark(args: Array<String>) {
  Thread.setDefaultUncaughtExceptionHandler { thread, error ->
    runCatching {
      SafeLog.writeThrowable("Необработанная ошибка в потоке ${thread.name}", error)
    }
  }
  // `veilark://import?url=...` arrives as a single argument from the shell protocol
  // handler. The inner URL is a bearer credential and is never written to the log.
  val importLink = ImportDeepLink.fromArguments(args.asList())
  val instanceGate = SingleInstanceGate(
    waitForPrimaryMillis = if ("--connect" in args) 8_000 else 0,
  )
  if (!instanceGate.isPrimary) {
    val activated = instanceGate.notifyPrimary(importLink)
    if (activated || !instanceGate.tryBecomePrimary()) {
      instanceGate.close()
      if (!activated) {
        javax.swing.JOptionPane.showMessageDialog(null,
          "Veilark is already running, but its window did not respond. Open Veilark from the system tray.\n" +
            "Veilark уже работает, но окно не ответило. Откройте его через значок в трее. VPN не остановлен.",
          "Veilark", javax.swing.JOptionPane.WARNING_MESSAGE)
      }
      exitProcess(if (activated) 0 else 1)
    }
  }
  UrlProtocolRegistration().ensureRegistered()
  try {
    application {
  val session = remember {
    WindowsVpnSession(preConnectCheck = ActiveTunnelConflict::message)
  }
  val exitScope = rememberCoroutineScope()
  var exitInProgress by remember { mutableStateOf(false) }
  val elevationManager = remember { ElevationManager() }
  val actions = remember { DesktopActions() }
  val sessionState by session.state.collectAsState()
  val trayState = rememberTrayState()
  var windowVisible by remember { mutableStateOf("--minimized" !in args || importLink != null) }
  var activationRevision by remember { mutableStateOf(0L) }
  var externalImportRequest by remember { mutableStateOf(importLink) }
  var language by remember { mutableStateOf(UiLanguageStore.load()) }
  val connected = sessionState.phase is VpnPhase.Connected ||
    sessionState.phase is VpnPhase.Degraded
  val connecting = actions.preparing || sessionState.phase is VpnPhase.Preparing ||
    sessionState.phase is VpnPhase.Connecting
  val stopping = sessionState.phase is VpnPhase.Stopping
  val stopRequired = sessionState.phase.requiresStopRetry
  val busy = connecting || stopping

  DisposableEffect(instanceGate) {
    instanceGate.setActivationHandler { forwardedImportLink ->
      windowVisible = true
      activationRevision += 1
      forwardedImportLink?.let { externalImportRequest = it }
    }
    onDispose { instanceGate.setActivationHandler {} }
  }

  LaunchedEffect(sessionState.phase) {
    when (val phase = sessionState.phase) {
      is VpnPhase.Connected -> trayState.sendNotification(
        Notification("Veilark", language.text("VPN подключён", "VPN connected"), Notification.Type.Info),
      )
      is VpnPhase.Error -> trayState.sendNotification(
        Notification(
          "Veilark",
          conciseTechnicalMessage(
            phase.message,
            language.text(
              "Не удалось подключиться. Откройте Veilark, чтобы посмотреть подробности.",
              "Could not connect. Open Veilark for details.",
            ),
            language.text(
              "Не удалось проверить защищённое соединение. Откройте Veilark для подробностей.",
              "Could not verify the secure connection. Open Veilark for details.",
            ),
            language,
          ),
          Notification.Type.Error,
        ),
      )
      is VpnPhase.Degraded -> trayState.sendNotification(
        Notification(
          "Veilark",
          conciseTechnicalMessage(
            phase.message,
            language.text(
              "Туннель запущен, но проверка интернета не завершена.",
              "The tunnel is running, but the internet check did not finish.",
            ),
            language.text(
              "Туннель запущен, но защищённая проверка интернета не прошла.",
              "The tunnel is running, but the secure internet check failed.",
            ),
            language,
          ),
          Notification.Type.Warning,
        ),
      )
      else -> Unit
    }
  }

  Tray(
    state = trayState,
    icon = painterResource("veilark-app-icon.png"),
    tooltip = when {
      stopRequired -> language.text("Veilark · Требуется остановка", "Veilark · Stop required")
      connected -> language.text("Veilark · Защищено", "Veilark · Protected")
      busy -> language.text("Veilark · Подключение", "Veilark · Connecting")
      else -> language.text("Veilark · Отключено", "Veilark · Disconnected")
    },
    onAction = { windowVisible = true },
    menu = {
      Item(
        when {
          stopping -> language.text("Отключение…", "Disconnecting…")
          stopRequired -> language.text("Повторить остановку", "Retry stop")
          connecting -> language.text("Остановить подключение", "Stop connecting")
          connected -> language.text("Отключить", "Disconnect")
          else -> language.text("Подключить", "Connect")
        },
        enabled = !stopping && (connected || connecting || stopRequired || actions.hasProfile),
        onClick = if (connected || connecting || stopRequired) actions.disconnect else actions.connect,
      )
      Item(language.text("Открыть Veilark", "Open Veilark"), onClick = { windowVisible = true })
      Separator()
      Item(
        language.text("Выход", "Exit"),
        enabled = !exitInProgress,
        onClick = {
          exitInProgress = true
          exitScope.launch {
            val stopped = runCatching { withContext(Dispatchers.IO) { session.disconnect() } }
            if (stopped.isSuccess) {
              exitApplication()
            } else {
              exitInProgress = false
              trayState.sendNotification(Notification(
                "Veilark",
                language.text("Не удалось остановить VPN. Откройте приложение и повторите остановку.",
                  "Could not stop the VPN. Open Veilark and retry."),
                Notification.Type.Error,
              ))
            }
          }
        },
      )
    },
  )
  Window(
    onCloseRequest = { windowVisible = false },
    visible = windowVisible,
    title = "Veilark",
    icon = painterResource("veilark-app-icon.png"),
    state = rememberWindowState(width = InitialWindowWidth, height = InitialWindowHeight),
  ) {
    LaunchedEffect(windowVisible, activationRevision) {
      if (windowVisible) {
        window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
        window.toFront()
        window.requestFocus()
      }
    }
    val windowDensity = LocalDensity.current
    DisposableEffect(windowDensity) {
      window.iconImages = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256).mapNotNull { size ->
        Thread.currentThread().contextClassLoader.getResource("icons/veilark-$size.png")
          ?.let(javax.imageio.ImageIO::read)
      }
      window.minimumSize = with(windowDensity) {
        java.awt.Dimension(
          MinimumWindowWidth.roundToPx(),
          MinimumWindowHeight.roundToPx(),
        )
      }
      window.transferHandler = object : TransferHandler() {
        override fun canImport(support: TransferSupport): Boolean =
          support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

        override fun importData(support: TransferSupport): Boolean {
          if (!canImport(support)) return false
          val files = runCatching {
            @Suppress("UNCHECKED_CAST")
            support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
          }.getOrDefault(emptyList())
          files.firstOrNull()?.toPath()?.let(actions.importFile) ?: return false
          return true
        }
      }
      onDispose { window.transferHandler = null }
    }
    VeilarkTheme {
      CompositionLocalProvider(LocalUiLanguage provides language) {
        VeilarkApp(
          session = session,
          elevationManager = elevationManager,
          desktopActions = actions,
          connectOnLaunch = "--connect" in args,
          externalImportUrl = externalImportRequest,
          onExternalImportConsumed = { externalImportRequest = null },
          onLanguage = { selected ->
            language = selected
            runCatching { UiLanguageStore.save(selected) }
          },
          onExit = ::exitApplication,
        )
      }
    }
  }
    }
  } finally {
    instanceGate.close()
  }
  // HTTP clients and the AWT toolkit keep non-daemon threads alive, so leaving
  // `main` is not enough to end the process once the window is closed.
  exitProcess(0)
}

@Composable
private fun VeilarkApp(
  session: WindowsVpnSession,
  elevationManager: ElevationManager,
  desktopActions: DesktopActions,
  connectOnLaunch: Boolean,
  externalImportUrl: String?,
  onExternalImportConsumed: () -> Unit,
  onLanguage: (UiLanguage) -> Unit,
  onExit: () -> Unit,
) {
  val language = LocalUiLanguage.current
  val scope = rememberCoroutineScope()
  val preparation = remember(scope) { ConnectionPreparation(scope) }
  val preparing by preparation.busy.collectAsState()
  desktopActions.preparing = preparing
  val importer = remember { ProfileImporter() }
  val profileStore = remember { ProfileStore() }
  val geoPreflight = remember { lazy(LazyThreadSafetyMode.NONE) { GeoRoutingPreflight() } }
  val updates = remember { DesktopUpdateController() }
  val updateState by updates.state.collectAsState()
  val storedProfilesResult: Result<StoredProfiles> = remember {
    when (val loaded = profileStore.loadResult()) {
      ProfileLoadResult.Missing -> Result.success(StoredProfiles())
      is ProfileLoadResult.Loaded -> runCatching {
        LegacyBuiltInTrustMigration.remove(loaded.stored).also { migrated ->
          if (migrated != loaded.stored) profileStore.save(migrated)
        }
      }
      is ProfileLoadResult.Failed -> Result.failure(loaded.cause)
    }
  }
  val storedProfiles = storedProfilesResult.getOrNull()
  if (storedProfiles == null) {
    ProfileStoreUnavailable(onExit)
    return
  }
  val elevated = remember { elevationManager.isElevated() }
  val state by session.state.collectAsState()
  val visiblePhase = if (preparing && (state.phase is VpnPhase.Idle || state.phase is VpnPhase.Error)) {
    VpnPhase.Preparing
  } else state.phase
  val snackbar = remember { SnackbarHostState() }
  var destination by remember { mutableStateOf(Destination.Home) }
  var profiles by remember {
    mutableStateOf(storedProfiles.profiles.associateBy(Profile::engine))
  }
  var subscriptions by remember { mutableStateOf(storedProfiles.subscriptions) }
  var selectedSubscriptionIds by remember {
    mutableStateOf(storedProfiles.selectedSubscriptionIds)
  }
  var selectedEngine by remember {
    mutableStateOf(
      storedProfiles.selectedEngine.takeIf(profiles::containsKey)
        ?: profiles.keys.firstOrNull()
        ?: VpnEngine.SingBox,
    )
  }
  var routingSettings by remember { mutableStateOf(storedProfiles.routing) }
  var selectedNodeTags by remember {
    mutableStateOf(storedProfiles.selectedNodeTags)
  }
  var importDialog by remember { mutableStateOf(false) }
  var importDraft by remember { mutableStateOf("") }
  var pendingDelete by remember { mutableStateOf<SubscriptionRecord?>(null) }
  var importing by remember { mutableStateOf(false) }
  var refreshing by remember { mutableStateOf(false) }
  var geoRefreshing by remember { mutableStateOf(false) }
  var probingProfileId by remember { mutableStateOf<String?>(null) }
  var catalogRevision by remember { mutableStateOf(0L) }
  var autoConnectAttempted by remember { mutableStateOf(false) }
  var nodeLatencies by remember {
    mutableStateOf<Map<String, Map<String, NodeLatency>>>(emptyMap())
  }
  val connected = state.phase is VpnPhase.Connected ||
    state.phase is VpnPhase.Degraded
  val configurationLocked = preparing || state.phase.locksConfiguration
  val profile = profiles[selectedEngine]
  val activeSubscription = selectedSubscriptionIds[selectedEngine]?.let { id ->
    subscriptions.firstOrNull { it.id == id }
  }

  LaunchedEffect(Unit) {
    updates.initialize()?.let { outcome ->
      snackbar.showSnackbar(outcome.message.ifBlank {
        language.text("Обновление Veilark завершено", "Veilark update completed")
      })
    }
  }

  fun refreshGeoData() {
    val phase = session.state.value.phase
    if (geoRefreshing || preparation.busy.value || phase.locksConfiguration) return
    geoRefreshing = true
    scope.launch {
      try {
        withContext(Dispatchers.IO) { geoPreflight.value.refresh() }
        snackbar.showSnackbar(
          language.text(
            "Геоданные обновлены. Новые правила применятся при следующем подключении.",
            "Geo data updated. New rules will apply on the next connection.",
          ),
        )
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        snackbar.showSnackbar(
          error.message ?: language.text(
            "Не удалось обновить геоданные маршрутизации",
            "Could not update routing geo data",
          ),
        )
      } finally {
        geoRefreshing = false
      }
    }
  }

  fun currentStored(): StoredProfiles = StoredProfiles(
    profiles = profiles.values.toList(),
    selectedEngine = selectedEngine,
    routing = routingSettings,
    selectedNodeTags = selectedNodeTags,
    subscriptions = subscriptions,
    selectedSubscriptionIds = selectedSubscriptionIds,
  )

  fun applyStored(updated: StoredProfiles) {
    profiles = updated.profiles.associateBy(Profile::engine)
    subscriptions = updated.subscriptions
    selectedEngine = updated.selectedEngine
    routingSettings = updated.routing
    selectedNodeTags = updated.selectedNodeTags
    selectedSubscriptionIds = updated.selectedSubscriptionIds
    catalogRevision += 1
  }

  fun configurationLockedNow(): Boolean = preparation.busy.value || session.state.value.phase.locksConfiguration

  suspend fun configuredProfile(): Result<Profile> {
    val selected = profile
      ?: return Result.failure(IllegalStateException(
        language.text("Сначала добавьте профиль", "Add a profile first"),
      ))
    val routingSnapshot = routingSettings
    val nodeSnapshot = selectedNodeTags[selected.engine] ?: ProfileSelection.AUTOMATIC_TAG
    return try { Result.success(run {
      val geoAssets = if (
        routingSnapshot.mode == RoutingMode.RussiaDirect ||
        routingSnapshot.mode == RoutingMode.RussiaVpn
      ) {
        withContext(Dispatchers.IO) {
          geoPreflight.value.requirePrepared(routingSnapshot)
        }
      } else {
        null
      }
      ProfileConfiguration.apply(
        selected,
        routingSnapshot,
        nodeSnapshot,
        geoRoutingAssets = geoAssets,
      )
    }) } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
  }

  fun connect() {
    if (configurationLockedNow()) return
    if (importing || refreshing || geoRefreshing) {
      scope.launch {
        snackbar.showSnackbar(language.text("Дождитесь завершения обновления данных", "Wait for the data update to finish"))
      }
      return
    }
    if (profile == null) {
      importDialog = true
      return
    }
    if (!elevationManager.isElevated()) {
      scope.launch {
        withContext(Dispatchers.IO) {
          profileStore.save(
            currentStored(),
          )
        }
        if (elevationManager.relaunch(listOf("--connect"))) {
          onExit()
        } else {
          snackbar.showSnackbar(
            language.text(
              "Для WinTUN нужны права администратора. Подтвердите запрос Windows.",
              "WinTUN requires administrator access. Confirm the Windows prompt.",
            ),
          )
        }
      }
      return
    }
    preparation.start(action = {
      val configured = configuredProfile().getOrThrow()
      kotlinx.coroutines.currentCoroutineContext().ensureActive()
      session.connect(configured)
    }, onFailure = { error ->
          snackbar.showSnackbar(error.message ?: language.text("Не удалось применить настройки", "Could not apply settings"))
    })
  }

  fun disconnect() {
    preparation.cancel()
    scope.launch {
      preparation.cancelAndJoin()
      runCatching { session.disconnect() }
        .onFailure { error ->
          snackbar.showSnackbar(
            error.message ?: language.text("Не удалось остановить VPN-ядро", "Could not stop the VPN core"),
          )
        }
    }
  }

  LaunchedEffect(
    profiles,
    subscriptions,
    selectedEngine,
    selectedSubscriptionIds,
    routingSettings,
    selectedNodeTags,
  ) {
    runCatching {
      withContext(Dispatchers.IO) { profileStore.save(currentStored()) }
    }.onFailure {
      snackbar.showSnackbar(
        language.text(
          "Не удалось сохранить подписки. Исходное хранилище и резервная копия сохранены.",
          "Could not save subscriptions. The original store and backup were preserved.",
        ),
      )
    }
  }

  // The DPAPI-backed profile is loaded asynchronously. The elevated --connect
  // process must wait for it instead of checking the initial empty state once.
  LaunchedEffect(connectOnLaunch, profile, geoRefreshing) {
    if (
      connectOnLaunch &&
      !autoConnectAttempted &&
      elevationManager.isElevated() &&
      profile != null &&
      !geoRefreshing
    ) {
      autoConnectAttempted = true
      preparation.start(action = {
        val configured = configuredProfile().getOrThrow()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        session.connect(configured)
      }, onFailure = { error ->
          snackbar.showSnackbar(error.message ?: language.text("Не удалось применить настройки", "Could not apply settings"))
      })
    }
  }

  fun accept(
    result: ImportResult,
    refreshedSubscription: SubscriptionRecord? = null,
  ) {
    when (result) {
      is ImportResult.Success -> {
        if (configurationLockedNow()) {
          scope.launch {
            snackbar.showSnackbar(language.text("Сначала отключите VPN, затем измените профили", "Disconnect VPN before changing profiles"))
          }
          return
        }
        val refreshed = refreshedSubscription != null
        if (refreshed && result.rejectedProfiles > 0) {
          scope.launch {
            snackbar.showSnackbar(
              language.text(
                "Обновление не применено: сервер вернул повреждённые записи (${result.rejectedProfiles})",
                "Update was not applied: the server returned invalid entries (${result.rejectedProfiles})",
              ),
            )
          }
          return
        }
        val updated = if (refreshedSubscription != null) {
          SubscriptionCatalog.put(
            currentStored(),
            refreshedSubscription.copy(
              profiles = result.profiles,
              sourceLabel = result.profiles.first().sourceLabel,
              sourceUrl = result.profiles.mapNotNull(Profile::sourceUrl)
                .distinct()
                .singleOrNull()
                ?: refreshedSubscription.sourceUrl,
            ),
            select = false,
          )
        } else {
          SubscriptionCatalog.fromImportedProfiles(currentStored(), result.profiles)
        }
        applyStored(updated)
        val invalidatedProfiles = buildSet {
          refreshedSubscription?.profiles?.mapTo(this, Profile::id)
          result.profiles.mapTo(this, Profile::id)
        }
        nodeLatencies = nodeLatencies - invalidatedProfiles
        if (!refreshed) destination = Destination.Home
        importDialog = false
        val importedNodeSummary = result.profiles.joinToString(" · ") {
          "${it.engine.displayName}: ${it.nodes.size}"
        }
        scope.launch {
          snackbar.showSnackbar(
            if (refreshed && result.rejectedProfiles == 0) {
              language.text(
                "Подписка обновлена · $importedNodeSummary",
                "Subscription refreshed · $importedNodeSummary",
              )
            } else if (result.rejectedProfiles == 0) {
              language.text(
                "Подписка импортирована · $importedNodeSummary",
                "Subscription imported · $importedNodeSummary",
              )
            } else {
              language.text(
                "Подписка импортирована · $importedNodeSummary · пропущено: ${result.rejectedProfiles}",
                "Subscription imported · $importedNodeSummary · skipped: ${result.rejectedProfiles}",
              )
            },
          )
        }
      }
      is ImportResult.Failure -> scope.launch {
        snackbar.showSnackbar(result.safeMessage)
      }
    }
  }

  fun refreshSubscription(subscription: SubscriptionRecord) {
    val sourceUrl = subscription.sourceUrl
    if (sourceUrl == null) {
      scope.launch {
        snackbar.showSnackbar(language.text(
          "Добавьте эту подписку по HTTPS ещё раз — старый источник не был сохранён",
          "Add this HTTPS subscription again; the previous source was not stored",
        ))
      }
      return
    }
    if (refreshing || importing || configurationLockedNow()) {
      scope.launch { snackbar.showSnackbar(language.text("Сначала завершите текущую операцию или остановите VPN", "Finish the current operation or stop VPN first")) }
      return
    }
    val expectedRevision = catalogRevision
    refreshing = true
    scope.launch {
      try {
        val result = withContext(Dispatchers.IO) { importer.fromHttps(sourceUrl) }
        if (
          expectedRevision != catalogRevision ||
          subscriptions.none { it.id == subscription.id }
        ) {
          snackbar.showSnackbar(language.text("Результат обновления отброшен: подписка уже изменилась", "Refresh result discarded because the subscription changed"))
        } else {
          accept(result, refreshedSubscription = subscription)
        }
      } finally {
        refreshing = false
      }
    }
  }

  fun selectSubscription(engine: VpnEngine, subscriptionId: String) {
    if (configurationLockedNow() || importing || refreshing) {
      scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
      return
    }
    runCatching {
      SubscriptionCatalog.select(currentStored(), engine, subscriptionId)
    }.onSuccess(::applyStored).onFailure {
      scope.launch { snackbar.showSnackbar(language.text("Эта подписка не содержит профиль выбранного ядра", "This subscription has no profile for the selected core")) }
    }
  }

  fun selectEndpoint(engine: VpnEngine, subscriptionId: String, nodeTag: String) {
    if (configurationLockedNow() || importing || refreshing) {
      scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
      return
    }
    runCatching {
      SubscriptionCatalog.select(currentStored(), engine, subscriptionId)
    }.onSuccess { selected ->
      applyStored(
        selected.copy(
          selectedNodeTags = selected.selectedNodeTags + (engine to nodeTag),
        ),
      )
    }.onFailure {
      scope.launch { snackbar.showSnackbar(language.text("Этот сервер больше недоступен", "This server is no longer available")) }
    }
  }

  fun removeSubscription(subscription: SubscriptionRecord) {
    if (configurationLockedNow() || importing || refreshing) {
      scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
      return
    }
    val before = currentStored()
    when (val result = ProfileLifecycle.remove(before, subscription.id)) {
      is ProfileRemovalResult.Removed -> {
        applyStored(result.stored)
        nodeLatencies = nodeLatencies - subscription.profiles.map(Profile::id).toSet()
        scope.launch {
          val response = snackbar.showSnackbar(
            message = language.text("Подписка «${subscription.name}» удалена", "Subscription “${subscription.name}” deleted"),
            actionLabel = language.text("Отменить", "Undo"),
            withDismissAction = true,
          )
          if (response == SnackbarResult.ActionPerformed) {
            if (subscriptions.any { it.id == subscription.id }) {
              snackbar.showSnackbar(language.text("Подписка уже добавлена заново", "The subscription was already added again"))
            } else {
              runCatching {
                SubscriptionCatalog.put(currentStored(), subscription, select = false)
              }.onSuccess { restored ->
                applyStored(restored)
                snackbar.showSnackbar(language.text("Подписка восстановлена", "Subscription restored"))
              }.onFailure {
                snackbar.showSnackbar(language.text("Не удалось восстановить подписку", "Could not restore the subscription"))
              }
            }
          }
        }
      }
      is ProfileRemovalResult.Protected -> scope.launch {
        snackbar.showSnackbar(language.text("Встроенную Veilark Trust удалить нельзя", "The built-in Veilark Trust subscription cannot be deleted"))
      }
      is ProfileRemovalResult.NotFound -> scope.launch {
        snackbar.showSnackbar(language.text("Подписка уже удалена", "The subscription is already deleted"))
      }
      is ProfileRemovalResult.Unavailable -> scope.launch {
        snackbar.showSnackbar(result.safeMessage)
      }
    }
  }

  fun probeNodes(profile: Profile) {
    if (probingProfileId != null || profile.nodes.isEmpty()) return
    probingProfileId = profile.id
    scope.launch {
      try {
        runCatching {
          withContext(Dispatchers.IO) { NodeLatencyProbe().probe(profile) }
        }.onSuccess { result ->
          if (profiles[profile.engine]?.id == profile.id) {
            nodeLatencies = nodeLatencies + (profile.id to result)
            val available = result.values.count(NodeLatency::available)
            snackbar.showSnackbar(language.text("Доступно узлов: $available из ${result.size}", "Available servers: $available of ${result.size}"))
          }
        }.onFailure {
          snackbar.showSnackbar(language.text("Не удалось проверить задержку узлов", "Could not test server latency"))
        }
      } finally {
        if (probingProfileId == profile.id) probingProfileId = null
      }
    }
  }

  // A validated `veilark://import` deep link opens the same reviewed import dialog
  // prefilled with its payload. The request is cleared immediately so that a
  // recomposition cannot reopen the dialog with a stale value.
  LaunchedEffect(externalImportUrl) {
    val value = externalImportUrl ?: return@LaunchedEffect
    onExternalImportConsumed()
    if (configurationLockedNow() || importing || refreshing) {
      snackbar.showSnackbar(language.text("Сначала отключите VPN", "Disconnect VPN first"))
    } else {
      importDraft = value
      importDialog = true
    }
  }

  SideEffect {
    desktopActions.connect = ::connect
    desktopActions.disconnect = ::disconnect
    desktopActions.hasProfile = profile != null
    desktopActions.importFile = { path ->
      if (configurationLockedNow() || importing || refreshing) {
        scope.launch { snackbar.showSnackbar(language.text("Сначала отключите VPN", "Disconnect VPN first")) }
      } else {
        importing = true
        scope.launch {
          val result = withContext(Dispatchers.IO) { importer.fromFile(path) }
          importing = false
          accept(result)
        }
      }
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      AppTopBar(
        phase = visiblePhase,
        onLanguage = onLanguage,
      )
    },
    bottomBar = {
      AppBottomBar(
        destination = destination,
        onDestination = { destination = it },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (destination != Destination.Updates) {
        UpdateBanner(
          state = updateState,
          onOpen = { destination = Destination.Updates },
        )
      }
      Box(Modifier.fillMaxWidth().weight(1f)) {
        AnimatedContent(
          targetState = destination,
          transitionSpec = {
            fadeIn(tween(160, easing = FastOutSlowInEasing))
              .togetherWith(fadeOut(tween(100)))
          },
        ) { currentDestination ->
          when (currentDestination) {
          Destination.Home -> HomeScreen(
            phase = visiblePhase,
            traffic = state.traffic,
            elevated = elevated,
            profile = profile,
            selectedEngine = selectedEngine,
            selectedNodeTag = selectedNodeTags[selectedEngine],
            nodeLatencies = profile?.let { nodeLatencies[it.id] }.orEmpty(),
            probing = probingProfileId == profile?.id,
            refreshing = refreshing,
            availableEngines = profiles.keys,
            onEngineSelect = { engine ->
              if (configurationLockedNow() || importing || refreshing) {
                scope.launch { snackbar.showSnackbar(language.text("Сначала отключите VPN", "Disconnect VPN first")) }
              } else if (profiles.containsKey(engine)) {
                selectedEngine = engine
              } else {
                importDialog = true
              }
            },
            onConnect = {
              connect()
            },
            onDisconnect = ::disconnect,
            onImport = {
              importDraft = ""
              importDialog = true
            },
            subscriptions = subscriptions,
            activeSubscriptionId = selectedSubscriptionIds[selectedEngine],
            onSelectEndpoint = { subscriptionId, nodeTag ->
              selectEndpoint(selectedEngine, subscriptionId, nodeTag)
            },
            onRefresh = { activeSubscription?.let(::refreshSubscription) },
            onProbe = { profile?.let(::probeNodes) },
            onOpenLogs = { destination = Destination.Logs },
          )
          Destination.Profiles -> ProfilesScreen(
            subscriptions = subscriptions,
            selectedEngine = selectedEngine,
            selectedSubscriptionIds = selectedSubscriptionIds,
            selectedNodeTag = selectedNodeTags[selectedEngine],
            nodeLatencies = profile?.let { nodeLatencies[it.id] }.orEmpty(),
            probing = probingProfileId == profile?.id,
            refreshing = refreshing,
            configurationEnabled = !configurationLocked,
            onSelectSubscription = ::selectSubscription,
            onSelectNode = { tag ->
              if (!configurationLockedNow() && !importing && !refreshing) {
                selectedNodeTags = selectedNodeTags + (selectedEngine to tag)
              } else {
                scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
              }
            },
            onRefresh = ::refreshSubscription,
            onProbe = { profile -> probeNodes(profile) },
            onDelete = {
              if (!configurationLockedNow() && !importing && !refreshing) {
                pendingDelete = it
              } else {
                scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
              }
            },
            importing = importing,
            onOpenSubscriptionAccount = {
              if (!TelegramBotLink.openConfigured()) {
                scope.launch {
                  snackbar.showSnackbar(
                    language.text(
                      "Не удалось открыть Telegram. Откройте @senyavpn_bot вручную.",
                      "Could not open Telegram. Open @senyavpn_bot manually.",
                    ),
                  )
                }
              }
            },
            onOpenWebApp = {
              if (!VeilarkWebAppLink.openConfigured()) {
                scope.launch {
                  snackbar.showSnackbar(
                    language.text(
                      "Не удалось открыть веб-кабинет.",
                      "Could not open the web account.",
                    ),
                  )
                }
              }
            },
            onPaste = {
              if (configurationLockedNow() || importing || refreshing) {
                scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
                return@ProfilesScreen
              }
              importing = true
              scope.launch {
                val result = withContext(Dispatchers.IO) {
                  runCatching {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    importer.fromText(
                      clipboard.getData(DataFlavor.stringFlavor) as String,
                      language.text("Буфер обмена", "Clipboard"),
                    )
                  }.getOrElse {
                    ImportResult.Failure(language.text("В буфере обмена нет текстового профиля", "The clipboard contains no text profile"))
                  }
                }
                importing = false
                accept(result)
              }
            },
            onFile = {
              if (configurationLockedNow() || importing || refreshing) {
                scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
                return@ProfilesScreen
              }
              chooseProfileFile()?.let { path ->
                importing = true
                scope.launch {
                  val result = withContext(Dispatchers.IO) { importer.fromFile(path) }
                  importing = false
                  accept(result)
                }
              }
            },
            onUrl = { importDialog = true },
          )
          Destination.Routing -> RoutingScreen(
            settings = routingSettings,
            hasAnyProfile = profiles.isNotEmpty(),
            hasSingBoxProfile = profiles[VpnEngine.SingBox] != null,
            configurationEnabled = !configurationLocked,
            geoRefreshing = geoRefreshing,
            onRefreshGeo = ::refreshGeoData,
            onSave = { updated ->
              if (configurationLockedNow()) {
                scope.launch { snackbar.showSnackbar(language.text("Сначала остановите VPN", "Stop VPN first")) }
              } else {
                val validation = runCatching {
                  if (updated.mode == RoutingMode.Manual) {
                    val direct = ProfileSelection.routingEntries(updated.directEntries)
                    val viaVpn = ProfileSelection.routingEntries(updated.vpnEntries)
                    require(
                      direct.domains.isNotEmpty() || direct.networks.isNotEmpty() ||
                        viaVpn.domains.isNotEmpty() || viaVpn.networks.isNotEmpty(),
                    ) {
                      "Добавьте хотя бы один домен или IP-диапазон"
                    }
                  }
                }
                if (validation.isSuccess) {
                  routingSettings = updated
                  scope.launch { snackbar.showSnackbar(language.text("Настройки маршрутизации сохранены", "Routing settings saved")) }
                } else {
                  scope.launch {
                    snackbar.showSnackbar(
                      validation.exceptionOrNull()?.message
                        ?: language.text("Проверьте домены и IP-диапазоны", "Check domains and IP ranges"),
                    )
                  }
                }
              }
            },
          )
          Destination.Diagnostics -> DiagnosticsScreen()
          Destination.Updates -> UpdatesScreen(
            state = updateState,
            onCheck = { scope.launch { updates.check() } },
            onDownload = { update -> scope.launch { updates.download(update) } },
            onCancelDownload = updates::cancelDownload,
            onInstall = { ready ->
              scope.launch {
                val snapshot = currentStored()
                val saved = runCatching {
                  withContext(Dispatchers.IO) { profileStore.save(snapshot) }
                }
                if (saved.isFailure) {
                  snackbar.showSnackbar(language.text("Обновление не запущено: не удалось сохранить подписки", "Update was not started because subscriptions could not be saved"))
                } else {
                  updates.install(ready) { session.disconnect() }
                    .onSuccess { onExit() }
                }
              }
            },
          )
            Destination.Logs -> LogsScreen()
          }
        }
      }
    }
  }

  if (importDialog) {
    ImportDialog(
      importing = importing,
      initialValue = importDraft,
      onDismiss = {
        if (!importing) {
          importDialog = false
          importDraft = ""
        }
      },
      onImport = { value ->
        importing = true
        scope.launch {
          val result = withContext(Dispatchers.IO) {
            if (value.trim().startsWith("https://", ignoreCase = true)) {
              importer.fromHttps(value)
            } else {
              importer.fromText(value, language.text("Вставка", "Paste"))
            }
          }
          importing = false
          accept(result)
        }
      },
    )
  }
  pendingDelete?.let { subscription ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text(language.text("Удалить подписку?", "Delete subscription?")) },
      text = {
        Text(
          language.text(
            "«${subscription.name}» и все её профили будут удалены. Действие можно отменить сразу после удаления.",
            "“${subscription.name}” and all its profiles will be deleted. You can undo immediately after deletion.",
          ),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            pendingDelete = null
            removeSubscription(subscription)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
        ) {
          Text(language.text("Удалить", "Delete"))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) { Text(language.text("Отмена", "Cancel")) }
      },
    )
  }
}

@Composable
private fun ProfileStoreUnavailable(onExit: () -> Unit) {
  val language = LocalUiLanguage.current
  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier = Modifier.widthIn(max = 560.dp).padding(24.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      shape = RoundedCornerShape(16.dp),
    ) {
      Column(Modifier.padding(24.dp)) {
        Icon(
          VeilarkMark,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(28.dp),
        )
        Text(
          language.text("Не удалось открыть защищённые данные", "Could not open protected data"),
          modifier = Modifier.padding(top = 14.dp),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          language.text(
            "Veilark не перезаписывал файл подписок. Перезапустите приложение под тем же пользователем Windows. Если ошибка повторится, сохраните журнал для диагностики.",
            "Veilark did not overwrite the subscription store. Restart the app as the same Windows user. If it happens again, save the log for diagnostics.",
          ),
          modifier = Modifier.padding(top = 8.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
          onClick = onExit,
          modifier = Modifier.padding(top = 20.dp),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(language.text("Закрыть Veilark", "Close Veilark"))
        }
      }
    }
  }
}

@Composable
private fun HomeScreen(
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
      if (profile != null) {
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
  is VpnPhase.Degraded -> conciseTechnicalMessage(
    message = phase.message,
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
  is VpnPhase.Error -> conciseTechnicalMessage(
    message = phase.message,
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

private fun conciseTechnicalMessage(
  message: String,
  fallback: String,
  certificateFallback: String = fallback,
  language: UiLanguage = UiLanguage.Russian,
): String {
  val normalized = message.replace(Regex("\\s+"), " ").trim()
  if (normalized.isBlank()) return fallback
  val lower = normalized.lowercase(Locale.ROOT)
  return when {
    lower.contains("активен другой vpn") || lower.contains("competing tunnel") ->
      language.text(
        normalized,
        "Another VPN tunnel is active. Disconnect it, then try Veilark again.",
      )
    lower.contains("pkix") ||
      lower.contains("certpath") ||
      lower.contains("certificate") ||
      lower.contains("sslhandshake") ->
      certificateFallback
    lower.contains("timeout") ||
      lower.contains("timed out") ||
      lower.contains("не отвечает") ->
      language.text("Узел не отвечает вовремя. Попробуйте другой сервер.", "The server timed out. Try another server.")
    lower.contains("connection refused") || lower.contains("соединение отклонено") ->
      language.text("Узел отклонил соединение. Попробуйте другой сервер.", "The server refused the connection. Try another server.")
    normalized.length > 180 ||
      lower.contains("exception") ||
      lower.contains("javax.") ||
      lower.contains("java.") ||
      lower.contains("sun.") -> fallback
    else -> normalized
  }
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

private data class EndpointPickerChoice(
  val subscriptionId: String,
  val subscriptionName: String,
  val profileId: String,
  val tag: String,
  val name: String,
  val detail: String,
  val country: NodeCountry?,
  val automatic: Boolean,
  val builtIn: Boolean,
)

private data class EndpointPickerGroup(
  val subscriptionId: String,
  val subscriptionName: String,
  val profileId: String,
  val builtIn: Boolean,
  val serverCount: Int,
  val choices: List<EndpointPickerChoice>,
)

@Composable
private fun EndpointPicker(
  subscriptions: List<SubscriptionRecord>,
  engine: VpnEngine,
  activeSubscriptionId: String?,
  selectedNodeTag: String?,
  latencies: Map<String, NodeLatency>,
  enabled: Boolean,
  onSelect: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalUiLanguage.current
  var expanded by remember { mutableStateOf(false) }
  var query by remember(engine) { mutableStateOf("") }
  val groups = remember(subscriptions, engine, language) {
    subscriptions.mapNotNull { subscription ->
      val profile = subscription.profile(engine) ?: return@mapNotNull null
      val choices = buildList {
        if (engine == VpnEngine.SingBox) {
          add(
            EndpointPickerChoice(
              subscriptionId = subscription.id,
              subscriptionName = subscription.name,
              profileId = profile.id,
              tag = ProfileSelection.AUTOMATIC_TAG,
              name = language.text("Автоматически", "Automatic"),
              detail = language.text("Лучший доступный узел", "Best available server"),
              country = null,
              automatic = true,
              builtIn = subscription.origin == SubscriptionOrigin.BuiltIn,
            ),
          )
        }
        profile.nodes.forEach { node ->
          add(
            EndpointPickerChoice(
              subscriptionId = subscription.id,
              subscriptionName = subscription.name,
              profileId = profile.id,
              tag = node.tag,
              name = NodePresentation.displayName(node.name),
              detail = node.protocol,
              country = NodePresentation.country(node.name),
              automatic = false,
              builtIn = subscription.origin == SubscriptionOrigin.BuiltIn,
            ),
          )
        }
      }
      EndpointPickerGroup(
        subscriptionId = subscription.id,
        subscriptionName = subscription.name,
        profileId = profile.id,
        builtIn = subscription.origin == SubscriptionOrigin.BuiltIn,
        serverCount = profile.nodes.size,
        choices = choices,
      )
    }
  }
  val activeGroup = groups.firstOrNull { it.subscriptionId == activeSubscriptionId }
    ?: groups.firstOrNull()
  val activeChoice = activeGroup?.choices
    ?.firstOrNull { it.tag == selectedNodeTag }
    ?: activeGroup?.choices?.firstOrNull()
  val totalChoices = groups.sumOf { it.choices.size }
  val totalServers = groups.sumOf { it.serverCount }
  val listState = rememberLazyListState()
  val needle = query.trim()
  val filteredGroups = remember(groups, needle) {
    if (needle.isEmpty()) groups else groups.mapNotNull { group ->
      val groupMatches = group.subscriptionName.contains(needle, ignoreCase = true)
      val choices = if (groupMatches) group.choices else group.choices.filter { choice ->
        choice.name.contains(needle, ignoreCase = true) ||
          choice.detail.contains(needle, ignoreCase = true) ||
          choice.tag.contains(needle, ignoreCase = true)
      }
      group.copy(choices = choices).takeIf { it.choices.isNotEmpty() }
    }
  }
  val arrowRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(160),
  )

  Column(modifier) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      // Keep the menu readable at normal scale while allowing it to fit
      // narrow windows and high-DPI layouts without being clipped.
      val popupWidth = (maxWidth - 16.dp).coerceAtMost(420.dp).coerceAtLeast(220.dp)
      OutlinedButton(
        onClick = {
          query = ""
          expanded = true
        },
        enabled = enabled && groups.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
      ) {
        EndpointMark(
          country = activeChoice?.country,
          automatic = activeChoice?.automatic == true,
          modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
          Text(
            activeChoice?.name ?: language.text("Нет доступных узлов", "No servers available"),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            activeChoice?.let { "${it.subscriptionName} · ${it.detail}" }
              ?: language.text(
                "Добавьте подписку для ${engine.displayName}",
                "Add a subscription for ${engine.displayName}",
              ),
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = .72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Icon(
          Icons.Rounded.ArrowDropDown,
          null,
          Modifier.graphicsLayer { rotationZ = arrowRotation },
        )
      }
      StablePickerPopup(
        expanded = expanded,
        onDismiss = {
          expanded = false
          query = ""
        },
        width = popupWidth,
      ) {
        Column(Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              language.text(
                "Серверы · ${engine.displayName} · $totalServers",
                "Servers · ${engine.displayName} · $totalServers",
              ),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
              onClick = {
                expanded = false
                query = ""
              },
            ) { Text(language.text("Закрыть", "Close")) }
          }
          if (totalChoices > 8) {
            OutlinedTextField(
              value = query,
              onValueChange = { query = it },
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
              singleLine = true,
              placeholder = { Text(language.text("Найти сервер", "Find server")) },
              shape = RoundedCornerShape(12.dp),
            )
          }
          HorizontalDivider()
          if (filteredGroups.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxWidth().height(88.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                language.text("Ничего не найдено", "Nothing found"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            Box(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
              LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                contentPadding = PaddingValues(start = 0.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
              ) {
                filteredGroups.forEach { group ->
                item(key = "group-${group.subscriptionId}") {
                  Row(
                    modifier = Modifier.fillMaxWidth()
                      .padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      group.subscriptionName,
                      modifier = Modifier.weight(1f),
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      if (group.builtIn) {
                        language.text("Встроенная", "Built-in")
                      } else {
                        language.text("${group.serverCount} серверов", "${group.serverCount} servers")
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                items(group.choices, key = { choice ->
                  "${choice.subscriptionId}:${choice.tag}"
                }) { choice ->
                  val selected = choice.subscriptionId == activeGroup?.subscriptionId &&
                    choice.tag == activeChoice?.tag
                  val latency = if (choice.profileId == activeGroup?.profileId) {
                    latencies[choice.tag]
                  } else {
                    null
                  }
                  Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
                      .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                      ) {
                        expanded = false
                        query = ""
                        onSelect(choice.subscriptionId, choice.tag)
                      },
                    color = if (selected) {
                      MaterialTheme.colorScheme.secondaryContainer
                    } else {
                      Color.Transparent
                    },
                    shape = RoundedCornerShape(10.dp),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      EndpointMark(
                        country = choice.country,
                        automatic = choice.automatic,
                        modifier = Modifier.size(22.dp),
                      )
                      Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                          choice.name,
                          fontWeight = FontWeight.Medium,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          choice.detail,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      if (latency != null) {
                        Text(
                          if (latency.available) {
                            "${latency.millis} ms"
                          } else {
                            language.text("таймаут", "timeout")
                          },
                          style = MaterialTheme.typography.labelMedium,
                          color = if (latency.available) {
                            MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.error
                          },
                        )
                      }
                      if (selected) {
                        Icon(
                          Icons.Rounded.Check,
                          null,
                          modifier = Modifier.padding(start = 8.dp).size(18.dp),
                          tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                      }
                    }
                  }
                }
                }
              }
              VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                  .padding(vertical = 5.dp, horizontal = 2.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EndpointMark(
  country: NodeCountry?,
  automatic: Boolean,
  modifier: Modifier = Modifier,
) {
  when {
    country != null -> CountryFlag(country, modifier)
    automatic -> Icon(Icons.Rounded.Tune, null, modifier)
    else -> Icon(Icons.Rounded.Language, null, modifier)
  }
}

private class AnchoredPopupPositionProvider(
  private val marginPx: Int,
  private val gapPx: Int,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
      anchorBounds.left
    } else {
      anchorBounds.right - popupContentSize.width
    }
    val maximumX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
    val x = preferredX.coerceIn(marginPx, maximumX)
    val below = anchorBounds.bottom + gapPx
    val above = anchorBounds.top - popupContentSize.height - gapPx
    val y = when {
      below + popupContentSize.height <= windowSize.height - marginPx -> below
      above >= marginPx -> above
      else -> (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
    }
    return IntOffset(x, y)
  }
}

@Composable
private fun StablePickerPopup(
  expanded: Boolean,
  onDismiss: () -> Unit,
  width: Dp,
  content: @Composable () -> Unit,
) {
  if (!expanded) return
  val density = LocalDensity.current
  val positionProvider = remember(density) {
    AnchoredPopupPositionProvider(
      marginPx = with(density) { 8.dp.roundToPx() },
      gapPx = with(density) { 5.dp.roundToPx() },
    )
  }
  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = true),
  ) {
    Surface(
      modifier = Modifier.width(width),
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
      content = content,
    )
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
      "${traffic?.let { formatBytes(it.bytesIn) } ?: "—"} / " +
        (traffic?.let { formatBytes(it.bytesOut) } ?: "—"),
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

@Composable
private fun RoutingShortcut(
  routing: RoutingSettings,
  engine: VpnEngine,
  connected: Boolean,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalUiLanguage.current
  val tlsSuffix = if (engine == VpnEngine.SingBox && routing.tlsFragment) {
    language.text(" · TLS-фрагментация", " · TLS fragmentation")
  } else {
    ""
  }
  Surface(
    modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Rounded.Route,
        null,
        modifier = Modifier.size(19.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Column(Modifier.weight(1f).padding(start = 11.dp)) {
        Text(language.text("Маршрутизация", "Routing"), style = MaterialTheme.typography.labelLarge)
        Text(
          when (routing.mode) {
            RoutingMode.All -> if (connected) {
              language.text("Весь трафик · правила активны", "All traffic · rules active") + tlsSuffix
            } else {
              language.text("Весь трафик через VPN", "All traffic through VPN") + tlsSuffix
            }
            RoutingMode.RussiaDirect -> language.text(
              "Россия напрямую · остальное через VPN",
              "Russia direct · everything else through VPN",
            ) + tlsSuffix
            RoutingMode.RussiaVpn -> language.text(
              "Россия через VPN · остальное напрямую",
              "Russia through VPN · everything else direct",
            ) + tlsSuffix
            RoutingMode.Manual -> language.text("Свои правила", "Custom rules") + tlsSuffix
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        language.text("Настроить", "Configure"),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }
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

@Composable
private fun ConnectionCard(
  phase: VpnPhase,
  traffic: TrafficSnapshot?,
  profile: Profile?,
  modifier: Modifier,
  onAction: () -> Unit,
  onOpenLogs: () -> Unit,
) {
  val healthy = phase is VpnPhase.Connected
  val degraded = phase is VpnPhase.Degraded
  val connected = healthy || degraded
  val busy = phase is VpnPhase.Preparing ||
    phase is VpnPhase.Connecting ||
    phase is VpnPhase.Stopping
  val failed = phase is VpnPhase.Error
  val statusContainerColor by animateColorAsState(
    targetValue = when {
      healthy -> MaterialTheme.colorScheme.primary
      degraded -> MaterialTheme.colorScheme.tertiaryContainer
      failed -> MaterialTheme.colorScheme.errorContainer
      else -> MaterialTheme.colorScheme.surfaceContainerLowest
    },
    animationSpec = tween(220),
  )
  val statusContentColor by animateColorAsState(
    targetValue = when {
      healthy -> MaterialTheme.colorScheme.onPrimary
      degraded -> MaterialTheme.colorScheme.onTertiaryContainer
      failed -> MaterialTheme.colorScheme.onErrorContainer
      else -> MaterialTheme.colorScheme.primary
    },
    animationSpec = tween(220),
  )
  Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
      Surface(
        modifier = Modifier.size(68.dp),
        color = statusContainerColor,
        contentColor = statusContentColor,
        shape = CircleShape,
      ) {
        Box(contentAlignment = Alignment.Center) {
          AnimatedContent(
            targetState = busy,
            transitionSpec = {
              (fadeIn(tween(160)) + scaleIn(tween(160), initialScale = .92f))
                .togetherWith(fadeOut(tween(120)) + scaleOut(tween(120), targetScale = .92f))
            },
          ) { isBusy ->
            if (isBusy) {
              Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(38.dp), strokeWidth = 3.dp)
              }
            } else {
              BrandGlyph(
                color = LocalContentColor.current,
                modifier = Modifier.size(38.dp),
              )
            }
          }
        }
      }
      Column(Modifier.weight(1f).padding(start = 18.dp)) {
      Text(
        when (phase) {
          VpnPhase.Idle -> "VPN выключен"
          VpnPhase.NeedsElevation -> "Нужны права администратора"
          VpnPhase.Preparing -> "Подготовка подключения"
          VpnPhase.Connecting -> "Подключение…"
          is VpnPhase.Connected -> "Соединение защищено"
          is VpnPhase.Degraded -> "Соединение нестабильно"
          VpnPhase.Stopping -> "Отключение…"
          is VpnPhase.Error -> if (phase.stopRequired) "Не удалось остановить" else "Не удалось подключиться"
        },
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Start,
      )
      Spacer(Modifier.height(5.dp))
      Text(
        when (phase) {
          is VpnPhase.Error -> phase.message
          is VpnPhase.Degraded -> phase.message
          else -> when {
            profile == null -> "Добавьте подписку или профиль"
            busy -> "Это может занять несколько секунд"
            connected -> profile.name
            else -> "Готово к безопасному подключению"
          }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (connected || failed) {
          LocalContentColor.current.copy(alpha = .86f)
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Start,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      }
      }
      AnimatedVisibility(visible = connected) {
        SessionMetrics(
          phase = phase,
          traffic = traffic,
          modifier = Modifier.padding(top = 18.dp),
        )
      }
      Spacer(Modifier.weight(1f))
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onAction,
        enabled = phase !is VpnPhase.Stopping,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (connected || busy) {
          ButtonDefaults.filledTonalButtonColors()
        } else {
          ButtonDefaults.buttonColors()
        },
      ) {
        if (busy) {
          Icon(Icons.Rounded.StopCircle, null, Modifier.size(19.dp))
          Spacer(Modifier.width(8.dp))
        }
        Text(
          when {
            phase.requiresStopRetry -> "Повторить остановку"
            profile == null -> "Добавить профиль"
            busy -> "Остановить подключение"
            connected -> "Отключить"
            else -> "Подключить"
          },
          style = MaterialTheme.typography.labelLarge,
        )
      }
      if (failed) {
        Row(
          modifier = Modifier.padding(top = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "Код: ${phase.code}",
            style = MaterialTheme.typography.bodySmall,
          )
          TextButton(onClick = onOpenLogs) {
            Text("Открыть журнал", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

@Composable
private fun QuickConnectionCard(
  profile: Profile?,
  selectedEngine: VpnEngine,
  availableEngines: Set<VpnEngine>,
  subscriptions: List<SubscriptionRecord>,
  activeSubscriptionId: String?,
  selectedNodeTag: String?,
  nodeLatencies: Map<String, NodeLatency>,
  busy: Boolean,
  probing: Boolean,
  refreshing: Boolean,
  onImport: () -> Unit,
  onSelectNode: (String) -> Unit,
  onRefresh: () -> Unit,
  onProbe: () -> Unit,
  onEngineSelect: (VpnEngine) -> Unit,
  onSelectSubscription: (String) -> Unit,
) {
  CardSection(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text("Подключение", style = MaterialTheme.typography.titleMedium)
          Text(
            "Ядро и сервер",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(onClick = onImport, enabled = !busy) {
          Icon(Icons.Rounded.Add, null, Modifier.size(17.dp))
          Text("Подписка", Modifier.padding(start = 6.dp))
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VpnEngine.entries.forEach { engine ->
          EngineChoice(
            engine = engine,
            selected = selectedEngine == engine,
            configured = engine in availableEngines,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            onSelect = onEngineSelect,
          )
        }
      }
      if (profile == null) {
        Surface(
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(14.dp),
        ) {
          Column(Modifier.padding(16.dp)) {
            Text("Для этого ядра нет подписки", fontWeight = FontWeight.Medium)
            Text(
              "Добавьте ссылку, URI или локальный профиль",
              modifier = Modifier.padding(top = 3.dp),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        SubscriptionDropdown(
          subscriptions = subscriptions.filter { it.profile(selectedEngine) != null },
          activeSubscriptionId = activeSubscriptionId,
          enabled = !busy,
          onSelect = onSelectSubscription,
        )
        NodeDropdown(
          profile = profile,
          selectedNodeTag = selectedNodeTag,
          latencies = nodeLatencies,
          enabled = !busy,
          compact = true,
          onSelectNode = onSelectNode,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onRefresh,
            enabled = !busy && !refreshing && profile.sourceUrl != null,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
          ) {
            if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Sync, null, Modifier.size(17.dp))
            Text("Обновить", Modifier.padding(start = 6.dp))
          }
          OutlinedButton(
            onClick = onProbe,
            enabled = !busy && !probing,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
          ) {
            if (probing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Speed, null, Modifier.size(17.dp))
            Text("Пинг", Modifier.padding(start = 6.dp))
          }
        }
      }
    }
  }
}

/**
 * Live tunnel evidence: how long the session has been up and how many bytes the
 * TUN adapter has actually moved. Without it a silent tunnel is
 * indistinguishable from a working one.
 */
@Composable
private fun SessionMetrics(
  phase: VpnPhase,
  traffic: TrafficSnapshot?,
  modifier: Modifier = Modifier,
) {
  val since = (phase as? VpnPhase.Connected)?.sinceEpochMillis
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(since) {
    while (since != null) {
      now = System.currentTimeMillis()
      delay(1_000)
    }
  }
  Column(modifier.fillMaxWidth()) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      MetricTile(
        label = "Получено",
        value = traffic?.let { formatBytes(it.bytesIn) } ?: "—",
        modifier = Modifier.weight(1f),
      )
      MetricTile(
        label = "Отправлено",
        value = traffic?.let { formatBytes(it.bytesOut) } ?: "—",
        modifier = Modifier.weight(1f),
      )
    }
    Text(
      buildString {
        append(traffic?.adapter ?: "Туннель активен")
        if (since != null) append(" · ${formatDuration(now - since)}")
      },
      modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
      style = MaterialTheme.typography.bodySmall,
      color = LocalContentColor.current.copy(alpha = .74f),
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun MetricTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    color = LocalContentColor.current.copy(alpha = .10f),
    contentColor = LocalContentColor.current,
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(
      Modifier.padding(vertical = 11.dp, horizontal = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = LocalContentColor.current.copy(alpha = .74f),
      )
      Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
      )
    }
  }
}

private fun formatBytes(value: Long): String {
  if (value < 1_024) return "$value Б"
  val units = listOf("КБ", "МБ", "ГБ", "ТБ")
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
private fun ProfileCard(
  profile: Profile?,
  selectedNodeTag: String?,
  nodeLatencies: Map<String, NodeLatency>,
  busy: Boolean,
  probing: Boolean,
  refreshing: Boolean,
  onImport: () -> Unit,
  onSelectNode: (String) -> Unit,
  onRefresh: () -> Unit,
  onProbe: () -> Unit,
) {
  Column {
    SectionTitle("Профиль")
    CardSection(Modifier.fillMaxWidth()) {
      Column {
        ListItem(
        headlineContent = {
          Text(
            profile?.name ?: "Профиль не добавлен",
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        supportingContent = {
          Text(
            profile?.let {
              val node = it.nodes.firstOrNull { node -> node.tag == selectedNodeTag }?.name
                ?: if (it.engine == VpnEngine.SingBox) "Автовыбор" else it.nodes.firstOrNull()?.name
                ?: "Нет endpoint"
              "${it.nodes.size} узлов · ${it.engine.displayName} · $node"
            } ?: "Импортировать подписку",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        leadingContent = {
          Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                VeilarkMark,
                null,
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        },
        trailingContent = {
          FilledTonalButton(
            onClick = onImport,
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
          ) {
            Text(if (profile == null) "Добавить" else "Сменить")
          }
        },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        if (profile != null) {
          HorizontalDivider(Modifier.padding(horizontal = 16.dp))
          NodeDropdown(
            profile = profile,
            selectedNodeTag = selectedNodeTag,
            latencies = nodeLatencies,
            enabled = !busy,
            compact = true,
            onSelectNode = onSelectNode,
          )
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onRefresh,
              enabled = !busy && !refreshing && profile.sourceUrl != null,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(13.dp),
            ) {
              if (refreshing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
              } else {
                Icon(Icons.Rounded.Sync, null, Modifier.size(17.dp))
              }
              Text("Обновить", Modifier.padding(start = 7.dp))
            }
            OutlinedButton(
              onClick = onProbe,
              enabled = !busy && !probing,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(13.dp),
            ) {
              if (probing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
              } else {
                Icon(Icons.Rounded.Speed, null, Modifier.size(17.dp))
              }
              Text("Задержка", Modifier.padding(start = 7.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RoutingCard(
  routing: RoutingSettings,
  engine: VpnEngine,
  connected: Boolean,
  onOpen: () -> Unit,
) {
  val manual = routing.mode == RoutingMode.Manual
  Column {
    SectionTitle("Маршрутизация")
    CardSection(Modifier.fillMaxWidth()) {
      ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = {
          Text(
            when (routing.mode) {
              RoutingMode.All -> "Весь трафик"
              RoutingMode.RussiaDirect -> "Россия напрямую"
              RoutingMode.RussiaVpn -> "Россия через VPN"
              RoutingMode.Manual -> "Свои правила"
            },
            fontWeight = FontWeight.Medium,
          )
        },
        supportingContent = {
          Text(
            when {
              manual -> {
                val direct = routing.directEntries.split(' ', '\n').count(String::isNotBlank)
                val viaVpn = routing.vpnEntries.split(' ', '\n').count(String::isNotBlank)
                "Напрямую: $direct · Через VPN: $viaVpn" +
                  if (routing.tlsFragment) " · TLS-фрагментация" else ""
              }
              routing.mode == RoutingMode.RussiaDirect ->
                "Российские ресурсы напрямую · остальное через туннель"
              routing.mode == RoutingMode.RussiaVpn ->
                "Российские ресурсы через туннель · остальное напрямую"
              routing.tlsFragment -> "Через туннель · TLS-фрагментация"
              connected -> "Маршрут активен"
              else -> "Применится при подключении"
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        leadingContent = {
          Icon(
            Icons.Rounded.Route,
            null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        trailingContent = {
          Text(
            "Настроить",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
  }
}

@Composable
private fun EngineCard(
  selectedEngine: VpnEngine,
  availableEngines: Set<VpnEngine>,
  enabled: Boolean,
  onSelect: (VpnEngine) -> Unit,
) {
  Column {
    SectionTitle("Режим подключения")
    CardSection(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.selectableGroup(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          EngineChoice(
            engine = VpnEngine.SingBox,
            selected = selectedEngine == VpnEngine.SingBox,
            configured = VpnEngine.SingBox in availableEngines,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onSelect = onSelect,
          )
          EngineChoice(
            engine = VpnEngine.TrustTunnel,
            selected = selectedEngine == VpnEngine.TrustTunnel,
            configured = VpnEngine.TrustTunnel in availableEngines,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onSelect = onSelect,
          )
        }
      }
    }
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

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
internal fun ProfilesScreen(
  subscriptions: List<SubscriptionRecord>,
  selectedEngine: VpnEngine,
  selectedSubscriptionIds: Map<VpnEngine, String>,
  selectedNodeTag: String?,
  nodeLatencies: Map<String, NodeLatency>,
  probing: Boolean,
  refreshing: Boolean,
  configurationEnabled: Boolean,
  onSelectSubscription: (VpnEngine, String) -> Unit,
  onSelectNode: (String) -> Unit,
  onRefresh: (SubscriptionRecord) -> Unit,
  onProbe: (Profile) -> Unit,
  onDelete: (SubscriptionRecord) -> Unit,
  importing: Boolean,
  onOpenSubscriptionAccount: () -> Unit,
  onOpenWebApp: () -> Unit,
  onPaste: () -> Unit,
  onFile: () -> Unit,
  onUrl: () -> Unit,
) {
  val language = LocalUiLanguage.current
  Page(maxWidth = 600.dp) {
    PageHeader(
      language.text("Подписки", "Subscriptions"),
      language.text("Источники и узлы обоих VPN-ядер", "Sources and servers for both VPN cores"),
      action = {
        Button(
          onClick = onUrl,
          enabled = configurationEnabled && !importing,
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
          Text(language.text("Добавить", "Add"), Modifier.padding(start = 8.dp))
        }
      },
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onOpenSubscriptionAccount,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Rounded.SupportAgent, null, Modifier.size(18.dp))
        Text(language.text("Telegram-бот", "Telegram bot"), Modifier.padding(start = 8.dp))
      }
      FilledTonalButton(
        onClick = onOpenWebApp,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(18.dp))
        Text(language.text("Веб-кабинет", "Web account"), Modifier.padding(start = 8.dp))
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        language.text("Источников: ${subscriptions.size}", "Sources: ${subscriptions.size}"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.weight(1f))
      TextButton(
        onClick = onPaste,
        enabled = configurationEnabled && !importing,
      ) {
        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(17.dp))
        Text(language.text("Из буфера", "Clipboard"), Modifier.padding(start = 6.dp))
      }
      TextButton(
        onClick = onFile,
        enabled = configurationEnabled && !importing,
      ) {
        Icon(Icons.Rounded.FileOpen, null, Modifier.size(17.dp))
        Text(language.text("Из файла", "File"), Modifier.padding(start = 6.dp))
      }
    }
    CardSection(Modifier.fillMaxWidth().heightIn(min = 92.dp)) {
      if (subscriptions.isEmpty()) {
        Box(
          Modifier.fillMaxWidth().height(92.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              language.text("Профилей пока нет", "No profiles yet"),
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              language.text(
                "Добавьте подписку или локальную конфигурацию",
                "Add a subscription or local configuration",
              ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
        }
      } else {
        Column {
          subscriptions.sortedWith(
            compareByDescending<SubscriptionRecord> { it.origin == SubscriptionOrigin.BuiltIn }
              .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
          ).forEachIndexed { index, subscription ->
            val activeEngines = subscription.profiles.mapNotNull { profile ->
              profile.engine.takeIf { selectedSubscriptionIds[it] == subscription.id }
            }
            val active = selectedEngine in activeEngines
            val preferredProfile = subscription.profile(selectedEngine)
              ?: subscription.profiles.first()
            val nodeSummary = subscription.profiles.joinToString(" · ") { profile ->
              "${profile.engine.displayName}: ${profile.nodes.size}"
            }
            ListItem(
              modifier = Modifier.clickable(
                enabled = configurationEnabled,
                onClick = {
                  onSelectSubscription(preferredProfile.engine, subscription.id)
                },
              ),
              headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    subscription.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (subscription.origin == SubscriptionOrigin.BuiltIn) {
                    Surface(
                      modifier = Modifier.padding(start = 8.dp),
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      shape = RoundedCornerShape(8.dp),
                    ) {
                      Text(
                        language.text("Встроенная", "Built-in"),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                      )
                    }
                  }
                }
              },
              supportingContent = {
                Text(
                  "$nodeSummary · ${subscription.sourceLabel}",
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              leadingContent = {
                Surface(
                  modifier = Modifier.size(36.dp),
                  shape = RoundedCornerShape(10.dp),
                  color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                  },
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      if (active) Icons.Rounded.Check else VeilarkMark,
                      null,
                      tint = if (active) LocalContentColor.current else MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              },
              trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (subscription.sourceUrl != null) {
                    IconButton(
                      onClick = { onRefresh(subscription) },
                      enabled = configurationEnabled && !refreshing,
                    ) {
                      if (refreshing && active) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                      } else {
                        Icon(Icons.Rounded.Sync, language.text("Обновить подписку", "Refresh subscription"))
                      }
                    }
                  }
                  if (subscription.origin == SubscriptionOrigin.User) {
                    IconButton(
                      onClick = { onDelete(subscription) },
                      enabled = configurationEnabled,
                    ) {
                      Icon(
                        Icons.Rounded.DeleteOutline,
                        language.text("Удалить подписку", "Delete subscription"),
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
              },
              colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (index < subscriptions.lastIndex) {
              HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
          }
        }
      }
    }
    val activeSubscription = selectedSubscriptionIds[selectedEngine]?.let { id ->
      subscriptions.firstOrNull { it.id == id }
    }
    val activeProfile = activeSubscription?.profile(selectedEngine)
    if (activeProfile != null) {
      Spacer(Modifier.height(10.dp))
      Text(
        language.text("Узел подключения", "Connection server"),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
      )
      CardSection(Modifier.fillMaxWidth()) {
        Column {
          NodeDropdown(
            profile = activeProfile,
            selectedNodeTag = selectedNodeTag,
            latencies = nodeLatencies,
            enabled = configurationEnabled && !probing,
            onSelectNode = onSelectNode,
          )
          OutlinedButton(
            onClick = { onProbe(activeProfile) },
            enabled = !probing,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
          ) {
            if (probing) {
              CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp))
            }
            Text(
              if (probing) {
                language.text("Проверяем все узлы…", "Testing all servers…")
              } else {
                language.text("Проверить задержку всех узлов", "Test latency for all servers")
              },
              Modifier.padding(start = 8.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SubscriptionDropdown(
  subscriptions: List<SubscriptionRecord>,
  activeSubscriptionId: String?,
  enabled: Boolean,
  compact: Boolean = false,
  onSelect: (String) -> Unit,
) {
  val language = LocalUiLanguage.current
  var expanded by remember { mutableStateOf(false) }
  val active = subscriptions.firstOrNull { it.id == activeSubscriptionId }
    ?: subscriptions.firstOrNull()
  Box(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 12.dp)) {
    OutlinedButton(
      onClick = { expanded = true },
      enabled = enabled && subscriptions.isNotEmpty(),
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      contentPadding = PaddingValues(
        horizontal = 14.dp,
        vertical = if (compact) 7.dp else 9.dp,
      ),
    ) {
      Icon(VeilarkMark, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
      Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
        Text(active?.name ?: language.text("Нет подписок", "No subscriptions"), fontWeight = FontWeight.Medium)
        Text(
          active?.let {
            if (it.origin == SubscriptionOrigin.BuiltIn) {
              language.text("Встроенная Veilark Trust", "Built-in Veilark Trust")
            } else {
              it.sourceLabel
            }
          } ?: language.text("Добавьте подписку", "Add a subscription"),
          style = MaterialTheme.typography.bodySmall,
          color = LocalContentColor.current.copy(alpha = .72f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(Icons.Rounded.ArrowDropDown, null)
    }
    StablePickerPopup(
      expanded = expanded,
      onDismiss = { expanded = false },
      width = 400.dp,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
        contentPadding = PaddingValues(6.dp),
      ) {
        items(subscriptions, key = SubscriptionRecord::id) { subscription ->
          val selected = subscription.id == active?.id
          Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
              .clickable {
                expanded = false
                onSelect(subscription.id)
              },
            color = if (selected) {
              MaterialTheme.colorScheme.secondaryContainer
            } else {
              Color.Transparent
            },
            shape = RoundedCornerShape(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                if (selected) Icons.Rounded.Check else VeilarkMark,
                null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) LocalContentColor.current else MaterialTheme.colorScheme.onSurface,
              )
              Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(subscription.name, fontWeight = FontWeight.Medium)
                Text(
                  subscription.profiles.joinToString(" · ") {
                    "${it.engine.displayName}: ${it.nodes.size}"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NodeDropdown(
  profile: Profile,
  selectedNodeTag: String?,
  latencies: Map<String, NodeLatency> = emptyMap(),
  enabled: Boolean = true,
  compact: Boolean = false,
  onSelectNode: (String) -> Unit,
) {
  val language = LocalUiLanguage.current
  var expanded by remember { mutableStateOf(false) }
  var query by remember(profile.id) { mutableStateOf("") }
  val nodesByTag = remember(profile.id, profile.nodes) {
    profile.nodes.associateBy { it.tag }
  }
  val choices = remember(profile.id, profile.engine, profile.nodes, language) {
    if (profile.engine == VpnEngine.SingBox) {
      listOf(ProfileSelection.AUTOMATIC_TAG to language.text("Автоматически", "Automatic")) +
        profile.nodes.map { it.tag to NodePresentation.displayName(it.name) }
    } else {
      profile.nodes.map { it.tag to NodePresentation.displayName(it.name) }
    }
  }
  val filteredChoices = remember(choices, query) {
    val needle = query.trim()
    if (needle.isEmpty()) choices else choices.filter { (tag, name) ->
      name.contains(needle, ignoreCase = true) ||
        tag.contains(needle, ignoreCase = true) ||
        nodesByTag[tag]?.protocol?.contains(needle, ignoreCase = true) == true
    }
  }
  val fallbackTag = if (profile.engine == VpnEngine.SingBox) {
    ProfileSelection.AUTOMATIC_TAG
  } else {
    profile.nodes.firstOrNull()?.tag
  }
  val activeTag = selectedNodeTag
    ?.takeIf { selected -> choices.any { it.first == selected } }
    ?: fallbackTag
  val activeName = choices.firstOrNull { it.first == activeTag }?.second
    ?: language.text("Нет узлов", "No servers")
  val activeLatency = activeTag?.let(latencies::get)
  val activeNode = activeTag?.let(nodesByTag::get)
  val activeDetail = if (activeTag == ProfileSelection.AUTOMATIC_TAG) {
    language.text("Выбор по доступности узлов", "Select by availability")
  } else {
    nodesByTag[activeTag]?.protocol.orEmpty()
  }
  val activeLatencyLabel = when {
    activeLatency == null -> ""
    activeLatency.available -> language.text(
      " · ${activeLatency.millis} мс",
      " · ${activeLatency.millis} ms",
    )
    else -> language.text(" · таймаут", " · timeout")
  }
  val arrowRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(160),
  )

  BoxWithConstraints(Modifier.fillMaxWidth().padding(if (compact) 0.dp else 16.dp)) {
    // The picker is anchored to the content area, so cap it to the available
    // width instead of letting a 420 dp popup overflow on scaled displays.
    val popupWidth = (maxWidth - 16.dp).coerceAtMost(420.dp).coerceAtLeast(220.dp)
    OutlinedButton(
      onClick = {
        query = ""
        expanded = true
      },
      enabled = enabled && choices.isNotEmpty(),
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      contentPadding = PaddingValues(
        horizontal = 16.dp,
        vertical = if (compact) 7.dp else 13.dp,
      ),
    ) {
      EndpointMark(
        country = activeNode?.let { NodePresentation.country(it.name) },
        automatic = activeTag == ProfileSelection.AUTOMATIC_TAG,
        modifier = Modifier.size(22.dp),
      )
      Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
        Text(
          activeName,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          "$activeDetail$activeLatencyLabel",
          style = MaterialTheme.typography.bodySmall,
          color = LocalContentColor.current.copy(alpha = .72f),
        )
      }
      Icon(
        Icons.Rounded.ArrowDropDown,
        null,
        Modifier.graphicsLayer { rotationZ = arrowRotation },
      )
    }
    StablePickerPopup(
      expanded = expanded,
      onDismiss = {
        expanded = false
        query = ""
      },
      width = popupWidth,
    ) {
      Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          singleLine = true,
          placeholder = { Text(language.text("Поиск узла", "Search servers")) },
          shape = RoundedCornerShape(12.dp),
        )
        HorizontalDivider()
        if (filteredChoices.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              language.text("Узлы не найдены", "No servers found"),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            contentPadding = PaddingValues(6.dp),
          ) {
            items(filteredChoices, key = { it.first }) { (tag, name) ->
              val selected = tag == activeTag
              Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                  .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                  ) {
                    expanded = false
                    query = ""
                    onSelectNode(tag)
                  },
                color = if (selected) {
                  MaterialTheme.colorScheme.secondaryContainer
                } else {
                  Color.Transparent
                },
                shape = RoundedCornerShape(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val node = nodesByTag[tag]
                  EndpointMark(
                    country = node?.let { NodePresentation.country(it.name) },
                    automatic = tag == ProfileSelection.AUTOMATIC_TAG,
                    modifier = Modifier.size(22.dp),
                  )
                  Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(name, fontWeight = FontWeight.Medium)
                    Text(
                      if (tag == ProfileSelection.AUTOMATIC_TAG) {
                        language.text("Выбор по доступности узлов", "Select by availability")
                      } else {
                        node?.protocol.orEmpty()
                      },
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  if (tag != ProfileSelection.AUTOMATIC_TAG) {
                    val latency = latencies[tag]
                    Text(
                      when {
                        latency == null -> "—"
                        latency.available -> language.text("${latency.millis} мс", "${latency.millis} ms")
                        else -> language.text("таймаут", "timeout")
                      },
                      style = MaterialTheme.typography.labelMedium,
                      color = when {
                        latency?.available == true && (latency.millis ?: 9999) < 180 ->
                          MaterialTheme.colorScheme.primary
                        latency?.available == false -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                      },
                    )
                  }
                  if (selected) {
                    Icon(
                      Icons.Rounded.Check,
                      null,
                      modifier = Modifier.padding(start = 8.dp).size(18.dp),
                      tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private val VpnEngine.displayName: String
  get() = when (this) {
    VpnEngine.SingBox -> "sing-box"
    VpnEngine.TrustTunnel -> "TrustTunnel"
  }

@Composable
private fun ImportItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  importing: Boolean,
  onClick: () -> Unit,
) {
  val language = LocalUiLanguage.current
  ListItem(
    headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
    supportingContent = { Text(subtitle) },
    leadingContent = {
      Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
    },
    trailingContent = {
      OutlinedButton(
        onClick = onClick,
        enabled = !importing,
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(language.text("Выбрать", "Choose"))
      }
    },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
  )
}

@Composable
internal fun RoutingScreen(
  settings: RoutingSettings,
  hasAnyProfile: Boolean,
  hasSingBoxProfile: Boolean,
  configurationEnabled: Boolean,
  geoRefreshing: Boolean,
  onRefreshGeo: () -> Unit,
  onSave: (RoutingSettings) -> Unit,
) {
  val language = LocalUiLanguage.current
  var selectedPreset by remember(settings) {
    mutableStateOf(
      when (settings.mode) {
        RoutingMode.All -> RoutingPresetUi.AllVpn
        RoutingMode.RussiaDirect -> RoutingPresetUi.RussiaDirect
        RoutingMode.RussiaVpn -> RoutingPresetUi.RussiaVpn
        RoutingMode.Manual -> RoutingPresetUi.Custom
      },
    )
  }
  var directEntries by remember(settings) { mutableStateOf(settings.directEntries) }
  var vpnEntries by remember(settings) { mutableStateOf(settings.vpnEntries) }
  var tlsFragment by remember(settings) { mutableStateOf(settings.tlsFragment) }
  val manual = selectedPreset == RoutingPresetUi.Custom
  val enabled = hasAnyProfile && configurationEnabled && !geoRefreshing

  Page(maxWidth = 600.dp) {
    PageHeader(
      language.text("Маршрутизация", "Routing"),
      language.text(
        "Готовые режимы и собственные правила direct/VPN",
        "Presets and custom direct/VPN rules",
      ),
      action = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onRefreshGeo,
            enabled = configurationEnabled && !geoRefreshing,
            shape = RoundedCornerShape(12.dp),
          ) {
            if (geoRefreshing) {
              CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
            }
            Text(
              language.text("Обновить GEO", "Update GEO"),
              Modifier.padding(start = 7.dp),
            )
          }
        Button(
          onClick = {
            onSave(
              RoutingSettings(
                mode = when (selectedPreset) {
                  RoutingPresetUi.AllVpn -> RoutingMode.All
                  RoutingPresetUi.RussiaDirect -> RoutingMode.RussiaDirect
                  RoutingPresetUi.RussiaVpn -> RoutingMode.RussiaVpn
                  RoutingPresetUi.Custom -> RoutingMode.Manual
                },
                directEntries = directEntries,
                vpnEntries = vpnEntries,
                tlsFragment = tlsFragment,
              ),
            )
          },
          enabled = enabled,
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(language.text("Сохранить", "Save"))
        }
        }
      },
    )
    Spacer(Modifier.height(8.dp))
    CardSection(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
          language.text("Схема трафика", "Traffic policy"),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          language.text(
            "Выберите готовое правило или настройте свои направления",
            "Choose a preset or define your own destinations",
          ),
          modifier = Modifier.padding(top = 2.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          RoutingPresetUi.entries.chunked(2).forEach { rowPresets ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              rowPresets.forEach { preset ->
                RoutingPresetChoice(
                  preset = preset,
                  selected = selectedPreset == preset,
                  enabled = enabled,
                  onSelect = { selectedPreset = preset },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
        AnimatedVisibility(
          visible = manual,
          enter = fadeIn(tween(180)),
          exit = fadeOut(tween(120)),
        ) {
          Column {
            HorizontalDivider(Modifier.padding(top = 10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
              if (maxWidth >= 520.dp) {
                Row(
                  Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  RoutingRuleField(
                    value = directEntries,
                    onValueChange = { directEntries = it },
                    label = language.text("Напрямую", "Direct"),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                  )
                  RoutingRuleField(
                    value = vpnEntries,
                    onValueChange = { vpnEntries = it },
                    label = language.text("Через VPN", "Through VPN"),
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                  )
                }
              } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  RoutingRuleField(
                    value = directEntries,
                    onValueChange = { directEntries = it },
                    label = language.text("Напрямую", "Direct"),
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                  )
                  RoutingRuleField(
                    value = vpnEntries,
                    onValueChange = { vpnEntries = it },
                    label = language.text("Через VPN", "Through VPN"),
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
            }
          }
        }
        HorizontalDivider()
        SettingRow(
          language.text("Фрагментация TLS", "TLS fragmentation"),
          language.text(
            "Разделяет TLS ClientHello для совместимых sing-box-подключений",
            "Splits TLS ClientHello for compatible sing-box connections",
          ),
          tlsFragment,
          enabled && hasSingBoxProfile,
          onCheckedChange = { tlsFragment = it },
        )
      }
    }
    if (!hasAnyProfile) {
      Text(
        language.text(
          "Добавьте подписку, чтобы настроить маршрутизацию.",
          "Add a subscription to configure routing.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp),
      )
    } else if (geoRefreshing) {
      Text(
        language.text(
          "Проверяем свежие правила России. Предыдущий проверенный кэш остаётся доступен.",
          "Validating the latest Russia rules. The previous verified cache remains available.",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp),
      )
    }
    Text(
      language.text(
        "Региональные пресеты используют проверяемые GeoIP/Geosite rule-set; свои правила принимают домены, IP и CIDR.",
        "Regional presets use verified GeoIP/Geosite rule sets; custom rules accept domains, IPs and CIDRs.",
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
  }
}

private enum class RoutingPresetUi(
  private val titleRu: String,
  private val titleEn: String,
  private val summaryRu: String,
  private val summaryEn: String,
  val backendKey: String,
) {
  AllVpn(
    titleRu = "Весь трафик через VPN",
    titleEn = "All traffic through VPN",
    summaryRu = "Все сайты и приложения идут через выбранный узел",
    summaryEn = "All sites and applications use the selected server",
    backendKey = "all_vpn",
  ),
  RussiaDirect(
    titleRu = "Россия напрямую",
    titleEn = "Russia direct",
    summaryRu = "Российские ресурсы — напрямую, остальное — через VPN",
    summaryEn = "Russian resources are direct; everything else uses VPN",
    backendKey = "ru_direct",
  ),
  RussiaVpn(
    titleRu = "Россия через VPN",
    titleEn = "Russia through VPN",
    summaryRu = "Российские ресурсы — через VPN, остальное — напрямую",
    summaryEn = "Russian resources use VPN; everything else is direct",
    backendKey = "ru_vpn",
  ),
  Custom(
    titleRu = "Свои правила",
    titleEn = "Custom rules",
    summaryRu = "Задайте направления вручную; остальное пойдёт через VPN",
    summaryEn = "Set destinations manually; everything else uses VPN",
    backendKey = "custom",
  );

  fun title(language: UiLanguage) = language.text(titleRu, titleEn)
  fun summary(language: UiLanguage) = language.text(summaryRu, summaryEn)
}

@Composable
private fun RoutingPresetChoice(
  preset: RoutingPresetUi,
  selected: Boolean,
  enabled: Boolean,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalUiLanguage.current
  val containerColor by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.surfaceContainerHigh
    },
    animationSpec = tween(160),
  )
  val contentColor by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSurface
    },
    animationSpec = tween(160),
  )
  Surface(
    modifier = modifier
      .heightIn(min = 64.dp)
      .graphicsLayer { alpha = if (enabled) 1f else .58f }
      .selectable(
        selected = selected,
        enabled = enabled,
        role = Role.RadioButton,
        onClick = onSelect,
      )
      .semantics {
        stateDescription = when {
          selected -> language.text("Выбрано", "Selected")
          enabled -> language.text("Доступно", "Available")
          else -> language.text(
            "Недоступно во время подключения или обновления данных",
            "Unavailable while connected or updating data",
          )
        }
      },
    color = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier.size(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
      ) {
        if (selected) {
          Icon(Icons.Rounded.Check, null, Modifier.padding(3.dp))
        }
      }
      Column(Modifier.weight(1f).padding(start = 10.dp)) {
        Text(
          preset.title(language),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          preset.summary(language),
          modifier = Modifier.padding(top = 2.dp),
          style = MaterialTheme.typography.bodySmall,
          color = LocalContentColor.current.copy(alpha = .76f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun RoutingRuleField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  enabled: Boolean,
  modifier: Modifier,
) {
  val language = LocalUiLanguage.current
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    label = { Text(label) },
    supportingText = {
      Text(
        language.text("Домены, IP или CIDR через пробел", "Domains, IPs or CIDRs separated by spaces"),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    minLines = 2,
    maxLines = 5,
    shape = RoundedCornerShape(12.dp),
  )
}

@Composable
private fun SettingRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  val language = LocalUiLanguage.current
  Row(
    Modifier
      .fillMaxWidth()
      .toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onCheckedChange,
      )
      .semantics(mergeDescendants = true) {
        stateDescription = if (checked) {
          language.text("Включено", "On")
        } else {
          language.text("Выключено", "Off")
        }
      }
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}

@Composable
private fun DiagnosticsScreen() {
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
private fun UpdateBanner(
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
private fun UpdatesScreen(
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
                is DesktopUpdateState.Failed -> state.message
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
private fun UpdatePrimaryAction(
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

@Composable
private fun LogsScreen() {
  val language = LocalUiLanguage.current
  var log by remember { mutableStateOf(readLog()) }
  val verticalScroll = rememberScrollState(initial = Int.MAX_VALUE)
  val horizontalScroll = rememberScrollState()
  val visibleLog = log.ifBlank { language.text("Ошибок и сетевых событий пока нет", "No errors or network events yet") }
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
                  .setContents(StringSelection(log), null)
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

@Composable
private fun Page(
  maxWidth: Dp = 560.dp,
  horizontalPadding: Dp = 12.dp,
  verticalPadding: Dp = 10.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier = Modifier.fillMaxHeight().widthIn(max = maxWidth).fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
      content = content,
    )
  }
}

@Composable
private fun ImportDialog(
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

private fun chooseProfileFile(): NioPath? {
  val dialog = FileDialog(null as Frame?, "Выберите профиль", FileDialog.LOAD).apply {
    setFilenameFilter { _, name ->
      name.endsWith(".json", true) ||
        name.endsWith(".txt", true) ||
        name.endsWith(".yaml", true) ||
        name.endsWith(".yml", true)
    }
    isVisible = true
  }
  return dialog.file?.let { NioPath.of(dialog.directory, it) }
}

private fun readLog(): String = runCatching {
  if (!Files.exists(VeilarkPaths.logFile)) return@runCatching ""
  Files.readAllLines(VeilarkPaths.logFile, Charsets.UTF_8)
    .takeLast(250)
    .joinToString(System.lineSeparator())
}.getOrDefault("Не удалось прочитать журнал")
