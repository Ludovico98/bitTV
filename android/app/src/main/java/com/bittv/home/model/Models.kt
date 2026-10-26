package com.bittv.home.model

/** Streaming services Ludovico can subscribe to. Free ones are always counted as available. */
enum class Svc(val label: String, val free: Boolean) {
    NETFLIX("Netflix", false),
    PRIME("Prime Video", false),
    NOW("NOW", false),
    PARAMOUNT("Paramount+", false),
    APPLETV("Apple TV", false),
    DISNEY("Disney+", false),
    TUBI("Tubi", true),
    ITVX("ITVX", true),
    LA7("La7", true),
    RAIPLAY("RaiPlay", true),
}

enum class TitleType { SERIES, MOVIE }

/** The poster wordmark style, as in the prototype. */
enum class TitleFont { BEBAS, PLAYFAIR, OSWALD, INTER }

/** A show or film. [c1]/[c2] are ARGB poster colours (the drawn posters, and the placeholder while artwork loads). */
data class Title(
    val id: String,
    val name: String,
    val type: TitleType,
    val genres: List<String>,
    val services: List<Svc>,
    val c1: Long,
    val c2: Long,
    val year: Int,
    val score: Double,
    val font: TitleFont,
    val blurb: String,
    /** TMDB backdrop path ("/abc.jpg"); null for the demo posters, which are drawn. */
    val art: String? = null,
    /** His apps that rent or sell it (Amazon's store opens in Prime Video, Apple's in Apple TV). */
    val buy: List<Svc> = emptyList(),
    /** Where else it streams or sells that isn't one of his apps ("Crunchyroll", "Sky Store"). */
    val elsewhere: List<String> = emptyList(),
    /** Kinds TMDB's genres don't name, from its keywords: [Tags]. */
    val tags: List<String> = emptyList(),
    /** The original language (ISO 639-1, "it"); empty when unknown (the demo's titles). */
    val lang: String = "",
)

/** Title.tags: the Home rows for kinds of show that TMDB marks with keywords rather than genres. */
object Tags {
    const val SITCOM = "sitcom"; const val PROCEDURAL = "procedural"; const val ANTHOLOGY = "anthology"
    const val LIMITED = "limited"; const val BIOGRAPHY = "biography"; const val HORROR = "horror"
    const val ROMANCE = "romance"; const val THRILLER = "thriller"
}

/** A TMDB image URL at one of its fixed widths (300, 780, 1280). */
fun tmdbImage(path: String, width: Int): String = "https://image.tmdb.org/t/p/w$width$path"

data class Progress(val titleId: String, val fraction: Float, val label: String)

/** An app on the left app bar. [packages] are candidate package names, checked against the TV's installed apps. */
data class AppEntry(val id: String, val label: String, val packages: List<String>, val svc: Svc?)

data class Prefs(
    val subs: Set<Svc> = Svc.entries.toSet() - Svc.PARAMOUNT,
    val watchLater: List<String> = listOf("longtide", "loworbit", "grandeur", "commissario", "static"),
    val animations: Boolean = true,
    val overscanGuides: Boolean = false,
    /** His order for the app bar (ids); empty = the default order. */
    val appOrder: List<String> = emptyList(),
    /** His order for the Home rows (keys); empty = the default order. */
    val rowOrder: List<String> = emptyList(),
    /** Apps switched off the app bar in Settings (ids). */
    val hiddenApps: Set<String> = emptySet(),
    /** Every app BitTV has met (see AppShelf); null until the TV's apps were first read. */
    val seenApps: Set<String>? = null,
)

fun Title.isAvailable(subs: Set<Svc>): Boolean = services.any { it in subs }

/**
 * The app a title opens in: the first service he has, otherwise the first one it streams on, otherwise an app that
 * rents or sells it; null when none of his apps has it at all.
 */
fun Title.mainService(subs: Set<Svc>): Svc? = services.firstOrNull { it in subs } ?: services.firstOrNull() ?: buy.firstOrNull()

/** For a title not on his subscriptions: how he could get it ("Paramount+", "Rent or buy", "Crunchyroll"); null if nowhere. */
fun Title.offer(subs: Set<Svc>): String? = when {
    isAvailable(subs) -> null
    services.isNotEmpty() -> services.first().label
    buy.isNotEmpty() -> "Rent or buy"
    else -> elsewhere.firstOrNull()
}

enum class WeatherKind { SUN, CLOUD, RAIN }

data class WeatherNow(val tempC: Int, val kind: WeatherKind)

/** What is playing in a media app. [art] is the cover (a Bitmap from the session, or anything Coil loads); [source] its package. */
data class NowPlaying(val track: String, val artist: String, val durationSec: Int, val positionSec: Int, val playing: Boolean,
                      val art: Any? = null, val source: String? = null)

/** [link]: the story, opened in the browser from Home. */
data class NewsItem(val headline: String, val source: String, val link: String? = null)

/** A phone, tablet or computer that can cast or mirror to the TV. */
data class CastDevice(val id: String, val name: String, val kind: Kind) {
    enum class Kind(val label: String, val via: String) {
        IPHONE("iPhone", "AirPlay"), IPAD("iPad", "AirPlay"), MAC("Mac", "AirPlay"), ANDROID("Android", "Google Cast"),
    }
}
