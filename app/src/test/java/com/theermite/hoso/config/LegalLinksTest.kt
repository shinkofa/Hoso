package com.theermite.hoso.config

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C — the in-app privacy policy link must point to a real, secure
 * URL. Google Play requires the policy to be reachable both from the
 * store listing and from inside the app. A blank or http:// link would
 * silently break that requirement.
 */
class LegalLinksTest {

    @Test
    fun privacy_policy_url_is_https_and_non_blank() {
        val url = LegalLinks.PRIVACY_POLICY_URL
        assertTrue("Privacy policy URL must not be blank", url.isNotBlank())
        assertTrue(
            "Privacy policy URL must be served over HTTPS, was: $url",
            url.startsWith("https://")
        )
    }
}
