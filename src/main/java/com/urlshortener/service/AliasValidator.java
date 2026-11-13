package com.urlshortener.service;

import com.urlshortener.exception.InvalidAliasException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Custom aliases share the URL path with the app's own routes, so a
 * reserved-word check prevents an alias from shadowing e.g. /actuator/health.
 */
public final class AliasValidator {

    private static final Pattern ALLOWED_CHARS = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");

    private static final Set<String> RESERVED_WORDS = Set.of(
            "shorten", "health", "actuator", "api", "admin",
            "static", "error", "favicon.ico", "robots.txt"
    );

    private AliasValidator() {
    }

    public static String validate(String rawAlias) {
        String alias = rawAlias.trim();
        if (!ALLOWED_CHARS.matcher(alias).matches()) {
            throw new InvalidAliasException(
                    "alias must be 3-30 characters and contain only letters, digits, hyphens, or underscores");
        }
        if (RESERVED_WORDS.contains(alias.toLowerCase())) {
            throw new InvalidAliasException("alias '" + alias + "' is reserved");
        }
        return alias;
    }
}
