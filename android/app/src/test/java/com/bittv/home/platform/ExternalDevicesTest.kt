package com.bittv.home.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalDevicesTest {
    // what the TCL reported on 2026-10-25: four HDMI ports, and the PS5 on HDMI 2 as a named (CEC) input
    private val tv = listOf(
        InputSnapshot("com.tcl.tvinput/.TvPassThroughService/HW15", "HDMI 1", hdmi = true, parentId = null, connected = true),
        InputSnapshot("com.tcl.tvinput/.TvPassThroughService/HW16", "HDMI 2", hdmi = true, parentId = null, connected = true),
        InputSnapshot("com.tcl.tvinput/.TvPassThroughService/HW17", "HDMI 3", hdmi = true, parentId = null, connected = false),
        InputSnapshot("com.tcl.tvinput/.TvPassThroughService/HDMI200004", "PlayStation 5", hdmi = true,
            parentId = "com.tcl.tvinput/.TvPassThroughService/HW16", connected = true),
        InputSnapshot("com.mediatek.AirplayAPK/.AirPlayTvInputService", "AirPlay", hdmi = false, parentId = null, connected = true),
        InputSnapshot("com.tcl.tvinput/.TunerInputService/HW2", "TV", hdmi = false, parentId = null, connected = true),
    )

    @Test fun aNamedDeviceByItsNameAndAPortWithoutOneByThePort() {
        val inputs = HdmiInputs.pick(tv)
        assertEquals(listOf("HDMI 1", "PlayStation 5"), inputs.map { it.label })
        assertEquals(HdmiInput("com.tcl.tvinput/.TvPassThroughService/HDMI200004", "PlayStation 5", "HDMI 2"), inputs[1])
        assertEquals("com.tcl.tvinput/.TvPassThroughService/HW15", inputs[0].inputId)   // switching goes to the port itself
    }

    @Test fun emptyPortsTheTunerAndAirPlayAreLeftOut() {
        assertEquals(emptyList<HdmiInput>(), HdmiInputs.pick(tv.map { it.copy(connected = false) }))
    }

    @Test fun anUnpluggedDeviceLeavesItsPortIfThePortStillReportsSomething() {
        val inputs = HdmiInputs.pick(tv.map { if (it.parentId != null) it.copy(connected = false) else it })
        assertEquals(listOf("HDMI 1", "HDMI 2"), inputs.map { it.label })
    }
}
