package com.haim.maps

import android.net.Uri

object BrowserConfig {
    const val INITIAL_URL = "https://www.google.com/maps"
    val ALLOWED_DOMAINS = listOf("accounts.google.com", "accounts.google.co.il", "accounts.youtube.com", "googleapis.com", "www.google.com/maps", "www.google.co.il/maps", "maps.google.com", "maps.google.co.il", "maps.app.goo.gl", "goo.gl/maps")
    val EXCLUDED_DOMAINS = emptyList<String>()

    private fun matchesRule(ruleString: String, targetUri: Uri): Boolean {
        if (ruleString.isBlank()) return false
        val cleanRule = ruleString.lowercase().trim().removePrefix("https://").removePrefix("http://")
        val parts = cleanRule.split("/", limit = 2)
        val ruleHost = parts[0].trim()
        val rulePath = if (parts.size > 1 && parts[1].isNotBlank()) "/" + parts[1].trim().trimEnd('/') else ""
        val targetHost = targetUri.host?.lowercase() ?: return false
        val targetPath = targetUri.path?.lowercase() ?: "/"
        val hostMatches = if (ruleHost.startsWith("*.")) {
            val baseDomain = ruleHost.removePrefix("*.")
            targetHost == baseDomain || targetHost.endsWith(".$baseDomain")
        } else targetHost == ruleHost
        if (!hostMatches) return false
        if (rulePath.isNotEmpty()) {
            val normalizedTargetPath = if (targetPath.endsWith("/")) targetPath.trimEnd('/') else targetPath
            if (normalizedTargetPath != rulePath && !targetPath.startsWith("$rulePath/")) return false
        }
        return true
    }

    fun isUrlAllowed(url: String): Boolean {
        if (url == "about:blank" || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("com.example.photosync:")) return true
        val uri = try { Uri.parse(url) } catch (_: Exception) { return false }
        for (excluded in EXCLUDED_DOMAINS) if (matchesRule(excluded, uri)) return false
        val host = uri.host?.lowercase() ?: return false
        val googleEcosystem = listOf("gstatic.com", "googleusercontent.com", "googleapis.com")
        for (domain in googleEcosystem) if (host == domain || host.endsWith(".$domain")) return true
        if (ALLOWED_DOMAINS.isEmpty()) return true
        return ALLOWED_DOMAINS.any { matchesRule(it, uri) }
    }
}
