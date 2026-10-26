package com.bittv.home.data

/**
 * Which apps are on the app bar. Every app on the TV is listed in Settings with a switch; [hidden] are the ones he
 * switched off. [seen] is every app BitTV has met, so an app installed later is recognised as new and goes on the
 * bar by itself (he can switch it off).
 */
object AppShelf {
    data class State(val seen: Set<String>, val hidden: Set<String>)

    /**
     * The state after looking at the apps [installed] now; null when nothing changes. The first time ([seen] null)
     * the bar stays as it was: BitTV's own apps ([known]) on it, everything else already on the TV off it.
     */
    fun reconcile(installed: Collection<String>, known: Set<String>, seen: Set<String>?, hidden: Set<String>): State? {
        if (seen == null) return State(installed.toSet() + known, hidden + (installed.toSet() - known))
        val new = installed.toSet() - seen
        return if (new.isEmpty()) null else State(seen + new, hidden - new)
    }
}
