package com.abtinf.glassmusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Thin monochrome line icons (24x24 viewport, round caps). Tint them with `Icon(tint = ...)`. */
private class P(val d: String, val fill: Boolean = false)

private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r v${h - 2 * r}a$r $r 0 0 1 -$r $r h${-(w - 2 * r)}a$r $r 0 0 1 -$r -$r v${-(h - 2 * r)}a$r $r 0 0 1 $r -$r z"

private fun circ(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun icon(name: String, vararg paths: P, stroke: Float = 1.7f): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach { p ->
        val nodes = PathParser().parsePathString(p.d).toNodes()
        if (p.fill) {
            b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
        } else {
            b.addPath(
                pathData = nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    return b.build()
}

object AmIcons {
    private const val HOME = "M3.5 10.8L12 3.5l8.5 7.3V19.5a1.2 1.2 0 0 1-1.2 1.2H15v-6H9v6H4.7a1.2 1.2 0 0 1-1.2-1.2z"
    private val GRID = listOf(rr(4f, 4f, 7f, 7f, 1.8f), rr(13f, 4f, 7f, 7f, 1.8f), rr(4f, 13f, 7f, 7f, 1.8f), rr(13f, 13f, 7f, 7f, 1.8f))
    private val RADIO = listOf(
        circ(12f, 12f, 1.6f), "M8.6 8.6a5 5 0 0 0 0 6.8", "M15.4 8.6a5 5 0 0 1 0 6.8",
        "M5.6 5.6a9.2 9.2 0 0 0 0 12.8", "M18.4 5.6a9.2 9.2 0 0 1 0 12.8",
    )
    private val LIBRARY = listOf(
        rr(3.5f, 3.5f, 17f, 17f, 4.5f), "M10 15.5V8.6l5-1.1v6.6", circ(8.5f, 15.5f, 1.5f), circ(13.5f, 14.1f, 1.5f),
    )
    private const val SEARCH_D = "M3.5 10.5a7 7 0 1 0 14 0a7 7 0 1 0 -14 0M16 16l4.5 4.5"

    val Home = icon("home", P(HOME))
    val HomeSelected = icon("homeSel", P(HOME, fill = true))
    val New = icon("new", *GRID.map { P(it) }.toTypedArray())
    val NewSelected = icon("newSel", *GRID.map { P(it, fill = true) }.toTypedArray())
    val Radio = icon("radio", *RADIO.map { P(it) }.toTypedArray())
    val RadioSelected = icon("radioSel", *RADIO.map { P(it) }.toTypedArray(), stroke = 2.3f)
    val Library = icon("library", *LIBRARY.map { P(it) }.toTypedArray())
    val LibrarySelected = icon("libSel", *LIBRARY.map { P(it) }.toTypedArray(), stroke = 2.3f)
    val Search = icon("search", P(SEARCH_D))
    val SearchSelected = icon("searchSel", P(SEARCH_D), stroke = 2.4f)

    val Play = icon("play", P("M8 5.5v13a.8.8 0 0 0 1.2.7l10.5-6.5a.8.8 0 0 0 0-1.4L9.2 4.8A.8.8 0 0 0 8 5.5z", true))
    val Pause = icon("pause", P(rr(6f, 4.5f, 4.2f, 15f, 1.3f), true), P(rr(13.8f, 4.5f, 4.2f, 15f, 1.3f), true))
    val Forward = icon(
        "forward",
        P("M3 6.5v11a.7.7 0 0 0 1.1.6l8-5.5a.7.7 0 0 0 0-1.2l-8-5.5A.7.7 0 0 0 3 6.5z", true),
        P("M12 6.5v11a.7.7 0 0 0 1.1.6l8-5.5a.7.7 0 0 0 0-1.2l-8-5.5A.7.7 0 0 0 12 6.5z", true),
    )
    val Rewind = icon(
        "rewind",
        P("M21 6.5v11a.7.7 0 0 1-1.1.6l-8-5.5a.7.7 0 0 1 0-1.2l8-5.5A.7.7 0 0 1 21 6.5z", true),
        P("M12 6.5v11a.7.7 0 0 1-1.1.6l-8-5.5a.7.7 0 0 1 0-1.2l8-5.5A.7.7 0 0 1 12 6.5z", true),
    )

    private const val STAR = "M12 3.8l2.4 5.1 5.6.7-4.1 3.9 1 5.5L12 16.2 7.1 19l1-5.5L4 9.6l5.6-.7z"
    val Star = icon("star", P(STAR))
    val StarFilled = icon("starFilled", P(STAR, true))
    val More = icon("more", P(circ(5.5f, 12f, 1.4f), true), P(circ(12f, 12f, 1.4f), true), P(circ(18.5f, 12f, 1.4f), true))
    val Close = icon("close", P("M6 6l12 12M18 6L6 18"))
    val Check = icon("check", P("M5 12.5l4.5 4.5L19 7.5"), stroke = 2.2f)
    val Undo = icon("undo", P("M9 14L4 9l5-5"), P("M4 9h10.5a5.5 5.5 0 0 1 0 11H11"))
    val Redo = icon("redo", P("M15 14l5-5-5-5"), P("M20 9H9.5a5.5 5.5 0 0 0 0 11H13"))
    val Plus = icon("plus", P("M12 5v14M5 12h14"))
    val Handle = icon("handle", P("M5 7.5h14M5 12h14M5 16.5h14"))
    val Mic = icon("mic", P("M12 3.5a3 3 0 0 0-3 3v5a3 3 0 0 0 6 0v-5a3 3 0 0 0-3-3z"), P("M6 11.5a6 6 0 0 0 12 0"), P("M12 17.5v3"))
    val Lyrics = icon(
        "lyrics",
        P("M5 4.5h14a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H11l-4.5 3.5V16.5H5A1.5 1.5 0 0 1 3.5 15V6A1.5 1.5 0 0 1 5 4.5z"),
        P("M8 9.5h8M8 12.5h5"),
    )
    val AirPlay = icon(
        "airplay",
        P("M6 17H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-1"),
        P("M12 15l5 6H7z"),
    )
    val Queue = icon(
        "queue",
        P("M9 7h11M9 12h11M9 17h11"),
        P(circ(4.6f, 7f, 1f), true), P(circ(4.6f, 12f, 1f), true), P(circ(4.6f, 17f, 1f), true),
    )
    val Shuffle = icon("shuffle", P("M16 4h4v4"), P("M4 20L20 4"), P("M20 16v4h-4"), P("M15 15l5 5"), P("M4 4l5 5"))
    val Repeat = icon("repeat", P("M17 2l4 4-4 4"), P("M3 12V10a4 4 0 0 1 4-4h14"), P("M7 22l-4-4 4-4"), P("M21 12v2a4 4 0 0 1-4 4H3"))
    val RepeatOne = icon(
        "repeatOne",
        P("M17 2l4 4-4 4"), P("M3 12V10a4 4 0 0 1 4-4h14"), P("M7 22l-4-4 4-4"), P("M21 12v2a4 4 0 0 1-4 4H3"),
        P("M11 10.5l1-.7v4.5"),
    )
    val ChevronRight = icon("chevRight", P("M9 5l7 7-7 7"), stroke = 2f)
    val ChevronLeft = icon("chevLeft", P("M15 5l-7 7 7 7"), stroke = 2.2f)
    val ChevronDown = icon("chevDown", P("M5 9l7 7 7-7"), stroke = 2f)
    val Sparkle = icon(
        "sparkle",
        P("M11 3.5l1.9 5.6 5.6 1.9-5.6 1.9L11 18.5l-1.9-5.6L3.5 11l5.6-1.9z"),
        P("M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z"),
    )
    val VolumeLow = icon("volLow", P("M4 9.5h3.5L12 5.5v13l-4.5-4H4z"), P("M15.5 9.5a3.5 3.5 0 0 1 0 5"))
    val VolumeHigh = icon("volHigh", P("M4 9.5h3.5L12 5.5v13l-4.5-4H4z"), P("M15.5 9.5a3.5 3.5 0 0 1 0 5"), P("M18.2 6.8a7 7 0 0 1 0 10.4"))
    val Download = icon("download", P("M12 4v11M7.5 11l4.5 4.5 4.5-4.5M5 19.5h14"))
    val Translate = icon(
        "translate",
        P("M4 5h9M8.5 3v2M6 5c.5 3 2.5 5.5 6 7M12 5c-.5 3-3 6.5-7.5 8.5"),
        P("M13.5 20l4-9 4 9M15 17h5"),
    )
    val Trash = icon("trash", P("M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"))
    val Person = icon("person", P(circ(12f, 8f, 4f)), P("M4.5 20.5a7.5 7.5 0 0 1 15 0"))
    val Note = icon("note", P("M9 18V6l10-2v12"), P(circ(6f, 18f, 3f)), P(circ(16f, 16f, 3f)))
    val Album = icon("album", P(circ(12f, 12f, 8.5f)), P(circ(12f, 12f, 2.5f)))
    val Playlist = icon("playlist", P("M4 7h12M4 12h12M4 17h7"), P("M17 14.5V20M17 14.5l4 1.2"), P(circ(15.2f, 20f, 1.8f)))
    val PlayNext = icon("playNext", P("M4 6h10M4 11h10M4 16h6"), P("M15 14.5v6l5-3z"))
    val AddToQueue = icon("addQueue", P("M4 6h14M4 11h14M4 16h8"), P("M17 15v6M14 18h6"))
    val Edit = icon("edit", P("M4 20h4L19 9l-4-4L4 16z"), P("M13.5 6.5l4 4"))
    val Shuffle2 = Shuffle
    val Share = icon("share", P("M12 15V4M8 8l4-4 4 4"), P("M7 11H6a1 1 0 0 0-1 1v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7a1 1 0 0 0-1-1h-1"))
    val Info = icon("info", P(circ(12f, 12f, 9f)), P("M12 11v5.5"), P("M12 7.6h.01"))
    val Moon = icon("moon", P("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"), P("M17 3.5v3M15.5 5h3"))
    val Device = icon("device", P(rr(6f, 3f, 12f, 18f, 3f)), P(circ(12f, 14.5f, 3.2f)), P("M12 6.6h.01"))
    val PlaylistAdd = icon("playlistAdd", P("M4 7h11M4 12h11M4 17h6"), P("M17 13.5v6M14 16.5h6"))
    val AlbumTile = icon("albumTile", P(rr(7f, 3f, 10f, 18f, 2.5f)), P("M10.5 17.5h3"))
    val Infinity = icon("infinity", P("M12 12c-2-2.5-3.5-4-5.5-4a4 4 0 0 0 0 8c2 0 3.5-1.5 5.5-4zm0 0c2 2.5 3.5 4 5.5 4a4 4 0 0 0 0-8c-2 0-3.5 1.5-5.5 4z"))
    val Mix = icon("mix", P("M12 20v-9"), P("M12 11L7 6M12 11l5-5"), P("M7 6v3.5M7 6h3.5M17 6v3.5M17 6h-3.5"))
    val Waveform = icon("waveform", P("M4 10v4M8 7v10M12 4v16M16 8v8M20 11v2"), stroke = 1.6f)
    val Filter = icon("filter", P("M4 7h16M7 12h10M10 17h4"))
}
