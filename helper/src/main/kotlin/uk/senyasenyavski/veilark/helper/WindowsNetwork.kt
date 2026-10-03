package uk.senyasenyavski.veilark.helper

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.IPHlpAPI
import com.sun.jna.platform.win32.IPHlpAPI.MIB_IF_ROW2
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions

/**
 * A Windows network adapter as reported by the IP Helper interface table.
 *
 * [alias] is the connection name Windows shows to the user and the value a VPN
 * core sets through its `interface_name` option. It is deliberately preferred
 * over [java.net.NetworkInterface], which exposes the driver description
 * instead and therefore cannot identify a tunnel by name.
 */
internal data class NetworkAdapter(
  val index: Int,
  val luid: Long,
  val alias: String,
  val description: String,
  val operational: Boolean,
  val mtu: Int,
  val bytesIn: Long,
  val bytesOut: Long,
  val interfaceGuid: String? = null,
) {
  val isTunnel: Boolean
    get() = TUNNEL_DESCRIPTIONS.any { description.contains(it, ignoreCase = true) }

  internal companion object {
    val TUNNEL_DESCRIPTIONS = listOf("wintun", "sing-tun", "wireguard")
  }
}

internal object WindowsNetwork {
  private const val OPER_STATUS_UP = 1

  /**
   * NDIS stacks a pseudo adapter per lightweight filter on top of every real
   * adapter. They duplicate the alias and the byte counters of their parent and
   * must never be treated as a tunnel in their own right.
   */
  private val FILTER_SUFFIX = Regex("""-\d{4}$""")

  private interface IpHelper : Library {
    fun GetIfTable2(table: PointerByReference): Int

    fun FreeMibTable(table: Pointer)

    companion object {
      val INSTANCE: IpHelper =
        Native.load("IPHlpAPI", IpHelper::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
  }

  /** Every non-pseudo adapter currently known to Windows, including down ones. */
  fun adapters(): List<NetworkAdapter> = runCatching { readTable() }
    .onFailure { SafeLog.write("Не удалось прочитать таблицу интерфейсов: ${it.message}") }
    .getOrDefault(emptyList())

  fun tunnels(): List<NetworkAdapter> = adapters().filter(NetworkAdapter::isTunnel)

  fun matching(predicate: (NetworkAdapter) -> Boolean): List<NetworkAdapter> =
    adapters().filter(predicate)

  /**
   * Refreshes a single adapter by LUID. The LUID is stable for the lifetime of
   * the adapter, while the interface index can be reused by Windows after an
   * adapter is removed.
   */
  fun refresh(luid: Long): NetworkAdapter? {
    val row = MIB_IF_ROW2()
    row.InterfaceLuid = luid
    if (IPHlpAPI.INSTANCE.GetIfEntry2(row) != 0) return null
    return row.toAdapter()
  }

  private fun readTable(): List<NetworkAdapter> {
    val reference = PointerByReference()
    if (IpHelper.INSTANCE.GetIfTable2(reference) != 0) return emptyList()
    val table = reference.value ?: return emptyList()
    try {
      val count = table.getInt(0)
      if (count <= 0) return emptyList()
      val rowSize = MIB_IF_ROW2().size()
      // MIB_IF_TABLE2 is { ULONG NumEntries; MIB_IF_ROW2 Table[] }. The rows
      // start at offset 8 because MIB_IF_ROW2 opens with an 8-byte aligned LUID.
      return (0 until count).mapNotNull { position ->
        val row = Structure.newInstance(
          MIB_IF_ROW2::class.java,
          table.share(ROW_OFFSET + position.toLong() * rowSize),
        )
        row.read()
        row.toAdapter().takeUnless { it.isFilterPseudoAdapter() }
      }
    } finally {
      IpHelper.INSTANCE.FreeMibTable(table)
    }
  }

  private fun MIB_IF_ROW2.toAdapter() = NetworkAdapter(
    index = InterfaceIndex,
    luid = InterfaceLuid,
    alias = Alias.toNullTerminatedString(),
    description = Description.toNullTerminatedString(),
    operational = OperStatus == OPER_STATUS_UP,
    mtu = Mtu,
    bytesIn = InOctets,
    bytesOut = OutOctets,
    interfaceGuid = InterfaceGuid.toGuidString(),
  )

  private fun NetworkAdapter.isFilterPseudoAdapter(): Boolean =
    FILTER_SUFFIX.containsMatchIn(description) ||
      description.contains("LightWeight Filter", ignoreCase = true)

  private fun CharArray.toNullTerminatedString(): String {
    val end = indexOf('\u0000').takeIf { it >= 0 } ?: size
    return concatToString(0, end).trim()
  }

  private const val ROW_OFFSET = 8L
}
