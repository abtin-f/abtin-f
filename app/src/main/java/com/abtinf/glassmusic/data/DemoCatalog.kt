package com.abtinf.glassmusic.data

/**
 * Built-in sample library shown when the device has no local music (or permission is denied).
 * Demo tracks have no audio file: playback is simulated so every screen can be explored offline.
 * Lyrics exist only for the two fictional "(Demo)" songs and are original placeholder text.
 */
object DemoCatalog {
    private var nextId = -1L
    private var nextAdded = 1_700_000_000L

    private fun t(
        title: String, artist: String, album: String, seconds: Int,
        lyrics: List<LyricLine> = emptyList(),
    ): Track {
        nextAdded -= 86_400
        val n = -nextId.toInt()
        val flac = n % 3 != 0
        return Track(
            nextId--, title, artist, album, seconds * 1000L, null, null, nextAdded, lyrics,
            format = if (flac) "FLAC" else "M4A",
            bitrateKbps = if (flac) 700 + (n * 37) % 330 else 256 + (n * 13) % 96,
            sizeBytes = seconds * (if (flac) 110_000L else 40_000L),
            year = 2015 + n % 10, trackNo = 1 + n % 9, albumArtist = artist,
            folder = "Music/Demo/$album/",
        )
    }

    private fun l(text: String, translation: String? = null, at: Int) = LyricLine(at * 1000L, text, translation)

    fun tracks(): List<Track> {
        nextId = -1L
        nextAdded = 1_700_000_000L
        val spanish = listOf(
            l("Caminamos bajo la luz de la ciudad", "We walk under the city light", 8),
            l("Sin prisa, sin miedo, sin mirar atrás", "No rush, no fear, no looking back", 16),
            l("Tu mano en mi mano, el mundo a nuestros pies", "Your hand in my hand, the world at our feet", 24),
            l("Y la noche nos abraza otra vez", "And the night embraces us once again", 32),
            l("Juntos hasta que se apague el sol", "Together until the sun goes out", 42),
            l("Te guardo en cada canción", "I keep you in every song", 50),
            l("Suena el mar, suena el viento", "The sea sounds, the wind sounds", 60),
            l("Y tú sigues siendo mi lugar", "And you are still my place", 68),
            l("Bailemos despacio hasta el amanecer", "Let's dance slowly until dawn", 78),
            l("Que nada nos pueda separar", "May nothing be able to separate us", 86),
            l("Juntos hasta que se apague el sol", "Together until the sun goes out", 96),
            l("Oh, oh, oh, juntos hasta el final", "Oh, oh, oh, together until the end", 106),
            l("Las luces se van, pero tú te quedas", "The lights fade, but you stay", 118),
            l("Y yo no quiero otra canción", "And I want no other song", 128),
            l("Juntos hasta que se apague el sol", "Together until the sun goes out", 138),
            l("Juntos hasta el final", "Together until the end", 150),
        )
        val english = listOf(
            l("Headlights on the empty avenue", at = 6),
            l("Radio low, the city's fast asleep", at = 14),
            l("Every signal turns to green for us", at = 23),
            l("We're only chasing what we can't keep", at = 31),
            l("Drive, drive, let the night decide", at = 41),
            l("Windows down and nothing left to hide", at = 49),
            l("Neon rivers running through my mind", at = 59),
            l("Take the long way, leave the world behind", at = 67),
            l("Drive, drive, let the night decide", at = 78),
            l("Windows down and nothing left to hide", at = 86),
            l("And when the morning finds us on the coast", at = 97),
            l("We'll remember this the most", at = 106),
        )
        return listOf(
            t("Juntos Hasta Siempre (Demo)", "Demo Artist", "Sample Sessions", 168, spanish),
            t("Night Drive (Demo)", "Demo Artist", "Sample Sessions", 122, english),
            t("Illegal", "PinkPantheress", "Fancy That", 152),
            t("Pain", "PinkPantheress", "Heaven knows", 118),
            t("ILY", "Kapo & Myke Towers", "ILY - Single", 174),
            t("Let It Happen", "Tame Impala", "Currents", 467),
            t("Eventually", "Tame Impala", "Currents", 318),
            t("The Less I Know the Better", "Tame Impala", "Currents", 216),
            t("Borderline", "Tame Impala", "The Slow Rush", 237),
            t("Lost in Yesterday", "Tame Impala", "The Slow Rush", 249),
            t("On Track", "Tame Impala", "The Slow Rush", 226),
            t("Dracula", "Tame Impala", "Deadbeat", 200),
            t("Loser", "Tame Impala", "Deadbeat", 195),
            t("The Adults Are Talking", "The Strokes", "The New Abnormal", 308),
            t("Bad Decisions", "The Strokes", "The New Abnormal", 215),
            t("Reptilia", "The Strokes", "Room on Fire", 217),
            t("Congratulations", "MGMT", "Congratulations", 250),
            t("Electric Feel", "MGMT", "Oracular Spectacular", 229),
            t("Kids", "MGMT", "Oracular Spectacular", 303),
            t("Sweep Me Off My Feet", "Pond", "Tasmania", 255),
            t("Down the Line", "Beach Fossils", "Somersault", 207),
            t("Bad Decisions (Live)", "Beach Fossils", "Somersault", 193),
            t("Locket", "Crumb", "Jinx", 245),
            t("Lamb's Wool", "Crumb", "Ice Melt", 236),
            t("Nina", "Crumb", "Jinx", 222),
            t("Sunday Morning Loop", "Summer Walker", "Late Bloom", 201),
            t("Golden Hour Radio", "Bruno Mars", "Afterglow", 212),
            t("Electric Garden", "Charli xcx", "Night Bloom", 189),
            t("Paper Planes Again", "Taylor Swift", "Quiet Places", 244),
            t("Saturn Return", "TIGRA & SPNCR", "Orbit", 198),
        )
    }
}
