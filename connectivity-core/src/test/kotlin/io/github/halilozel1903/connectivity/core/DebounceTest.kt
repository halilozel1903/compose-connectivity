package io.github.halilozel1903.connectivity.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DebounceTest {
    private val wifi = ConnectivityStatus.Available(NetworkDetails.Wifi)
    private val cellular = ConnectivityStatus.Available(NetworkDetails.Cellular)
    private val offline = ConnectivityStatus.Unavailable

    /** Emits each status at the given virtual time in milliseconds. */
    private fun timeline(vararg steps: Pair<Long, ConnectivityStatus>): Flow<ConnectivityStatus> = flow {
        var now = 0L
        for ((at, status) in steps) {
            delay(at - now)
            now = at
            emit(status)
        }
    }

    private suspend fun TestScope.record(flow: Flow<ConnectivityStatus>): List<Pair<Long, ConnectivityStatus>> =
        flow.map { testScheduler.currentTime to it }.toList()

    private fun Flow<ConnectivityStatus>.debounced() = debounceConnectivity(1.seconds, 500.milliseconds)

    @Test
    fun `first status is emitted immediately`() = runTest {
        assertEquals(listOf(0L to offline), record(timeline(0L to offline).debounced()))
    }

    @Test
    fun `short drop is swallowed`() = runTest {
        val result = record(timeline(0L to wifi, 100L to offline, 400L to wifi).debounced())

        assertEquals(listOf(0L to wifi), result)
    }

    @Test
    fun `lasting drop is reported after the offline delay`() = runTest {
        val result = record(timeline(0L to wifi, 100L to offline, 5_000L to wifi).debounced())

        assertEquals(listOf(0L to wifi, 1_100L to offline, 5_500L to wifi), result)
    }

    @Test
    fun `flaky reconnect does not flash online`() = runTest {
        val result = record(
            timeline(
                0L to offline,
                1_000L to wifi, // lasts 200 ms only
                1_200L to offline,
                3_000L to wifi, // lasts
            ).debounced(),
        )

        assertEquals(listOf(0L to offline, 3_500L to wifi), result)
    }

    @Test
    fun `changes that stay online pass through at once`() = runTest {
        val losing = ConnectivityStatus.Losing(NetworkDetails.Wifi, 2_000)
        val result = record(timeline(0L to wifi, 100L to losing, 300L to cellular).debounced())

        assertEquals(listOf(0L to wifi, 100L to losing, 300L to cellular), result)
    }

    @Test
    fun `handover through offline ends on the new network without a gap`() = runTest {
        val result = record(timeline(0L to wifi, 100L to offline, 600L to cellular).debounced())

        assertEquals(listOf(0L to wifi, 600L to cellular), result)
    }

    @Test
    fun `immediate config reports every change`() = runTest {
        val result = record(
            timeline(0L to wifi, 100L to offline, 200L to wifi).debounceConnectivity(ConnectivityConfig.Immediate),
        )

        assertEquals(listOf(0L to wifi, 100L to offline, 200L to wifi), result)
    }

    @Test
    fun `full pipeline from events`() = runTest {
        val events = flow {
            emit(NetworkEvent.Available(1, NetworkDetails.Wifi))
            delay(100)
            emit(NetworkEvent.Lost(1))
            delay(300)
            emit(NetworkEvent.Available(2, NetworkDetails.Cellular))
            delay(5_000)
            emit(NetworkEvent.Lost(2))
        }

        val result = events.connectivityStatus().map { testScheduler.currentTime to it }.toList()

        assertEquals(listOf(0L to wifi, 400L to cellular, 6_400L to offline), result)
    }

    @Test
    fun `negative delays are rejected`() {
        assertFailsWith<IllegalArgumentException> { ConnectivityConfig(offlineDelay = (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { ConnectivityConfig(onlineDelay = (-1).seconds) }
    }
}
