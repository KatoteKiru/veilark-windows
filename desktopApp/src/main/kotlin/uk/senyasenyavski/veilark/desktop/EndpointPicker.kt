package uk.senyasenyavski.veilark.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.input.key.key
import com.example.veilark.profile.ProfileSelection
import uk.senyasenyavski.veilark.helper.NodeLatency
import uk.senyasenyavski.veilark.helper.SubscriptionOrigin
import uk.senyasenyavski.veilark.helper.SubscriptionRecord
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.profile.NodeCountry
import uk.senyasenyavski.veilark.profile.NodePresentation

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
internal fun EndpointPicker(
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
internal fun EndpointMark(
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
internal fun StablePickerPopup(
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
