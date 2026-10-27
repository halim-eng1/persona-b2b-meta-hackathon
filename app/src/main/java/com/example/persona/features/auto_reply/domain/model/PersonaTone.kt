package com.example.persona.features.auto_reply.domain.model

enum class PersonaTone(
    val id: String,
    val titleArabic: String,
    val subtitle: String,
    val samplePreview: String
) {
    WITTY_EGYPTIAN(
        id = "WITTY_EGYPTIAN",
        titleArabic = "ابن بلد لمّاح",
        subtitle = "Witty & Dynamic",
        samplePreview = "واقفين على رجلينا مستنيين الأوردر بتاعك أهو 😎 قولّي نجهزلك إيه؟"
    ),
    STANDARD_POLITE(
        id = "STANDARD_POLITE",
        titleArabic = "خدمة عملاء كلاسيك",
        subtitle = "Polite & Standard",
        samplePreview = "أهلاً بحضرتك يا فندم! نورتنا 😊 أقدر أساعدك إزاي النهارده؟"
    );

    companion object {
        val DEFAULT = WITTY_EGYPTIAN

        fun fromString(value: String?): PersonaTone {
            if (value == null) return DEFAULT
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                it.id.equals(value, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}
