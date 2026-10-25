package com.bittv.home.platform

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** One TV input as the system lists it. [parentId] is set for a device the TV recognised over HDMI (CEC). */
/** [recent]: added within [InputSettle]'s window, when its state is not yet the device's real one. [power]: its CEC
 *  power status ([CecPower]), null when unknown. [blank]: its preview showed no picture ([NoPicture]). */
data class InputSnapshot(val id: String, val label: String, val hdmi: Boolean, val parentId: String?, val connected: Boolean,
                         val on: Boolean = connected, val recent: Boolean = false, val power: Int? = null, val blank: Boolean = false)

/** Something plugged into the TV that can show a picture, with the name it reports (e.g. "PlayStation 5"). */
/** [on] is false while the device rests (standby): the tile then shows that instead of waking it for a preview. */
/** [noPicture]: it may be on, but its preview showed nothing — not tuned again until the TV reports a change. */
data class ExternalDevice(val inputId: String, val label: String, val port: String?, val on: Boolean = true, val noPicture: Boolean = false)

object ExternalDevices {
    /**
     * Named HDMI devices that are connected. Bare ports are left out: a port reports "connected" for a soundbar's
     * audio link or a cable with nothing on it, and showing those would be a fake tile.
     */
    fun pick(inputs: List<InputSnapshot>): List<ExternalDevice> {
        val byId = inputs.associateBy { it.id }
        return inputs.filter { it.hdmi && it.parentId != null && it.connected }
            .map { ExternalDevice(it.id, it.label, it.parentId?.let { p -> byId[p]?.label },
                it.on && !it.recent && CecPower.mayBeOn(it.power) && !it.blank, noPicture = it.blank) }
    }
}

/**
 * The device's power as HDMI-CEC reports it (HdmiDeviceInfo.POWER_STATUS_*). The TCL's input state is not enough: a
 * re-added input keeps the default CONNECTED for good, so a PS5 in rest mode read as on (2026-10-25, dumpsys
 * hdmi_control power_status 1 with the input's state 0) and its black preview dimmed the whole panel.
 */
object CecPower {
    const val UNKNOWN = -1; const val ON = 0; const val STANDBY = 1; const val TO_ON = 2; const val TO_STANDBY = 3
    /** False only when CEC says it rests or is going to; unknown leaves it to the input state. */
    fun mayBeOn(status: Int?): Boolean = status != STANDBY && status != TO_STANDBY
}

/**
 * Inputs whose preview showed no picture. Kept until the TV reports a change for that input (state, info, re-add) or
 * Home is shown again (changes while away are not watched).
 */
class NoPicture {
    private val ids = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()   // read off the main thread too
    fun mark(id: String) { ids += id }
    fun changed(id: String) { ids -= id }
    fun clear() = ids.clear()
    operator fun contains(id: String) = id in ids
}

/** When a preview stops trying: the TV runs its HDMI picture path (and its dimming) for as long as one is open. */
object PreviewSignal {
    /** No picture this long after tuning: give up. */
    const val GIVE_UP_MS = 2_500L
    /** The TCL reports UNKNOWN as every session starts, before the real status; after a picture it means it went away. */
    fun blank(reason: Int, hadPicture: Boolean): Boolean = when (reason) {
        TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING, TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> false
        TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN -> hadPicture
        else -> true
    }
}

/**
 * The TCL removes and re-adds an HDMI device's input on wake and on hotplug, and a re-added input reads CONNECTED
 * (the system's default) until the device's CEC power status arrives a second or two later — a PS5 in standby
 * looked switched on in that window (seen on the TV, 2026-10-25). An input is not trusted for [windowMs] after it
 * was added. Times are elapsedRealtime.
 */
class InputSettle(private val windowMs: Long = 10_000) {
    private val addedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()   // read off the main thread too
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

    /**
     * A preview opens once the Devices row has had focus this long. Opening one makes the TCL switch the
     * panel's source to HDMI (TclpqDBC setInputSource 16) and back on release; without the wait, Down held past the
     * row opened and closed sessions within ~10 ms of each other.
     */
    const val DWELL_MS = 600L
}

/** The TV's inputs through the public TvInputManager (no permission needed to list them). */
class AndroidTvInputs(private val context: Context) {
    private val tim = context.getSystemService(TvInputManager::class.java)

    private val settle = InputSettle()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val blank = NoPicture()

    fun devices(): List<ExternalDevice> = try {
        val now = android.os.SystemClock.elapsedRealtime()
        ExternalDevices.pick(tim?.tvInputList.orEmpty().map { i ->
            val state = tim?.getInputState(i.id) ?: TvInputManager.INPUT_STATE_DISCONNECTED
            InputSnapshot(i.id, i.loadLabel(context).toString(), hdmi = i.type == TvInputInfo.TYPE_HDMI, parentId = i.parentId,
                connected = state != TvInputManager.INPUT_STATE_DISCONNECTED, on = TvApps.mayPreview(state), recent = settle.recent(i.id, now),
                power = cecPower(i), blank = i.id in blank)
        })
    } catch (e: Exception) { emptyList() }

    /** A preview of [inputId] showed no picture: Home shows it without one until the TV reports a change. */
    fun noPicture(inputId: String) { blank.mark(inputId); onChange?.invoke() }

    /**
     * The CEC power status the TV keeps for the input's device (TvInputInfo.getHdmiDeviceInfo, a system API: read by
     * reflection, which the TCL allows a normal app), null when unknown. Passive: it asks the device nothing.
     */
    private fun cecPower(i: TvInputInfo): Int? = try {
        hdmiInfo?.invoke(i)?.let { powerStatus?.invoke(it) as? Int }?.takeIf { it != CecPower.UNKNOWN }
    } catch (e: Exception) { null }
    private val hdmiInfo by lazy { runCatching { TvInputInfo::class.java.getMethod("getHdmiDeviceInfo") }.getOrNull() }
    private val powerStatus by lazy { runCatching { Class.forName("android.hardware.hdmi.HdmiDeviceInfo").getMethod("getDevicePowerStatus") }.getOrNull() }

    /** Calls [onChange] when a device is plugged in, removed or switched on/off, until [stop]. */
    private var callback: TvInputManager.TvInputCallback? = null
    private var onChange: (() -> Unit)? = null
    fun watch(onChange: () -> Unit) {
        this.onChange = onChange
        blank.clear()
        val cb = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) { blank.changed(inputId); onChange() }
            // the TV updated the device's HDMI info (its CEC power status among it)
            override fun onTvInputInfoUpdated(info: TvInputInfo) { blank.changed(info.id); onChange() }
            override fun onInputAdded(inputId: String) {
                settle.added(inputId, android.os.SystemClock.elapsedRealtime())
                blank.changed(inputId)
                onChange()
                // read again once its state can be trusted
                handler.postDelayed(onChange, settle.window)
            }
            override fun onInputRemoved(inputId: String) = onChange()
        }
        callback = cb
        runCatching { tim?.registerCallback(cb, handler) }
    }
    fun stop() { callback?.let { runCatching { tim?.unregisterCallback(it) } }; callback = null; onChange = null; handler.removeCallbacksAndMessages(null) }
}
