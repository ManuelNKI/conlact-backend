package com.conlact.conlact_backend.validation;

import java.net.URI;

public final class HttpUrlValidator {
    private HttpUrlValidator() {}

    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank() && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
