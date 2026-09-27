package io.github.halilozel1903.connectivity.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tunes how raw network events become the status your UI sees.
 *
 * @property offlineDelay How long the device must stay offline before it is reported. Short drops,
 *   such as a Wi-Fi to cellular handover, are swallowed.
 * @property onlineDelay How long the device must stay online after being offline before it is
 *   reported, so a flaky network doesn't flash "Back online" on and off.
 * @property requireValidatedInternet Report networks whose internet access the system couldn't
 *   verify (captive portals) as offline. Off by default because some networks block the system's
 *   check even though the internet works fine.
 */
public data class ConnectivityConfig(
    public val offlineDelay: Duration = 1.seconds,
    public val onlineDelay: Duration = 500.milliseconds,
    public val requireValidatedInternet: Boolean = false,
) {
    init {
        require(!offlineDelay.isNegative()) { "offlineDelay must not be negative" }
        require(!onlineDelay.isNegative()) { "onlineDelay must not be negative" }
    }

    public companion object {
        public val Default: ConnectivityConfig = ConnectivityConfig()

        /** Reports every change as it happens. Handy for tests and debug screens. */
        public val Immediate: ConnectivityConfig = ConnectivityConfig(Duration.ZERO, Duration.ZERO)
    }
}
