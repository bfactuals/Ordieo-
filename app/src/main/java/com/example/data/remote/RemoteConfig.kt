package com.example.data.remote

import com.example.AppConfig
import com.example.CREATOR_CONFIG
import com.example.REMOTE_CONFIG_URL
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class RemoteSocialLink(
    val type: String,
    val label: String,
    val subtitle: String,
    val url: String
)

data class RemoteAboutConfig(
    val creatorName: String = CREATOR_CONFIG.creatorName,
    val tagline: String = CREATOR_CONFIG.creatorTagline,
    val links: List<RemoteSocialLink> = defaultSocialLinks()
)

data class RemoteShareConfig(
    val title: String = "Share ${AppConfig.APP_NAME}",
    val message: String = CREATOR_CONFIG.appShareIntro,
    val url: String = CREATOR_CONFIG.appShareUrl
)

data class RemoteSupportConfig(
    val enabled: Boolean = true,
    val title: String = "Support the Creator",
    val message: String = CREATOR_CONFIG.supportNote,
    val upiId: String = CREATOR_CONFIG.supportUpiId,
    val url: String = CREATOR_CONFIG.supportLinkUrl,
    val urlLabel: String = CREATOR_CONFIG.supportLinkLabel
)

data class RemotePromoConfig(
    val enabled: Boolean = false,
    val label: String = "FROM THE CREATOR",
    val title: String = "",
    val subtitle: String = "",
    val ctaLabel: String = "Open",
    val url: String = "",
    val expiresAt: String? = null
) {
    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        if (title.isBlank() || url.isBlank()) return false
        return !RemoteConfigParser.isExpired(expiresAt, nowMs)
    }
}

data class RemoteUpdateConfig(
    val latestVersion: String = AppConfig.APP_VERSION,
    val minVersion: String = "2.0.0",
    val title: String = "Update Available",
    val message: String = "A newer version of ${AppConfig.APP_NAME} is available with improvements and fixes.",
    val url: String = ""
) {
    fun isUpdateAvailable(installedVersion: String = AppConfig.APP_VERSION): Boolean {
        if (url.isBlank() || latestVersion.isBlank()) return false
        return RemoteConfigParser.compareVersions(latestVersion, installedVersion) > 0
    }

    fun isForceUpdate(installedVersion: String = AppConfig.APP_VERSION): Boolean {
        if (url.isBlank() || minVersion.isBlank()) return false
        return RemoteConfigParser.compareVersions(installedVersion, minVersion) < 0
    }
}

data class RemoteConfig(
    val about: RemoteAboutConfig = RemoteAboutConfig(),
    val share: RemoteShareConfig = RemoteShareConfig(),
    val support: RemoteSupportConfig = RemoteSupportConfig(),
    val promo: RemotePromoConfig = RemotePromoConfig(),
    val update: RemoteUpdateConfig = RemoteUpdateConfig()
)

data class UpdatePromptState(
    val visible: Boolean = false,
    val forced: Boolean = false,
    val latestVersion: String = "",
    val title: String = "Update Available",
    val message: String = "",
    val url: String = ""
)

fun defaultSocialLinks(): List<RemoteSocialLink> = listOf(
    RemoteSocialLink(
        type = "instagram",
        label = "Instagram",
        subtitle = CREATOR_CONFIG.instagramHandle,
        url = CREATOR_CONFIG.instagramUrl
    ),
    RemoteSocialLink(
        type = "youtube",
        label = "YouTube",
        subtitle = CREATOR_CONFIG.youtubeHandle,
        url = CREATOR_CONFIG.youtubeUrl
    ),
    RemoteSocialLink(
        type = "email",
        label = "Contact / Email",
        subtitle = CREATOR_CONFIG.contactEmail,
        url = "mailto:${CREATOR_CONFIG.contactEmail}"
    )
)

object RemoteConfigParser {

    private val moshi: Moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    fun parseOrNull(rawJson: String): RemoteConfig? {
        if (rawJson.isBlank()) return null
        return runCatching {
            val root = mapAdapter.fromJson(rawJson) ?: return null
            parseRoot(root)
        }.getOrNull()
    }

    fun parseOrDefault(rawJson: String?): RemoteConfig {
        if (rawJson.isNullOrBlank()) return RemoteConfig()
        return parseOrNull(rawJson) ?: RemoteConfig()
    }

