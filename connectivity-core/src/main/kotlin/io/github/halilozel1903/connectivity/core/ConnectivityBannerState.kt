package io.github.halilozel1903.connectivity.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration

/** What an offline banner should show. */
public enum class ConnectivityBannerState {
    /** Nothing to say: online, and it has been for a while (or since the start). */
    Hidden,

    /** "You're offline". Stays until the connection comes back. */
    Offline,

    /** "Back online". Shown briefly after being offline, then [Hidden]. */
    BackOnline;

    public val isVisible: Boolean get() = this != Hidden

    public companion object {
        /** The state to show before any transition happened: offline shows, online doesn't. */
        public fun initial(status: ConnectivityStatus): ConnectivityBannerState =
            if (status.isOnline) Hidden else Offline
    }
}

/**
 * Turns statuses into banner states: [ConnectivityBannerState.Offline] while offline,
 * [ConnectivityBannerState.BackOnline] for [backOnlineDuration] after reconnecting, then
 * [ConnectivityBannerState.Hidden]. Being online at the start never shows "Back online".
 *
 * Feed it an already debounced flow, such as `ConnectivityMonitor.status`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun Flow<ConnectivityStatus>.toBannerState(backOnlineDuration: Duration): Flow<ConnectivityBannerState> = flow {
    var wasOffline = false
    val states = map { it.isOnline }
        .distinctUntilChanged()
        .transformLatest { online ->
            when {
                !online -> {
                    wasOffline = true
                    emit(ConnectivityBannerState.Offline)
                }
                wasOffline -> {
                    wasOffline = false
                    emit(ConnectivityBannerState.BackOnline)
                    delay(backOnlineDuration)
                    emit(ConnectivityBannerState.Hidden)
                }
                else -> emit(ConnectivityBannerState.Hidden)
            }
        }
    emitAll(states.distinctUntilChanged())
}
