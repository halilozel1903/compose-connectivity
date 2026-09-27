package io.github.halilozel1903.connectivity.core

import io.github.halilozel1903.connectivity.core.ConnectivityBannerState.BackOnline
import io.github.halilozel1903.connectivity.core.ConnectivityBannerState.Hidden
import io.github.halilozel1903.connectivity.core.ConnectivityBannerState.Offline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BannerStateTest {
    private val online = ConnectivityStatus.Available(NetworkDetails.Wifi)
    private val offline = ConnectivityStatus.Unavailable

    private fun timeline(vararg steps: Pair<Long, ConnectivityStatus>): Flow<ConnectivityStatus> = flow {
        var now = 0L
        for ((at, status) in steps) {
            delay(at - now)
            now = at
            emit(status)
        }
    }

    @Test
    fun `online at start stays hidden`() = runTest {
        val result = timeline(0L to online).toBannerState(3.seconds).toList()

        assertEquals(listOf(Hidden), result)
    }

    @Test
    fun `offline then back online auto hides`() = runTest {
        val result = timeline(0L to online, 1_000L to offline, 4_000L to online)
            .toBannerState(3.seconds)
            .map { testScheduler.currentTime to it }
            .toList()

        assertEquals(
            listOf(0L to Hidden, 1_000L to Offline, 4_000L to BackOnline, 7_000L to Hidden),
            result,
        )
    }

    @Test
    fun `offline at start shows immediately`() = runTest {
        val result = timeline(0L to offline, 2_000L to online).toBannerState(1.seconds).toList()

        assertEquals(listOf(Offline, BackOnline, Hidden), result)
    }

    @Test
    fun `dropping again while back online shows offline again`() = runTest {
        val result = timeline(0L to offline, 1_000L to online, 2_000L to offline)
            .toBannerState(3.seconds)
            .map { testScheduler.currentTime to it }
            .toList()

        assertEquals(listOf(0L to Offline, 1_000L to BackOnline, 2_000L to Offline), result)
    }

    @Test
    fun `detail changes while online do not re-trigger the banner`() = runTest {
        val cellular = ConnectivityStatus.Available(NetworkDetails.Cellular)
        val result = timeline(0L to offline, 100L to online, 200L to cellular).toBannerState(1.seconds).toList()

        assertEquals(listOf(Offline, BackOnline, Hidden), result)
    }

    @Test
    fun `works with a fake monitor`() = runTest {
        val monitor = FakeConnectivityMonitor()
        val states = mutableListOf<ConnectivityBannerState>()
        backgroundScope.launch { monitor.status.toBannerState(2.seconds).collect { states += it } }
        runCurrent()

        monitor.goOffline()
        runCurrent()
        monitor.goOnline(NetworkDetails.Cellular)
        runCurrent()
        assertEquals(listOf(Hidden, Offline, BackOnline), states)

        testScheduler.advanceTimeBy(2_001)
        assertEquals(listOf(Hidden, Offline, BackOnline, Hidden), states)
        assertTrue(monitor.status.value.isMetered)
    }

    @Test
    fun `initial state and visibility`() {
        assertEquals(Hidden, ConnectivityBannerState.initial(online))
        assertEquals(Offline, ConnectivityBannerState.initial(offline))
        assertFalse(Hidden.isVisible)
        assertTrue(BackOnline.isVisible)
    }
}
