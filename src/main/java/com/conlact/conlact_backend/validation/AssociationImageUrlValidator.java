package com.conlact.conlact_backend.validation;

import java.net.URI;
import java.util.List;

public final class AssociationImageUrlValidator {

    private static final int MAX_URL_LENGTH = 2048;
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp"
    );

    private AssociationImageUrlValidator() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        if (value.length() > MAX_URL_LENGTH) {
            return false;
        }

        try {
            URI uri = URI.create(value.trim());

            if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return false;
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return false;
            }

            String pathLower = path.toLowerCase();
            return ALLOWED_EXTENSIONS.stream().anyMatch(pathLower::endsWith);
        } catch (Exception e) {
            return false;
        }
    }
}
