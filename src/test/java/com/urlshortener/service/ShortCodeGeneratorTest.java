package com.urlshortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeGeneratorTest {

    private static final String ALPHABET_REGEX = "[0-9a-zA-Z]{6}";

    @Test
    void encodesToFixedWidthUrlSafeCode() {
        String code = ShortCodeGenerator.encode(1L);

        assertThat(code).matches(ALPHABET_REGEX);
    }

    @Test
    void isDeterministic() {
        assertThat(ShortCodeGenerator.encode(42L))
                .isEqualTo(ShortCodeGenerator.encode(42L));
    }

    @Test
    void neverCollidesAcrossALargeRangeOfSequentialIds() {
        Set<String> seen = new HashSet<>();
        int sampleSize = 500_000;

        for (long id = 0; id < sampleSize; id++) {
            String code = ShortCodeGenerator.encode(id);
            assertThat(seen.add(code))
                    .as("code %s for id %d collided with a previous id", code, id)
                    .isTrue();
        }
        assertThat(seen).hasSize(sampleSize);
    }

    @Test
    void doesNotProduceVisiblySequentialCodesForSequentialIds() {
        // Sanity check that the permutation is actually spreading values out,
        // not just echoing the id back zero-padded (id=1 -> "000001", id=2 -> "000002").
        assertThat(ShortCodeGenerator.encode(0L)).isEqualTo("000000");
        assertThat(ShortCodeGenerator.encode(1L)).isNotEqualTo("000001");
        assertThat(ShortCodeGenerator.encode(2L)).isNotEqualTo("000002");

        // Consecutive ids should not sort adjacently once the permutation wraps,
        // which (by construction, MULTIPLIER > MOD / 3) happens by id=3.
        String codeTwo = ShortCodeGenerator.encode(2L);
        String codeThree = ShortCodeGenerator.encode(3L);
        assertThat(codeThree.compareTo(codeTwo))
                .as("expected wraparound to break simple ordering by id=3")
                .isLessThan(0);
    }

    @Test
    void rejectsNegativeIds() {
        assertThatThrownBy(() -> ShortCodeGenerator.encode(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdsOutsideTheKeyspace() {
        long mod = 62L * 62 * 62 * 62 * 62 * 62; // 62^6

        assertThat(ShortCodeGenerator.encode(mod - 1)).matches(ALPHABET_REGEX);
        assertThatThrownBy(() -> ShortCodeGenerator.encode(mod))
                .isInstanceOf(IllegalStateException.class);
    }
}
