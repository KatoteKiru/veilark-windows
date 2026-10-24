package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.RoutingSettings

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
