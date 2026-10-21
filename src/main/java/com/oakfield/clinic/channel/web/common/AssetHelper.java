package com.oakfield.clinic.channel.web.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.Random;

/**
 * Static asset URLs with a cache-busting suffix.
 *
 * PRECISION BASELINE (non-vulnerability, keep): java.util.Random is used only
 * to produce a cache-busting suffix on a static asset URL. This use is
 * non-cryptographic - nothing here is a secret or a token, so a
 * cryptographically strong generator is intentionally NOT required. (Do NOT
 * substitute SecureRandom; this location tests scanner precision.)
 */
@Named("asset")
@ApplicationScoped
public class AssetHelper {

    private static final Random CACHE_BUST = new Random(); // non-cryptographic: cache-busting only

    /** Numeric cache-busting suffix for static asset URLs (see precision baseline note above). */
    public int cacheBust() {
        return CACHE_BUST.nextInt(1_000_000);
    }

    /** Full resource path with the cache-busting suffix appended. */
    public String css(String path) {
        return path + "?v=" + CACHE_BUST.nextInt(1_000_000);
    }
}
