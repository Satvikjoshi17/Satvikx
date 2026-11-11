package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.entity.TrackEntity
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent acoustic & contextual vibe classification engine.
 * Maps metadata and environmental signals to distinct session listening modes and energy curves.
 */
@Singleton
class SessionVibeClassifier @Inject constructor() {

    companion object {
        val ALL_GENRE_KEYWORDS = listOf(
            "lofi", "chill", "acoustic", "rock", "pop", "hip hop", "rap",
            "edm", "synthwave", "ambient", "romantic", "bollywood",
            "punjabi", "classical", "jazz", "metal", "indie", "workout",
            "retro", "trap", "r&b", "soul", "electronic", "dance",
            "ghazal", "sufi", "devotional", "bhajan", "instrumental",
            "phonk", "hardstyle", "drill", "bhangra", "cyberpunk"
        )
    }

    /**
     * Classifies a track into one of the 8 canonical session modes.
     */
    fun classifyTrackVibe(track: TrackEntity): SessionVibeMode {
        val combined = "${track.title} ${track.artist}".lowercase(Locale.ROOT)

        return when {
            // 1. Gym Mode: Phonk, workout, high bpm, hardstyle, bass-boosted, speed-up
            combined.contains("phonk") || combined.contains("drift") || combined.contains("workout") ||
            combined.contains("gym") || combined.contains("hardstyle") || combined.contains("bass boost") ||
            combined.contains("kordhell") || combined.contains("brazilian phonk") || combined.contains("speed up") ||
            combined.contains("gym motivation") || combined.contains("hyperpop") ->
                SessionVibeMode.GYM_MODE

            // 2. Night Drive Mode: Synthwave, retrowave, night drive, 80s, cyberpunk, midnight
            combined.contains("synth") || combined.contains("retro") || combined.contains("cyber") ||
            combined.contains("neon") || combined.contains("night drive") || combined.contains("drive") ||
            combined.contains("after hours") || combined.contains("blinding lights") || combined.contains("80s") ||
            combined.contains("midnight") || combined.contains("the midnight") || combined.contains("synthwave") ||
            combined.contains("retrowave") || combined.contains("kavinsky") || combined.contains("fm-84") ||
            combined.contains("the weeknd") || combined.contains("post malone") || combined.contains("billie eilish") ->
                SessionVibeMode.NIGHT_DRIVE

            // 3. Lo-Fi Focus Mode: Chill, lofi, lo-fi, study, peaceful, sleep, acoustic, slowed, reverb
            combined.contains("lofi") || combined.contains("lo-fi") || combined.contains("chill") ||
            combined.contains("slowed") || combined.contains("reverb") || combined.contains("anuv jain") ||
            combined.contains("prateek kuhad") || combined.contains("zaeden") || combined.contains("study") ||
            combined.contains("ambient") || combined.contains("peaceful") || combined.contains("sleep") ||
            combined.contains("downtempo") || combined.contains("relax") || combined.contains("clairo") ||
            combined.contains("beabadoobee") || combined.contains("boywithuke") || combined.contains("d4vd") ||
            combined.contains("jeremy zucker") || combined.contains("olivia rodrigo") ->
                SessionVibeMode.LOFI_FOCUS

            // 4. Punjabi Mode: Desi swag, bhangra, punjabi chartbusters
            combined.contains("punjabi") || combined.contains("sidhu") || combined.contains("moose wala") ||
            combined.contains("karan aujla") || combined.contains("diljit") || combined.contains("dosanjh") ||
            combined.contains("ap dhillon") || combined.contains("shubh") || combined.contains("amrit maan") ||
            combined.contains("jatt") || combined.contains("bhangra") || combined.contains("desi") ->
                SessionVibeMode.PUNJABI

            // 5. Rap / Drill / Hip Hop Mode
            combined.contains("rap") || combined.contains("hip hop") || combined.contains("drill") ||
            combined.contains("drake") || combined.contains("travis scott") || combined.contains("kendrick") ||
            combined.contains("eminem") || combined.contains("divine") || combined.contains("emiway") ||
            combined.contains("mc stan") || combined.contains("seedhe maut") || combined.contains("kr\$na") ||
            combined.contains("trap") || combined.contains("freestyle") ->
                SessionVibeMode.RAP

            // 6. Devotional Mode: Spiritual, bhajans, mantras, aartis, chalisa, stotram, govind, hare murari
            combined.contains("bhajan") || combined.contains("aarti") || combined.contains("mantra") ||
            combined.contains("krishna") || combined.contains("shiva") || combined.contains("hanuman") ||
            combined.contains("ram") || combined.contains("devotional") || combined.contains("spiritual") ||
            combined.contains("meditation") || combined.contains("peaceful chant") || combined.contains("chalisa") ||
            combined.contains("stotram") || combined.contains("govind") || combined.contains("hare murari") ->
                SessionVibeMode.DEVOTIONAL

            // 7. Romantic Mode: Melodic Bollywood, acoustic love, romantic duets, global pop ballads
            combined.contains("arijit") || combined.contains("shreya") || combined.contains("atif") ||
            combined.contains("pritam") || combined.contains("jubin") || combined.contains("darshan raval") ||
            combined.contains("kesariya") || combined.contains("tum hi ho") || combined.contains("channa mereya") ||
            combined.contains("ishq") || combined.contains("love") || combined.contains("romantic") ||
            combined.contains("hawayein") || combined.contains("raataan") || combined.contains("sufi") ||
            combined.contains("ghazal") || combined.contains("jagjit") ||
            combined.contains("ed sheeran") || combined.contains("taylor swift") || combined.contains("lewis capaldi") ||
            combined.contains("james arthur") || combined.contains("shawn mendes") || combined.contains("bruno mars") ||
            combined.contains("adele") || combined.contains("stephen sanchez") || combined.contains("dean lewis") ||
            combined.contains("jvke") || combined.contains("john legend") || combined.contains("calum scott") ||
            combined.contains("passenger") || combined.contains("sam smith") || combined.contains("conan gray") ||
            combined.contains("ruth b") || combined.contains("alec benjamin") || combined.contains("lauv") ||
            combined.contains("perfect") || combined.contains("photograph") || combined.contains("thinking out loud") ||
            combined.contains("someone you loved") || combined.contains("say you won't let go") || combined.contains("until i found you") ||
            combined.contains("golden hour") || combined.contains("all of me") || combined.contains("ballad") ||
            combined.contains("acoustic") || combined.contains("sweetheart") || combined.contains("darling") ||
            combined.contains("lover") || combined.contains("you are the reason") || combined.contains("dandelions") ->
                SessionVibeMode.ROMANTIC

            // 8. Party Mode: EDM, dance, club, chartbusters, energetic remixes, high-energy pop
            combined.contains("dance") || combined.contains("club") || combined.contains("party") ||
            combined.contains("edm") || combined.contains("martin garrix") || combined.contains("alan walker") ||
            combined.contains("david guetta") || combined.contains("dj") || combined.contains("festival") ||
            combined.contains("remix") || combined.contains("dua lipa") || combined.contains("sabrina carpenter") ||
            combined.contains("tate mcrae") || combined.contains("miley cyrus") || combined.contains("harry styles") ||
            combined.contains("katy perry") || combined.contains("maroon 5") || combined.contains("justin bieber") ||
            combined.contains("kid laroi") || combined.contains("onerepublic") || combined.contains("chainsmokers") ||
            combined.contains("coldplay") || combined.contains("charlie puth") ->
                SessionVibeMode.PARTY

            else -> SessionVibeMode.DISCOVERY
        }
    }

