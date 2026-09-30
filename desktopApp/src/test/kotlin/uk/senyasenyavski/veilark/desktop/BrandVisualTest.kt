package uk.senyasenyavski.veilark.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings

class BrandVisualTest {
  @Test
  fun launcherResourcesCoverWindowsScaling() {
    for (size in listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)) {
      val source = javaClass.classLoader.getResource("icons/veilark-$size.png")
      val image = ImageIO.read(assertNotNull(source))
      assertEquals(size, image.width)
      assertEquals(size, image.height)
      assertTrue(image.colorModel.hasAlpha())
    }
    assertEquals("Veilark", VeilarkMark.name)
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun renderNativeWorkspaceInBothThemesAndDpi() {
    val output = File("build/reports/ui").apply { mkdirs() }
    for (dark in listOf(false, true)) {
      for (scale in listOf(1f, 1.25f, 1.5f)) {
       for (destination in listOf(Destination.Home, Destination.Profiles, Destination.Routing)) {
        for (wide in listOf(false, true)) {
        val scene = ImageComposeScene(
          width = ((if (wide) 900 else 540) * scale).toInt(), height = ((if (wide) 680 else 480) * scale).toInt(), density = Density(scale),
        ) {
          CompositionLocalProvider(LocalUiLanguage provides if (dark) UiLanguage.Russian else UiLanguage.English) {
            VeilarkTheme(darkTheme = dark) {
              Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                  AppTopBar(VpnPhase.Idle, {})
                  Row(Modifier.weight(1f)) {
                  AppSidebar(destination, wide, {})
                  Column(Modifier.weight(1f)) {
                    if (destination == Destination.Profiles) {
                      ProfilesScreen(
                        subscriptions = emptyList(), selectedEngine = VpnEngine.TrustTunnel,
                        selectedSubscriptionIds = emptyMap(), selectedNodeTag = null, nodeLatencies = emptyMap(),
                        probing = false, refreshing = false, configurationEnabled = true,
                        onSelectSubscription = { _, _ -> }, onSelectNode = {}, onRefresh = {}, onProbe = {},
                        onDelete = {}, importing = false, onOpenSubscriptionAccount = {}, onOpenWebApp = {},
                        onPaste = {}, onFile = {}, onUrl = {},
                      )
                    } else if (destination == Destination.Routing) {
                      RoutingScreen(RoutingSettings(mode = RoutingMode.Manual), true, true, true, false, {}, {})
                    } else {
                    HomeScreen(
                      phase = VpnPhase.Idle, traffic = null, profile = null, elevated = true,
                      selectedEngine = VpnEngine.TrustTunnel, selectedNodeTag = null,
                      nodeLatencies = emptyMap(), probing = false, refreshing = false,
                      availableEngines = emptySet(), subscriptions = emptyList(), activeSubscriptionId = null,
                      onEngineSelect = {}, onSelectEndpoint = { _, _ -> }, onConnect = {}, onDisconnect = {}, onImport = {},
                      onRefresh = {}, onProbe = {}, onOpenLogs = {},
                    )
                    }
                  }
                  }
                }
              }
            }
          }
        }
        try {
          scene.render(0).close()
          val image = scene.render(1_000_000_000)
          val bytes = assertNotNull(image.encodeToData()).bytes
          File(output, "${destination.name.lowercase()}-${if (dark) "dark" else "light"}-$scale-${if (wide) "wide" else "compact"}.png").writeBytes(bytes)
          image.close()
          assertTrue(bytes.size > 1000)
          assertFalse(scene.hasInvalidations(), "An idle workspace must not keep scheduling frames")
        } finally { scene.close() }
        }
       }
      }
    }
  }
}
