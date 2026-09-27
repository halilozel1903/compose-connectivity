package io.github.halilozel1903.connectivity

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import io.github.halilozel1903.connectivity.core.ConnectivityMonitor
import io.github.halilozel1903.connectivity.core.ConnectivityStatus

/**
 * Wraps a screen with a [ConnectivityBanner] on top and hands the current status to [content].
 *
 * ```kotlin
 * OfflineAware { status ->
 *     FeedScreen(canRefresh = status.isOnline, saveData = status.isMetered)
 * }
 * ```
 *
 * @param banner The banner to show above the content. Pass `{}` to hide it.
 */
@Composable
public fun OfflineAware(
    modifier: Modifier = Modifier,
    monitor: ConnectivityMonitor = rememberConnectivityMonitor(),
    banner: @Composable () -> Unit = { ConnectivityBanner(monitor = monitor) },
    content: @Composable (status: ConnectivityStatus) -> Unit,
) {
    val status by rememberConnectivityStatus(monitor)
    Column(modifier) {
        banner()
        content(status)
    }
}

/**
 * Dims this element and, when [blockInput] is `true`, swallows touches on it while [status] is
 * offline. Good for actions that need the network, such as "Refresh" or "Send".
 *
 * Touch blocking is visual help only: accessibility services can still activate the element, so
 * also pass `enabled = status.isOnline` to components that support it.
 */
public fun Modifier.offlineAware(
    status: ConnectivityStatus,
    offlineAlpha: Float = 0.38f,
    blockInput: Boolean = true,
): Modifier {
    if (status.isOnline) return this
    val dimmed = alpha(offlineAlpha)
    return if (blockInput) {
        dimmed.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    } else {
        dimmed
    }
}
