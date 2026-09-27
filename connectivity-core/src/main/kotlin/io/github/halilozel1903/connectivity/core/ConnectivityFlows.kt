package io.github.halilozel1903.connectivity.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration

/** Folds default network events into statuses. Consecutive duplicates are dropped. */
public fun Flow<NetworkEvent>.toConnectivityStatus(
    requireValidatedInternet: Boolean = false,
): Flow<ConnectivityStatus> =
    scan(DefaultNetworkState.Empty) { state, event -> state.reduce(event) }
        .drop(1) // the seed is not a real observation
        .map { it.toStatus(requireValidatedInternet) }
        .distinctUntilChanged()

/**
 * Debounces online/offline flips so flaky networks don't make the UI flicker.
 *
 * - The first status is emitted right away.
 * - Going offline is emitted only after it lasted [offlineDelay].
 * - Coming back online is emitted only after it lasted [onlineDelay].
 * - Changes that keep the online state (Wi-Fi to cellular, metered flag, `Losing`) pass through
 *   immediately and cancel a pending flip that didn't last.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun Flow<ConnectivityStatus>.debounceConnectivity(
    offlineDelay: Duration,
    onlineDelay: Duration,
): Flow<ConnectivityStatus> = flow {
    var lastEmitted: ConnectivityStatus? = null
    val debounced = transformLatest { status ->
        val previous = lastEmitted
        val wait = when {
            previous == null || previous.isOnline == status.isOnline -> Duration.ZERO
            status.isOnline -> onlineDelay
            else -> offlineDelay
        }
        if (wait.isPositive()) delay(wait)
        lastEmitted = status
        emit(status)
    }
    emitAll(debounced.distinctUntilChanged())
}

/** Applies [ConnectivityConfig.offlineDelay] and [ConnectivityConfig.onlineDelay]. */
public fun Flow<ConnectivityStatus>.debounceConnectivity(config: ConnectivityConfig): Flow<ConnectivityStatus> =
    debounceConnectivity(config.offlineDelay, config.onlineDelay)

/** The full pipeline used by the Android monitor: events to statuses, then debounced. */
public fun Flow<NetworkEvent>.connectivityStatus(
    config: ConnectivityConfig = ConnectivityConfig.Default,
): Flow<ConnectivityStatus> =
    toConnectivityStatus(config.requireValidatedInternet).debounceConnectivity(config)
