package com.bittv.home.platform


/** The TV's own apps behind the top shortcuts and the HDMI tiles (checked on the TCL 55C6K-UK, 2026-10-25). */
object TvApps {
    /** Browser: TCL's, else common TV browsers. */
    val BROWSER = listOf("com.tcl.browser", "org.mozilla.tv.firefox", "com.android.chrome", "com.opera.browser")
    /** Media: TCL's Media Center (videos, music and pictures from USB drives and the home network), else its Gallery. */
    val MEDIA = listOf("com.tcl.ui_mediaCenter", "com.tcl.gallery")
    private val INPUT_APPS = listOf("com.tcl.tv")

    /** Which app shows an HDMI input full screen: TCL's TV app (no chooser with Live Channels), else what there is. */
    fun inputApp(handlers: List<String>): String? = INPUT_APPS.firstOrNull { it in handlers } ?: handlers.firstOrNull()
}
