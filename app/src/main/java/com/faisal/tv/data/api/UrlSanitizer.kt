package com.faisal.tv.data.api

object UrlSanitizer {
    /**
     * Replaces unencoded literal spaces with "%20" and trims whitespace.
     */
    fun sanitizeUrl(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.replace(" ", "%20")

    /**
     * Resolves the playable stream URL by priority: links.android -> links.web -> links.ios,
     * then applies URL sanitization.
     */
    fun getStreamUrl(links: LinksDto?): String? {
        val raw = links?.android?.takeIf { it.isNotBlank() }
            ?: links?.web?.takeIf { it.isNotBlank() }
            ?: links?.ios?.takeIf { it.isNotBlank() }
        return sanitizeUrl(raw)
    }
}
