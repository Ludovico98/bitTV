package com.bittv.home.platform

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** One TV input as the system lists it. [parentId] is set for a device the TV recognised over HDMI (CEC). */
data class InputSnapshot(val id: String, val label: String, val hdmi: Boolean, val parentId: String?, val connected: Boolean)

/** Something plugged into an HDMI port: [name] is what the device calls itself (e.g. "PlayStation 5"), when it says. */
data class HdmiInput(val inputId: String, val name: String?, val port: String) {
    /** What the Inputs menu shows: the device's name, else the port. */
    val label: String get() = name ?: port
}

object HdmiInputs {
    /**
     * What is plugged in: first each device the TV knows by name (its own input, on its port), then the other ports
     * as just the port ("HDMI 1"). Nothing else: no tuner, no AirPlay. The TCL can't say which of those other ports
     * has a cable in: every port reads "connected" or "standby" and its cable status "disconnected", the PS5's
     * included (dumpsys tv_input, 2026-10-26) — only a device's own name (HDMI-CEC) is reliable. So they are all
     * listed, as the way to a device that gives no name.
     */
    fun pick(inputs: List<InputSnapshot>): List<HdmiInput> {
        val ports = inputs.filter { it.hdmi && it.parentId == null }.sortedBy { it.label }
        val devices = inputs.filter { it.hdmi && it.parentId != null && it.connected }
        val named = ports.flatMap { port -> devices.filter { it.parentId == port.id }.map { HdmiInput(it.id, it.label, port.label) } }
        val bare = ports.filter { port -> port.connected && devices.none { it.parentId == port.id } }.map { HdmiInput(it.id, null, it.label) }
        return named + bare
    }
}

/**
 * The TV's HDMI inputs through the public TvInputManager (no permission needed to list them). Reading the list asks
 * the devices nothing: no session is opened, so nothing is woken or switched (see docs/tv-profile.md for why a
 * session on a PS5 port did both).
 */
class AndroidTvInputs(private val context: Context) {
    private val tim = context.getSystemService(TvInputManager::class.java)

    fun inputs(): List<HdmiInput> = try {
        HdmiInputs.pick(tim?.tvInputList.orEmpty().map { i ->
            val state = tim?.getInputState(i.id) ?: TvInputManager.INPUT_STATE_DISCONNECTED
            InputSnapshot(i.id, i.loadLabel(context).toString(), hdmi = i.type == TvInputInfo.TYPE_HDMI, parentId = i.parentId,
                connected = state != TvInputManager.INPUT_STATE_DISCONNECTED)
        })
    } catch (e: Exception) { emptyList() }
}
