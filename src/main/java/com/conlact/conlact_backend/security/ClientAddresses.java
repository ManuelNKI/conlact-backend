package com.conlact.conlact_backend.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;

public final class ClientAddresses {

    private ClientAddresses() {
    }

    public static String clientIp(HttpServletRequest request, String trustedProxies) {
        String remote = request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()
                ? "local"
                : request.getRemoteAddr();
        if (!isTrusted(remote, trustedProxies)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String client = forwarded.split(",")[0].trim();
        return client.isBlank() ? remote : client;
    }

    private static boolean isTrusted(String remote, String trustedProxies) {
        if (trustedProxies == null || trustedProxies.isBlank()) {
            return false;
        }
        List<String> trusted = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return trusted.contains(remote);
    }
}
