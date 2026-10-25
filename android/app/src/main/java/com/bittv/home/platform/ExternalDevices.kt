package com.bittv.home.platform

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** One TV input as the system lists it. [parentId] is set for a device the TV recognised over HDMI (CEC). */
data class InputSnapshot(val id: String, val label: String, val hdmi: Boolean, val parentId: String?, val connected: Boolean)

/** Something plugged into the TV that can show a picture, with the name it reports (e.g. "PlayStation 5"). */
data class ExternalDevice(val inputId: String, val label: String, val port: String?)

object ExternalDevices {
    /**
     * Named HDMI devices that are connected. Bare ports are left out: a port reports "connected" for a soundbar's
     * audio link or a cable with nothing on it, and showing those would be a fake tile.
     */
    fun pick(inputs: List<InputSnapshot>): List<ExternalDevice> {
        val byId = inputs.associateBy { it.id }
        return inputs.filter { it.hdmi && it.parentId != null && it.connected }
            .map { ExternalDevice(it.id, it.label, it.parentId?.let { p -> byId[p]?.label }) }
    }
}

/** The TV's inputs through the public TvInputManager (no permission needed to list them). */
class AndroidTvInputs(private val context: Context) {
    private val tim = context.getSystemService(TvInputManager::class.java)

    fun devices(): List<ExternalDevice> = try {
        ExternalDevices.pick(tim?.tvInputList.orEmpty().map { i ->
            InputSnapshot(i.id, i.loadLabel(context).toString(), hdmi = i.type == TvInputInfo.TYPE_HDMI, parentId = i.parentId,
                connected = tim?.getInputState(i.id) != TvInputManager.INPUT_STATE_DISCONNECTED)
        })
    } catch (e: Exception) { emptyList() }

    /** Calls [onChange] when a device is plugged in, removed or switched on/off, until [stop]. */
    private var callback: TvInputManager.TvInputCallback? = null
    fun watch(onChange: () -> Unit) {
        val cb = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) = onChange()
            override fun onInputAdded(inputId: String) = onChange()
            override fun onInputRemoved(inputId: String) = onChange()
        }
        callback = cb
        runCatching { tim?.registerCallback(cb, android.os.Handler(android.os.Looper.getMainLooper())) }
    }
    fun stop() { callback?.let { runCatching { tim?.unregisterCallback(it) } }; callback = null }
}
