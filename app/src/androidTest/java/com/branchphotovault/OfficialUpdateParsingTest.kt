package com.branchphotovault

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.branchphotovault.data.update.GithubReleaseUpdater
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Offline security parsing tests; no external network or 350 MB downloads required. */
@RunWith(AndroidJUnit4::class)
class OfficialUpdateParsingTest {
    private fun example(
        tag: String = "v1.4.4",
        prerelease: Boolean = false,
        url: String = "https://github.com/surakiatforwork-art/Image_Keeper/releases/download/v1.4.4/ImageKeeper_v1.4.4.apk",
        digest: String = "sha256:" + "a".repeat(64)
    ): String = JSONObject().apply {
        put("tag_name", tag)
        put("name", "Keeper new version")
        put("body", "Release notes")
        put("draft", false)
        put("prerelease", prerelease)
        put("assets", JSONArray().put(JSONObject().apply {
            put("name", "ImageKeeper_v1.4.4.apk")
            put("browser_download_url", url)
            put("digest", digest)
            put("size", 390000000L)
        }))
    }.toString()

    @Test fun acceptsVerifiedNewerStableRelease() {
        val update = GithubReleaseUpdater.parseRelease(example(), "1.4.3")
        assertTrue(update.newer)
        assertEquals("v1.4.4", update.tag)
        assertEquals("a".repeat(64), update.sha256)
        assertTrue(update.size > 1000000)
    }

    @Test fun recognizesSameAndOlderRelease() {
        assertFalse(GithubReleaseUpdater.parseRelease(example(), "1.4.4").newer)
        assertFalse(GithubReleaseUpdater.parseRelease(example(tag="v1.4.2"), "1.4.3").newer)
    }

    @Test fun stableReleaseIsNewerThanSameBaseBeta() {
        assertTrue(GithubReleaseUpdater.parseRelease(example(tag="v1.4.9"), "1.4.9-beta.1").newer)
        assertTrue(GithubReleaseUpdater.parseRelease(example(tag="v1.4.10"), "1.4.9-beta.1").newer)
    }

    @Test fun rejectsPrereleaseWrongHostAndMissingDigest() {
        for (text in listOf(
            example(prerelease = true),
            example(url = "https://malicious.example/download/ImageKeeper.apk"),
            example(digest = ""),
            example(digest = "sha256:garbage")
        )) {
            var rejected = false
            try { GithubReleaseUpdater.parseRelease(text, "1.4.3") }
            catch (_: Exception) { rejected = true }
            assertTrue("Unsafe APK metadata must be rejected", rejected)
        }
    }
}
