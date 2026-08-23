package uk.senyasenyavski.veilark.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase

private val DarkColors = darkColorScheme(
  primary = Color(0xFFA9C7FF),
  onPrimary = Color(0xFF00315F),
  primaryContainer = Color(0xFF1A4977),
  onPrimaryContainer = Color(0xFFD3E4FF),
  secondary = Color(0xFFBBC7DB),
  onSecondary = Color(0xFF253141),
  secondaryContainer = Color(0xFF3B4758),
  onSecondaryContainer = Color(0xFFD7E3F8),
  tertiary = Color(0xFFD5BEE5),
  // Amber is reserved for the degraded tunnel state, which is neither a success
  // nor a hard failure.
  tertiaryContainer = Color(0xFF5C4300),
  onTertiaryContainer = Color(0xFFFFDF9B),
  background = Color(0xFF111318),
  onBackground = Color(0xFFE2E2E9),
  surface = Color(0xFF111318),
  onSurface = Color(0xFFE2E2E9),
  surfaceVariant = Color(0xFF43474E),
  onSurfaceVariant = Color(0xFFC3C6CF),
  surfaceContainerLowest = Color(0xFF0C0E13),
  surfaceContainerLow = Color(0xFF191C20),
  surfaceContainer = Color(0xFF1D2024),
  surfaceContainerHigh = Color(0xFF272A2F),
  surfaceContainerHighest = Color(0xFF32353A),
  outline = Color(0xFF8D9199),
  outlineVariant = Color(0xFF43474E),
  error = Color(0xFFFFB4AB),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
  primary = Color(0xFF285F9A),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD3E4FF),
  onPrimaryContainer = Color(0xFF001C38),
  secondary = Color(0xFF535F70),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFD7E3F8),
  onSecondaryContainer = Color(0xFF101C2B),
  tertiary = Color(0xFF6B5778),
  tertiaryContainer = Color(0xFFFFDF9B),
  onTertiaryContainer = Color(0xFF3E2E00),
  background = Color(0xFFF9F9FF),
  onBackground = Color(0xFF1A1C20),
  surface = Color(0xFFF9F9FF),
  onSurface = Color(0xFF1A1C20),
  surfaceVariant = Color(0xFFDFE2EB),
  onSurfaceVariant = Color(0xFF43474E),
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF3F3FA),
  surfaceContainer = Color(0xFFEDEDF4),
  surfaceContainerHigh = Color(0xFFE7E8EE),
  surfaceContainerHighest = Color(0xFFE1E2E9),
  outline = Color(0xFF74777F),
  outlineVariant = Color(0xFFC3C6CF),
  error = Color(0xFFBA1A1A),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
)

private val VeilarkTypography = Typography(
  headlineMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 21.sp,
    lineHeight = 28.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
)

@Composable
internal fun VeilarkTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
    typography = VeilarkTypography,
    content = content,
  )
}

internal enum class Destination(
  val icon: ImageVector,
) {
  Home(Icons.Rounded.Home),
  Profiles(Icons.Rounded.Shield),
  Routing(Icons.Rounded.Route),
  Diagnostics(Icons.Rounded.BugReport),
  Updates(Icons.Rounded.Update),
  Logs(Icons.Rounded.Description),
  ;

  fun title(language: UiLanguage): String = when (this) {
    Home -> language.text("Подключение", "Connection")
    Profiles -> language.text("Подписки", "Subscriptions")
    Routing -> language.text("Маршруты", "Routing")
    Diagnostics -> language.text("Диагностика", "Diagnostics")
    Updates -> language.text("Обновления", "Updates")
    Logs -> language.text("Журнал", "Logs")
  }
}

