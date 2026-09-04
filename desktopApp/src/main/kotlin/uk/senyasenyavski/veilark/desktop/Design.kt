package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.profile.NodeCountry

/**
 * Visual world: Google Material 3 on desktop, the same tonal system as Veilark
 * Android. Noto Sans is the self-hosted face so Windows does not fall back to
 * Segoe. Country marks are drawn geometry, not emoji.
 */
private val VeilarkSans = FontFamily(
  Font("fonts/NotoSans-Regular.ttf", FontWeight.Normal),
  Font("fonts/NotoSans-Medium.ttf", FontWeight.Medium),
  Font("fonts/NotoSans-SemiBold.ttf", FontWeight.SemiBold),
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFFA1CAFC),
  onPrimary = Color(0xFF003257),
  primaryContainer = Color(0xFF064A75),
  onPrimaryContainer = Color(0xFFD2E4FF),
  secondary = Color(0xFFBCC7D1),
  onSecondary = Color(0xFF263138),
  secondaryContainer = Color(0xFF3C4852),
  onSecondaryContainer = Color(0xFFDCE3EA),
  tertiary = Color(0xFFE2C38C),
  onTertiary = Color(0xFF412D04),
  tertiaryContainer = Color(0xFF5C4300),
  onTertiaryContainer = Color(0xFFFFDF9B),
  background = Color(0xFF111317),
  onBackground = Color(0xFFE2E2E7),
  surface = Color(0xFF111317),
  onSurface = Color(0xFFE2E2E7),
  surfaceVariant = Color(0xFF43474D),
  onSurfaceVariant = Color(0xFFC3C7CF),
  surfaceContainerLowest = Color(0xFF0C0E12),
  surfaceContainerLow = Color(0xFF191C20),
  surfaceContainer = Color(0xFF1D2024),
  surfaceContainerHigh = Color(0xFF272A2E),
  surfaceContainerHighest = Color(0xFF32353A),
  outline = Color(0xFF8D9199),
  outlineVariant = Color(0xFF43474D),
  error = Color(0xFFFFB4AB),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
  primary = Color(0xFF275D8C),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD2E4FF),
  onPrimaryContainer = Color(0xFF0B446F),
  secondary = Color(0xFF52606B),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFDCE3EA),
  onSecondaryContainer = Color(0xFF27323B),
  tertiary = Color(0xFF7A590C),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFDF9B),
  onTertiaryContainer = Color(0xFF3E2E00),
  background = Color(0xFFF9F9FC),
  onBackground = Color(0xFF191C20),
  surface = Color(0xFFF9F9FC),
  onSurface = Color(0xFF191C20),
  surfaceVariant = Color(0xFFE0E3E8),
  onSurfaceVariant = Color(0xFF43474E),
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF3F3F7),
  surfaceContainer = Color(0xFFEDEDF2),
  surfaceContainerHigh = Color(0xFFE7E8ED),
  surfaceContainerHighest = Color(0xFFE1E2E7),
  outline = Color(0xFF73777F),
  outlineVariant = Color(0xFFC3C7CF),
  error = Color(0xFFBA1A1A),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
)

private val VeilarkTypography = Typography(
  headlineMedium = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    lineHeight = 26.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = VeilarkSans,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
  ),
)

private val VeilarkShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

internal val VeilarkEmphasized = tween<Float>(280, easing = FastOutSlowInEasing)
internal val VeilarkColorMotion = tween<Color>(360, easing = FastOutSlowInEasing)
internal val VeilarkSpring = spring<Float>(
  dampingRatio = Spring.DampingRatioMediumBouncy,
  stiffness = Spring.StiffnessMediumLow,
)

@Composable
internal fun VeilarkTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
    typography = VeilarkTypography,
    shapes = VeilarkShapes,
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

  fun barTitle(language: UiLanguage): String = when (this) {
    Home -> "VPN"
    Profiles -> language.text("Серверы", "Servers")
    Routing -> language.text("Сеть", "Network")
    Updates -> language.text("Обновления", "Updates")
    else -> title(language)
  }
}

