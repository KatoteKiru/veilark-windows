package uk.senyasenyavski.veilark.helper

import java.nio.file.Path
import uk.senyasenyavski.veilark.model.GeoRoutingAssets

/** In-process leases keep an active core's local rule-set generation alive. */
internal object GeoGenerationLeases {
  private val owners = mutableMapOf<Path, Int>()
  @Synchronized fun acquire(assets: GeoRoutingAssets?): AutoCloseable {
    val paths = assets?.let { listOf(it.geoIpRuPath, it.geoSiteCategoryRuPath)
      .map { value -> Path.of(value).toAbsolutePath().normalize().parent }.toSet() }.orEmpty()
    paths.forEach { owners[it] = (owners[it] ?: 0) + 1 }
    var closed = false
    return AutoCloseable { synchronized(this) {
      if (!closed) {
        closed = true
        paths.forEach { path ->
          val count = (owners[path] ?: 1) - 1
          if (count == 0) owners.remove(path) else owners[path] = count
        }
      }
    } }
  }
  @Synchronized fun isPinned(path: Path): Boolean = (owners[path.toAbsolutePath().normalize()] ?: 0) > 0
  @Synchronized fun ifUnpinned(path: Path, action: () -> Unit) {
    if (!isPinned(path)) action()
  }
}