    /**
     * Determines the energy level (0.0 to 1.0) of a track.
     */
    fun estimateEnergyLevel(track: TrackEntity): Float {
        val vibe = classifyTrackVibe(track)
        val combined = "${track.title} ${track.artist}".lowercase(Locale.ROOT)

        var energy = vibe.baseEnergy
        if (combined.contains("slowed") || combined.contains("acoustic") || combined.contains("unplugged")) {
            energy = (energy - 0.20f).coerceAtLeast(0.10f)
        }
        if (combined.contains("remix") || combined.contains("fast") || combined.contains("speed up") || combined.contains("phonk")) {
            energy = (energy + 0.15f).coerceAtMost(1.0f)
        }
        return energy
    }

    /**
     * Extracts recognized genre tags from the provided text.
     */
    fun detectGenres(text: String): List<String> {
        val lower = text.lowercase(Locale.ROOT)
        return ALL_GENRE_KEYWORDS.filter { lower.contains(it) }
    }

    /**
     * Generates a targeted search query for candidate retrieval matching the vibe.
     */
    fun getVibeSearchQuery(vibe: SessionVibeMode): String {
        return when (vibe) {
            SessionVibeMode.GYM_MODE -> "gym motivation high energy phonk songs audio"
            SessionVibeMode.NIGHT_DRIVE -> "synthwave retrowave night drive cyberpunk songs official audio"
            SessionVibeMode.LOFI_FOCUS -> "chill lofi acoustic indie study beats official audio"
            SessionVibeMode.ROMANTIC -> "romantic acoustic love songs official audio"
            SessionVibeMode.PUNJABI -> "punjabi chartbusters hit songs official audio"
            SessionVibeMode.RAP -> "top hip hop rap tracks official audio"
            SessionVibeMode.DEVOTIONAL -> "peaceful devotional spiritual bhajans audio"
            SessionVibeMode.PARTY -> "edm festival dance party hits official audio"
            SessionVibeMode.DISCOVERY -> "trending popular melody songs official audio"
        }
    }

    /**
     * Time-of-day awareness helper.
     */
    fun getTimeOfDayContext(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..21 -> "EVENING"
            else -> "LATE_NIGHT"
        }
    }
}

