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
}

/** The apps on the app bar, as installed on this TV: each entry's first candidate package that can be opened. */
class AppsRepo(private val packages: Packages) {
    fun packageFor(app: AppEntry): String? = app.packages.firstOrNull(packages::isLaunchable)
    /** Ids of the apps that are installed; read again on every resume (apps come and go). */
    fun installed(apps: List<AppEntry>): Set<String> = apps.filter { packageFor(it) != null }.map { it.id }.toSet()
    fun launch(app: AppEntry): Boolean = packageFor(app)?.let(packages::launch) ?: false
    fun openStore(app: AppEntry) = packages.openStore(app.packages.first())
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
    override fun openStore(pkg: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(i) } catch (e: ActivityNotFoundException) { }
    }
}