    private fun parseRoot(root: Map<String, Any?>): RemoteConfig {
        val defaults = RemoteConfig()

        // 1. About
        val aboutMap = root.optMap("about")
        val creatorName = aboutMap.optString("creatorName", "name")
            ?: root.optString("creatorName")
            ?: defaults.about.creatorName
        val tagline = aboutMap.optString("tagline", "creatorTagline", "bio", "creatorBio")
            ?: root.optString("creatorTagline", "creatorBio")
            ?: defaults.about.tagline
        val parsedLinks = parseSocialLinks(aboutMap ?: root)
        val about = RemoteAboutConfig(
            creatorName = creatorName,
            tagline = tagline,
            links = parsedLinks ?: defaults.about.links
        )

        // 2. Share
        val shareMap = root.optMap("share")
        val share = RemoteShareConfig(
            title = shareMap.optString("title", "subject") ?: defaults.share.title,
            message = shareMap.optString("message", "text", "intro") ?: defaults.share.message,
            url = shareMap.optString("url", "appUrl", "link") ?: defaults.share.url
        )

        // 3. Support
        val supportMap = root.optMap("support")
        val support = if (supportMap != null) {
            RemoteSupportConfig(
                enabled = supportMap.optBool("enabled") ?: true,
                title = supportMap.optString("title") ?: defaults.support.title,
                message = supportMap.optString("message", "note", "description") ?: defaults.support.message,
                upiId = supportMap.optString("upiId", "upi", "upi_id") ?: "",
                url = supportMap.optString("url", "feedbackUrl", "supportUrl", "link") ?: "",
                urlLabel = supportMap.optString("urlLabel", "linkLabel", "buttonLabel") ?: defaults.support.urlLabel
            )
        } else {
            defaults.support
        }

        // 4. Promo ("FROM THE CREATOR")
        val promoMap = root.optMap("promo")
        val promo = if (promoMap != null) {
            RemotePromoConfig(
                enabled = promoMap.optBool("enabled") ?: false,
                label = promoMap.optString("label", "badge") ?: "FROM THE CREATOR",
                title = promoMap.optString("title") ?: "",
                subtitle = promoMap.optString("subtitle", "message", "description") ?: "",
                ctaLabel = promoMap.optString("ctaLabel", "cta", "buttonText", "buttonLabel") ?: "Open",
                url = promoMap.optString("url", "link") ?: "",
                expiresAt = promoMap.optString("expiresAt", "expires_at", "expiry")
            )
        } else {
            defaults.promo
        }

        // 5. Update
        val updateMap = root.optMap("update")
        val update = if (updateMap != null) {
            RemoteUpdateConfig(
                latestVersion = updateMap.optString("latestVersion", "latest_version", "version")
                    ?: defaults.update.latestVersion,
                minVersion = updateMap.optString("minVersion", "min_version", "minimumVersion")
                    ?: defaults.update.minVersion,
                title = updateMap.optString("title") ?: defaults.update.title,
                message = updateMap.optString("message", "notes", "changelog", "releaseNotes")
                    ?: defaults.update.message,
                url = updateMap.optString("url", "updateUrl", "downloadUrl", "link") ?: ""
            )
        } else {
            defaults.update
        }

        return RemoteConfig(
            about = about,
            share = share,
            support = support,
            promo = promo,
            update = update
        )
    }

    private fun parseSocialLinks(source: Map<String, Any?>): List<RemoteSocialLink>? {
        val rawLinks = source["links"]
        val collected = mutableListOf<RemoteSocialLink>()
        var explicitlyProvided = false

        when (rawLinks) {
            is List<*> -> {
                explicitlyProvided = true
                rawLinks.forEach { entry ->
                    val obj = entry.asStringMap() ?: return@forEach
                    val rawUrl = obj.optString("url", "href", "link") ?: return@forEach
                    val type = (obj.optString("type", "icon", "platform", "id") ?: inferPlatform(rawUrl))
                        .lowercase(Locale.US)
                    val label = obj.optString("label", "title", "name") ?: defaultLabelForType(type)
                    val subtitle = obj.optString("handle", "subtitle", "value")
                        ?: rawUrl.removePrefix("mailto:").removePrefix("https://").removePrefix("http://")
                    val finalUrl = if (type == "email" && !rawUrl.startsWith("mailto:", ignoreCase = true)) {
                        "mailto:$rawUrl"
                    } else {
                        rawUrl
                    }
                    if (isAllowedSocialLink(type, label, finalUrl)) {
                        collected.add(RemoteSocialLink(type, label, subtitle, finalUrl))
                    }
                }
            }
            is Map<*, *> -> {
                explicitlyProvided = true
                val linkMap = rawLinks.asStringMap().orEmpty()
                linkMap.forEach { (key, value) ->
                    val rawUrl = (value as? String)?.trim().orEmpty()
                    if (rawUrl.isBlank()) return@forEach
                    val type = key.lowercase(Locale.US)
                    val label = defaultLabelForType(type)
                    val finalUrl = if ((type == "email" || type == "contact") && !rawUrl.startsWith("mailto:", ignoreCase = true) && rawUrl.contains("@")) {
                        "mailto:$rawUrl"
                    } else {
                        rawUrl
                    }
                    val subtitle = rawUrl.removePrefix("mailto:").removePrefix("https://").removePrefix("http://")
                    if (isAllowedSocialLink(type, label, finalUrl)) {
                        collected.add(RemoteSocialLink(type, label, subtitle, finalUrl))
                    }
                }
            }
            else -> {
                // Support flat keys inside `about` (e.g. instagramUrl, youtubeUrl, contactEmail)
                val igUrl = source.optString("instagramUrl", "instagram")
                val ytUrl = source.optString("youtubeUrl", "youtube")
                val email = source.optString("contactEmail", "email")
                if (igUrl != null || ytUrl != null || email != null) {
                    explicitlyProvided = true
                    if (!igUrl.isNullOrBlank()) {
                        val handle = source.optString("instagramHandle")
                            ?: igUrl.removePrefix("https://").removePrefix("http://")
                        if (isAllowedSocialLink("instagram", "Instagram", igUrl)) {
                            collected.add(RemoteSocialLink("instagram", "Instagram", handle, igUrl))
                        }
                    }
                    if (!ytUrl.isNullOrBlank()) {
                        val handle = source.optString("youtubeHandle")
                            ?: ytUrl.removePrefix("https://").removePrefix("http://")
                        if (isAllowedSocialLink("youtube", "YouTube", ytUrl)) {
                            collected.add(RemoteSocialLink("youtube", "YouTube", handle, ytUrl))
                        }
                    }
                    if (!email.isNullOrBlank()) {
                        val mailUrl = if (email.startsWith("mailto:", ignoreCase = true)) email else "mailto:$email"
                        val display = email.removePrefix("mailto:")
                        collected.add(RemoteSocialLink("email", "Contact / Email", display, mailUrl))
                    }
                }
            }
        }

        return if (explicitlyProvided) collected else null
    }

