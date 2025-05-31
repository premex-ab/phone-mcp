package se.premex.mcpserver

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

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
}
