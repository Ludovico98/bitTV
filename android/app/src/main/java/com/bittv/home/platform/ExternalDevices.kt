package com.bittv.home.platform

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** One TV input as the system lists it. [parentId] is set for a device the TV recognised over HDMI (CEC). */
/** [recent]: added within [InputSettle]'s window, when its state is not yet the device's real one. */
data class InputSnapshot(val id: String, val label: String, val hdmi: Boolean, val parentId: String?, val connected: Boolean,
                         val on: Boolean = connected, val recent: Boolean = false)

/** Something plugged into the TV that can show a picture, with the name it reports (e.g. "PlayStation 5"). */
/** [on] is false while the device rests (standby): the tile then shows that instead of waking it for a preview. */
data class ExternalDevice(val inputId: String, val label: String, val port: String?, val on: Boolean = true)

object ExternalDevices {
    /**
     * Named HDMI devices that are connected. Bare ports are left out: a port reports "connected" for a soundbar's
     * audio link or a cable with nothing on it, and showing those would be a fake tile.
     */
    fun pick(inputs: List<InputSnapshot>): List<ExternalDevice> {
        val byId = inputs.associateBy { it.id }
        return inputs.filter { it.hdmi && it.parentId != null && it.connected }
            .map { ExternalDevice(it.id, it.label, it.parentId?.let { p -> byId[p]?.label }, it.on && !it.recent) }
    }
}

/**
 * The TCL removes and re-adds an HDMI device's input on wake and on hotplug, and a re-added input reads CONNECTED
 * (the system's default) until the device's CEC power status arrives a second or two later — a PS5 in standby
 * looked switched on in that window (seen on the TV, 2026-10-25). An input is not trusted for [windowMs] after it
 * was added. Times are elapsedRealtime.
 */
class InputSettle(private val windowMs: Long = 10_000) {
    private val addedAt = mutableMapOf<String, Long>()
    fun added(id: String, now: Long) { addedAt[id] = now }
    fun recent(id: String, now: Long): Boolean = addedAt[id]?.let { now - it < windowMs } ?: false
    val window: Long get() = windowMs
}

/** The live HDMI picture on Home. */
object HdmiPreview {
    /**
     * Tune params that keep a preview passive. TCL's HDMI session (TvPassThroughSession.onTune) otherwise selects the
     * device over CEC — a <Set Stream Path> that wakes the PS5, which then takes the TV over to its input — and on
     * release sends it <Standby> (read from the TV's TIF_TvInput, 2026-10-25).
     */
    val TUNE_FLAGS = mapOf("isCecDeviceDisable" to true)
}

/** The TV's inputs through the public TvInputManager (no permission needed to list them). */
class AndroidTvInputs(private val context: Context) {
    private val tim = context.getSystemService(TvInputManager::class.java)

    private val settle = InputSettle()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun devices(): List<ExternalDevice> = try {
        val now = android.os.SystemClock.elapsedRealtime()
        ExternalDevices.pick(tim?.tvInputList.orEmpty().map { i ->
            val state = tim?.getInputState(i.id) ?: TvInputManager.INPUT_STATE_DISCONNECTED
            InputSnapshot(i.id, i.loadLabel(context).toString(), hdmi = i.type == TvInputInfo.TYPE_HDMI, parentId = i.parentId,
                connected = state != TvInputManager.INPUT_STATE_DISCONNECTED, on = TvApps.mayPreview(state), recent = settle.recent(i.id, now))
        })
    } catch (e: Exception) { emptyList() }

    /** Calls [onChange] when a device is plugged in, removed or switched on/off, until [stop]. */
    private var callback: TvInputManager.TvInputCallback? = null
    fun watch(onChange: () -> Unit) {
        val cb = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) = onChange()
            override fun onInputAdded(inputId: String) {
                settle.added(inputId, android.os.SystemClock.elapsedRealtime())
                onChange()
                // read again once its state can be trusted
                handler.postDelayed(onChange, settle.window)
            }
            override fun onInputRemoved(inputId: String) = onChange()
        }
        callback = cb
        runCatching { tim?.registerCallback(cb, handler) }
    }
    fun stop() { callback?.let { runCatching { tim?.unregisterCallback(it) } }; callback = null; handler.removeCallbacksAndMessages(null) }
}
