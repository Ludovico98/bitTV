package com.bittv.home.data

import com.bittv.home.model.AppEntry
import com.bittv.home.model.Progress
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType

/** The prototype's fictional catalogue, used until a TMDB key is configured (plan 2). */
object DemoCatalog {
    private fun T(
        id: String, name: String, type: TitleType, genres: List<String>, services: List<Svc>,
        c1: Long, c2: Long, year: Int, score: Double, font: TitleFont, blurb: String,
    ) = Title(id, name, type, genres, services, c1, c2, year, score, font, blurb)

    val titles: List<Title> = listOf(
        T("epidemic", "Epidemic", TitleType.SERIES, listOf("Sci-Fi", "Drama"), listOf(Svc.NETFLIX), 0xFF12333F, 0xFFD0453A, 2026, 8.4, TitleFont.BEBAS,
            "A virologist races a contagion that rewrites the memories of everyone it touches."),
        T("eclipse", "Eclipse", TitleType.MOVIE, listOf("Sci-Fi", "Thriller"), listOf(Svc.APPLETV), 0xFF1C2747, 0xFFF0A35E, 2025, 8.1, TitleFont.PLAYFAIR,
            "When the sun goes dark for nine days, a city has to decide who it trusts."),
        T("northernline", "Northern Line", TitleType.SERIES, listOf("Crime", "Drama"), listOf(Svc.ITVX), 0xFF1D2B33, 0xFF6FB3C9, 2024, 7.9, TitleFont.INTER,
            "A transport cop follows a string of disappearances along the last train out of London."),
        T("longtide", "The Long Tide", TitleType.SERIES, listOf("Drama"), listOf(Svc.NOW), 0xFF13293D, 0xFF8FC1D4, 2025, 8.6, TitleFont.PLAYFAIR,
            "Three generations of a fishing family hold on as the sea takes their village."),
        T("kingsguard", "Kingsguard", TitleType.SERIES, listOf("Fantasy", "Action"), listOf(Svc.PRIME), 0xFF2A1A10, 0xFFE09B3D, 2026, 8.2, TitleFont.BEBAS,
            "The last sworn knight of a fallen crown escorts its heir across a burning kingdom."),
        T("loworbit", "Low Orbit", TitleType.MOVIE, listOf("Sci-Fi", "Adventure"), listOf(Svc.DISNEY), 0xFF0B1D3A, 0xFF57A6FF, 2025, 7.7, TitleFont.OSWALD,
            "A repair crew stranded above Earth has ninety minutes to bring themselves home."),
        T("ironveil", "Iron Veil", TitleType.MOVIE, listOf("Action", "Thriller"), listOf(Svc.NETFLIX, Svc.PRIME), 0xFF26170F, 0xFFFF6A2B, 2024, 7.4, TitleFont.BEBAS,
            "An ex-operative is pulled back for one extraction that was never meant to succeed."),
        T("quietfield", "Quiet Field", TitleType.MOVIE, listOf("Horror"), listOf(Svc.NOW), 0xFF1A1D14, 0xFF9FB36B, 2023, 7.2, TitleFont.PLAYFAIR,
            "Something in the barley answers back."),
        T("bakehouse", "The Bakehouse", TitleType.SERIES, listOf("Comedy"), listOf(Svc.ITVX, Svc.NETFLIX), 0xFF3A2414, 0xFFF2C46D, 2024, 7.8, TitleFont.INTER,
            "A chaotic family bakery in Leeds tries to survive its own owners."),
        T("grandeur", "Small Grandeur", TitleType.SERIES, listOf("Comedy", "Drama"), listOf(Svc.APPLETV), 0xFF2B1F3A, 0xFFE7A6D8, 2026, 8.3, TitleFont.PLAYFAIR,
            "A disgraced hotelier gets one last chance running the smallest hotel in Europe."),
        T("lisbon", "Last Stop Lisbon", TitleType.MOVIE, listOf("Romance", "Comedy"), listOf(Svc.PRIME), 0xFF3B1F24, 0xFFFFB38A, 2025, 7.1, TitleFont.PLAYFAIR,
            "Two strangers miss the same train and spend one long day in Lisbon."),
        T("deepline", "Deepline", TitleType.SERIES, listOf("Thriller"), listOf(Svc.NETFLIX), 0xFF06222B, 0xFF2EC4B6, 2025, 8.0, TitleFont.OSWALD,
            "A research station at the bottom of the Atlantic goes silent."),
        T("hollowcrown", "Hollow Crown Hill", TitleType.SERIES, listOf("Mystery"), listOf(Svc.DISNEY), 0xFF1E1A2E, 0xFFB39DDB, 2024, 7.6, TitleFont.PLAYFAIR,
            "A village where every house keeps one secret, and one of them is murder."),
        T("overtime", "Overtime", TitleType.MOVIE, listOf("Sport", "Drama"), listOf(Svc.NOW), 0xFF10261A, 0xFF46D17A, 2023, 7.3, TitleFont.BEBAS,
            "A non-league side goes on the cup run of the century."),
        T("ducklings", "Ducklings", TitleType.SERIES, listOf("Kids", "Comedy"), listOf(Svc.DISNEY), 0xFF1F3B5C, 0xFFFFD54F, 2025, 7.5, TitleFont.INTER,
            "Five ducklings, one pond, endless trouble."),
        T("piccolomondo", "Il Piccolo Mondo", TitleType.SERIES, listOf("Drama"), listOf(Svc.RAIPLAY), 0xFF2B2416, 0xFFE6C07B, 2024, 7.9, TitleFont.PLAYFAIR,
            "Una famiglia di Como attraversa il dopoguerra tra segreti e speranze."),
        T("commissario", "Commissario Neri", TitleType.SERIES, listOf("Crime"), listOf(Svc.RAIPLAY), 0xFF1B1F2A, 0xFFC94F4F, 2025, 8.0, TitleFont.OSWALD,
            "A Napoli, un commissario testardo riapre un caso chiuso da vent’anni."),
        T("propaganda", "Propaganda Live", TitleType.SERIES, listOf("Talk", "Comedy"), listOf(Svc.LA7), 0xFF2A0F12, 0xFFFF4D5A, 2026, 8.2, TitleFont.BEBAS,
            "La settimana raccontata con satira, musica e ospiti."),
        T("piazza", "Piazza Aperta", TitleType.SERIES, listOf("News", "Talk"), listOf(Svc.LA7), 0xFF0F1E2E, 0xFF4AA3DF, 2026, 7.0, TitleFont.INTER,
            "Inchieste e dibattito sui temi della settimana."),
        T("roadkings", "Road Kings", TitleType.MOVIE, listOf("Action"), listOf(Svc.TUBI), 0xFF2A1D0D, 0xFFFFA31A, 2019, 6.8, TitleFont.BEBAS,
            "Desert racers, stolen cash and a very angry sheriff."),
        T("diner", "Midnight Diner", TitleType.MOVIE, listOf("Comedy"), listOf(Svc.TUBI), 0xFF1A1430, 0xFFFF7AD9, 2021, 6.9, TitleFont.INTER,
            "One night shift, five customers, zero working fryers."),
        T("redharbour", "Red Harbour", TitleType.SERIES, listOf("Crime", "Thriller"), listOf(Svc.PARAMOUNT), 0xFF2D0D0D, 0xFFFF3B3B, 2025, 8.1, TitleFont.OSWALD,
            "A port city where the docks run on favours."),
        T("dustfrontier", "Dust Frontier", TitleType.SERIES, listOf("Western", "Drama"), listOf(Svc.PARAMOUNT), 0xFF3A2A17, 0xFFE5A663, 2024, 8.0, TitleFont.PLAYFAIR,
            "Settlers, cattle and a railroad nobody asked for."),
        T("skyward", "Skyward", TitleType.MOVIE, listOf("Animation", "Family"), listOf(Svc.DISNEY, Svc.APPLETV), 0xFF10325A, 0xFF7FD3FF, 2025, 7.8, TitleFont.INTER,
            "A girl builds wings from her grandfather’s old kites."),
        T("static", "Static", TitleType.SERIES, listOf("Sci-Fi", "Mystery"), listOf(Svc.APPLETV), 0xFF101418, 0xFF9AD1FF, 2026, 8.5, TitleFont.OSWALD,
            "A radio host starts receiving calls from next week."),
        T("harbourlights", "Harbour Lights", TitleType.SERIES, listOf("Drama", "Romance"), listOf(Svc.ITVX), 0xFF14283D, 0xFFFFCF8A, 2023, 7.4, TitleFont.PLAYFAIR,
            "A Cornish lifeboat crew and the town that depends on them."),
        T("nightshift", "Night Shift", TitleType.SERIES, listOf("Comedy"), listOf(Svc.NOW), 0xFF161A2F, 0xFF8C9EFF, 2025, 7.7, TitleFont.BEBAS,
            "The worst-run hospital night shift in Britain."),
        T("vanta", "Vanta", TitleType.MOVIE, listOf("Action", "Sci-Fi"), listOf(Svc.PRIME), 0xFF0C0C14, 0xFFA07BFF, 2026, 7.5, TitleFont.OSWALD,
            "A thief steals a material that swallows light, and the light wants it back."),
        T("wildcoast", "Wild Coast", TitleType.SERIES, listOf("Documentary"), listOf(Svc.NETFLIX, Svc.NOW), 0xFF0D2A24, 0xFF6EE7B7, 2024, 8.7, TitleFont.INTER,
            "A year on the wildest shoreline of the British Isles."),
        T("laughtrack", "Laugh Track", TitleType.MOVIE, listOf("Comedy"), listOf(Svc.NETFLIX), 0xFF3A1D0B, 0xFFFFB547, 2025, 7.0, TitleFont.BEBAS,
            "A failing stand-up accidentally becomes a cult leader."),
        T("marlowe", "The Marlowe Affair", TitleType.MOVIE, listOf("Mystery", "Thriller"), listOf(Svc.ITVX), 0xFF1B1B1B, 0xFFD4AF37, 2022, 7.6, TitleFont.PLAYFAIR,
            "A 1950s jewel theft with one too many suspects."),
        T("tenements", "Tenements", TitleType.SERIES, listOf("Drama", "Crime"), listOf(Svc.NOW), 0xFF22201C, 0xFFC8A27A, 2024, 8.3, TitleFont.OSWALD,
            "Glasgow, 1972: one building, one summer, one fire."),
        T("zerohour", "Zero Hour", TitleType.MOVIE, listOf("Action"), listOf(Svc.PARAMOUNT), 0xFF10131A, 0xFFFF8C42, 2025, 7.2, TitleFont.BEBAS,
            "Twelve minutes to stop a bridge from falling."),
        T("spacepuppies", "Space Puppies", TitleType.SERIES, listOf("Kids", "Animation"), listOf(Svc.DISNEY), 0xFF1A1446, 0xFFFFE066, 2025, 7.3, TitleFont.INTER,
            "Three puppies, one rocket, a galaxy of snacks."),
    )

