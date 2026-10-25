package com.bittv.home.platform

import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.Prefs
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.home.RowItem
import com.bittv.home.ui.home.homeRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDevicesTest {
    // what the TCL reported on 2026-10-25: four HDMI ports, and the PS5 on HDMI 2 as a named (CEC) input
    private val tv = listOf(
        InputSnapshot("com.tcl.tvinput/.TvPassThroughService/HW15", "HDMI 1", hdmi = true, parentId = null, connected = true),
        InputSnapshot("com.tcl.tvinput/.TvPassThroughService/HW16", "HDMI 2", hdmi = true, parentId = null, connected = true),
        InputSnapshot("com.tcl.tvinput/.TvPassThroughService/HDMI200004", "PlayStation 5", hdmi = true,
            parentId = "com.tcl.tvinput/.TvPassThroughService/HW16", connected = true),
        InputSnapshot("com.mediatek.AirplayAPK/.AirPlayTvInputService", "AirPlay", hdmi = false, parentId = null, connected = true),
        InputSnapshot("com.tcl.tvinput/.TunerInputService/HW2", "TV", hdmi = false, parentId = null, connected = true),
    )

    @Test fun onlyNamedDevicesThatAreConnectedAreShown() {
        assertEquals(listOf(ExternalDevice("com.tcl.tvinput/.TvPassThroughService/HDMI200004", "PlayStation 5", "HDMI 2")),
            ExternalDevices.pick(tv))
    }

    @Test fun unpluggedDeviceDisappears() {
        assertEquals(emptyList<ExternalDevice>(), ExternalDevices.pick(tv.map { if (it.parentId != null) it.copy(connected = false) else it }))
    }

    @Test fun aDeviceInStandbyHasNoPreview() {
        val ps5 = tv.map { if (it.parentId != null) it.copy(on = false) else it }
        assertEquals(listOf(false), ExternalDevices.pick(ps5).map { it.on })
    }

    // the TCL re-adds the PS5's input on wake and on hotplug, and for a second or two reports it CONNECTED (the
    // default) before the CEC power status arrives — the PS5 was woken by a preview opened in that window
    @Test fun aJustAddedInputIsNotTrustedToBeOn() {
        val fresh = tv.map { if (it.parentId != null) it.copy(recent = true) else it }
        assertEquals(listOf(false), ExternalDevices.pick(fresh).map { it.on })
    }

    @Test fun anInputIsRecentOnlyForTheSettleWindowAfterItWasAdded() {
        val settle = InputSettle(windowMs = 10_000)
        settle.added("ps5", now = 1_000)
        assertTrue(settle.recent("ps5", now = 10_999))
        assertFalse(settle.recent("ps5", now = 11_000))
        assertFalse(settle.recent("other", now = 1_000))
        settle.added("ps5", now = 20_000)   // re-added: a new window
        assertTrue(settle.recent("ps5", now = 25_000))
    }

    // TCL's HDMI session (TvPassThroughSession.onTune) skips its CEC device select — the <Set Stream Path> that
    // wakes the PS5 and makes it take over the TV — and the <Standby> on release when this flag is in the tune params
    @Test fun previewsTuneWithoutCec() {
        assertEquals(mapOf("isCecDeviceDisable" to true), HdmiPreview.TUNE_FLAGS)
    }

    private fun state(devices: List<ExternalDevice>?) = HomeState(Prefs(), DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps,
        emptySet(), emptyList(), null, emptyList(), null, devices = devices)

    @Test fun nothingConnectedMeansNoDevicesRow() {
        assertTrue(homeRows(state(emptyList())).none { it.key == "dev" })
    }

    @Test fun aConnectedDeviceIsTheRowsOnlyTile() {
        val dev = homeRows(state(ExternalDevices.pick(tv))).first { it.key == "dev" }
        assertEquals(listOf("PlayStation 5 · HDMI 2"), dev.items.map { (it as RowItem.Device).label })
    }
}
