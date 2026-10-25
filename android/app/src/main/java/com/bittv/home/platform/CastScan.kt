package com.bittv.home.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.bittv.home.model.CastDevice
import kotlinx.coroutines.delay

/** One service seen on the network. */
data class Found(val type: String, val name: String)

/**
 * Which network announcements are phones, tablets or computers that can cast to the TV. Apple devices announce
 * their own name (`_companion-link`); Android phones announce Google's cross-device service with an id only.
 * Cast speakers, hubs, bridges and this TV are not sources and are left out.
 */
object CastScan {
    const val APPLE = "_companion-link._tcp"
    const val ANDROID = "_googcrossdevice._tcp"
    val TYPES = listOf(APPLE, ANDROID)

    fun devices(found: List<Found>, tvName: String = "Smart TV Pro"): List<CastDevice> {
        val apple = found.filter { it.type.startsWith(APPLE) && it.name != tvName }.map { it.name }.distinct().map { n ->
            val kind = when {
                n.contains("iPad", ignoreCase = true) -> CastDevice.Kind.IPAD
                n.contains("Mac", ignoreCase = true) -> CastDevice.Kind.MAC
                else -> CastDevice.Kind.IPHONE
            }
            CastDevice("ios:$n", n, kind)
        }
        val androidIds = found.filter { it.type.startsWith(ANDROID) }.map { it.name }.distinct()
        val android = androidIds.mapIndexed { i, id ->
            CastDevice("android:$id", if (androidIds.size == 1) "Android phone" else "Android phone ${i + 1}", CastDevice.Kind.ANDROID)
        }
        return apple + android
    }
}

/** Looks for casting devices on the home network for [ms] milliseconds (NSD / mDNS; no permission needed). */
class NetworkScanner(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    suspend fun scan(ms: Long = 4000): List<CastDevice> {
        val found = java.util.Collections.synchronizedList(mutableListOf<Found>())
        val listeners = CastScan.TYPES.map { type ->
            object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) { found += Found(info.serviceType.trimEnd('.'), info.serviceName) }
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }.also { runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, it) } }
        }
        try { delay(ms) } finally { listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } } }
        return CastScan.devices(found.toList())
    }
}
