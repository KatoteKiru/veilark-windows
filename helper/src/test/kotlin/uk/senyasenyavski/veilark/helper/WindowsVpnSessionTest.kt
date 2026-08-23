package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase

class WindowsVpnSessionTest {
  @Test
  fun `active competing tunnel fails before a core is started`() = runBlocking {
    val controller = FakeController(VpnEngine.SingBox)
    val session = WindowsVpnSession(
      controllers = listOf(controller),
      logger = {},
      preConnectCheck = { "Активен другой VPN «happ-tun»" },
    )

    session.connect(profile(VpnEngine.SingBox))

    val error = assertIs<VpnPhase.Error>(session.state.value.phase)
    assertEquals("COMPETING_TUNNEL", error.code)
    assertFalse(controller.started)
  }

  @Test
  fun `session launches the controller matching the profile engine`() {
    runBlocking {
      val singBox = FakeController(VpnEngine.SingBox)
      val trustTunnel = FakeController(VpnEngine.TrustTunnel)
      val session = WindowsVpnSession(listOf(singBox, trustTunnel), logger = {})

      session.connect(profile(VpnEngine.TrustTunnel))

      assertFalse(singBox.started)
      assertTrue(trustTunnel.started)
      assertEquals(VpnEngine.TrustTunnel, session.state.value.engine)
      assertIs<VpnPhase.Connected>(session.state.value.phase)

      session.disconnect()
      assertTrue(trustTunnel.stopped)
      assertIs<VpnPhase.Idle>(session.state.value.phase)
    }
  }

  @Test
  fun `disconnect failure is propagated and never reported as idle`() {
    runBlocking {
      val controller = FakeController(VpnEngine.SingBox, failOnStop = true)
      val session = WindowsVpnSession(listOf(controller), logger = {})
      session.connect(profile(VpnEngine.SingBox))

      assertFailsWith<IllegalStateException> { session.disconnect() }

      val error = assertIs<VpnPhase.Error>(session.state.value.phase)
      assertEquals("STOP_FAILED", error.code)
      assertTrue(controller.started)
    }
  }

  @Test
  fun `session reports an unexpected exit for either engine`() {
    runBlocking {
      VpnEngine.entries.forEach { engine ->
        val controller = FakeController(engine)
        val session = fastSession(controller)
        session.connect(profile(engine))
        controller.crash()

        withTimeout(2_000) {
          while (session.state.value.phase !is VpnPhase.Error) delay(10)
        }

        val error = assertIs<VpnPhase.Error>(session.state.value.phase)
        assertEquals("CORE_EXITED", error.code)
        assertTrue(error.message.contains(engine.displayName))
        assertNull(session.state.value.traffic)
      }
    }
  }

  @Test
  fun `failed startup is stopped and never reported as connected`() {
    runBlocking {
      val controller = FakeController(VpnEngine.SingBox, failOnStart = true)
      val session = WindowsVpnSession(listOf(controller), logger = {})

      session.connect(profile(VpnEngine.SingBox))

      assertTrue(controller.stopped)
      assertFalse(controller.started)
      val error = assertIs<VpnPhase.Error>(session.state.value.phase)
      assertEquals("CORE_START_FAILED", error.code)
    }
  }

  @Test
  fun `a tunnel that carries no traffic is degraded rather than torn down`() {
    runBlocking {
      val controller = FakeController(
        engine = VpnEngine.SingBox,
        startHealth = EngineHealth.Unhealthy("другой VPN владеет маршрутом"),
      )
      val session = WindowsVpnSession(listOf(controller), logger = {})

      session.connect(profile(VpnEngine.SingBox))

      val degraded = assertIs<VpnPhase.Degraded>(session.state.value.phase)
      assertTrue(degraded.message.contains("другой VPN владеет маршрутом"))
      // The core keeps running so the user can act on the reason.
      assertTrue(controller.started)
      assertFalse(controller.stopped)
    }
  }

