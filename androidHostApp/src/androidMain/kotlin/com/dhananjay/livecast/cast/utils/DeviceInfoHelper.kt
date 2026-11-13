package com.dhananjay.livecast.cast.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.dhananjay.livecast.cast.data.model.DeviceInfo
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Helper class to collect device information
 */
object DeviceInfoHelper {

    /**
     * Get complete device information
     */
    fun getDeviceInfo(context: Context): DeviceInfo {
        val displayMetrics = getDisplayMetrics(context)
        val batteryInfo = getBatteryInfo(context)
        val networkInfo = getNetworkInfo(context)
        val appInfo = getAppInfo(context)

        return DeviceInfo.fromCurrentDevice(
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
            screenDensity = displayMetrics.density,
            batteryLevel = batteryInfo.first,
            isCharging = batteryInfo.second,
            networkType = networkInfo.first,
            ipAddress = networkInfo.second,
            appVersion = appInfo.first,
            appVersionCode = appInfo.second,
        )
    }

    /**
     * Get display metrics
     */
    private fun getDisplayMetrics(context: Context): DisplayMetrics {
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            displayMetrics.widthPixels = bounds.width()
            displayMetrics.heightPixels = bounds.height()
            displayMetrics.density = context.resources.displayMetrics.density
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
        }

        return displayMetrics
    }

    /**
     * Get battery level and charging status
     * Returns Pair(batteryLevel, isCharging)
     */
    private fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            0
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return Pair(batteryLevel, isCharging)
    }

    /**
     * Get network type and IP address
     * Returns Pair(networkType, ipAddress)
     */
    private fun getNetworkInfo(context: Context): Pair<String, String> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            when {
                capabilities == null -> "none"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            when (connectivityManager.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "wifi"
                ConnectivityManager.TYPE_MOBILE -> "mobile"
                ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                else -> "none"
            }
        }

        val ipAddress = getLocalIpAddress(context, networkType)

        return Pair(networkType, ipAddress)
    }

    /**
     * Get local IP address
     */
    private fun getLocalIpAddress(context: Context, networkType: String): String {
        return try {
            if (networkType == "wifi") {
                // Try to get WiFi IP
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                } else {
                    getIpFromNetworkInterfaces()
                }
            } else {
                getIpFromNetworkInterfaces()
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get IP from network interfaces (fallback)
     */
    private fun getIpFromNetworkInterfaces(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get app version info
     * Returns Pair(versionName, versionCode)
     */
    private fun getAppInfo(context: Context): Pair<String, Int> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            Pair(versionName, versionCode)
        } catch (e: Exception) {
            Pair("unknown", 0)
        }
    }
}

