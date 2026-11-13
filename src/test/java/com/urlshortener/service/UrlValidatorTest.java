package com.urlshortener.service;

import com.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?query=1#fragment",
            "https://sub.example.co.in:8080/a/b/c"
    })
    void acceptsAbsoluteHttpAndHttpsUrls(String url) {
        assertThat(UrlValidator.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsWhitespace() {
        assertThat(UrlValidator.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not-a-url",
            "ftp://example.com/file",
            "javascript:alert(1)",
            "//example.com/no-scheme",
            "https://",
            "mailto:someone@example.com"
    })
    void rejectsAnythingNotAnAbsoluteHttpOrHttpsUrl(String url) {
        assertThatThrownBy(() -> UrlValidator.validate(url))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> UrlValidator.validate(null))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlsOverMaxLength() {
        String longUrl = "https://example.com/" + "a".repeat(2050);
        assertThatThrownBy(() -> UrlValidator.validate(longUrl))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("2048");
    }
}
