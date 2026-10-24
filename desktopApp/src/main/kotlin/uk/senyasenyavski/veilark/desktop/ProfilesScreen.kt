package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.key
import com.example.veilark.profile.ProfileSelection
import uk.senyasenyavski.veilark.helper.NodeLatency
import uk.senyasenyavski.veilark.helper.SubscriptionOrigin
import uk.senyasenyavski.veilark.helper.SubscriptionRecord
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.profile.NodePresentation
import java.io.File

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
