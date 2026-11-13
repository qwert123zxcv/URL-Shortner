package com.urlshortener.service;

import com.urlshortener.exception.InvalidUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Deliberately narrow: only http/https absolute URLs with a host are
 * accepted. This blocks common nuisance/abuse inputs (javascript:,
 * mailto:, relative paths, blank hosts) without pulling in a full URL
 * validation library for a take-home exercise.
 */
public final class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_LENGTH = 2048;

    private UrlValidator() {
    }

    public static String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("url must not be blank");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("url must be at most " + MAX_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a valid URI: " + e.getMessage());
        }

        if (!uri.isAbsolute()) {
            throw new InvalidUrlException("url must be absolute (include a scheme, e.g. https://)");
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("url scheme must be http or https, got: " + scheme);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must include a host");
        }

        return trimmed;
    }
}
