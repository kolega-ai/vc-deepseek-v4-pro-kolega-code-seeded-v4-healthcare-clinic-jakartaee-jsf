package com.oakfield.clinic.decision;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Opaque signed token codec.
 * <p>
 * Session tokens: {@code base64url(tokenId) "." expiryEpochSeconds "." base64url(HMAC-SHA256(secret, tokenId ":" expiry))}.
 * The channel forwards the token verbatim; the decision tier verifies the
 * signature and resolves the session row from its own database.
 * <p>
 * Core-mock tokens (minted by decision, verified by core-mock):
 * {@code scope "." subject "." expiryEpochSeconds "." base64url(HMAC-SHA256(coreSecret, scope ":" subject ":" expiry))}.
 */
public final class TokenCodec {

    private TokenCodec() { }

    public static String signSession(String secret, String tokenId, long expiryEpochSeconds) {
        String payload = tokenId + ":" + expiryEpochSeconds;
        String sig = base64url(hmacSha256(secret, payload));
        return base64url(tokenId) + "." + expiryEpochSeconds + "." + sig;
    }

    public static String signCoreToken(String coreSecret, String scope, String subject, long expiryEpochSeconds) {
        String payload = scope + ":" + subject + ":" + expiryEpochSeconds;
        String sig = base64url(hmacSha256(coreSecret, payload));
        return base64url(scope) + "." + base64url(subject) + "." + expiryEpochSeconds + "." + sig;
    }

    public record SessionTokenParts(String tokenId, long expiryEpochSeconds) { }

    public static SessionTokenParts parseAndVerifySession(String secret, String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new NotAuthenticatedException("Malformed session token.");
        String tokenId = decodeUtf8(parts[0]);
        long expiry;
        try {
            expiry = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new NotAuthenticatedException("Malformed session token.");
        }
        String expected = base64url(hmacSha256(secret, tokenId + ":" + expiry));
        if (!constantTimeEquals(expected, parts[2])) {
            throw new NotAuthenticatedException("Session token signature invalid.");
        }
        if (expiry < System.currentTimeMillis() / 1000) {
            throw new NotAuthenticatedException("Session token expired.");
        }
        return new SessionTokenParts(tokenId, expiry);
    }

    public record CoreTokenParts(String scope, String subject, long expiryEpochSeconds) { }

    public static CoreTokenParts parseAndVerifyCore(String coreSecret, String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 4) throw new NotAuthenticatedException("Malformed core token.");
        String scope = decodeUtf8(parts[0]);
        String subject = decodeUtf8(parts[1]);
        long expiry;
        try {
            expiry = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new NotAuthenticatedException("Malformed core token.");
        }
        String expected = base64url(hmacSha256(coreSecret, scope + ":" + subject + ":" + expiry));
        if (!constantTimeEquals(expected, parts[3])) {
            throw new NotAuthenticatedException("Core token signature invalid.");
        }
        if (expiry < System.currentTimeMillis() / 1000) {
            throw new NotAuthenticatedException("Core token expired.");
        }
        return new CoreTokenParts(scope, subject, expiry);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Non-cryptographic helper for ETag generation over reference data (see ReferenceResource). */
    public static String md5Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    private static byte[] hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String base64url(String value) {
        return base64url(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeUtf8(String base64url) {
        try {
            return new String(Base64.getUrlDecoder().decode(base64url), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new NotAuthenticatedException("Malformed session token.");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
