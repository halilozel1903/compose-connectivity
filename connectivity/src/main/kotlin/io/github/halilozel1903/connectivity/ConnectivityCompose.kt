package io.github.halilozel1903.connectivity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.halilozel1903.connectivity.core.ConnectivityMonitor
import io.github.halilozel1903.connectivity.core.ConnectivityStatus
import io.github.halilozel1903.connectivity.core.FakeConnectivityMonitor

/**
 * Overrides the monitor used by [rememberConnectivityMonitor] and every component of this library
 * below it. Provide a [FakeConnectivityMonitor] in tests, previews or screenshot builds.
 *
 * ```kotlin
 * CompositionLocalProvider(LocalConnectivityMonitor provides FakeConnectivityMonitor(ConnectivityStatus.Unavailable)) {
 *     MyScreen()
 * }
 * ```
 */
public val LocalConnectivityMonitor: ProvidableCompositionLocal<ConnectivityMonitor?> =
    staticCompositionLocalOf { null }

/**
 * The monitor to use here: [LocalConnectivityMonitor] if provided, a fake online monitor in
 * `@Preview`, otherwise the process wide [AndroidConnectivityMonitor].
 */
@Composable
public fun rememberConnectivityMonitor(): ConnectivityMonitor {
    val provided = LocalConnectivityMonitor.current
    val inPreview = LocalInspectionMode.current
    val context = LocalContext.current.applicationContext
    return remember(provided, inPreview, context) {
        when {
            provided != null -> provided
            inPreview -> FakeConnectivityMonitor()
            else -> AndroidConnectivityMonitor.getInstance(context)
        }
    }
}

/**
 * The current, debounced connectivity status. Listening stops while the lifecycle is below
 * `STARTED`, so the network callback is released when the app is in the background.
 *
 * ```kotlin
 * val status by rememberConnectivityStatus()
 * Button(onClick = ::sync, enabled = status.isOnline) { Text("Sync") }
 * ```
 */
@Composable
public fun rememberConnectivityStatus(
    monitor: ConnectivityMonitor = rememberConnectivityMonitor(),
): State<ConnectivityStatus> = monitor.status.collectAsStateWithLifecycle()
