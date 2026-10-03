package uk.senyasenyavski.veilark.desktop

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The shared Veilark mark; one vector source for Android, desktop and the cabinet. */
internal val VeilarkMark: ImageVector = ImageVector.Builder(
  name = "Veilark", defaultWidth = 24.dp, defaultHeight = 24.dp,
  viewportWidth = 432f, viewportHeight = 432f,
).apply {
  addPath(
    pathData = addPathNodes("M48 48L165 127V264L172 269V145L216 115L261 145V269L267 264V127L384 48V248L259 342L233 219V177L216 165L200 177V219L174 342L48 248ZM90 132V218L124 242V152ZM309 152V242L342 218V132Z"),
    fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd,
  )
  addPath(
    pathData = addPathNodes("M207 219H226L248 354L216 385L184 354Z"),
    fill = SolidColor(Color.Black),
  )
}.build()

@Composable
internal fun BrandGlyph(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
  Icon(VeilarkMark, contentDescription = null, modifier = modifier, tint = color)
}
