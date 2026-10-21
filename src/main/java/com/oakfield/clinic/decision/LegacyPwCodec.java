package com.oakfield.clinic.decision;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Credential codec kept for accounts provisioned by the staffing vendor, which
 * only supports the older PBKDF2-HMAC-SHA1 derivation with a shared salt.
 * New portal accounts use {@link PasswordHasher}.
 */
public final class LegacyPwCodec {

    private static final String VENDOR_SALT = "oakfield-staffing";

    private LegacyPwCodec() { }

    public static String derive(String passcode) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passcode.toCharArray(),
                    VENDOR_SALT.getBytes(StandardCharsets.UTF_8), 1024, 128);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return "legacy1$" + Base64.getEncoder().encodeToString(key);
        } catch (Exception e) {
            throw new IllegalStateException("Legacy credential codec unavailable", e);
        }
    }

    public static boolean matches(String passcode, String stored) {
        if (passcode == null || stored == null || !stored.startsWith("legacy1$")) return false;
        try {
            byte[] expected = Base64.getDecoder().decode(stored.substring("legacy1$".length()));
            byte[] actual = Base64.getDecoder().decode(derive(passcode).substring("legacy1$".length()));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
