package io.github.halilozel1903.connectivity.core

/**
 * The connectivity of the device's default network, the one apps use unless they ask otherwise.
 */
public sealed interface ConnectivityStatus {
    /** Details of the default network, or `null` when offline. */
    public val details: NetworkDetails?

    /** There is a usable default network. [Losing] counts as online: it still works for now. */
    public val isOnline: Boolean get() = details != null

    public val isOffline: Boolean get() = !isOnline

    /** The primary transport, or `null` when offline. */
    public val transport: Transport? get() = details?.primaryTransport

    /** `true` only when online over a metered network. */
    public val isMetered: Boolean get() = details?.isMetered == true

    /** `true` only when online and the system verified internet access. */
    public val isValidated: Boolean get() = details?.isValidated == true

    /** A default network is up. */
    public data class Available(override val details: NetworkDetails) : ConnectivityStatus

    /**
     * The default network is about to go away, typically while the device hands over from Wi-Fi to
     * cellular. It still works for about [maxMsToLive] milliseconds.
     */
    public data class Losing(
        override val details: NetworkDetails,
        public val maxMsToLive: Int,
    ) : ConnectivityStatus

    /** There is no usable default network. */
    public data object Unavailable : ConnectivityStatus {
        override val details: NetworkDetails? get() = null
    }
}

/**
 * A short, human readable summary such as `Wi-Fi · unmetered · internet verified` or `Offline`.
 */
public fun ConnectivityStatus.describe(): String {
    val details = details ?: return "Offline"
    val parts = buildList {
        val transports = details.transports.sortedBy { it.ordinal }.joinToString(" + ") { it.label }
        add(transports.ifEmpty { "Unknown network" })
        add(if (details.isMetered) "metered" else "unmetered")
        add(if (details.isValidated) "internet verified" else "not verified")
    }
    val summary = parts.joinToString(" · ")
    return if (this is ConnectivityStatus.Losing) "Losing $summary" else summary
}
