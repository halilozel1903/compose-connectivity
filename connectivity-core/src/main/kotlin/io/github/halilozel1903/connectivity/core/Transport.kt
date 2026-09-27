package io.github.halilozel1903.connectivity.core

/** The kind of link a network runs over. A network can use several, for example VPN over Wi-Fi. */
public enum class Transport(public val label: String) {
    Wifi("Wi-Fi"),
    Cellular("Cellular"),
    Ethernet("Ethernet"),
    Vpn("VPN"),
    Bluetooth("Bluetooth"),
    Other("Other"),
}
