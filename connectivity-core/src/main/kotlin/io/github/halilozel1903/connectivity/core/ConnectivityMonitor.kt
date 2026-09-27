package io.github.halilozel1903.connectivity.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Something that knows whether the device is online. The Android module provides the real
 * implementation; [FakeConnectivityMonitor] is for tests, previews and screenshots.
 */
public interface ConnectivityMonitor {
    /** The current, debounced status. Collect it to keep it fresh. */
    public val status: StateFlow<ConnectivityStatus>
}

/** A [ConnectivityMonitor] you drive by hand. */
public class FakeConnectivityMonitor(
    initial: ConnectivityStatus = ConnectivityStatus.Available(NetworkDetails.Wifi),
) : ConnectivityMonitor {
    private val mutableStatus = MutableStateFlow(initial)

    override val status: StateFlow<ConnectivityStatus> = mutableStatus.asStateFlow()

    public fun setStatus(status: ConnectivityStatus) {
        mutableStatus.value = status
    }

    public fun goOffline() {
        setStatus(ConnectivityStatus.Unavailable)
    }

    public fun goOnline(details: NetworkDetails = NetworkDetails.Wifi) {
        setStatus(ConnectivityStatus.Available(details))
    }
}
