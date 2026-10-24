package uk.senyasenyavski.veilark.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.senyasenyavski.veilark.model.VpnEngine

internal val VpnEngine.displayName: String
  get() = when (this) {
    VpnEngine.SingBox -> "sing-box"
    VpnEngine.TrustTunnel -> "TrustTunnel"
  }

@Composable
internal fun Page(
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
