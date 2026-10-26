package com.bittv.home.platform

import com.bittv.home.model.CastDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastScanTest {
    @Test fun appleDevicesKeepTheirNames() {
        assertEquals(listOf(CastDevice("ios:Ludovico’s iPhone", "Ludovico’s iPhone", CastDevice.Kind.IPHONE),
            CastDevice("ios:Ludovico’s MacBook Air", "Ludovico’s MacBook Air", CastDevice.Kind.MAC),
            CastDevice("ios:iPad di Ludovico", "iPad di Ludovico", CastDevice.Kind.IPAD)),
            CastScan.devices(listOf(Found("_companion-link._tcp", "Ludovico’s iPhone"), Found("_companion-link._tcp", "Ludovico’s MacBook Air"),
                Found("_companion-link._tcp", "iPad di Ludovico"))))
    }

    @Test fun androidPhonesAreFoundByCrossDeviceServiceAndNumbered() {
        // what this network announced on 2026-10-25: one Android phone, plus things that are not phones
        val found = listOf(Found("_googcrossdevice._tcp", "A5E1FED1E233C3EE7B9DA3DE8554962F"),
            Found("_googlecast._tcp", "Google-Nest-Hub-Max-a431"), Found("_googlecast._tcp", "Smart-TV-Pro-e6fd"),
            Found("_spotify-connect._tcp", "SpZc-3BAC72"), Found("_hap._tcp", "Hive Bridge"))
        assertEquals(listOf(CastDevice("android:A5E1FED1E233C3EE7B9DA3DE8554962F", "Android phone", CastDevice.Kind.ANDROID)), CastScan.devices(found))
        val two = CastScan.devices(found + Found("_googcrossdevice._tcp", "B0"))
        assertEquals(listOf("Android phone 1", "Android phone 2"), two.map { it.name })
    }

    @Test fun duplicatesAndTheTvItselfAreDropped() {
        assertEquals(1, CastScan.devices(listOf(Found("_companion-link._tcp", "Ludovico’s iPhone"), Found("_companion-link._tcp", "Ludovico’s iPhone"))).size)
        assertTrue(CastScan.devices(listOf(Found("_companion-link._tcp", "Smart TV Pro"))).isEmpty())
    }
}

class TvAppsTest {
    @Test fun browserAndMediaUseTheTvsOwnApps() {
        val tv = setOf("com.tcl.browser", "com.tcl.ui_mediaCenter", "com.tcl.gallery")
        assertEquals("com.tcl.browser", TvApps.BROWSER.first { it in tv })
        assertEquals("com.tcl.ui_mediaCenter", TvApps.MEDIA.first { it in tv })
    }

    @Test fun hdmiOpensInTclsTvAppNotAChooser() {
        assertEquals("com.tcl.tv", TvApps.inputApp(listOf("com.google.android.tv", "com.tcl.tv")))
        assertEquals("com.google.android.tv", TvApps.inputApp(listOf("com.google.android.tv")))
        assertEquals(null, TvApps.inputApp(emptyList()))
    }

    }
