package se.premex.mcp

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

data class NetworkAddress(
    val interfaceName: String,
    val interfaceDisplayName: String,
    val address: String,
    val addressFamily: AddressFamily
) {
    val interfaceLabel: String
        get() = if (
            interfaceDisplayName.isBlank() || interfaceDisplayName == interfaceName
        ) {
            interfaceName
        } else {
            "$interfaceDisplayName ($interfaceName)"
        }
}

enum class AddressFamily {
    IPV4,
    IPV6
}

internal data class NetworkInterfaceSnapshot(
    val name: String,
    val displayName: String,
    val isUp: Boolean,
    val addresses: List<InetAddress>
)

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Get the device's WiFi IP address if available, otherwise return null
     */
    fun getWifiIpAddress(context: Context): String? {
        try {
            // Method 1: Try using WifiManager (most direct)
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                val wifiInfo = wifiManager.connectionInfo
                val ipAddress = wifiInfo?.ipAddress
                if (ipAddress != null && ipAddress != 0) {
                    return String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                }
            }

            // Method 2: Check all network interfaces (more comprehensive)
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()

                // Skip loopback interfaces like 127.0.0.1
                if (networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // Only include IPv4 addresses that are not loopback
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress
                        // Exclude local and special addresses
                        if (ip != null && !ip.startsWith("127.") && !ip.startsWith("0.")) {
                            Log.d(TAG, "Found IP address: $ip on interface: ${networkInterface.displayName}")
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }

        return null
    }

    /**
     * Returns every active, non-loopback IP address that the server can bind to.
     * Virtual interfaces are intentionally included because Android VPNs commonly use them.
     */
    fun getBindableNetworkAddresses(): List<NetworkAddress> {
        return try {
            val snapshots = mutableListOf<NetworkInterfaceSnapshot>()
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                try {
                    snapshots += NetworkInterfaceSnapshot(
                        name = networkInterface.name.orEmpty(),
                        displayName = networkInterface.displayName.orEmpty(),
                        isUp = networkInterface.isUp,
                        addresses = Collections.list(networkInterface.inetAddresses)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to inspect interface ${networkInterface.name}", e)
                }
            }

            collectBindableNetworkAddresses(snapshots)
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating network interfaces", e)
            emptyList()
        }
    }

    fun connectionUrl(host: String, port: Int): String {
        val urlHost = if (host.contains(':')) {
            "[${host.replace("%", "%25")}]"
        } else {
            host
        }
        return "http://$urlHost:$port/sse"
    }

    internal fun collectBindableNetworkAddresses(
        interfaces: List<NetworkInterfaceSnapshot>
    ): List<NetworkAddress> {
        return interfaces
            .asSequence()
            .filter { it.isUp }
            .flatMap { networkInterface ->
                networkInterface.addresses.asSequence().mapNotNull { inetAddress ->
                    if (inetAddress.isAnyLocalAddress || inetAddress.isLoopbackAddress) {
                        return@mapNotNull null
                    }

                    val addressFamily = when (inetAddress) {
                        is Inet4Address -> AddressFamily.IPV4
                        is Inet6Address -> AddressFamily.IPV6
                        else -> return@mapNotNull null
                    }

                    inetAddress.hostAddress?.let { hostAddress ->
                        NetworkAddress(
                            interfaceName = networkInterface.name,
                            interfaceDisplayName = networkInterface.displayName,
                            address = hostAddress,
                            addressFamily = addressFamily
                        )
                    }
                }
            }
            .distinctBy { it.interfaceName to it.address }
            .sortedWith(
                compareBy<NetworkAddress> { it.interfaceName }
                    .thenBy { it.addressFamily }
                    .thenBy { it.address }
            )
            .toList()
    }
}