    val progress: List<Progress> = listOf(
        Progress("epidemic", 0.62f, "S1 · E5 · 21 min left"),
        Progress("eclipse", 0.4f, "1 h 12 min left"),
        Progress("kingsguard", 0.75f, "S2 · E3 · 14 min left"),
        Progress("propaganda", 0.5f, "Ep. 12 · 48 min left"),
        Progress("northernline", 0.2f, "S1 · E2 · 38 min left"),
        Progress("deepline", 0.3f, "S1 · E1 · 33 min left"),
        Progress("redharbour", 0.55f, "S1 · E4 · 25 min left"),
    )

    // Package names to verify on the TV with `pm list packages` (plan 2); the first installed one wins.
    val apps: List<AppEntry> = listOf(
        AppEntry("netflix", "Netflix", listOf("com.netflix.ninja"), Svc.NETFLIX),
        AppEntry("prime", "Prime Video", listOf("com.amazon.amazonvideo.livingroom"), Svc.PRIME),
        AppEntry("now", "NOW", listOf("com.nowtv.uk", "com.bskyb.nowtv.beta"), Svc.NOW),
        AppEntry("paramount", "Paramount+", listOf("com.cbs.ott", "com.cbs.ca"), Svc.PARAMOUNT),
        AppEntry("appletv", "Apple TV", listOf("com.apple.atve.androidtv.appletv"), Svc.APPLETV),
        AppEntry("spotify", "Spotify", listOf("com.spotify.tv.android"), null),
        AppEntry("smarttube", "SmartTube", listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv"), null),
        AppEntry("disney", "Disney+", listOf("com.disney.disneyplus"), Svc.DISNEY),
        AppEntry("tubi", "Tubi", listOf("com.tubitv"), Svc.TUBI),
        AppEntry("itvx", "ITVX", listOf("air.ITVMobilePlayer", "com.itv.itvx"), Svc.ITVX),
        AppEntry("la7", "La7", listOf("it.la7.la7tv", "it.la7.app"), Svc.LA7),
        AppEntry("raiplay", "RaiPlay", listOf("it.rainet.androidtv", "it.rainet.raiplay"), Svc.RAIPLAY),
        AppEntry("playstore", "Play Store", listOf("com.android.vending"), null),
        AppEntry("buttonmapper", "Button Mapper", listOf("flar2.homebutton"), null),
    )
}
