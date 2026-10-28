package com.example.persona.features.chat_import.data.parser

import java.net.URI

/**
 * Extracts and deduplicates the primary store website / e-commerce storefront URL
 * from an imported knowledge document.
 *
 * Excludes social media platforms and messaging links, and strips sub-product/catalog paths
 * to provide a clean root or storefront base URL (e.g. https://velo.ordersapps.com/ar).
 */
object StoreWebsiteExtractor {

    private val EXPLICIT_STORE_WEBSITE_REGEX = Regex(
        """(?:المتجر\s+الإلكتروني(?:\s+الرسمي)?|الموقع\s+الرسمي|المتجر\s+الرسمي|موقع\s+المتجر|الموقع|متجرنا|المتجر)\s*[:：\-]\s*(https?://[^\s)\]>"']+)""",
        RegexOption.IGNORE_CASE
    )

    private val GENERAL_URL_REGEX = Regex("""https?://[^\s)\]>"']+""", RegexOption.IGNORE_CASE)

    private val EXCLUDED_HOSTS = setOf(
        "facebook.com", "www.facebook.com", "fb.com", "m.facebook.com",
        "instagram.com", "www.instagram.com",
        "tiktok.com", "www.tiktok.com",
        "wa.me", "api.whatsapp.com", "whatsapp.com", "www.whatsapp.com",
        "t.me", "telegram.me",
        "twitter.com", "www.twitter.com", "x.com", "www.x.com",
        "youtube.com", "www.youtube.com", "youtu.be",
        "linkedin.com", "www.linkedin.com"
    )

    private val PRODUCT_SUBPATH_REGEX = Regex(
        """/(?:product|products|item|items|p|c|category|categories|cart|checkout)/.*$""",
        RegexOption.IGNORE_CASE
    )

    fun extract(documentText: String): String? {
        if (documentText.isBlank()) return null

        // 1. Check for explicit store website header / label
        val explicitMatch = EXPLICIT_STORE_WEBSITE_REGEX.find(documentText)
        if (explicitMatch != null) {
            val candidateUrl = cleanUrl(explicitMatch.groupValues[1])
            val normalized = normalizeToStorefront(candidateUrl)
            if (isValidStoreUrl(normalized)) {
                return normalized
            }
        }

        // 2. Scan all URLs and find valid candidates
        val allUrls = GENERAL_URL_REGEX.findAll(documentText)
            .map { cleanUrl(it.value) }
            .filter { isValidStoreUrl(it) }
            .map { normalizeToStorefront(it) }
            .distinct()
            .toList()

        if (allUrls.isEmpty()) return null

        // 3. If multiple candidates exist, pick the shortest common root/storefront path
        return allUrls.minByOrNull { it.length }
    }

    private fun cleanUrl(raw: String): String {
        return raw.trim()
            .trimEnd('.', ',', ';', ':', ')', ']', '}', '"', '\'')
    }

    private fun isValidStoreUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            EXCLUDED_HOSTS.none { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeToStorefront(url: String): String {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return url
            val rawPath = uri.rawPath ?: ""
            val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""

            // Strip product/sub-catalog specific path extensions
            val cleanPath = rawPath.replace(PRODUCT_SUBPATH_REGEX, "")
                .trimEnd('/')

            "$scheme://$host$port$cleanPath"
        } catch (_: Exception) {
            url.trimEnd('/')
        }
    }
}