    private fun isAllowedSocialLink(type: String, label: String, url: String): Boolean {
        if (url.isBlank()) return false
        val combined = "$type $label $url".lowercase(Locale.US)
        // Strictly never render GitHub links or icons in the About UI
        if (combined.contains("github")) return false
        return true
    }

    private fun inferPlatform(url: String): String {
        val lower = url.lowercase(Locale.US)
        return when {
            lower.startsWith("mailto:") -> "email"
            lower.contains("instagram.com") -> "instagram"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "youtube"
            lower.contains("x.com") || lower.contains("twitter.com") -> "x"
            lower.contains("t.me") || lower.contains("telegram") -> "telegram"
            else -> "website"
        }
    }

    private fun defaultLabelForType(type: String): String = when (type) {
        "instagram", "ig" -> "Instagram"
        "youtube", "yt" -> "YouTube"
        "email", "mail", "contact" -> "Contact / Email"
        "x", "twitter" -> "X / Twitter"
        "telegram" -> "Telegram"
        "website", "web" -> "Website"
        else -> type.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Compares two semantic version strings (e.g. "v2.1.0" vs "v2.0.0").
     * Returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = normalizeVersion(v1).split('.').map { it.toIntOrNull() ?: 0 }
        val parts2 = normalizeVersion(v2).split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    fun normalizeVersion(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V")
    }

    /**
     * Checks whether an ISO date/datetime string (e.g. "2026-12-31" or "2026-12-31T23:59:59Z")
     * has already passed relative to [nowMs]. Returns false if null/blank/unparseable.
     */
    fun isExpired(expiresAt: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val raw = expiresAt?.trim().orEmpty()
        if (raw.isEmpty()) return false

        // Numeric epoch milliseconds
        raw.toLongOrNull()?.let { epoch ->
            return nowMs > epoch
        }

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(raw)
            }.getOrNull()
            if (parsed != null) {
                val expiryMs = if (pattern == "yyyy-MM-dd") {
                    // Treat date-only expiry as inclusive until end of that day UTC
                    parsed.time + 24L * 60L * 60L * 1000L - 1L
                } else {
                    parsed.time
                }
                return nowMs > expiryMs
            }
        }
        return false
    }

    private fun Any?.asStringMap(): Map<String, Any?>? {
        val raw = this as? Map<*, *> ?: return null
        val out = LinkedHashMap<String, Any?>(raw.size)
        raw.forEach { (k, v) ->
            if (k is String) out[k] = v
        }
        return out
    }

    private fun Map<String, Any?>?.optMap(key: String): Map<String, Any?>? {
        if (this == null) return null
        return this[key].asStringMap()
    }

    private fun Map<String, Any?>?.optString(vararg keys: String): String? {
        if (this == null) return null
        for (k in keys) {
            val v = this[k]
            if (v is String && v.trim().isNotEmpty()) {
                return v.trim()
            }
        }
        return null
    }

    private fun Map<String, Any?>?.optBool(vararg keys: String): Boolean? {
        if (this == null) return null
        for (k in keys) {
            val v = this[k]
            when (v) {
                is Boolean -> return v
                is String -> {
                    if (v.equals("true", ignoreCase = true)) return true
                    if (v.equals("false", ignoreCase = true)) return false
                }
            }
        }
        return null
    }
}

class RemoteConfigFetcher {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches REMOTE_CONFIG_URL with cache-busting `?t=<timestamp>`.
     * Returns Pair(rawJson, parsedConfig) on success, or null silently if offline/invalid.
     */
    suspend fun fetchLatest(): Pair<String, RemoteConfig>? = withContext(Dispatchers.IO) {
        runCatching {
            val separator = if (REMOTE_CONFIG_URL.contains("?")) "&" else "?"
            val urlWithCacheBust = "$REMOTE_CONFIG_URL${separator}t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(urlWithCacheBust)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty().trim()
                val parsed = RemoteConfigParser.parseOrNull(body) ?: return@withContext null
                Pair(body, parsed)
            }
        }.getOrNull()
    }
}
