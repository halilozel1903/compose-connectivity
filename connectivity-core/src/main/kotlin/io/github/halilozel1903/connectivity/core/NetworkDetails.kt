package io.github.halilozel1903.connectivity.core

/**
 * What is known about the current default network.
 *
 * @property transports Every transport the network uses. A VPN usually reports [Transport.Vpn]
 *   together with the underlying transport.
 * @property hasInternet The network claims it can reach the internet (`NET_CAPABILITY_INTERNET`).
 * @property isValidated The system actually verified internet access (`NET_CAPABILITY_VALIDATED`).
 *   `false` behind captive portals or on networks that block the system's connectivity check.
 * @property isMetered Traffic may cost the user money or count against a data plan.
 * @property isSuspended The network exists but can't pass traffic right now, for example cellular
 *   data during a voice call on some devices (API 28+ only).
 */
public data class NetworkDetails(
    public val transports: Set<Transport>,
    public val hasInternet: Boolean = true,
    public val isValidated: Boolean = true,
    public val isMetered: Boolean = false,
    public val isSuspended: Boolean = false,
) {
    /** The transport worth showing to users: VPN first, then wired, Wi-Fi and cellular. */
    public val primaryTransport: Transport
        get() = TransportPriority.firstOrNull { it in transports } ?: Transport.Other

    public val isVpn: Boolean get() = Transport.Vpn in transports

    public companion object {
        private val TransportPriority = listOf(
            Transport.Vpn,
            Transport.Ethernet,
            Transport.Wifi,
            Transport.Cellular,
            Transport.Bluetooth,
            Transport.Other,
        )

        /** An unmetered, validated Wi-Fi network. */
        public val Wifi: NetworkDetails = NetworkDetails(setOf(Transport.Wifi))

        /** A metered, validated cellular network. */
        public val Cellular: NetworkDetails = NetworkDetails(setOf(Transport.Cellular), isMetered = true)

        /** An unmetered, validated wired network. */
        public val Ethernet: NetworkDetails = NetworkDetails(setOf(Transport.Ethernet))

        /** A VPN running over Wi-Fi. */
        public val VpnOverWifi: NetworkDetails = NetworkDetails(setOf(Transport.Vpn, Transport.Wifi))

        /** Used when a network is known to exist but its capabilities could not be read. */
        public val Unknown: NetworkDetails = NetworkDetails(emptySet(), isValidated = false)
    }
}
