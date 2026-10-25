package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.GeoRoutingAssets

/** Exact union, not approximate supernetting. No DNS lookups or OS route changes.
 * Only consumed by Fairway: legacy engines keep their original Geo inputs.
 */
internal object FairwayIpv4Routes {
  const val MAX_DIRECT_PREFIXES = 16_384 // Native OwnedDirectRoutes limit.
  private const val MAX_INPUT_PREFIXES = 131_072

  fun fromGeoAssets(assets: GeoRoutingAssets): List<String> {
    val ipv4 = assets.trustTunnelExclusions.filter { '/' in it && ':' !in it }
    require(ipv4.isNotEmpty()) { "Fairway RU Geo IPv4 routes are unavailable" }
    return compact(ipv4)
  }

  fun compact(cidrs: List<String>): List<String> {
    require(cidrs.size <= MAX_INPUT_PREFIXES) { "Too many Fairway IPv4 route inputs" }
    if (cidrs.isEmpty()) return emptyList()
    val ranges = cidrs.map(::parse).sortedBy { it.first }
    val output = ArrayList<String>()
    var first = ranges.first().first
    var last = ranges.first().last
    fun emit() {
      var address = first
      while (address <= last) {
        val aligned = java.lang.Long.numberOfTrailingZeros(address).coerceAtMost(32)
        val fitting = 63 - java.lang.Long.numberOfLeadingZeros(last - address + 1)
        val hostBits = minOf(aligned, fitting)
        val ipv4 = (3 downTo 0).joinToString(".") { ((address ushr (8 * it)) and 255).toString() }
        output.add("$ipv4/${32 - hostBits}")
        require(output.size <= MAX_DIRECT_PREFIXES) { "Fairway IPv4 route plan exceeds native capacity" }
        address += 1L shl hostBits
      }
    }
    for (index in 1 until ranges.size) {
      val next = ranges[index]
      if (next.first <= last + 1) last = maxOf(last, next.last)
      else { emit(); first = next.first; last = next.last }
    }
    emit()
    return output.toList()
  }

  private fun parse(cidr: String): LongRange {
    require(cidr.length in 3..18) { "Invalid Fairway IPv4 CIDR" }
    val parts = cidr.split('/')
    require(parts.size == 2) { "Invalid Fairway IPv4 CIDR" }
    val bits = parts[1].toIntOrNull()
    require(bits != null && bits in 1..32 && bits.toString() == parts[1]) { "Invalid Fairway IPv4 prefix length" }
    val octets = parts[0].split('.')
    require(octets.size == 4) { "Invalid Fairway IPv4 address" }
    var address = 0L
    for (octet in octets) {
      val number = octet.toIntOrNull()
      require(number != null && number in 0..255 && number.toString() == octet) { "Invalid Fairway IPv4 address" }
      address = (address shl 8) or number.toLong()
    }
    val firstOctet = address ushr 24
    require(firstOctet != 0L && firstOctet != 127L && firstOctet < 224L) { "Unsupported Fairway IPv4 route" }
    val size = 1L shl (32 - bits)
    require(address % size == 0L) { "Noncanonical Fairway IPv4 route" }
    return address..(address + size - 1)
  }
}
