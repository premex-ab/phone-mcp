package se.premex.mcp

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkUtilsTest {
    @Test
    fun collectBindableNetworkAddresses_includesActiveVpnAddresses() {
        val addresses = NetworkUtils.collectBindableNetworkAddresses(
            listOf(
                NetworkInterfaceSnapshot(
                    name = "wlan0",
                    displayName = "wlan0",
                    isUp = true,
                    addresses = listOf(InetAddress.getByName("192.168.1.20"))
                ),
                NetworkInterfaceSnapshot(
                    name = "tun0",
                    displayName = "VPN tunnel",
                    isUp = true,
                    addresses = listOf(
                        InetAddress.getByName("10.8.0.2"),
                        InetAddress.getByName("fd00::2")
                    )
                )
            )
        )

        assertEquals(
            listOf(
                NetworkAddress("tun0", "VPN tunnel", "10.8.0.2", AddressFamily.IPV4),
                NetworkAddress("tun0", "VPN tunnel", "fd00:0:0:0:0:0:0:2", AddressFamily.IPV6),
                NetworkAddress("wlan0", "wlan0", "192.168.1.20", AddressFamily.IPV4)
            ),
            addresses
        )
    }

    @Test
    fun collectBindableNetworkAddresses_excludesDownAndLocalAddresses() {
        val addresses = NetworkUtils.collectBindableNetworkAddresses(
            listOf(
                NetworkInterfaceSnapshot(
                    name = "lo",
                    displayName = "lo",
                    isUp = true,
                    addresses = listOf(
                        InetAddress.getByName("127.0.0.1"),
                        InetAddress.getByName("0.0.0.0")
                    )
                ),
                NetworkInterfaceSnapshot(
                    name = "tun0",
                    displayName = "tun0",
                    isUp = false,
                    addresses = listOf(InetAddress.getByName("10.8.0.2"))
                )
            )
        )

        assertEquals(emptyList<NetworkAddress>(), addresses)
    }

    @Test
    fun connectionUrl_formatsIpv4AndScopedIpv6Hosts() {
        assertEquals(
            "http://10.8.0.2:4567/sse",
            NetworkUtils.connectionUrl("10.8.0.2", 4567)
        )
        assertEquals(
            "http://[fe80::1234%25tun0]:4567/sse",
            NetworkUtils.connectionUrl("fe80::1234%tun0", 4567)
        )
    }
}
