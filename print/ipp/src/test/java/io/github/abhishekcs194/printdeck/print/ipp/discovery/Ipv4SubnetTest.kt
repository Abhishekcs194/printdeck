package io.github.abhishekcs194.printdeck.print.ipp.discovery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Ipv4SubnetTest {

    @Test
    fun `parses and formats dotted quad`() {
        assertThat(parseIpv4("192.168.100.248")).isEqualTo(0xC0A864F8L)
        assertThat(formatIpv4(0xC0A864F8L)).isEqualTo("192.168.100.248")
    }

    @Test
    fun `handles addresses above 127 without sign trouble`() {
        // The classic bug: 192.x sets the top bit, so a signed Int wraps negative.
        val address = parseIpv4("255.255.255.255")
        assertThat(address).isEqualTo(4_294_967_295L)
        assertThat(formatIpv4(address)).isEqualTo("255.255.255.255")
    }

    @Test
    fun `masks host bits when building the containing subnet`() {
        val subnet = Ipv4Subnet.containing("192.168.100.248", 24)
        assertThat(subnet.asCidr()).isEqualTo("192.168.100.0/24")
    }

    @Test
    fun `a 24 excludes network and broadcast`() {
        val subnet = Ipv4Subnet.parse("192.168.1.0/24")
        assertThat(subnet.hostCount).isEqualTo(254)
        assertThat(formatIpv4(subnet.firstHost)).isEqualTo("192.168.1.1")
        assertThat(formatIpv4(subnet.lastHost)).isEqualTo("192.168.1.254")
        assertThat(subnet.hosts().count()).isEqualTo(254)
    }

    @Test
    fun `a 31 is point to point and reserves nothing`() {
        val subnet = Ipv4Subnet.parse("10.0.0.0/31")
        assertThat(subnet.hostCount).isEqualTo(2)
        assertThat(subnet.hosts().map(::formatIpv4).toList())
            .containsExactly("10.0.0.0", "10.0.0.1")
    }

    @Test
    fun `a 32 is a single host`() {
        val subnet = Ipv4Subnet.parse("10.1.2.3/32")
        assertThat(subnet.hostCount).isEqualTo(1)
        assertThat(subnet.hosts().map(::formatIpv4).toList()).containsExactly("10.1.2.3")
    }

    @Test
    fun `containment respects boundaries`() {
        val subnet = Ipv4Subnet.parse("192.168.100.0/24")
        assertThat(parseIpv4("192.168.100.0") in subnet).isTrue()
        assertThat(parseIpv4("192.168.100.255") in subnet).isTrue()
        assertThat(parseIpv4("192.168.101.0") in subnet).isFalse()
        assertThat(parseIpv4("192.168.99.255") in subnet).isFalse()
    }

    @Test
    fun `a 16 enumerates every host without materialising them`() {
        val subnet = Ipv4Subnet.parse("192.168.0.0/16")
        assertThat(subnet.hostCount).isEqualTo(65_534)
        // Sequence, so taking a few is cheap even though the range is huge.
        assertThat(subnet.hosts().take(2).map(::formatIpv4).toList())
            .containsExactly("192.168.0.1", "192.168.0.2")
    }

    @Test
    fun `malformed input is rejected`() {
        listOf("", "1.2.3", "1.2.3.4.5", "256.1.1.1", "a.b.c.d", "1.2.3.-1").forEach {
            assertThat(runCatching { parseIpv4(it) }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
