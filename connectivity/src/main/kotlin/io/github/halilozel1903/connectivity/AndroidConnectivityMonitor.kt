package io.github.halilozel1903.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.github.halilozel1903.connectivity.core.ConnectivityConfig
import io.github.halilozel1903.connectivity.core.ConnectivityMonitor
import io.github.halilozel1903.connectivity.core.ConnectivityStatus
import io.github.halilozel1903.connectivity.core.DefaultNetworkState
import io.github.halilozel1903.connectivity.core.NetworkDetails
import io.github.halilozel1903.connectivity.core.NetworkEvent
import io.github.halilozel1903.connectivity.core.Transport
import io.github.halilozel1903.connectivity.core.connectivityStatus
import io.github.halilozel1903.connectivity.core.toConnectivityStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * A [ConnectivityMonitor] backed by `ConnectivityManager.registerDefaultNetworkCallback`.
 *
 * The callback is registered only while [status] has subscribers (plus [stopTimeoutMillis]), so an
 * idle monitor costs nothing. Most apps should use the shared instance from [getInstance] or
 * [Context.connectivityMonitor] instead of creating their own.
 *
 * ```kotlin
 * val monitor = AndroidConnectivityMonitor.getInstance(context)
 * monitor.status.collect { status -> if (status.isOffline) showOfflineUi() }
 * ```
 *
 * @param context Any context; the application context is used.
 * @param config Debounce delays and whether unvalidated networks count as online.
 * @param scope Where the shared status flow lives.
 * @param stopTimeoutMillis How long to keep listening after the last subscriber left, so that
 *   configuration changes don't re-register the callback.
 */
public class AndroidConnectivityMonitor(
    context: Context,
    public val config: ConnectivityConfig = ConnectivityConfig.Default,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    stopTimeoutMillis: Long = 5_000,
) : ConnectivityMonitor {

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** Default network events as reported by the system, without any processing. */
    public val events: Flow<NetworkEvent> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            // No connectivity service (for example in some test environments): report offline.
            trySend(NetworkEvent.NoNetwork)
            awaitClose()
            return@callbackFlow
        }

        // registerDefaultNetworkCallback never reports "no network", so start from a snapshot.
        trySend(manager.currentEvent())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkEvent.Available(network.id, manager.detailsOf(network)))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(NetworkEvent.CapabilitiesChanged(network.id, networkCapabilities.toDetails()))
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(NetworkEvent.Losing(network.id, maxMsToLive))
            }

            override fun onLost(network: Network) {
                trySend(NetworkEvent.Lost(network.id))
            }
        }

        val registered = try {
            manager.registerDefaultNetworkCallback(callback)
            true
        } catch (e: RuntimeException) {
            // SecurityException without ACCESS_NETWORK_STATE, or TooManyRequestsException when the
            // app holds too many callbacks. Keep the snapshot instead of crashing the collector.
            false
        }

        awaitClose {
            if (registered) {
                runCatching { manager.unregisterNetworkCallback(callback) }
            }
        }
    }.buffer(Channel.UNLIMITED) // every event matters to the reducer: never drop one

    /** Statuses without debouncing, useful for logging. */
    public val rawStatus: Flow<ConnectivityStatus> = events.toConnectivityStatus(config.requireValidatedInternet)

    override val status: StateFlow<ConnectivityStatus> = events
        .connectivityStatus(config)
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis),
            initialValue = currentStatus(),
        )

    /** Reads the status synchronously, without debouncing. */
    public fun currentStatus(): ConnectivityStatus {
        val event = connectivityManager?.currentEvent() ?: NetworkEvent.NoNetwork
        return DefaultNetworkState.Empty.reduce(event).toStatus(config.requireValidatedInternet)
    }

    public companion object {
        @Volatile
        private var shared: AndroidConnectivityMonitor? = null

        /** The process wide monitor with [ConnectivityConfig.Default]. */
        public fun getInstance(context: Context): AndroidConnectivityMonitor =
            shared ?: synchronized(this) {
                shared ?: AndroidConnectivityMonitor(context.applicationContext).also { shared = it }
            }
    }
}

/** The process wide [AndroidConnectivityMonitor]. */
public fun Context.connectivityMonitor(): ConnectivityMonitor = AndroidConnectivityMonitor.getInstance(this)

private val Network.id: Long get() = networkHandle

private fun ConnectivityManager.currentEvent(): NetworkEvent {
    val network: Network? = try {
        activeNetwork
    } catch (e: SecurityException) {
        null
    }
    return if (network == null) NetworkEvent.NoNetwork else NetworkEvent.Available(network.id, detailsOf(network))
}

private fun ConnectivityManager.detailsOf(network: Network): NetworkDetails? = try {
    getNetworkCapabilities(network)?.toDetails()
} catch (e: SecurityException) {
    null
}

internal fun NetworkCapabilities.toDetails(): NetworkDetails {
    val transports = buildSet {
        if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(Transport.Wifi)
        if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(Transport.Cellular)
        if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(Transport.Ethernet)
        if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add(Transport.Vpn)
        if (hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add(Transport.Bluetooth)
        if (isEmpty()) add(Transport.Other)
    }
    val notMetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
        (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
            )
    val suspended = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
    return NetworkDetails(
        transports = transports,
        hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        isMetered = !notMetered,
        isSuspended = suspended,
    )
}
