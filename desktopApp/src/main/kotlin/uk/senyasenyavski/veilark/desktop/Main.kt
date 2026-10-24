package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.WindowPosition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import uk.senyasenyavski.veilark.helper.WindowsDwm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.veilark.profile.ImportDeepLink
import com.example.veilark.profile.ProfileSelection
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
import uk.senyasenyavski.veilark.helper.SubscriptionRecord
import uk.senyasenyavski.veilark.helper.SafeLog
import uk.senyasenyavski.veilark.helper.WindowsVpnSession
import uk.senyasenyavski.veilark.helper.ConnectionPreparation
import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.helper.UpdateInstallOutcome
import uk.senyasenyavski.veilark.model.ImportErrorCode
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.model.requiresStopRetry
import uk.senyasenyavski.veilark.model.locksConfiguration
import uk.senyasenyavski.veilark.profile.ProfileConfiguration
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path as NioPath
import javax.swing.TransferHandler
import kotlin.system.exitProcess

private class DesktopActions {
  var preparing by mutableStateOf(false)
  var connect: () -> Unit = {}
  var disconnect: () -> Unit = {}
  var importFile: (NioPath) -> Unit = {}
  var navigate: (Destination) -> Unit = {}
  var hasProfile by mutableStateOf(false)
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
    WindowsVpnSession(preConnectCheck = ActiveTunnelConflict::competitorAlias)
  }
  val exitScope = rememberCoroutineScope()
  var exitInProgress by remember { mutableStateOf(false) }
  val elevationManager = remember { ElevationManager() }
  val actions = remember { DesktopActions() }
  val sessionState by session.state.collectAsState()
  val trayState = rememberTrayState()
  val updates = remember { DesktopUpdateController() }
  val updateNoticePolicy = remember { UpdateNoticePolicy.persistent() }
  val availableUpdateState by updates.state.collectAsState()
  var windowVisible by remember { mutableStateOf("--minimized" !in args || importLink != null) }
  var activationRevision by remember { mutableStateOf(0L) }
  var updateOpenRevision by remember { mutableStateOf(0L) }
  var externalImportRequest by remember { mutableStateOf(importLink) }
  var language by remember { mutableStateOf(UiLanguageStore.load()) }
  val placementStore = remember { WindowPlacementStore.persistent() }
  val windowState = remember {
    val saved = placementStore.load()?.let {
      WindowPlacementStore.sanitize(
        it,
        WindowPlacementStore.screenBounds(),
        MinimumWindowWidth.value.toInt(),
        MinimumWindowHeight.value.toInt(),
      )
    }
    androidx.compose.ui.window.WindowState(
      placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
      position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
      width = saved?.width?.dp ?: InitialWindowWidth,
      height = saved?.height?.dp ?: InitialWindowHeight,
    )
  }
  // Shows, restores and focuses the window even when it is already visible
  // behind other windows (activationRevision re-runs the focus effect).
  val openWindow: () -> Unit = remember {
    {
      windowVisible = true
      activationRevision += 1
    }
  }
  // Application-scoped: also runs while the window is hidden in the tray and
  // on the profile-store failure screen.
  LaunchedEffect(updates) { updates.runBackgroundChecks() }
  LaunchedEffect(availableUpdateState) {
    val update = (availableUpdateState as? DesktopUpdateState.Available)?.update
      ?: return@LaunchedEffect
    if (updateNoticePolicy.shouldNotify(update.versionCode)) {
      runCatching { trayState.sendNotification(Notification(
        "Veilark ${update.versionName}",
        language.text("Доступно обновление. Откройте Veilark → Обновления.",
          "An update is available. Open Veilark → Updates."), Notification.Type.Info,
      )) }.onSuccess { updateNoticePolicy.markAttempted(update.versionCode) }
    }
  }
  val connected = sessionState.phase is VpnPhase.Connected ||
    sessionState.phase is VpnPhase.Degraded
  val connecting = actions.preparing || sessionState.phase is VpnPhase.Preparing ||
    sessionState.phase is VpnPhase.Connecting
  val stopping = sessionState.phase is VpnPhase.Stopping
  val stopRequired = sessionState.phase.requiresStopRetry
  val busy = connecting || stopping

  DisposableEffect(instanceGate) {
    instanceGate.setActivationHandler { forwardedImportLink ->
      openWindow()
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
          phaseProblemText(
            phase,
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
          phaseProblemText(
            phase,
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

  // A single primary click behaves like the "Open Veilark" menu item.
  LaunchedEffect(Unit) { TrayPrimaryClick.install(openWindow) }
  Tray(
    state = trayState,
    icon = painterResource("veilark-app-icon.png"),
    tooltip = when {
      stopRequired -> language.text("Veilark · Требуется остановка", "Veilark · Stop required")
      connected -> language.text("Veilark · Защищено", "Veilark · Protected")
      busy -> language.text("Veilark · Подключение", "Veilark · Connecting")
      else -> language.text("Veilark · Отключено", "Veilark · Disconnected")
    },
    // Double-click or a click on a notification balloon.
    onAction = {
      openWindow()
      if (availableUpdateState is DesktopUpdateState.Available) updateOpenRevision++
    },
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
        // Resolved at click time: VeilarkApp installs the real handlers after
        // the tray menu has already been composed.
        onClick = { if (connected || connecting || stopRequired) actions.disconnect() else actions.connect() },
      )
      Item(language.text("Открыть Veilark", "Open Veilark"), onClick = openWindow)
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
    onPreviewKeyEvent = { event ->
      val shortcut = if (event.type == KeyEventType.KeyDown) {
        desktopShortcut(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)
      } else null
      when (shortcut) {
        null -> false
        is DesktopShortcut.Navigate -> { actions.navigate(shortcut.destination); true }
        DesktopShortcut.HideToTray -> { windowVisible = false; true }
        DesktopShortcut.ToggleConnection -> {
          if (!stopping) {
            if (connected || connecting || stopRequired) actions.disconnect()
            else if (actions.hasProfile) actions.connect()
          }
          true
        }
      }
    },
    visible = windowVisible,
    title = "Veilark",
    icon = painterResource("veilark-app-icon.png"),
    state = windowState,
  ) {
    // Windows 11 caption: dark mode follows the theme; Mica and rounded
    // corners on 22H2+. Best-effort no-op elsewhere. Re-applied when the
    // window first gets a native peer (start minimized to tray).
    val darkTheme = isSystemInDarkTheme()
    LaunchedEffect(darkTheme, windowVisible) {
      if (windowVisible) WindowsDwm.apply(window, darkTheme)
    }
    LaunchedEffect(windowState) {
      snapshotFlow { Triple(windowState.position, windowState.size, windowState.placement) }
        .collectLatest { (position, size, placement) ->
          delay(500)
          val maximized = placement == WindowPlacement.Maximized
          val previous = placementStore.load()
          val bounds = if (placement == WindowPlacement.Floating && position is WindowPosition.Absolute) {
            SavedWindowBounds(
              x = position.x.value.toInt(),
              y = position.y.value.toInt(),
              width = size.width.value.toInt(),
              height = size.height.value.toInt(),
            )
          } else {
            previous?.copy(maximized = maximized)
          }
          bounds?.let { placementStore.save(it.copy(maximized = maximized)) }
        }
    }
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
          updates = updates,
          updateOpenRevision = updateOpenRevision,
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
  updates: DesktopUpdateController,
  updateOpenRevision: Long,
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
  val importer = remember { ProfileImporter() }
  val profileStore = remember { ProfileStore() }
  val geoPreflight = remember { lazy(LazyThreadSafetyMode.NONE) { GeoRoutingPreflight() } }
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
    ProfileStoreUnavailable(updates = updates, session = session, onExit = onExit)
    return
  }
  val elevated = remember { elevationManager.isElevated() }
  val state by session.state.collectAsState()
  val visiblePhase = if (preparing && (state.phase is VpnPhase.Idle || state.phase is VpnPhase.Error)) {
    VpnPhase.Preparing
  } else state.phase
  val snackbar = remember { SnackbarHostState() }
  var destination by remember { mutableStateOf(Destination.Home) }
  LaunchedEffect(updateOpenRevision) {
    if (updateOpenRevision > 0) destination = Destination.Updates
  }
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
      snackbar.showSnackbar(updateOutcomeText(outcome, language))
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
          failureText(
            error,
            "Не удалось обновить геоданные маршрутизации",
            "Could not update routing geo data",
            language,
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
        // The elevated instance connects with whatever this store holds, so a
        // failed save must not hand it a stale selection.
        val saved = runCatching {
          withContext(Dispatchers.IO) { profileStore.save(currentStored()) }
        }
        if (saved.isFailure) {
          snackbar.showSnackbar(
            language.text(
              "Не удалось сохранить выбор перед перезапуском с правами администратора",
              "Could not save the selection before restarting as administrator",
            ),
          )
        } else if (elevationManager.relaunch(listOf("--connect"))) {
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
          snackbar.showSnackbar(failureText(error, "Не удалось применить настройки", "Could not apply settings", language))
    })
  }

  fun disconnect() {
    preparation.cancel()
    scope.launch {
      preparation.cancelAndJoin()
      runCatching { session.disconnect() }
        .onFailure { error ->
          snackbar.showSnackbar(
            failureText(error, "Не удалось остановить VPN-ядро", "Could not stop the VPN core", language),
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
          snackbar.showSnackbar(failureText(error, "Не удалось применить настройки", "Could not apply settings", language))
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
        snackbar.showSnackbar(importFailureText(result, language))
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
    desktopActions.preparing = preparing
    desktopActions.connect = ::connect
    desktopActions.disconnect = ::disconnect
    desktopActions.hasProfile = profile != null
    desktopActions.navigate = { destination = it }
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
      AppBottomBar(destination = destination, onDestination = { destination = it })
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
                    ImportResult.Failure(ImportErrorCode.NoText)
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
              chooseProfileFile(language)?.let { path ->
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
                val hasRules = runCatching {
                  updated.mode != RoutingMode.Manual || run {
                    val direct = ProfileSelection.routingEntries(updated.directEntries)
                    val viaVpn = ProfileSelection.routingEntries(updated.vpnEntries)
                    direct.domains.isNotEmpty() || direct.networks.isNotEmpty() ||
                      viaVpn.domains.isNotEmpty() || viaVpn.networks.isNotEmpty()
                  }
                }
                val problem = hasRules.fold(
                  onSuccess = { valid ->
                    if (valid) {
                      null
                    } else {
                      language.text(
                        "Добавьте хотя бы один домен или IP-диапазон",
                        "Add at least one domain or IP range",
                      )
                    }
                  },
                  onFailure = { error ->
                    failureText(error, "Проверьте домены и IP-диапазоны", "Check domains and IP ranges", language)
                  },
                )
                if (problem == null) {
                  routingSettings = updated
                  scope.launch { snackbar.showSnackbar(language.text("Настройки маршрутизации сохранены", "Routing settings saved")) }
                } else {
                  scope.launch { snackbar.showSnackbar(problem) }
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
private fun ProfileStoreUnavailable(
  updates: DesktopUpdateController,
  session: WindowsVpnSession,
  onExit: () -> Unit,
) {
  val language = LocalUiLanguage.current
  val scope = rememberCoroutineScope()
  val updateState by updates.state.collectAsState()
  // A newer release may fix the store problem, so updates stay reachable here.
  // Installing never saves profiles: the unreadable store is left untouched.
  // initialize() consumes the previous installation result, so show it here.
  var installOutcome by remember { mutableStateOf<UpdateInstallOutcome?>(null) }
  LaunchedEffect(updates) { installOutcome = updates.initialize() }
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
        installOutcome?.let { outcome ->
          Text(
            updateOutcomeText(outcome, language),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        val pendingUpdate = when (val state = updateState) {
          is DesktopUpdateState.Available -> state.update
          is DesktopUpdateState.Downloading -> state.update
          is DesktopUpdateState.Ready -> state.update
          is DesktopUpdateState.Installing -> state.update
          is DesktopUpdateState.Failed -> state.update
          else -> null
        }
        if (pendingUpdate != null) {
          Text(
            (updateState as? DesktopUpdateState.Failed)?.let { updateErrorText(it.error, language) }
              ?: language.text(
                "Доступна версия ${pendingUpdate.versionName}. Установка не изменит файл подписок.",
                "Version ${pendingUpdate.versionName} is available. Installing it does not modify the subscription store.",
              ),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Row(
          modifier = Modifier.padding(top = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (pendingUpdate != null) {
            UpdatePrimaryAction(
              state = updateState,
              onCheck = { scope.launch { updates.check() } },
              onDownload = { update -> scope.launch { updates.download(update) } },
              onCancelDownload = updates::cancelDownload,
              onInstall = { ready ->
                scope.launch { updates.install(ready) { session.disconnect() }.onSuccess { onExit() } }
              },
            )
          }
          OutlinedButton(
            onClick = onExit,
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(language.text("Закрыть Veilark", "Close Veilark"))
          }
        }
      }
    }
  }
}
