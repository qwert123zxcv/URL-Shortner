package com.urlshortener.service;

import com.urlshortener.exception.InvalidAliasException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliasValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "my-docs", "my_docs", "Product123", "a-b_c-1"})
    void acceptsAlphanumericHyphenUnderscoreWithinLengthBounds(String alias) {
        assertThat(AliasValidator.validate(alias)).isEqualTo(alias);
    }

    @Test
    void trimsWhitespace() {
        assertThat(AliasValidator.validate("  my-docs  ")).isEqualTo("my-docs");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ab",                       // too short
            "this-alias-is-way-too-long-to-be-allowed-here", // too long
            "has space",
            "has/slash",
            "has.dot",
            "emoji😀code"
    })
    void rejectsDisallowedFormats(String alias) {
        assertThatThrownBy(() -> AliasValidator.validate(alias))
                .isInstanceOf(InvalidAliasException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"shorten", "health", "actuator", "SHORTEN", "Api"})
    void rejectsReservedWordsCaseInsensitively(String alias) {
        assertThatThrownBy(() -> AliasValidator.validate(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("reserved");
    }
}
