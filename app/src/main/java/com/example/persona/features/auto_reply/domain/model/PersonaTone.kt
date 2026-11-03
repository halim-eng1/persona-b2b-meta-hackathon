package com.example.persona.features.auto_reply.domain.model

enum class PersonaTone(
    val id: String,
    val titleArabic: String,
    val subtitle: String,
    val samplePreview: String,
    val samplePreviewEn: String = samplePreview
) {
    WITTY_EGYPTIAN(
        id = "WITTY_EGYPTIAN",
        titleArabic = "خدمة العملاء المصري",
        subtitle = "Egyptian Local Style",
        samplePreview = "أهلاً بيك يا فندم منورنا والله ❤️ قولّي أقدر أساعدك في إيه النهاردة؟",
        samplePreviewEn = "Welcome! We're glad to have you here ❤️ How can I help you today?"
    ),
    STANDARD_POLITE(
        id = "STANDARD_POLITE",
        titleArabic = "خدمة عملاء كلاسيك",
        subtitle = "Polite & Standard",
        samplePreview = "أهلاً بحضرتك يا فندم! نورتنا 😊 أقدر أساعدك إزاي النهارده؟",
        samplePreviewEn = "Hello and welcome! 😊 How can I help you today?"
    );

    fun getLocalizedPreview(isArabic: Boolean): String = if (isArabic) samplePreview else samplePreviewEn
    fun getLocalizedTitle(isArabic: Boolean): String = if (isArabic) titleArabic else subtitle

    companion object {
        val DEFAULT = STANDARD_POLITE

        fun fromString(value: String?): PersonaTone {
            if (value == null) return DEFAULT
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                it.id.equals(value, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}
