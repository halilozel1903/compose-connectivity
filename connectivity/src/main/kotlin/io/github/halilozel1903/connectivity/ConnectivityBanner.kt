package io.github.halilozel1903.connectivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.halilozel1903.connectivity.core.ConnectivityBannerState
import io.github.halilozel1903.connectivity.core.ConnectivityMonitor
import io.github.halilozel1903.connectivity.core.toBannerState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Colors of [ConnectivityBanner]. */
@Immutable
public class ConnectivityBannerColors(
    public val offlineContainer: Color,
    public val offlineContent: Color,
    public val onlineContainer: Color,
    public val onlineContent: Color,
)

public object ConnectivityBannerDefaults {
    /** How long "Back online" stays before the banner hides itself. */
    public val BackOnlineDuration: Duration = 3.seconds

    public const val OfflineText: String = "You're offline"

    public const val BackOnlineText: String = "Back online"

    /** Snackbar-like colors for offline and a calm green for back online. */
    @Composable
    public fun colors(
        offlineContainer: Color = MaterialTheme.colorScheme.inverseSurface,
        offlineContent: Color = MaterialTheme.colorScheme.inverseOnSurface,
        onlineContainer: Color = Color(0xFF1B873F),
        onlineContent: Color = Color.White,
    ): ConnectivityBannerColors = ConnectivityBannerColors(
        offlineContainer = offlineContainer,
        offlineContent = offlineContent,
        onlineContainer = onlineContainer,
        onlineContent = onlineContent,
    )
}

/**
 * A full width banner that slides in with "You're offline" when the connection drops, turns into
 * "Back online" when it returns and hides itself after [backOnlineDuration].
 *
 * Put it at the top of a screen, below the app bar, or use [OfflineAware] which does that for you.
 * The status comes from [monitor], which is already debounced, so short drops don't flash it.
 */
@Composable
public fun ConnectivityBanner(
    modifier: Modifier = Modifier,
    monitor: ConnectivityMonitor = rememberConnectivityMonitor(),
    backOnlineDuration: Duration = ConnectivityBannerDefaults.BackOnlineDuration,
    colors: ConnectivityBannerColors = ConnectivityBannerDefaults.colors(),
    offlineText: String = ConnectivityBannerDefaults.OfflineText,
    backOnlineText: String = ConnectivityBannerDefaults.BackOnlineText,
) {
    val initial = remember(monitor) { ConnectivityBannerState.initial(monitor.status.value) }
    val states = remember(monitor, backOnlineDuration) { monitor.status.toBannerState(backOnlineDuration) }
    val state by states.collectAsStateWithLifecycle(initialValue = initial)
    ConnectivityBanner(
        state = state,
        modifier = modifier,
        colors = colors,
        offlineText = offlineText,
        backOnlineText = backOnlineText,
    )
}

/**
 * The stateless banner: shows whatever [state] says, animated. Use it when you compute
 * [ConnectivityBannerState] yourself, for example in a ViewModel.
 */
@Composable
public fun ConnectivityBanner(
    state: ConnectivityBannerState,
    modifier: Modifier = Modifier,
    colors: ConnectivityBannerColors = ConnectivityBannerDefaults.colors(),
    offlineText: String = ConnectivityBannerDefaults.OfflineText,
    backOnlineText: String = ConnectivityBannerDefaults.BackOnlineText,
) {
    // While the banner animates out, keep showing the last visible message instead of "Hidden".
    val lastVisible = remember { LastVisible() }
    val shown = if (state.isVisible) state else lastVisible.state
    SideEffect {
        if (state.isVisible) lastVisible.state = state
    }

    AnimatedVisibility(
        visible = state.isVisible,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        val offline = shown == ConnectivityBannerState.Offline
        val container by animateColorAsState(
            targetValue = if (offline) colors.offlineContainer else colors.onlineContainer,
            label = "bannerContainer",
        )
        val content by animateColorAsState(
            targetValue = if (offline) colors.offlineContent else colors.onlineContent,
            label = "bannerContent",
        )
        Surface(
            color = container,
            contentColor = content,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(color = content, pulsing = offline)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (offline) offlineText else backOnlineText,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private class LastVisible {
    var state: ConnectivityBannerState = ConnectivityBannerState.Offline
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "statusDot")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
            label = "statusDotAlpha",
        )
        animated
    } else {
        1f
    }
    Box(
        Modifier
            .size(8.dp)
            .drawBehind { drawCircle(color = color, alpha = alpha) },
    )
}