  @Test
  fun `session reports traffic loss and recovers without a false disconnect`() {
    runBlocking {
      val controller = FakeController(VpnEngine.SingBox)
      val session = fastSession(controller)
      session.connect(profile(VpnEngine.SingBox))

      controller.health = EngineHealth.Unhealthy("test traffic failure")
      withTimeout(2_000) {
        while (session.state.value.phase !is VpnPhase.Degraded) delay(10)
      }
      assertTrue(
        assertIs<VpnPhase.Degraded>(session.state.value.phase)
          .message.contains("test traffic failure"),
      )

      controller.health = EngineHealth.Healthy
      withTimeout(2_000) {
        while (session.state.value.phase !is VpnPhase.Connected) delay(10)
      }
      assertTrue(controller.started)
      session.disconnect()
    }
  }

  @Test
  fun `byte counters reach the published state while connected`() {
    runBlocking {
      val controller = FakeController(VpnEngine.SingBox)
      controller.statistics = TunnelStatistics("Veilark", 12, bytesIn = 2_048, bytesOut = 1_024)
      val session = fastSession(controller)

      session.connect(profile(VpnEngine.SingBox))

      val traffic = session.state.value.traffic
      assertEquals("Veilark", traffic?.adapter)
      assertEquals(2_048, traffic?.bytesIn)
      assertEquals(1_024, traffic?.bytesOut)

      session.disconnect()
      assertNull(session.state.value.traffic)
    }
  }

  @Test
  fun `disconnect interrupts a connection attempt that is still running`() {
    runBlocking {
      val controller = FakeController(VpnEngine.SingBox, blockStart = true)
      val session = WindowsVpnSession(listOf(controller), logger = {})

      val connecting = launch { session.connect(profile(VpnEngine.SingBox)) }
      withTimeout(2_000) {
        while (session.state.value.phase !is VpnPhase.Connecting) delay(10)
      }

      session.disconnect()
      connecting.join()

      assertIs<VpnPhase.Idle>(session.state.value.phase)
      assertTrue(controller.stopped)
      assertEquals(1, controller.stopCalls.get())
      assertEquals(1, controller.maxConcurrentStops.get())
    }
  }

  @Test
  fun `startup failure racing stop performs one teardown`() {
    runBlocking {
      val controller = FakeController(
        engine = VpnEngine.SingBox,
        failOnStart = true,
        blockStart = true,
        blockStop = true,
      )
      val session = WindowsVpnSession(listOf(controller), logger = {})
      val connecting = launch { session.connect(profile(VpnEngine.SingBox)) }
      withTimeout(2_000) {
        while (session.state.value.phase !is VpnPhase.Connecting) delay(10)
      }

      controller.releaseStart()
      withTimeout(2_000) {
        while (controller.stopCalls.get() == 0) delay(10)
      }
      val disconnecting = launch { session.disconnect() }
      withTimeout(2_000) {
        while (session.state.value.phase !is VpnPhase.Stopping) delay(10)
      }
      controller.releaseStop()
      connecting.join()
      disconnecting.join()

      assertEquals(1, controller.stopCalls.get())
      assertEquals(1, controller.maxConcurrentStops.get())
      assertIs<VpnPhase.Idle>(session.state.value.phase)
    }
  }

  @Test
  fun `concurrent disconnects perform one serialized teardown`() {
    runBlocking {
      val controller = FakeController(VpnEngine.SingBox, blockStop = true)
      val session = WindowsVpnSession(listOf(controller), logger = {})
      session.connect(profile(VpnEngine.SingBox))

      val first = launch { session.disconnect() }
      val second = launch { session.disconnect() }
      withTimeout(2_000) {
        while (controller.stopCalls.get() == 0) delay(10)
      }
      assertEquals(1, controller.stopCalls.get())
      assertEquals(1, controller.maxConcurrentStops.get())

      controller.releaseStop()
      first.join()
      second.join()
      assertEquals(1, controller.stopCalls.get())
      assertIs<VpnPhase.Idle>(session.state.value.phase)
    }
  }

  @Test
  fun `disconnect publishes stopping before controller shutdown finishes`() {
    runBlocking {
      val controller = FakeController(VpnEngine.SingBox, blockStop = true)
      val session = WindowsVpnSession(listOf(controller), logger = {})
      session.connect(profile(VpnEngine.SingBox))

      val disconnecting = launch { session.disconnect() }
      withTimeout(2_000) {
        while (session.state.value.phase !is VpnPhase.Stopping) delay(10)
      }
      assertIs<VpnPhase.Stopping>(session.state.value.phase)

      controller.releaseStop()
      disconnecting.join()
      assertIs<VpnPhase.Idle>(session.state.value.phase)
    }
  }

