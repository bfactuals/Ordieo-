package com.example

import com.example.data.remote.RemoteConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigParserTest {

    @Test
    fun parseOrNull_parsesFullConfigAndFiltersGitHubLinks() {
        val json = """
            {
              "about": {
                "creatorName": "Bfactuals",
                "tagline": "Built for fast bilingual captions.",
                "links": [
                  { "type": "instagram", "label": "Instagram", "handle": "@bfactuals", "url": "https://instagram.com/bfactuals" },
                  { "type": "github", "label": "GitHub", "handle": "@bfactuals", "url": "https://github.com/bfactuals" },
                  { "type": "youtube", "label": "YouTube", "handle": "Bfactuals", "url": "https://youtube.com/@bfactuals" },
                  { "type": "email", "label": "Email", "handle": "hi@bfactuals.com", "url": "hi@bfactuals.com" }
                ]
              },
              "share": {
                "title": "Try Transcribe",
                "message": "Fast captions and Hinglish!",
                "url": "https://transcribe.example.com"
              },
              "support": {
                "enabled": false,
                "title": "Buy me a chai",
                "message": "Thanks!",
                "upiId": "bfactuals@upi",
                "url": "https://support.example.com"
              },
              "promo": {
                "enabled": true,
                "label": "FROM THE CREATOR",
                "title": "New Creator Pack",
                "subtitle": "Free LUTs & fonts",
                "ctaLabel": "Open",
                "url": "https://bfactuals.com/pack",
                "expiresAt": "2099-12-31"
              },
              "update": {
                "latestVersion": "v2.2.0",
                "minVersion": "v2.1.0",
                "title": "New Release",
                "message": "Faster compression!",
                "url": "https://bfactuals.com/update"
              }
            }
        """.trimIndent()

        val config = RemoteConfigParser.parseOrNull(json)
        assertNotNull(config)
        requireNotNull(config)

        assertEquals("Bfactuals", config.about.creatorName)
        assertEquals("Built for fast bilingual captions.", config.about.tagline)
        // GitHub link must be stripped out
        assertEquals(3, config.about.links.size)
        assertTrue(config.about.links.none { it.url.contains("github", ignoreCase = true) })
        assertEquals("mailto:hi@bfactuals.com", config.about.links[2].url)

        assertEquals("Try Transcribe", config.share.title)
        assertEquals("https://transcribe.example.com", config.share.url)

        assertFalse(config.support.enabled)
        assertEquals("bfactuals@upi", config.support.upiId)

        assertTrue(config.promo.isActive())
        assertEquals("New Creator Pack", config.promo.title)

        assertTrue(config.update.isUpdateAvailable("v2.0.0"))
        assertTrue(config.update.isForceUpdate("v2.0.0"))
        assertFalse(config.update.isForceUpdate("v2.1.0"))
        assertFalse(config.update.isUpdateAvailable("v2.2.0"))
    }

    @Test
    fun promoExpiry_hidesExpiredPromos() {
        val expiredJson = """
            {
              "promo": {
                "enabled": true,
                "title": "Old Promo",
                "url": "https://example.com",
                "expiresAt": "2020-01-01"
              }
            }
        """.trimIndent()

        val config = RemoteConfigParser.parseOrNull(expiredJson)!!
        assertFalse(config.promo.isActive())
    }

    @Test
    fun compareVersions_handlesSemanticNumbersAndVPrefix() {
        assertTrue(RemoteConfigParser.compareVersions("v2.10.0", "v2.2.0") > 0)
        assertEquals(0, RemoteConfigParser.compareVersions("v2.0.0", "2.0.0"))
        assertTrue(RemoteConfigParser.compareVersions("v1.9.9", "v2.0.0") < 0)
    }

    @Test
    fun parseOrNull_returnsNullOnInvalidJsonAndDefaultFallsBack() {
        assertNull(RemoteConfigParser.parseOrNull("not valid json"))
        val fallback = RemoteConfigParser.parseOrDefault("not valid json")
        assertEquals(CREATOR_CONFIG.creatorName, fallback.about.creatorName)
        assertFalse(fallback.promo.enabled)
    }
}
