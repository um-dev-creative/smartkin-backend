package com.umdc.smartkin.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtUtilTest {

    private static String tokenWithPayload(String json) {
        var payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "header." + payload + ".signature";
    }

    @Test
    void readsTheUidClaim() {
        var uid = UUID.randomUUID();

        assertEquals(uid, JwtUtil.getUidFromToken(tokenWithPayload("{\"uid\":\"" + uid + "\"}")));
    }

    @Test
    void returnsNullWhenThereIsNoUidClaim() {
        assertNull(JwtUtil.getUidFromToken(tokenWithPayload("{\"sub\":\"ana\"}")));
    }

    @Test
    void returnsNullForNullOrBlankTokens() {
        assertNull(JwtUtil.getUidFromToken(null));
        assertNull(JwtUtil.getUidFromToken("  "));
    }

    @Test
    void returnsNullForTokensWithoutPayload() {
        assertNull(JwtUtil.getUidFromToken("onlyonepart"));
    }

    @Test
    void returnsNullForMalformedPayloads() {
        assertNull(JwtUtil.getUidFromToken("header.***.signature"));
        assertNull(JwtUtil.getUidFromToken(tokenWithPayload("not json")));
        assertNull(JwtUtil.getUidFromToken(tokenWithPayload("{\"uid\":\"not-a-uuid\"}")));
    }

    @Test
    void cannotBeInstantiatedByAccident() throws ReflectiveOperationException {
        Constructor<JwtUtil> constructor = JwtUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }
}
