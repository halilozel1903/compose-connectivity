package io.github.halilozel1903.connectivity.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.connectivity.ConnectivityBanner
import io.github.halilozel1903.connectivity.ConnectivityBannerDefaults
import io.github.halilozel1903.connectivity.LocalConnectivityMonitor
import io.github.halilozel1903.connectivity.OfflineAware
import io.github.halilozel1903.connectivity.connectivityMonitor
import io.github.halilozel1903.connectivity.core.ConnectivityStatus
import io.github.halilozel1903.connectivity.core.FakeConnectivityMonitor
import io.github.halilozel1903.connectivity.core.NetworkDetails
import io.github.halilozel1903.connectivity.core.describe
import io.github.halilozel1903.connectivity.offlineAware
import io.github.halilozel1903.connectivity.rememberConnectivityMonitor
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Emulator networking can't be toggled reliably, so `scripts/screenshots.sh` starts the app with
 * `--es fakeStatus <scene>` to show a simulated status:
 *
 * - `offline`: no network
 * - `online`: unmetered Wi-Fi
 * - `metered`: metered cellular
 * - `vpn`: VPN over Wi-Fi
 * - `reconnected`: starts offline and comes back, keeping "Back online" visible for the screenshot
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val scene = intent.getStringExtra(EXTRA_FAKE_STATUS)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SampleApp(scene)
            }
        }
    }

    companion object {
        const val EXTRA_FAKE_STATUS = "fakeStatus"
    }
}

private fun initialStatusFor(scene: String?): ConnectivityStatus? = when (scene) {
    "offline", "reconnected" -> ConnectivityStatus.Unavailable
    "online" -> ConnectivityStatus.Available(NetworkDetails.Wifi)
    "metered" -> ConnectivityStatus.Available(NetworkDetails.Cellular)
    "vpn" -> ConnectivityStatus.Available(NetworkDetails.VpnOverWifi)
    else -> null
}

@Composable
private fun SampleApp(scene: String?) {
    val fakeStart = initialStatusFor(scene)
    val fake = remember { FakeConnectivityMonitor(fakeStart ?: ConnectivityStatus.Available(NetworkDetails.Wifi)) }
    var simulate by rememberSaveable { mutableStateOf(fakeStart != null) }
    val live = LocalContext.current.connectivityMonitor()

    if (scene == "reconnected") {
        LaunchedEffect(Unit) {
            delay(1_500)
            fake.goOnline()
        }
    }

    CompositionLocalProvider(LocalConnectivityMonitor provides if (simulate) fake else live) {
        NewsScreen(
            simulated = simulate,
            // Keep "Back online" on screen long enough for the screenshot.
            backOnlineDuration = if (scene == "reconnected") 1.minutes else ConnectivityBannerDefaults.BackOnlineDuration,
            onLive = { simulate = false },
            onSimulate = { status ->
                fake.setStatus(status)
                simulate = true
            },
        )
    }
}

private data class Article(val source: String, val title: String, val savedOffline: Boolean)

private val articles = listOf(
    Article("Tech Daily", "Why your app should stop flickering when the Wi-Fi drops", savedOffline = true),
    Article("The Commute", "Tunnels, trains and the art of the offline-first app", savedOffline = true),
    Article("Design Notes", "Banners, snackbars or dialogs? Telling users they're offline", savedOffline = false),
    Article("Data Plan", "Metered networks: download less, earn more trust", savedOffline = false),
    Article("Weekend", "Five hikes with zero signal and great views", savedOffline = false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewsScreen(
    simulated: Boolean,
    backOnlineDuration: Duration,
    onLive: () -> Unit,
    onSimulate: (ConnectivityStatus) -> Unit,
) {
    val monitor = rememberConnectivityMonitor()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Daily Brief") }) },
    ) { padding ->
        OfflineAware(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            monitor = monitor,
            banner = { ConnectivityBanner(monitor = monitor, backOnlineDuration = backOnlineDuration) },
        ) { status ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ConnectionCard(status, simulated, onLive, onSimulate) }
                item { Actions(status) }
                items(articles) { article -> ArticleCard(article, status) }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    status: ConnectivityStatus,
    simulated: Boolean,
    onLive: () -> Unit,
    onSimulate: (ConnectivityStatus) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (status) {
                        is ConnectivityStatus.Available -> "Online"
                        is ConnectivityStatus.Losing -> "Losing connection"
                        ConnectivityStatus.Unavailable -> "Offline"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (status.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (simulated) "Simulated" else "Live",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(status.describe(), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Fact("Transport", status.transport?.label ?: "None")
            Fact("Metered", if (status.isOnline) yesNo(status.isMetered) else "–")
            Fact("Internet verified", if (status.isOnline) yesNo(status.isValidated) else "–")
            HorizontalDivider()
            SimulationChips(status, simulated, onLive, onSimulate)
        }
    }
}

@Composable
private fun SimulationChips(
    status: ConnectivityStatus,
    simulated: Boolean,
    onLive: () -> Unit,
    onSimulate: (ConnectivityStatus) -> Unit,
) {
    val wifi = ConnectivityStatus.Available(NetworkDetails.Wifi)
    val cellular = ConnectivityStatus.Available(NetworkDetails.Cellular)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !simulated, onClick = onLive, label = { Text("Live") })
        FilterChip(
            selected = simulated && status == ConnectivityStatus.Unavailable,
            onClick = { onSimulate(ConnectivityStatus.Unavailable) },
            label = { Text("Offline") },
        )
        FilterChip(selected = simulated && status == wifi, onClick = { onSimulate(wifi) }, label = { Text("Wi-Fi") })
        FilterChip(
            selected = simulated && status == cellular,
            onClick = { onSimulate(cellular) },
            label = { Text("Cellular") },
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun yesNo(value: Boolean) = if (value) "Yes" else "No"

@Composable
private fun Actions(status: ConnectivityStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {}, enabled = status.isOnline, modifier = Modifier.weight(1f)) {
                Text("Refresh")
            }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).offlineAware(status)) {
                Text(if (status.isMetered) "Download on Wi-Fi" else "Download all")
            }
        }
        if (status.isMetered) {
            Text(
                "You're on a metered connection: videos won't autoplay and images load in lower quality.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleCard(article: Article, status: ConnectivityStatus) {
    val readable = status.isOnline || article.savedOffline
    // Saved articles stay readable offline; the rest are dimmed and can't be opened.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (readable) Modifier else Modifier.offlineAware(status)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                article.source.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(article.title, style = MaterialTheme.typography.titleMedium)
            if (article.savedOffline) {
                Text(
                    "Saved for offline reading",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
