package com.abtin.iphonekeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** iOS keyboard colours (light and dark appearance). */
class KeyboardTheme(
    val dark: Boolean,
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val special: Int,
    val specialPressed: Int,
    val text: Int,
    val shadow: Int,
    val divider: Int,
    val action: Int = Color.rgb(0, 122, 255),
    val actionPressed: Int = Color.rgb(0, 96, 210),
    val actionText: Int = Color.WHITE,
) {
    companion object {
        val LIGHT = KeyboardTheme(
            dark = false,
            background = Color.rgb(209, 212, 218),
            key = Color.WHITE,
            keyPressed = Color.rgb(171, 176, 186),
            special = Color.rgb(171, 176, 186),
            specialPressed = Color.WHITE,
            text = Color.BLACK,
            shadow = Color.rgb(137, 138, 141),
            divider = Color.rgb(184, 187, 194),
        )
        val DARK = KeyboardTheme(
            dark = true,
            background = Color.rgb(43, 43, 45),
            key = Color.rgb(107, 107, 107),
            keyPressed = Color.rgb(70, 70, 70),
            special = Color.rgb(70, 70, 70),
            specialPressed = Color.rgb(107, 107, 107),
            text = Color.WHITE,
            shadow = Color.argb(160, 0, 0, 0),
            divider = Color.rgb(80, 80, 82),
        )

        fun resolve(context: Context, prefs: Prefs): KeyboardTheme = when (prefs.theme) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> {
                val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
            }
        }
    }
}
