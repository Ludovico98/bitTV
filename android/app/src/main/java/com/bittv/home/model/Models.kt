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

/** A show or film. [c1]/[c2] are ARGB poster colours, also used for the ambient colour bleed. */
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
)

/** A TMDB image URL at one of its fixed widths (300, 780, 1280). */
fun tmdbImage(path: String, width: Int): String = "https://image.tmdb.org/t/p/w$width$path"

data class Progress(val titleId: String, val fraction: Float, val label: String)

/** An app on the left app bar. [packages] are candidate package names, checked against the TV's installed apps. */
data class AppEntry(val id: String, val label: String, val packages: List<String>, val svc: Svc?)

data class Prefs(
    val subs: Set<Svc> = Svc.entries.toSet() - Svc.PARAMOUNT,
    val watchLater: List<String> = listOf("longtide", "loworbit", "grandeur", "commissario", "static"),
    val animations: Boolean = true,
    val ambient: Boolean = true,
    val voice: String? = null,
    val overscanGuides: Boolean = false,
)

fun Title.isAvailable(subs: Set<Svc>): Boolean = services.any { it in subs }

/** The service a title opens in: the first one he has, otherwise the first one listed. */
fun Title.mainService(subs: Set<Svc>): Svc = services.firstOrNull { it in subs } ?: services.first()

enum class WeatherKind { SUN, CLOUD, RAIN }

data class WeatherNow(val tempC: Int, val kind: WeatherKind)

data class NowPlaying(val track: String, val artist: String, val durationSec: Int, val positionSec: Int, val playing: Boolean)

data class NewsItem(val headline: String, val source: String)

/** A phone, tablet or computer that can cast or mirror to the TV. */
data class CastDevice(val id: String, val name: String, val kind: Kind) {
    enum class Kind(val label: String, val via: String) {
        IPHONE("iPhone", "AirPlay"), IPAD("iPad", "AirPlay"), MAC("Mac", "AirPlay"), ANDROID("Android", "Google Cast"),
    }
}
