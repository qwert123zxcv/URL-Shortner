package com.urlshortener.service;

import java.math.BigInteger;

/**
 * Derives a fixed-width, URL-safe short code from a monotonically
 * increasing DB sequence id.
 *
 * Collision-freedom is by construction, not by luck: multiplying id by a
 * MULTIPLIER that is coprime to MOD is a bijection over [0, MOD) (a
 * standard "multiplicative permutation" trick, cf. Hashids/Optimus).
 * Two distinct ids in that range can never collide to the same code --
 * no randomness + retry-on-conflict loop needed. The permutation also
 * keeps codes from looking sequential (1, 2, 3, ...), which would
 * otherwise leak how many links have been created and in what order.
 * MULTIPLIER is deliberately sized close to MOD (rather than tiny) so
 * the scrambling effect kicks in from the first few ids, not only after
 * thousands of rows. The multiply-then-mod step uses BigInteger so the
 * multiplier isn't constrained by 64-bit overflow.
 *
 * A unique DB constraint on short_code remains as a defense-in-depth
 * safety net, but is not load-bearing for correctness.
 *
 * Valid for ids in [0, MOD): with BASE=62 and CODE_LENGTH=6, that's
 * 62^6 = 56,800,235,584 (~56.8 billion) possible ids -- far beyond what
 * this exercise needs.
 */
public final class ShortCodeGenerator {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length(); // 62
    private static final int CODE_LENGTH = 6;
    private static final long MOD = pow(BASE, CODE_LENGTH); // 56,800,235,584
    private static final BigInteger MOD_BI = BigInteger.valueOf(MOD);

    // Coprime with MOD = 2^6 * 31^6 (odd, not a multiple of 31), verified
    // via gcd(MULTIPLIER, MOD) == 1. Chosen close to 0.39 * MOD so the
    // permutation wraps around (and thus scrambles) within the first few ids.
    private static final BigInteger MULTIPLIER = BigInteger.valueOf(21_915_088_127L);

    private ShortCodeGenerator() {
    }

    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative: " + id);
        }
        if (id >= MOD) {
            throw new IllegalStateException(
                    "id " + id + " exceeds generator keyspace of " + MOD
                            + " -- widen CODE_LENGTH");
        }

        long permuted = BigInteger.valueOf(id).multiply(MULTIPLIER).mod(MOD_BI).longValueExact();

        char[] chars = new char[CODE_LENGTH];
        long value = permuted;
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (value % BASE));
            value /= BASE;
        }
        return new String(chars);
    }

    private static long pow(long base, int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }
}
