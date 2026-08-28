package io.github.abhishekcs194.printdeck.print.ipp.discovery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * These messages are the last thing a stuck user reads. A confident wrong cause
 * is worse than no cause at all, because they will act on it — so the tests
 * check both that a real cause is named and that an unproven one is not.
 */
class DiscoveryDiagnosticsTest {

    private val home = Ipv4Subnet.parse("192.168.100.0/24")

    @Test
    fun `finding printers needs no explanation`() {
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(
                hasNetwork = true,
                localSubnets = listOf(home),
                printersFound = 2,
            ),
        )
        assertThat(diagnosis).isInstanceOf(DiscoveryDiagnosis.Found::class.java)
        assertThat(diagnosis.headline).isEqualTo("2 printers found")
        assertThat(diagnosis.suggestions).isEmpty()
    }

    @Test
    fun `a single printer is described in the singular`() {
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(true, listOf(home), printersFound = 1),
        )
        assertThat(diagnosis.headline).isEqualTo("1 printer found")
    }

    @Test
    fun `no network is reported as such rather than as a missing printer`() {
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(hasNetwork = false, localSubnets = emptyList()),
        )
        assertThat(diagnosis).isEqualTo(DiscoveryDiagnosis.NotConnected)
        assertThat(diagnosis.suggestions).isNotEmpty()
    }

    @Test
    fun `a router on another network is named, with the fix`() {
        // The real household case: a second access point sharing one Wi-Fi name,
        // with its own range behind it.
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(
                hasNetwork = true,
                localSubnets = listOf(home),
                foreignRoutersReachable = listOf("192.168.101.1"),
            ),
        )

        assertThat(diagnosis).isInstanceOf(DiscoveryDiagnosis.PrinterOnAnotherNetwork::class.java)
        assertThat(diagnosis.explanation).contains("192.168.101.1")
        assertThat(diagnosis.explanation).contains("192.168.100.0/24")
        // Must say plainly that waiting or retrying is not the answer.
        assertThat(diagnosis.explanation).contains("searching for longer will not help")
        assertThat(diagnosis.suggestions.joinToString()).contains("bridge")
    }

    @Test
    fun `several foreign routers are all listed`() {
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(
                hasNetwork = true,
                localSubnets = listOf(home),
                foreignRoutersReachable = listOf("192.168.101.1", "10.42.0.1"),
            ),
        )
        assertThat(diagnosis.explanation).contains("192.168.101.1")
        assertThat(diagnosis.explanation).contains("10.42.0.1")
    }

    @Test
    fun `with no evidence, causes are offered rather than asserted`() {
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(hasNetwork = true, localSubnets = listOf(home)),
        )

        assertThat(diagnosis).isInstanceOf(DiscoveryDiagnosis.NotFoundOnThisNetwork::class.java)
        assertThat(diagnosis.headline).contains("192.168.100.0/24")

        // Client isolation is a real cause but cannot be proven from here, so it
        // is listed as something to check - never stated as the diagnosis.
        val advice = diagnosis.suggestions.joinToString()
        assertThat(advice).contains("isolation")
        assertThat(diagnosis.explanation).doesNotContain("isolation")
    }

    @Test
    fun `the two-access-points-one-name trap is spelled out`() {
        // Nobody guesses this on their own; it has to be said.
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(hasNetwork = true, localSubnets = listOf(home)),
        )
        assertThat(diagnosis.suggestions.joinToString())
            .contains("share one Wi-Fi name")
    }

    @Test
    fun `hard evidence outranks guesswork`() {
        // A reachable foreign router is proof; it must win over the generic case.
        val diagnosis = DiscoveryDiagnostics.diagnose(
            DiscoveryDiagnostics.Evidence(
                hasNetwork = true,
                localSubnets = listOf(home),
                foreignRoutersReachable = listOf("192.168.101.1"),
                printersFound = 0,
            ),
        )
        assertThat(diagnosis).isInstanceOf(DiscoveryDiagnosis.PrinterOnAnotherNetwork::class.java)
    }

    @Test
    fun `every failure gives the user something to do next`() {
        listOf(
            DiscoveryDiagnostics.Evidence(false, emptyList()),
            DiscoveryDiagnostics.Evidence(true, listOf(home)),
            DiscoveryDiagnostics.Evidence(true, listOf(home), listOf("192.168.101.1")),
        ).forEach { evidence ->
            assertThat(DiscoveryDiagnostics.diagnose(evidence).suggestions).isNotEmpty()
        }
    }
}
