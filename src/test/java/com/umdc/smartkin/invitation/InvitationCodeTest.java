package com.umdc.smartkin.invitation;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvitationCodeTest {

    @Test
    void generatedCodesHaveEightCharactersWithoutLookAlikes() {
        var seen = new HashSet<String>();
        for (var i = 0; i < 500; i++) {
            var code = InvitationCode.generate();
            assertEquals(8, code.length());
            assertTrue(code.matches("[2-9A-HJKMNP-Z]{8}"), code);
            seen.add(code);
        }
        assertTrue(seen.size() > 495, "codes must not repeat");
    }

    @Test
    void formatSplitsTheCodeInTwoGroups() {
        assertEquals("ABCD-EFGH", InvitationCode.format("ABCDEFGH"));
    }

    @Test
    void normalizeAcceptsWhatPeopleType() {
        assertEquals("ABCDEFGH", InvitationCode.normalize("abcd-efgh").orElseThrow());
        assertEquals("ABCDEFGH", InvitationCode.normalize("  ABCD EFGH ").orElseThrow());
        assertEquals("ABCDEFGH", InvitationCode.normalize("ABCDEFGH").orElseThrow());
    }

    @Test
    void normalizeRejectsWhatCannotBeACode() {
        assertTrue(InvitationCode.normalize(null).isEmpty());
        assertTrue(InvitationCode.normalize("").isEmpty());
        assertTrue(InvitationCode.normalize("ABCD-EFG").isEmpty());
        assertTrue(InvitationCode.normalize("ABCD-EFGHJ").isEmpty());
        assertTrue(InvitationCode.normalize("ABCD-EFG0").isEmpty(), "0 is not in the alphabet");
        assertTrue(InvitationCode.normalize("ABCD-EFG!").isEmpty());
    }

    @Test
    void hashIsAStableSha256InHexadecimal() {
        var hash = InvitationCode.hash("ABCDEFGH");

        assertEquals(64, hash.length());
        assertEquals(hash, InvitationCode.hash("ABCDEFGH"));
        assertNotEquals(hash, InvitationCode.hash("ABCDEFGJ"));
        assertFalse(hash.contains("ABCDEFGH"));
    }

    @Test
    void theUtilityClassCannotBeInstantiated() throws Exception {
        var constructor = InvitationCode.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(failure.getCause() instanceof IllegalStateException);
    }
}
