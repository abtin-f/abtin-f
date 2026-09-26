package com.abtin.iphonekeyboard

import android.content.Context
import android.content.SharedPreferences

/** Thin typed wrapper over the keyboard's SharedPreferences. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var soundEnabled: Boolean
        get() = sp.getBoolean("sound_enabled", true)
        set(v) = sp.edit().putBoolean("sound_enabled", v).apply()

    /** 0..100 */
    var soundVolume: Int
        get() = sp.getInt("sound_volume", 60)
        set(v) = sp.edit().putInt("sound_volume", v.coerceIn(0, 100)).apply()

    /** Mute clicks when the phone is on silent / vibrate, like iOS. */
    var respectSilent: Boolean
        get() = sp.getBoolean("respect_silent", false)
        set(v) = sp.edit().putBoolean("respect_silent", v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    /** 0..100 */
    var vibrateStrength: Int
        get() = sp.getInt("vibrate_strength", 35)
        set(v) = sp.edit().putInt("vibrate_strength", v.coerceIn(0, 100)).apply()

    var keyPreview: Boolean
        get() = sp.getBoolean("key_preview", true)
        set(v) = sp.edit().putBoolean("key_preview", v).apply()

    var autoCap: Boolean
        get() = sp.getBoolean("auto_cap", true)
        set(v) = sp.edit().putBoolean("auto_cap", v).apply()

    var doubleSpacePeriod: Boolean
        get() = sp.getBoolean("double_space", true)
        set(v) = sp.edit().putBoolean("double_space", v).apply()

    var suggestions: Boolean
        get() = sp.getBoolean("suggestions", true)
        set(v) = sp.edit().putBoolean("suggestions", v).apply()

    var persianDigits: Boolean
        get() = sp.getBoolean("persian_digits", true)
        set(v) = sp.edit().putBoolean("persian_digits", v).apply()

    /** "auto", "light" or "dark" */
    var theme: String
        get() = sp.getString("theme", "auto") ?: "auto"
        set(v) = sp.edit().putString("theme", v).apply()

    /** Keyboard height in percent, 80..125 */
    var heightPercent: Int
        get() = sp.getInt("height", 100)
        set(v) = sp.edit().putInt("height", v.coerceIn(80, 125)).apply()

    var lang: Lang
        get() = if (sp.getString("lang", "fa") == "en") Lang.EN else Lang.FA
        set(v) = sp.edit().putString("lang", if (v == Lang.EN) "en" else "fa").apply()

    var recentEmoji: List<String>
        get() = sp.getString("recent_emoji", "")!!.split(' ').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("recent_emoji", v.joinToString(" ")).apply()
}
