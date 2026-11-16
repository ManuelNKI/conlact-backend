package com.conlact.conlact_backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AuthSecrets {

    private AuthSecrets() {
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible.", ex);
        }
    }

    public static boolean matches(String raw, String storedHash) {
        if (raw == null || storedHash == null || !raw.matches("\\d{6}")) {
            return false;
        }
        byte[] actual = sha256(raw).getBytes(StandardCharsets.UTF_8);
        byte[] expected = storedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }
}
