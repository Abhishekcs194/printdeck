package io.github.abhishekcs194.printdeck.print.ipp.discovery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The guard is the only thing standing between a subnet sweep and the public
 * internet, so it is tested at every boundary rather than sampled.
 */
class PrivateAddressGuardTest {

    @Test
    fun `accepts the RFC 1918 ranges`() {
        listOf(
            "10.0.0.1", "10.255.255.254",
            "172.16.0.1", "172.31.255.254",
            "192.168.0.1", "192.168.255.254",
        ).forEach { assertThat(PrivateAddressGuard.isAllowed(it)).isTrue() }
    }

    @Test
    fun `accepts link local and carrier NAT, where printers really do appear`() {
        assertThat(PrivateAddressGuard.isAllowed("169.254.1.1")).isTrue() // AirPrint ad-hoc
        assertThat(PrivateAddressGuard.isAllowed("100.64.0.1")).isTrue() // some CPE
    }

    @Test
    fun `rejects public addresses`() {
        listOf("8.8.8.8", "1.1.1.1", "197.226.230.69", "172.15.255.255", "172.32.0.1", "192.169.0.1")
            .forEach { assertThat(PrivateAddressGuard.isAllowed(it)).isFalse() }
    }

    @Test
    fun `rejects loopback so the client cannot be aimed at the phone itself`() {
        assertThat(PrivateAddressGuard.isAllowed("127.0.0.1")).isFalse()
        assertThat(PrivateAddressGuard.isAllowed("127.255.255.254")).isFalse()
    }

    @Test
    fun `rejects malformed input rather than throwing`() {
        assertThat(PrivateAddressGuard.isAllowed("not-an-address")).isFalse()
        assertThat(PrivateAddressGuard.isAllowed("")).isFalse()
    }

    @Test
    fun `a subnet is scannable only if it lies wholly inside private space`() {
        assertThat(PrivateAddressGuard.isScannable(Ipv4Subnet.parse("192.168.100.0/24"))).isTrue()
        assertThat(PrivateAddressGuard.isScannable(Ipv4Subnet.parse("10.0.0.0/8"))).isTrue()
        // Straddles the edge of 192.168/16, so it is refused outright.
        assertThat(PrivateAddressGuard.isScannable(Ipv4Subnet.parse("192.168.0.0/15"))).isFalse()
        assertThat(PrivateAddressGuard.isScannable(Ipv4Subnet.parse("0.0.0.0/0"))).isFalse()
    }

    @Test
    fun `require explains itself when refusing`() {
        val error = runCatching { PrivateAddressGuard.require("8.8.8.8") }.exceptionOrNull()
        assertThat(error).hasMessageThat().contains("your own network")
    }
}
