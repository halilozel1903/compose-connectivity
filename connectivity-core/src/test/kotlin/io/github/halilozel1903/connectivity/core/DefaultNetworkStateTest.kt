package io.github.halilozel1903.connectivity.core

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultNetworkStateTest {
    private fun statusAfter(vararg events: NetworkEvent, requireValidated: Boolean = false): ConnectivityStatus =
        events.fold(DefaultNetworkState.Empty) { state, event -> state.reduce(event) }.toStatus(requireValidated)

    @Test
    fun `no network is unavailable`() {
        assertEquals(ConnectivityStatus.Unavailable, DefaultNetworkState.Empty.toStatus())
        assertEquals(ConnectivityStatus.Unavailable, statusAfter(NetworkEvent.NoNetwork))
    }

    @Test
    fun `available network with capabilities`() {
        val status = statusAfter(NetworkEvent.Available(1, NetworkDetails.Wifi))

        assertEquals(ConnectivityStatus.Available(NetworkDetails.Wifi), status)
        assertEquals(Transport.Wifi, status.transport)
        assertTrue(status.isOnline)
        assertTrue(status.isValidated)
    }

    @Test
    fun `capabilities arriving after available fill in the details`() {
        val status = statusAfter(
            NetworkEvent.Available(1, null),
            NetworkEvent.CapabilitiesChanged(1, NetworkDetails.Cellular),
        )

        assertEquals(ConnectivityStatus.Available(NetworkDetails.Cellular), status)
        assertTrue(status.isMetered)
    }

    @Test
    fun `available without capabilities is online with unknown details`() {
        assertEquals(
            ConnectivityStatus.Available(NetworkDetails.Unknown),
            statusAfter(NetworkEvent.Available(1, null)),
        )
    }

    @Test
    fun `handover keeps the device online when the old network is lost late`() {
        val status = statusAfter(
            NetworkEvent.Available(1, NetworkDetails.Wifi),
            NetworkEvent.Losing(1, 3_000),
            NetworkEvent.Available(2, NetworkDetails.Cellular),
            NetworkEvent.Lost(1),
        )

        assertEquals(ConnectivityStatus.Available(NetworkDetails.Cellular), status)
    }

    @Test
    fun `losing and lost for the current network`() {
        val wifi = NetworkEvent.Available(7, NetworkDetails.Wifi)

        assertEquals(ConnectivityStatus.Losing(NetworkDetails.Wifi, 500), statusAfter(wifi, NetworkEvent.Losing(7, 500)))
        assertEquals(ConnectivityStatus.Unavailable, statusAfter(wifi, NetworkEvent.Lost(7)))
    }

    @Test
    fun `losing for another network is ignored`() {
        val status = statusAfter(NetworkEvent.Available(1, NetworkDetails.Wifi), NetworkEvent.Losing(2, 500))

        assertEquals(ConnectivityStatus.Available(NetworkDetails.Wifi), status)
    }

    @Test
    fun `losing survives a capabilities update of the same network`() {
        val status = statusAfter(
            NetworkEvent.Available(1, NetworkDetails.Wifi),
            NetworkEvent.Losing(1, 900),
            NetworkEvent.CapabilitiesChanged(1, NetworkDetails.Wifi.copy(isValidated = false)),
        )

        assertEquals(ConnectivityStatus.Losing(NetworkDetails.Wifi.copy(isValidated = false), 900), status)
    }

    @Test
    fun `networks without internet or suspended are offline`() {
        val noInternet = NetworkDetails(setOf(Transport.Wifi), hasInternet = false)
        val suspended = NetworkDetails.Cellular.copy(isSuspended = true)

        assertEquals(ConnectivityStatus.Unavailable, statusAfter(NetworkEvent.Available(1, noInternet)))
        assertEquals(ConnectivityStatus.Unavailable, statusAfter(NetworkEvent.Available(1, suspended)))
    }

    @Test
    fun `captive portal is online unless validation is required`() {
        val portal = NetworkDetails.Wifi.copy(isValidated = false)
        val event = NetworkEvent.Available(1, portal)

        assertEquals(ConnectivityStatus.Available(portal), statusAfter(event))
        assertEquals(ConnectivityStatus.Unavailable, statusAfter(event, requireValidated = true))
    }

    @Test
    fun `event flow folds into distinct statuses`() = runTest {
        val statuses = flowOf(
            NetworkEvent.NoNetwork,
            NetworkEvent.Available(1, null),
            NetworkEvent.CapabilitiesChanged(1, NetworkDetails.Wifi),
            NetworkEvent.CapabilitiesChanged(1, NetworkDetails.Wifi),
            NetworkEvent.Lost(1),
        ).toConnectivityStatus().toList()

        assertEquals(
            listOf(
                ConnectivityStatus.Unavailable,
                ConnectivityStatus.Available(NetworkDetails.Unknown),
                ConnectivityStatus.Available(NetworkDetails.Wifi),
                ConnectivityStatus.Unavailable,
            ),
            statuses,
        )
    }

    @Test
    fun `primary transport prefers vpn then ethernet then wifi`() {
        assertEquals(Transport.Vpn, NetworkDetails.VpnOverWifi.primaryTransport)
        assertEquals(Transport.Ethernet, NetworkDetails(setOf(Transport.Wifi, Transport.Ethernet)).primaryTransport)
        assertEquals(Transport.Other, NetworkDetails.Unknown.primaryTransport)
        assertTrue(NetworkDetails.VpnOverWifi.isVpn)
    }

    @Test
    fun `describe is readable`() {
        assertEquals("Offline", ConnectivityStatus.Unavailable.describe())
        assertEquals("Wi-Fi · unmetered · internet verified", ConnectivityStatus.Available(NetworkDetails.Wifi).describe())
        assertEquals("Cellular · metered · internet verified", ConnectivityStatus.Available(NetworkDetails.Cellular).describe())
        assertEquals("Wi-Fi + VPN · unmetered · internet verified", ConnectivityStatus.Available(NetworkDetails.VpnOverWifi).describe())
        assertEquals("Losing Wi-Fi · unmetered · internet verified", ConnectivityStatus.Losing(NetworkDetails.Wifi, 1).describe())
    }
}
