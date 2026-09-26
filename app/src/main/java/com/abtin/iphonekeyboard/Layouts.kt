package com.abtin.iphonekeyboard

enum class KeyType {
    CHAR, SHIFT, DELETE, SPACE, RETURN, MODE_NUM, MODE_SYM, MODE_ALPHA, GLOBE, EMOJI, ZWNJ, SPACER
}

enum class Lang { EN, FA }

enum class Page { ALPHA, NUM, SYM }

/** Field flavours that change the bottom row, like iOS does for e-mail and URL fields. */
enum class FieldKind { TEXT, EMAIL, URL }

class Key(
    val type: KeyType,
    val label: String = "",
    val output: String = label,
    val width: Float = 1f,
    val alternates: List<String> = emptyList(),
    /** Letter keys are drawn in upper case while shift is active (English only). */
    val shiftable: Boolean = false,
)

class Row(val keys: List<Key>)

class KeyboardLayout(val lang: Lang, val page: Page, val shifted: Boolean, val rows: List<Row>) {
    /** Persian letter pages are right-to-left: suggestion order and text direction follow it. */
    val rtl: Boolean get() = lang == Lang.FA
}

object Layouts {

    private val EN_ALTERNATES = mapOf(
        "e" to "e è é ê ë ē ė ę",
        "y" to "y ÿ",
        "u" to "u û ü ù ú ū",
        "i" to "i î ï í ī į ì",
        "o" to "o ô ö ò ó œ ø ō õ",
        "a" to "a à á â ä æ ã å ā",
        "s" to "s ß ś š",
        "l" to "l ł",
        "z" to "z ž ź ż",
        "c" to "c ç ć č",
        "n" to "n ñ ń",
        "0" to "0 °",
        "-" to "- – — •",
        "/" to "/ \\",
        "$" to "$ ₽ ¥ € ¢ £ ₩",
        "&" to "& §",
        "\"" to "\" „ “ ” « »",
        "." to ". …",
        "?" to "? ¿",
        "!" to "! ¡",
        "'" to "' ‘ ’ `",
        "%" to "% ‰",
        "=" to "= ≠ ≈",
    )

    private val FA_ALTERNATES = mapOf(
        "ا" to "ا آ أ إ ء",
        "ی" to "ی ئ ي ى",
        "ه" to "ه ۀ ة",
        "و" to "و ؤ",
        "ک" to "ک ك",
        "ت" to "ت ة",
        "ز" to "ز ژ",
        "ج" to "ج چ",
        "ل" to "ل لا",
        "۰" to "۰ ٫ °",
        "." to ". … ٫",
        "،" to "، ,",
        "؟" to "؟ ?",
        "؛" to "؛ ;",
        "-" to "- – — ـ",
        "﷼" to "﷼ $ € £ ¥",
        "\"" to "\" « » „ “ ”",
    )

    private fun alts(ch: String, lang: Lang): List<String> {
        val map = if (lang == Lang.EN) EN_ALTERNATES else FA_ALTERNATES
        return map[ch]?.split(' ') ?: emptyList()
    }

    private fun chars(s: String, lang: Lang, shiftable: Boolean = false): List<Key> =
        s.split(' ').filter { it.isNotEmpty() }.map {
            Key(KeyType.CHAR, it, it, 1f, alts(it, lang), shiftable)
        }

    private fun spacer(w: Float) = Key(KeyType.SPACER, width = w)

    fun get(lang: Lang, page: Page, shifted: Boolean, field: FieldKind, persianDigits: Boolean): KeyboardLayout {
        val rows = when (page) {
            Page.ALPHA -> if (lang == Lang.EN) englishAlpha() else if (shifted) persianShift() else persianAlpha()
            Page.NUM -> numbers(lang, persianDigits)
            Page.SYM -> symbols(lang)
        }
        return KeyboardLayout(lang, page, shifted, rows + bottomRow(lang, page, field))
    }

    private fun englishAlpha() = listOf(
        Row(chars("q w e r t y u i o p", Lang.EN, true)),
        Row(listOf(spacer(0.5f)) + chars("a s d f g h j k l", Lang.EN, true) + spacer(0.5f)),
        Row(
            listOf(Key(KeyType.SHIFT, width = 1.35f), spacer(0.15f)) +
                chars("z x c v b n m", Lang.EN, true) +
                listOf(spacer(0.15f), Key(KeyType.DELETE, width = 1.35f))
        ),
    )

    private fun persianAlpha() = listOf(
        Row(chars("ض ص ث ق ف غ ع ه خ ح ج چ", Lang.FA)),
        Row(listOf(spacer(0.5f)) + chars("ش س ی ب ل ا ت ن م ک گ", Lang.FA) + spacer(0.5f)),
        Row(
            listOf(Key(KeyType.SHIFT, width = 1.5f)) +
                chars("ظ ط ژ ز ر ذ د پ و", Lang.FA) +
                listOf(Key(KeyType.DELETE, width = 1.5f))
        ),
    )