@Composable
internal fun CompactAppBar(
  destination: Destination,
  phase: VpnPhase,
  engine: VpnEngine,
  onDestination: (Destination) -> Unit,
  onLanguage: (UiLanguage) -> Unit,
) {
  val language = LocalUiLanguage.current
  var navigationExpanded by remember { mutableStateOf(false) }
  var settingsExpanded by remember { mutableStateOf(false) }
  Surface(
    modifier = Modifier.fillMaxWidth().height(48.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box {
        IconButton(onClick = { navigationExpanded = true }) {
          Icon(Icons.Rounded.Menu, language.text("Открыть меню", "Open menu"))
        }
        DropdownMenu(
          expanded = navigationExpanded,
          onDismissRequest = { navigationExpanded = false },
        ) {
          listOf(
            Destination.Home,
            Destination.Profiles,
            Destination.Diagnostics,
            Destination.Updates,
            Destination.Logs,
          ).forEach { item ->
            DropdownMenuItem(
              text = { Text(item.title(language)) },
              leadingIcon = { Icon(item.icon, null) },
              trailingIcon = if (item == destination) {
                { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
              } else {
                null
              },
              onClick = {
                navigationExpanded = false
                onDestination(item)
              },
            )
          }
        }
      }
      Image(
        painter = painterResource("veilark-logo.png"),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
      )
      Text(
        "Veilark",
        modifier = Modifier.padding(start = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Box(
        Modifier.padding(start = 9.dp).size(7.dp).background(
          when (phase) {
            is VpnPhase.Connected -> MaterialTheme.colorScheme.primary
            is VpnPhase.Degraded -> MaterialTheme.colorScheme.tertiary
            is VpnPhase.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
          },
          CircleShape,
        ),
      )
      Text(
        destination.title(language),
        modifier = Modifier.weight(1f).padding(start = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        when (engine) {
          VpnEngine.SingBox -> "sing-box"
          VpnEngine.TrustTunnel -> "TrustTunnel"
        },
        modifier = Modifier.padding(horizontal = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
      Box {
        IconButton(onClick = { settingsExpanded = true }) {
          Icon(Icons.Rounded.Settings, language.text("Настройки", "Settings"))
        }
        DropdownMenu(
          expanded = settingsExpanded,
          onDismissRequest = { settingsExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text(Destination.Routing.title(language)) },
            leadingIcon = { Icon(Icons.Rounded.Route, null) },
            trailingIcon = if (destination == Destination.Routing) {
              { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
            } else {
              null
            },
            onClick = {
              settingsExpanded = false
              onDestination(Destination.Routing)
            },
          )
          HorizontalDivider()
          UiLanguage.entries.forEach { item ->
            DropdownMenuItem(
              text = { Text(if (item == UiLanguage.Russian) "Русский" else "English") },
              leadingIcon = { Icon(Icons.Rounded.Language, null) },
              trailingIcon = if (item == language) {
                { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
              } else {
                null
              },
              onClick = {
                settingsExpanded = false
                onLanguage(item)
              },
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun PageHeader(
  title: String,
  subtitle: String,
  action: (@Composable () -> Unit)? = null,
) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).padding(end = if (action == null) 0.dp else 10.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 1.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    action?.invoke()
  }
}

@Composable
internal fun CardSection(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(12.dp),
  ) {
    content()
  }
}

@Composable
internal fun ShieldMark(
  checked: Boolean,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier) {
    val shield = Path().apply {
      moveTo(size.width * .5f, size.height * .08f)
      lineTo(size.width * .82f, size.height * .2f)
      lineTo(size.width * .78f, size.height * .62f)
      quadraticTo(size.width * .72f, size.height * .82f, size.width * .5f, size.height * .94f)
      quadraticTo(size.width * .28f, size.height * .82f, size.width * .22f, size.height * .62f)
      lineTo(size.width * .18f, size.height * .2f)
      close()
    }
    drawPath(
      shield,
      color,
      style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
    )
    if (checked) {
      drawLine(
        color,
        Offset(size.width * .34f, size.height * .52f),
        Offset(size.width * .46f, size.height * .64f),
        4.dp.toPx(),
        StrokeCap.Round,
      )
      drawLine(
        color,
        Offset(size.width * .46f, size.height * .64f),
        Offset(size.width * .68f, size.height * .39f),
        4.dp.toPx(),
        StrokeCap.Round,
      )
    }
  }
}
