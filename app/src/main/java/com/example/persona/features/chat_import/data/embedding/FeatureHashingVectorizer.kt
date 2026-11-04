package com.example.persona.features.chat_import.data.embedding

import kotlin.math.sqrt

/**
 * Deterministic, lightweight Multilingual Feature Hashing & BM25 Vector Engine.
 *
 * Implements:
 * 1. Egyptian & Gulf Arabic + English commerce query expansion & synonym dictionary.
 * 2. Arabic letter normalization and diacritic removal.
 * 3. Word unigram, bigram, and subword character n-gram (3-gram, 4-gram) feature extraction.
 * 4. BM25 term-frequency saturation weighting (k1 = 1.2).
 * 5. Signed feature hashing (FNV-1a) mapping onto a 384-dimensional space.
 * 6. L2 normalization for instant cosine similarity via dot product.
 */
object FeatureHashingVectorizer {

    const val DIMENSION = 384
    private const val K1 = 1.2f

    private val SYNONYM_GROUPS: List<List<String>> = listOf(
        listOf("هاتف", "تليفون", "موبايل", "جوال", "فون", "رقم", "رقمكم", "ارقام", "أرقام", "خدمة العملاء", "خدمه العملاء", "اتصال", "phone", "mobile", "call"),
        listOf("متجر", "محل", "ستور", "فرع", "فروع", "مكان", "مكانكم", "عنوان", "عنوانكم", "store", "shop", "branch", "location"),
        listOf("سعر", "اسعار", "أسعار", "بكام", "تكلفة", "تمن", "فلوس", "مصاريف", "حساب", "price", "cost"),
        listOf("شحن", "توصيل", "دليفري", "مندوب", "مصاريف الشحن", "شحن المحافظات", "delivery", "shipping"),
        listOf("طلب", "طلبي", "طلبك", "طلبات", "اوردر", "أوردر", "اوردرات", "أوردرات", "حجز", "شراء", "order"),
        listOf("مقاس", "مقاسات", "سايز", "حجم", "احجام", "أحجام", "size", "sizes"),
        listOf("لون", "الوان", "ألوان", "كولور", "color", "colors"),
        listOf("استبدال", "استرجاع", "ترجيع", "تبديل", "سياسة الاسترجاع", "سياسة الاستبدال", "ضمان", "exchange", "refund", "return"),
        listOf("خامة", "قماش", "قطن", "ميلتون", "صوف", "كتان", "خامات", "material", "fabric", "cotton"),
        listOf("فيسبوك", "فيس", "facebook", "fb"),
        listOf("انستجرام", "انستقرام", "انستا", "instagram", "ig"),
        listOf("تيك توك", "تيكتوك", "تيك", "tiktok"),
        listOf("واتساب", "الواتس", "whatsapp"),
        listOf("موقع", "ويب سايت", "ويبسايت", "رابط", "الرابط", "لينك", "اللينك", "صفحة", "website", "link", "url"),
        listOf("هودي", "هوديز", "سويت شيرت", "سويت شيرتس", "hoodie", "hoodies", "sweatshirt"),
        listOf("تيشيرت", "تي شيرت", "قميص", "بلوزة", "tshirt", "t-shirt", "shirt"),
        listOf("بنطلون", "بنطلونات", "جينز", "سروال", "pants", "trousers", "jeans"),
        listOf("اوفرسايز", "اوفر سايز", "واسع", "oversize", "oversized")
    )

    fun normalizeArabic(text: String): String {
        var str = text
        // Remove Arabic diacritics / tashkeel
        str = str.replace(Regex("""[\u064B-\u065F\u0670]"""), "")
        // Normalize Alef variants
        str = str.replace(Regex("""[إأآا]"""), "ا")
        // Normalize Taa Marbuta
        str = str.replace('ة', 'ه')
        // Normalize Yaa variants
        str = str.replace('ى', 'ي')
        // Remove Tatweel / Kashida
        str = str.replace("ـ", "")
        return str
    }

    fun expandSynonyms(text: String): String {
        val normalized = normalizeArabic(text).lowercase()
        val tokens = normalized.split(Regex("""[\s,،؛?؟!.\-_/]+""")).filter { it.isNotBlank() }
        val expandedTokens = mutableListOf<String>()
        expandedTokens.addAll(tokens)

        for (token in tokens) {
            for (group in SYNONYM_GROUPS) {
                val matches = group.any { syn ->
                    val normSyn = normalizeArabic(syn).lowercase()
                    token == normSyn || (token.length >= 4 && normSyn.length >= 4 && (token.contains(normSyn) || normSyn.contains(token)))
                }
                if (matches) {
                    for (syn in group) {
                        expandedTokens.add(normalizeArabic(syn).lowercase())
                    }
                }
            }
        }
        return expandedTokens.joinToString(" ")
    }

    fun vectorize(rawText: String): FloatArray {
        val expanded = expandSynonyms(rawText)
        val normalized = normalizeArabic(expanded).lowercase()
        val tokens = normalized.split(Regex("""[\s,،؛?؟!.\-_/]+""")).filter { it.length > 1 }

        val termFrequencies = mutableMapOf<String, Float>()

        // 1. Word unigrams
        for (token in tokens) {
            termFrequencies[token] = (termFrequencies[token] ?: 0f) + 1.5f
        }

        // 2. Word bigrams
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]}_${tokens[i + 1]}"
            termFrequencies[bigram] = (termFrequencies[bigram] ?: 0f) + 2.0f
        }

        // 3. Subword character n-grams (3-grams and 4-grams)
        for (token in tokens) {
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val trigram = "c3_${token.substring(i, i + 3)}"
                    termFrequencies[trigram] = (termFrequencies[trigram] ?: 0f) + 0.8f
                }
            }
            if (token.length >= 4) {
                for (i in 0..token.length - 4) {
                    val fourgram = "c4_${token.substring(i, i + 4)}"
                    termFrequencies[fourgram] = (termFrequencies[fourgram] ?: 0f) + 1.0f
                }
            }
        }

        val vector = FloatArray(DIMENSION)
        if (termFrequencies.isEmpty()) {
            return vector
        }

        for ((feature, tf) in termFrequencies) {
            // BM25 term frequency saturation
            val weight = (tf * (K1 + 1f)) / (tf + K1)

            // 32-bit FNV-1a signed feature hash
            val hash = fnv1aHash(feature)
            val index = (hash.toLong() and 0x7FFFFFFF) % DIMENSION
            val sign = if ((hash and 1) == 0) 1.0f else -1.0f

            vector[index.toInt()] += sign * weight
        }

        // L2 Normalization
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    private fun fnv1aHash(str: String): Int {
        var hash = 0x811c9dc5.toInt()
        val prime = 0x01000193
        for (i in 0 until str.length) {
            hash = hash xor str[i].code
            hash *= prime
        }
        return hash
    }
}