internal val PrimaryDestinations = listOf(
  Destination.Home,
  Destination.Profiles,
  Destination.Routing,
  Destination.Updates,
)

internal val OverflowDestinations = listOf(
  Destination.Diagnostics,
  Destination.Logs,
)

@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.size(36.dp),
    shape = CircleShape,
    color = Color(0xFF1D242C),
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Image(
      painter = painterResource("veilark-logo.png"),
      contentDescription = "Veilark",
      modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape),
      contentScale = ContentScale.Fit,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
  phase: VpnPhase,
  onLanguage: (UiLanguage) -> Unit,
) {
  val language = LocalUiLanguage.current
  TopAppBar(
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Text(
          "Veilark",
          modifier = Modifier.padding(start = 10.dp),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
        )
      }
    },
    actions = {
      StatusChip(phase = phase)
      TextButton(
        onClick = {
          onLanguage(
            if (language == UiLanguage.Russian) UiLanguage.English else UiLanguage.Russian,
          )
        },
        contentPadding = PaddingValues(horizontal = 10.dp),
      ) {
        Icon(Icons.Rounded.Language, null, Modifier.size(18.dp))
        Text(
          if (language == UiLanguage.Russian) "EN" else "RU",
          modifier = Modifier.padding(start = 4.dp),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    },
    windowInsets = WindowInsets(0, 0, 0, 0),
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.surface,
      titleContentColor = MaterialTheme.colorScheme.onSurface,
      actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
  )
}

