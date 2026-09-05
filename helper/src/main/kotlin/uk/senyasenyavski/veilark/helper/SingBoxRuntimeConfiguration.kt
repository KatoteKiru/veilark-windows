package uk.senyasenyavski.veilark.helper

import org.json.JSONObject

/** A process-only copy; persisted subscriptions and routing remain unchanged. */
internal object SingBoxRuntimeConfiguration {
  fun forStartup(config: String): String = JSONObject(config).apply {
    // Own the logging contract: the startup marker is emitted at INFO and is
    // read from stdout. Do not honor an imported output file or disabled flag.
    put("log", JSONObject().put("level", "info").put("timestamp", true))
  }.toString(2)
}
