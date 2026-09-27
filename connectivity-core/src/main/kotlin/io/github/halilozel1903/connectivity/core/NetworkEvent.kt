package io.github.halilozel1903.connectivity.core

/**
 * A platform independent mirror of `ConnectivityManager.NetworkCallback` events for the default
 * network. The Android module translates callbacks into these; everything after that is pure Kotlin.
 */
public sealed interface NetworkEvent {
    /** A network became the default. [details] may be `null` if its capabilities weren't readable yet. */
    public data class Available(public val networkId: Long, public val details: NetworkDetails?) : NetworkEvent

    public data class CapabilitiesChanged(public val networkId: Long, public val details: NetworkDetails) : NetworkEvent

    public data class Losing(public val networkId: Long, public val maxMsToLive: Int) : NetworkEvent

    public data class Lost(public val networkId: Long) : NetworkEvent

    /** There is no default network at all, for example when the monitor starts in airplane mode. */
    public data object NoNetwork : NetworkEvent
}

/**
 * What is known about the default network after a sequence of [NetworkEvent]s.
 *
 * Callbacks for different networks can interleave while the default network changes (the new one
 * becomes available before the old one is reported lost), so events are matched by network id.
 */
public data class DefaultNetworkState(
    public val networkId: Long? = null,
    public val details: NetworkDetails? = null,
    public val losingMs: Int? = null,
) {
    public fun reduce(event: NetworkEvent): DefaultNetworkState = when (event) {
        is NetworkEvent.Available -> DefaultNetworkState(
            networkId = event.networkId,
            details = event.details ?: details.takeIf { networkId == event.networkId },
            losingMs = null,
        )
        is NetworkEvent.CapabilitiesChanged -> DefaultNetworkState(
            networkId = event.networkId,
            details = event.details,
            losingMs = losingMs.takeIf { networkId == event.networkId },
        )
        is NetworkEvent.Losing -> if (event.networkId == networkId) copy(losingMs = event.maxMsToLive) else this
        is NetworkEvent.Lost -> if (event.networkId == networkId) Empty else this
        NetworkEvent.NoNetwork -> Empty
    }

    /**
     * Maps this state to a [ConnectivityStatus].
     *
     * @param requireValidatedInternet Treat networks without validated internet access as offline.
     */
    public fun toStatus(requireValidatedInternet: Boolean = false): ConnectivityStatus {
        if (networkId == null) return ConnectivityStatus.Unavailable
        val current = details ?: NetworkDetails.Unknown
        val usable = current.hasInternet &&
            !current.isSuspended &&
            (!requireValidatedInternet || current.isValidated)
        return when {
            !usable -> ConnectivityStatus.Unavailable
            losingMs != null -> ConnectivityStatus.Losing(current, losingMs)
            else -> ConnectivityStatus.Available(current)
        }
    }

    public companion object {
        public val Empty: DefaultNetworkState = DefaultNetworkState()
    }
}
