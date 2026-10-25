package com.bittv.home.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bittv.home.model.AppEntry

/** What BitTV needs from the package manager; faked in tests. */
interface Packages {
    fun isLaunchable(pkg: String): Boolean
    fun launch(pkg: String): Boolean
    fun openStore(pkg: String)
    /** Opens [url] in [pkg] if that app handles it (a title's page inside the app). */
    fun openLink(pkg: String, url: String): Boolean = false
    /** Opens [pkg]'s own search with [query], if the app offers one. */
    fun searchIn(pkg: String, query: String): Boolean = false
    /** The app's own TV banner (its official Google TV artwork), if it ships one. */
    fun banner(pkg: String): android.graphics.Bitmap? = null
}

/** The apps on the app bar, as installed on this TV: each entry's first candidate package that can be opened. */
class AppsRepo(private val packages: Packages) {
    fun packageFor(app: AppEntry): String? = app.packages.firstOrNull(packages::isLaunchable)
    /** Ids of the apps that are installed; read again on every resume (apps come and go). */
    fun installed(apps: List<AppEntry>): Set<String> = apps.filter { packageFor(it) != null }.map { it.id }.toSet()
    fun launch(app: AppEntry): Boolean = packageFor(app)?.let(packages::launch) ?: false
    /** A title in its app: its own page there, else the app's search for it, else just the app. */
    fun openTitle(app: AppEntry, url: String?, name: String): Boolean {
        val pkg = packageFor(app) ?: return false
        return (url != null && packages.openLink(pkg, url)) || packages.searchIn(pkg, name) || packages.launch(pkg)
    }
    fun openStore(app: AppEntry) = packages.openStore(app.packages.first())
    fun banner(app: AppEntry): android.graphics.Bitmap? = packageFor(app)?.let(packages::banner)
    /** Opens the first of [pkgs] that is installed (the Browser and Media shortcuts); false if none is. */
    fun launchFirst(pkgs: List<String>): Boolean = pkgs.firstOrNull(packages::isLaunchable)?.let(packages::launch) ?: false

    companion object {
        /** The services he has: those whose app is installed on the TV (Settings no longer lists subscriptions). */
        fun services(apps: List<AppEntry>, installed: Set<String>): Set<com.bittv.home.model.Svc> =
            apps.filter { it.id in installed }.mapNotNull { it.svc }.toSet()
    }
}

/** The real package manager: TV (leanback) entry first, phone launcher entry as a fallback, Play Store pages. */
class AndroidPackages(private val context: Context) : Packages {
    private val pm get() = context.packageManager
    private fun intentFor(pkg: String): Intent? = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    override fun isLaunchable(pkg: String) = intentFor(pkg) != null
    override fun launch(pkg: String): Boolean {
        val i = intentFor(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return try { context.startActivity(i); true } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
    }
    override fun banner(pkg: String): android.graphics.Bitmap? = try {
        pm.getApplicationBanner(pkg)?.let { d ->
            android.graphics.Bitmap.createBitmap(320, 180, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
                d.setBounds(0, 0, 320, 180); d.draw(android.graphics.Canvas(b))
            }
        }
    } catch (e: Exception) { null }

    private fun start(i: Intent): Boolean = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).let {
        if (pm.resolveActivity(it, 0) == null) false
        else try { context.startActivity(it); true } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
    }
    override fun openLink(pkg: String, url: String): Boolean {
        // ITVX accepts its web links but shows its home page (checked on the TV): its own search finds the title instead
        if (pkg == "air.ITVMobilePlayer") return false
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg)
            // a fresh start: an app already open in the background would otherwise come forward and ignore the link
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // Netflix's TV app only acts on a link that says where it came from (it closes straight away otherwise)
        if (pkg == "com.netflix.ninja") { i.data = Uri.parse(url.replaceFirst("https://", "http://")); i.putExtra("source", "30") }
        return start(i)
    }
    override fun searchIn(pkg: String, query: String) =
        start(Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(android.app.SearchManager.QUERY, query))

    override fun openStore(pkg: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(i) } catch (e: ActivityNotFoundException) { }
    }
}