  @Test
  fun `disconnect fails closed while a non cancellable connect preflight is still running`() {
    runBlocking {
      val controller = FakeController(
        VpnEngine.SingBox,
        blockStart = true,
        nonCancellableStart = true,
      )
      val session = WindowsVpnSession(
        controllers = listOf(controller),
        disconnectJoinTimeoutMillis = 50,
        logger = {},
      )
      val connecting = launch { session.connect(profile(VpnEngine.SingBox)) }
      withTimeout(2_000) {
        while (session.state.value.phase !is VpnPhase.Connecting) delay(10)
      }

      assertFailsWith<IllegalStateException> { session.disconnect() }
      val error = assertIs<VpnPhase.Error>(session.state.value.phase)
      assertEquals("STOP_FAILED", error.code)

      controller.releaseStart()
      connecting.join()
      assertFalse(controller.started)
    }
  }

  @Test
  fun `a missing engine is reported instead of silently doing nothing`() {
    runBlocking {
      val session = WindowsVpnSession(
        listOf(FakeController(VpnEngine.SingBox)),
        logger = {},
      )

      session.connect(profile(VpnEngine.TrustTunnel))

      val error = assertIs<VpnPhase.Error>(session.state.value.phase)
      assertEquals("ENGINE_NOT_FOUND", error.code)
    }
  }

  private fun fastSession(vararg controllers: EngineController) = WindowsVpnSession(
    controllers = controllers.toList(),
    statisticsIntervalMillis = 10,
    healthChecksEveryTicks = 1,
    logger = {},
  )

  private class FakeController(
    override val engine: VpnEngine,
    private val failOnStart: Boolean = false,
    private val failOnStop: Boolean = false,
    private val startHealth: EngineHealth = EngineHealth.Healthy,
    blockStart: Boolean = false,
    blockStop: Boolean = false,
    private val nonCancellableStart: Boolean = false,
  ) : EngineController {
    var started = false
    var stopped = false
    var health: EngineHealth = EngineHealth.Healthy
    var statistics: TunnelStatistics? = null
    val stopCalls = AtomicInteger()
    val maxConcurrentStops = AtomicInteger()
    private val concurrentStops = AtomicInteger()

    /** Keeps [start] suspended so a test can observe the connecting phase. */
    private val gate = CompletableDeferred<Unit>().apply { if (!blockStart) complete(Unit) }
    private val stopGate = CompletableDeferred<Unit>().apply { if (!blockStop) complete(Unit) }

    override suspend fun start(profile: Profile): EngineHealth {
      if (nonCancellableStart) {
        withContext(NonCancellable) { gate.await() }
      } else {
        gate.await()
      }
      currentCoroutineContext().ensureActive()
      if (failOnStart) error("startup failed")
      started = true
      return startHealth
    }

    override suspend fun stop() {
      stopCalls.incrementAndGet()
      val concurrent = concurrentStops.incrementAndGet()
      maxConcurrentStops.updateAndGet { current -> maxOf(current, concurrent) }
      try {
        stopGate.await()
        if (failOnStop) error("stop failed")
        stopped = true
        started = false
      } finally {
        concurrentStops.decrementAndGet()
      }
    }

    override fun isAlive(): Boolean = started
    override suspend fun health(): EngineHealth = health
    override fun statistics(): TunnelStatistics? = statistics

    fun crash() {
      started = false
    }

    fun releaseStop() {
      stopGate.complete(Unit)
    }

    fun releaseStart() {
      gate.complete(Unit)
    }
  }

  private fun profile(engine: VpnEngine) = Profile(
    id = engine.name,
    name = engine.displayName,
    engine = engine,
    config = if (engine == VpnEngine.SingBox) "{}" else "tt://opaque-profile",
    nodes = listOf(Node(engine.name, engine.displayName, engine.displayName)),
    sourceLabel = "test",
  )

  private val VpnEngine.displayName: String
    get() = if (this == VpnEngine.SingBox) "sing-box" else "TrustTunnel"
}
