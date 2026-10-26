package com.example.persona.features.chat_import.domain.model

enum class ChatFormat(val displayName: String) {
    WHATSAPP_ENGLISH_US("WhatsApp English (US)"),
    WHATSAPP_ARABIC("WhatsApp Arabic"),
    WHATSAPP_EUROPEAN("WhatsApp European"),
    UNKNOWN("Unknown Format");

    companion object {
        /** Regex patterns for the timestamp prefix of each format */
        val REGEX_ENGLISH_US: Regex =
            Regex("""^(\d{1,2}/\d{1,2}/\d{2,4},?\s+\d{1,2}:\d{2}(?::\d{2})?\s*[APap][Mm])\s*[-–—]\s*""")

        val REGEX_ARABIC: Regex =
            Regex("""^\[(\d{1,2}/\d{1,2}/\d{2,4},?\s+\d{1,2}:\d{2}(?::\d{2})?\s*[صم]?)\]\s*""")

        val REGEX_EUROPEAN: Regex =
            Regex("""^(\d{1,2}\.\d{1,2}\.\d{2,4},?\s+\d{1,2}:\d{2}(?::\d{2})?)\s*[-–—]\s*""")
    }
}
