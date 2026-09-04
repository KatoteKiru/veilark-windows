package uk.senyasenyavski.veilark.desktop

import androidx.compose.foundation.layout.Column
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
        val scene = ImageComposeScene(
          width = (460 * scale).toInt(), height = (720 * scale).toInt(), density = Density(scale),
        ) {
          CompositionLocalProvider(LocalUiLanguage provides UiLanguage.English) {
            VeilarkTheme(darkTheme = dark) {
              Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                  AppTopBar(VpnPhase.Idle, {})
                  Column(Modifier.weight(1f).padding(24.dp)) {
                    CompactConnectionWorkspace(
                      phase = VpnPhase.Idle, traffic = null, profile = null,
                      selectedEngine = VpnEngine.TrustTunnel, selectedNodeTag = null,
                      nodeLatencies = emptyMap(), probing = false, refreshing = false,
                      availableEngines = emptySet(), subscriptions = emptyList(), activeSubscriptionId = null,
                      onEngineSelect = {}, onSelectEndpoint = { _, _ -> }, onAction = {}, onImport = {},
                      onRefresh = {}, onProbe = {}, onOpenLogs = {},
                    )
                  }
                  AppBottomBar(Destination.Home, {})
                }
              }
            }
          }
        }
        try {
          scene.render(0).close()
          val image = scene.render(1_000_000_000)
          val bytes = assertNotNull(image.encodeToData()).bytes
          File(output, "workspace-${if (dark) "dark" else "light"}-$scale.png").writeBytes(bytes)
          image.close()
          assertTrue(bytes.size > 1000)
          assertFalse(scene.hasInvalidations(), "An idle workspace must not keep scheduling frames")
        } finally { scene.close() }
      }
    }
  }
}
