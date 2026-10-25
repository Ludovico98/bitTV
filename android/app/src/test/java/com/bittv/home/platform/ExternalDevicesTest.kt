package com.bittv.home.platform

import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.Prefs
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.home.RowItem
import com.bittv.home.ui.home.homeRows
import org.junit.Assert.assertEquals
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
