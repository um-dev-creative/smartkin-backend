package com.umdc.smartkin.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/// The one-time code of a child invitation: 8 characters drawn from an alphabet without look-alikes (no 0/O, 1/I/L), shown as `ABCD-EFGH`.
/// 31^8 combinations, a 7-day lifetime and a single use keep guessing impractical; only the SHA-256 of the normalised code is stored.
public final class InvitationCode {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 8;
    private static final int GROUP = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationCode() {
        throw new IllegalStateException("Utility class");
    }

    /// Draws a new code.
    ///
    /// @return the code in its normalised form (8 characters, no hyphen)
    public static String generate() {
        var code = new StringBuilder(LENGTH);
        for (var i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /// Shows a normalised code the way people type it.
    ///
    /// @param code the normalised code
    /// @return `ABCD-EFGH`
    public static String format(String code) {
        return code.substring(0, GROUP) + "-" + code.substring(GROUP);
    }

    /// Cleans what the user typed: trims, upper-cases and drops spaces and hyphens.
    ///
    /// @param typed the typed text
    /// @return the normalised code, or empty when it cannot be a code
    public static Optional<String> normalize(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        var code = typed.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (code.length() != LENGTH || !code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(code);
    }

    /// The SHA-256 of a normalised code, in hexadecimal.
    ///
    /// @param code the normalised code
    /// @return 64 hexadecimal characters
    public static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
