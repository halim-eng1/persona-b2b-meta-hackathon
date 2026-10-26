package com.example.persona.features.chat_import.data.normalizer

import javax.inject.Inject

class ArabicTextNormalizer @Inject constructor() {

    companion object {
        // Arabic diacritics (tashkeel) Unicode range
        private val DIACRITICS_REGEX = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]")

        // Tatweel (kashida) — decorative elongation
        private const val TATWEEL = '\u0640'

        // Alef variants → standard Alef
        private val ALEF_VARIANTS = charArrayOf(
            '\u0623', // أ ALEF WITH HAMZA ABOVE
            '\u0625', // إ ALEF WITH HAMZA BELOW
            '\u0622', // آ ALEF WITH MADDA ABOVE
            '\u0671'  // ٱ ALEF WASLA
        )
        private const val ALEF_STANDARD = '\u0627' // ا

        // Teh Marbuta → Heh
        private const val TEH_MARBUTA = '\u0629' // ة
        private const val HEH = '\u0647'          // ه

        // Alef Maksura → Yaa
        private const val ALEF_MAKSURA = '\u0649' // ى
        private const val YAA = '\u064A'           // ي

        // Waw with Hamza → Waw
        private const val WAW_HAMZA = '\u0624'     // ؤ
        private const val WAW = '\u0648'            // و
    }

    /**
     * Returns null if the text contains no Arabic characters (pure Latin/emoji).
     * Returns the normalized text if Arabic content is detected.
     */
    fun normalize(text: String): String? {
        if (!containsArabic(text)) return null

        var result = text

        // 1. Remove diacritics (tashkeel)
        result = DIACRITICS_REGEX.replace(result, "")

        // 2. Remove tatweel
        result = result.replace(TATWEEL.toString(), "")

        // 3. Standardize Alef variants
        for (variant in ALEF_VARIANTS) {
            result = result.replace(variant, ALEF_STANDARD)
        }

        // 4. Teh Marbuta → Heh
        result = result.replace(TEH_MARBUTA, HEH)

        // 5. Alef Maksura → Yaa
        result = result.replace(ALEF_MAKSURA, YAA)

        // 6. Waw with Hamza → Waw
        result = result.replace(WAW_HAMZA, WAW)

        // 7. Normalize whitespace
        result = result.trim().replace(Regex("\\s+"), " ")

        return result
    }

    /**
     * Checks if the string contains at least one Arabic character.
     * Arabic Unicode block: U+0600 to U+06FF
     */
    fun containsArabic(text: String): Boolean {
        return text.any { it.code in 0x0600..0x06FF }
    }
}