    /** Combining marks get a dotted circle so they are visible on the key cap. */
    private fun mark(m: String) = Key(KeyType.CHAR, "◌$m", m)

    private fun persianShift() = listOf(
        Row(listOf("ً", "ٌ", "ٍ", "َ", "ُ", "ِ", "ّ", "ْ").map { mark(it) } + chars("] [ } {", Lang.FA)),
        Row(listOf(spacer(0.5f)) + chars("ؤ ئ ي إ أ آ ة » « : ؛", Lang.FA) + spacer(0.5f)),
        Row(
            listOf(Key(KeyType.SHIFT, width = 1.5f)) +
                chars("ك", Lang.FA) + mark("ٔ") + chars("ء ـ", Lang.FA) + mark("ٰ") +
                chars("٪ × < >", Lang.FA) +
                listOf(Key(KeyType.DELETE, width = 1.5f))
        ),
    )

    private fun numbers(lang: Lang, persianDigits: Boolean): List<Row> {
        val fa = lang == Lang.FA
        val digits = if (fa && persianDigits) "۱ ۲ ۳ ۴ ۵ ۶ ۷ ۸ ۹ ۰" else "1 2 3 4 5 6 7 8 9 0"
        val row2 = if (fa) "- / : ؛ ( ) ﷼ & @ \"" else "- / : ; ( ) $ & @ \""
        val row3 = if (fa) ". ، ؟ ! '" else ". , ? ! '"
        return listOf(
            Row(chars(digits, lang)),
            Row(chars(row2, lang)),
            Row(
                listOf(Key(KeyType.MODE_SYM, "#+=", width = 1.35f), spacer(0.15f)) +
                    chars(row3, lang).map { Key(it.type, it.label, it.output, 1.4f, it.alternates) } +
                    listOf(spacer(0.15f), Key(KeyType.DELETE, width = 1.35f))
            ),
        )
    }

    private fun symbols(lang: Lang): List<Row> {
        val fa = lang == Lang.FA
        val row3 = if (fa) ". ، ؟ ! '" else ". , ? ! '"
        return listOf(
            Row(chars("[ ] { } # % ^ * + =", lang)),
            Row(chars(if (fa) "_ \\ | ~ < > € £ $ •" else "_ \\ | ~ < > € £ ¥ •", lang)),
            Row(
                listOf(Key(KeyType.MODE_NUM, if (fa) "۱۲۳" else "123", width = 1.35f), spacer(0.15f)) +
                    chars(row3, lang).map { Key(it.type, it.label, it.output, 1.4f, it.alternates) } +
                    listOf(spacer(0.15f), Key(KeyType.DELETE, width = 1.35f))
            ),
        )
    }

    private fun bottomRow(lang: Lang, page: Page, field: FieldKind): Row {
        val fa = lang == Lang.FA
        val modeKey = if (page == Page.ALPHA) {
            Key(KeyType.MODE_NUM, if (fa) "۱۲۳" else "123", width = 1.25f)
        } else {
            Key(KeyType.MODE_ALPHA, if (fa) "الفبا" else "ABC", width = 1.25f)
        }
        val globe = Key(KeyType.GLOBE, width = 1.1f)
        val emoji = Key(KeyType.EMOJI, width = 1.1f)
        val ret = Key(KeyType.RETURN, width = 2.5f)
        val spaceLabel = if (fa) "فاصله" else "space"
        return when {
            field == FieldKind.EMAIL -> Row(
                listOf(
                    modeKey, globe,
                    Key(KeyType.SPACE, spaceLabel, " ", 2.95f),
                    Key(KeyType.CHAR, "@", "@", 1.1f),
                    Key(KeyType.CHAR, ".", ".", 1.1f),
                    ret,
                )
            )
            field == FieldKind.URL -> Row(
                listOf(
                    modeKey, globe,
                    Key(KeyType.CHAR, ".", ".", 1.1f),
                    Key(KeyType.CHAR, "/", "/", 1.1f),
                    Key(KeyType.CHAR, ".com", ".com", 2.95f, listOf(".com", ".ir", ".net", ".org", ".edu")),
                    ret,
                )
            )
            fa -> Row(
                listOf(
                    modeKey, globe, emoji,
                    Key(KeyType.SPACE, spaceLabel, " ", 3.55f),
                    Key(KeyType.ZWNJ, "نیم‌فاصله", "\u200C", 1.2f),
                    Key(KeyType.RETURN, width = 1.8f),
                )
            )
            else -> Row(listOf(modeKey, globe, emoji, Key(KeyType.SPACE, spaceLabel, " ", 4.05f), ret))
        }
    }
}
