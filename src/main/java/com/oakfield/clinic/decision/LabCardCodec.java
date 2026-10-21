package com.oakfield.clinic.decision;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encodes a lab result reference for the "open on this device" card link. The
 * reference is packed with a random IV under AES/CBC and recovered when the
 * card is opened.
 */
public final class LabCardCodec {

    private static final SecureRandom RANDOM = new SecureRandom();

    private LabCardCodec() { }

    private static byte[] key() {
        String configured = System.getenv("LAB_CARD_KEY");
        if (configured == null || configured.isBlank()) {
            configured = "oakfield-lab-card-key-2026-0000";
        }
        byte[] raw = configured.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        System.arraycopy(raw, 0, key, 0, Math.min(raw.length, key.length));
        return key;
    }

    public static String encode(long resultId) {
        try {
            byte[] iv = new byte[16];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new IvParameterSpec(iv));
            byte[] body = cipher.doFinal(String.valueOf(resultId).getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(body, 0, packed, iv.length, body.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("Lab card could not be encoded.", e);
        }
    }

    public static long decode(String code) throws BadPaddingException, IllegalBlockSizeException {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(code);
            byte[] iv = new byte[16];
            System.arraycopy(packed, 0, iv, 0, 16);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(packed, 16, packed.length - 16);
            return Long.parseLong(new String(plain, StandardCharsets.UTF_8).trim());
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Lab card is malformed.", e);
        }
    }
}