@Composable
internal fun AppBottomBar(
  destination: Destination,
  onDestination: (Destination) -> Unit,
) {
  val language = LocalUiLanguage.current
  var moreOpen by remember { mutableStateOf(false) }
  val overflowSelected = destination in OverflowDestinations
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 0.dp,
    windowInsets = WindowInsets(0, 0, 0, 0),
  ) {
    PrimaryDestinations.forEach { item ->
      NavigationBarItem(
        selected = item == destination,
        onClick = { onDestination(item) },
        icon = { Icon(item.icon, contentDescription = item.title(language)) },
        label = {
          Text(
            item.barTitle(language),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
      )
    }
    NavigationBarItem(
      selected = overflowSelected,
      onClick = { moreOpen = true },
      icon = {
        Box {
          Icon(
            Icons.Rounded.MoreHoriz,
            contentDescription = language.text("Ещё", "More"),
          )
          DropdownMenu(
            expanded = moreOpen,
            onDismissRequest = { moreOpen = false },
          ) {
            OverflowDestinations.forEach { item ->
              DropdownMenuItem(
                text = { Text(item.title(language)) },
                leadingIcon = { Icon(item.icon, contentDescription = null) },
                onClick = {
                  moreOpen = false
                  onDestination(item)
                },
              )
            }
          }
        }
      },
      label = { Text(language.text("Ещё", "More")) },
    )
  }
}

@Composable
private fun StatusChip(phase: VpnPhase) {
  val language = LocalUiLanguage.current
  val tone = when (phase) {
    is VpnPhase.Connected -> MaterialTheme.colorScheme.primaryContainer
    is VpnPhase.Degraded -> MaterialTheme.colorScheme.tertiaryContainer
    is VpnPhase.Error -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHighest
  }
  val onTone = when (phase) {
    is VpnPhase.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
    is VpnPhase.Degraded -> MaterialTheme.colorScheme.onTertiaryContainer
    is VpnPhase.Error -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Surface(
    color = tone,
    contentColor = onTone,
    shape = CircleShape,
  ) {
    Text(
      when (phase) {
        VpnPhase.Idle -> language.text("Готов", "Ready")
        VpnPhase.NeedsElevation -> language.text("Права", "Admin")
        VpnPhase.Preparing, VpnPhase.Connecting -> language.text("Связь", "Link")
        is VpnPhase.Connected -> language.text("Защита", "Secure")
        is VpnPhase.Degraded -> language.text("Сбой", "Unstable")
        VpnPhase.Stopping -> language.text("Стоп", "Stop")
        is VpnPhase.Error -> language.text("Ошибка", "Error")
      },
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
    )
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
    Column(Modifier.weight(1f).padding(end = if (action == null) 0.dp else 12.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
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
    shape = MaterialTheme.shapes.medium,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    content()
  }
}

@Composable
internal fun CountryFlag(
  country: NodeCountry,
  modifier: Modifier = Modifier,
) {
  val palette = remember(country.code) { flagPalette(country.code) }
  Canvas(modifier) {
    val radius = CornerRadius(size.minDimension * 0.16f, size.minDimension * 0.16f)
    drawRoundRect(color = palette.background, cornerRadius = radius)
    palette.stripes.forEach { stripe ->
      if (stripe.vertical) {
        drawRect(
          color = stripe.color,
          topLeft = Offset(size.width * stripe.start, 0f),
          size = Size(size.width * (stripe.end - stripe.start), size.height),
        )
      } else {
        drawRect(
          color = stripe.color,
          topLeft = Offset(0f, size.height * stripe.start),
          size = Size(size.width, size.height * (stripe.end - stripe.start)),
        )
      }
    }
    if (palette.disc != Color.Unspecified) {
      val diameter = size.minDimension * 0.42f
      drawCircle(
        color = palette.disc,
        radius = diameter / 2f,
        center = Offset(size.width / 2f, size.height / 2f),
      )
    }
    palette.canton?.let { canton ->
      drawRect(
        color = canton,
        size = Size(size.width * 0.42f, size.height * 0.54f),
      )
    }
    palette.cross?.let { cross ->
      val bar = size.minDimension * 0.18f
      drawRect(
        color = cross,
        topLeft = Offset(0f, (size.height - bar) / 2f),
        size = Size(size.width, bar),
      )
      drawRect(
        color = cross,
        topLeft = Offset(size.width * 0.32f - bar / 2f, 0f),
        size = Size(bar, size.height),
      )
    }
    drawRoundRect(
      color = Color(0x33000000),
      cornerRadius = radius,
      style = Stroke(width = 1.dp.toPx()),
    )
  }
}

private data class FlagStripe(
  val color: Color,
  val start: Float,
  val end: Float,
  val vertical: Boolean = false,
)

private data class FlagPalette(
  val background: Color,
  val stripes: List<FlagStripe> = emptyList(),
  val canton: Color? = null,
  val cross: Color? = null,
  val disc: Color = Color.Unspecified,
)

private fun flagPalette(code: String): FlagPalette = when (code.uppercase()) {
  "DE" -> FlagPalette(
    background = Color(0xFF000000),
    stripes = listOf(
      FlagStripe(Color(0xFF000000), 0f, 0.33f),
      FlagStripe(Color(0xFFDD0000), 0.33f, 0.66f),
      FlagStripe(Color(0xFFFFCE00), 0.66f, 1f),
    ),
  )
  "NL" -> FlagPalette(
    background = Color(0xFF21468B),
    stripes = listOf(
      FlagStripe(Color(0xFFAE1C28), 0f, 0.33f),
      FlagStripe(Color.White, 0.33f, 0.66f),
      FlagStripe(Color(0xFF21468B), 0.66f, 1f),
    ),
  )
  "RU" -> FlagPalette(
    background = Color(0xFF0039A6),
    stripes = listOf(
      FlagStripe(Color.White, 0f, 0.33f),
      FlagStripe(Color(0xFF0039A6), 0.33f, 0.66f),
      FlagStripe(Color(0xFFD52B1E), 0.66f, 1f),
    ),
  )
  "FR" -> FlagPalette(
    background = Color.White,
    stripes = listOf(
      FlagStripe(Color(0xFF0055A4), 0f, 0.33f, vertical = true),
      FlagStripe(Color.White, 0.33f, 0.66f, vertical = true),
      FlagStripe(Color(0xFFEF4135), 0.66f, 1f, vertical = true),
    ),
  )
  "IT" -> FlagPalette(
    background = Color.White,
    stripes = listOf(
      FlagStripe(Color(0xFF009246), 0f, 0.33f, vertical = true),
      FlagStripe(Color.White, 0.33f, 0.66f, vertical = true),
      FlagStripe(Color(0xFFCE2B37), 0.66f, 1f, vertical = true),
    ),
  )
  "US" -> FlagPalette(
    background = Color(0xFFB22234),
    stripes = listOf(
      FlagStripe(Color(0xFFB22234), 0f, 1f),
      FlagStripe(Color.White, 0.14f, 0.28f),
      FlagStripe(Color.White, 0.42f, 0.56f),
      FlagStripe(Color.White, 0.70f, 0.84f),
    ),
    canton = Color(0xFF3C3B6E),
  )
  "GB", "UK" -> FlagPalette(
    background = Color(0xFF012169),
    cross = Color(0xFFC8102E),
  )
  "FI" -> FlagPalette(background = Color.White, cross = Color(0xFF003580))
  "SE" -> FlagPalette(background = Color(0xFF006AA7), cross = Color(0xFFFECC00))
  "NO" -> FlagPalette(background = Color(0xFFBA0C2F), cross = Color(0xFF00205B))
  "CH" -> FlagPalette(background = Color(0xFFDA291C), cross = Color.White)
  "AT" -> FlagPalette(
    background = Color.White,
    stripes = listOf(
      FlagStripe(Color(0xFFED2939), 0f, 0.33f),
      FlagStripe(Color.White, 0.33f, 0.66f),
      FlagStripe(Color(0xFFED2939), 0.66f, 1f),
    ),
  )
  "PL" -> FlagPalette(
    background = Color.White,
    stripes = listOf(
      FlagStripe(Color.White, 0f, 0.5f),
      FlagStripe(Color(0xFFDC143C), 0.5f, 1f),
    ),
  )
  "CZ" -> FlagPalette(
    background = Color.White,
    stripes = listOf(
      FlagStripe(Color.White, 0f, 0.5f),
      FlagStripe(Color(0xFFD7141A), 0.5f, 1f),
    ),
    canton = Color(0xFF11457E),
  )
  "ES" -> FlagPalette(
    background = Color(0xFFC60B1E),
    stripes = listOf(
      FlagStripe(Color(0xFFC60B1E), 0f, 0.25f),
      FlagStripe(Color(0xFFFFC400), 0.25f, 0.75f),
      FlagStripe(Color(0xFFC60B1E), 0.75f, 1f),
    ),
  )
  "TR" -> FlagPalette(background = Color(0xFFE30A17), disc = Color.White)
  "CA" -> FlagPalette(
    background = Color.White,
    stripes = listOf(
      FlagStripe(Color(0xFFFF0000), 0f, 0.25f, vertical = true),
      FlagStripe(Color.White, 0.25f, 0.75f, vertical = true),
      FlagStripe(Color(0xFFFF0000), 0.75f, 1f, vertical = true),
    ),
  )
  "JP" -> FlagPalette(background = Color.White, disc = Color(0xFFBC002D))
  "SG" -> FlagPalette(
    background = Color.White,
    stripes = listOf(
      FlagStripe(Color(0xFFEF3340), 0f, 0.5f),
      FlagStripe(Color.White, 0.5f, 1f),
    ),
  )
  "HK" -> FlagPalette(background = Color(0xFFDE2910))
  "KR" -> FlagPalette(background = Color.White)
  else -> FlagPalette(background = Color(0xFF275D8C))
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
      style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
    )
    if (checked) {
      drawLine(
        color,
        Offset(size.width * .34f, size.height * .52f),
        Offset(size.width * .46f, size.height * .64f),
        3.5.dp.toPx(),
        StrokeCap.Round,
      )
      drawLine(
        color,
        Offset(size.width * .46f, size.height * .64f),
        Offset(size.width * .68f, size.height * .39f),
        3.5.dp.toPx(),
        StrokeCap.Round,
      )
    }
  }
}
