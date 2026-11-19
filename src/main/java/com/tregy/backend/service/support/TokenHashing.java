package com.tregy.backend.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Refresh tokens, verification links and reset links (and invitations in 1b) all use
// a 256-bit random raw token that only the client or the email ever sees; the
// database stores its SHA-256 hex digest. A fast hash is enough here (unlike
// passwords): the input is 256 bits of randomness, so it cannot be brute-forced.
public final class TokenHashing {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RAW_TOKEN_BYTES = 32;

    private TokenHashing() {
    }

    // URL-safe, so it can go straight into a link query string or a cookie.
    public static String generateRawToken() {
        byte[] randomBytes = new byte[RAW_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String sha256Hex(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

