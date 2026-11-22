package com.umdc.smartkin.security;

import com.umdc.smartkin.security.revocation.RevocationChecker;
import com.umdc.smartkin.security.revocation.SessionValidationUnavailableException;
import com.umdc.security.exception.CertificateSecurityException;
import com.umdc.security.jwt.JwtConfigProperties;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionJwtServiceImplTest {

    private static final byte[] SECRET = new byte[64];
    private static final String ISSUER = "backbone-rest";
    private static final String AUDIENCE = "backbone-rest-client";
    private static final UUID APPLICATION = UUID.randomUUID();

    static {
        for (int i = 0; i < SECRET.length; i++) {
            SECRET[i] = (byte) (i * 5 + 1);
        }
    }

    private final SecretKey key = Keys.hmacShaKeyFor(SECRET);
    private final Set<String> revoked = new HashSet<>();
    private final Set<String> asked = new HashSet<>();
    private boolean outage;
    private SessionJwtServiceImpl service;

    /** Fake deny-list: records every jti it is asked about. */
    private final RevocationChecker denyList = jti -> {
        asked.add(jti);
        if (outage) {
            throw new SessionValidationUnavailableException("redis down", null);
        }
        return revoked.contains(jti);
    };

    private SessionJwtServiceImpl serviceWith(SessionClaimPolicy policy) {
        JwtConfigProperties properties = new JwtConfigProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(SECRET));
        properties.setExpirationMs(60_000L);
        return new SessionJwtServiceImpl(properties, policy, denyList);
    }

    @BeforeEach
    void setUp() {
        service = serviceWith(new SessionClaimPolicy(ISSUER, AUDIENCE, APPLICATION));
    }

    /** A token shaped like backbone-rest's, with every claim overridable. */
    private JwtBuilder backboneLike(UUID uid) {
        return Jwts.builder()
                .claims(Map.of("type", "session-token", "uid", uid.toString(), "aid", APPLICATION.toString()))
                .id(UUID.randomUUID().toString())
                .subject("tester")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key);
    }

    @Test
    void acceptsATokenShapedLikeBackbonesAndResolvesItsUid() {
        UUID uid = UUID.randomUUID();
        String token = backboneLike(uid).compact();

        assertTrue(service.isValid(token));
        assertEquals(uid, service.getVerifiedUid(token).orElseThrow());
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String forged = Jwts.builder().claims(Map.of("type", "session-token", "uid", UUID.randomUUID().toString()))
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(new byte[64])).compact();

        assertFalse(service.isValid(forged));
        assertTrue(service.getVerifiedUid(forged).isEmpty());
    }

    @Test
    void rejectsAnExpiredToken() {
        String expired = backboneLike(UUID.randomUUID())
                .expiration(Date.from(Instant.now().minusSeconds(60))).compact();

        assertFalse(service.isValid(expired));
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        String noExp = Jwts.builder().claims(Map.of("type", "session-token", "uid", UUID.randomUUID().toString()))
                .issuer(ISSUER).audience().add(AUDIENCE).and().signWith(key).compact();

        assertFalse(service.isValid(noExp));
    }

    @Test
    void rejectsATokenThatIsNotASessionToken() {
        String refresh = backboneLike(UUID.randomUUID()).claim("type", "refresh-token").compact();

        assertFalse(service.isValid(refresh));
    }

    @Test
    void rejectsAnotherIssuerOrAudience() {
        String issuer = backboneLike(UUID.randomUUID()).issuer("someone-else").compact();
        String audience = backboneLike(UUID.randomUUID()).audience().clear().add("another-client").and().compact();

        assertFalse(service.isValid(issuer));
        assertFalse(service.isValid(audience));
    }

    @Test
    void rejectsATokenMissingIssuerOrAudienceWhenTheyAreRequired() {
        String noClaims = Jwts.builder().claims(Map.of("type", "session-token", "uid", UUID.randomUUID().toString()))
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key).compact();

        assertFalse(service.isValid(noClaims));
    }

    @Test
    void aBlankIssuerOrAudienceTurnsThatCheckOff() {
        SessionJwtServiceImpl lenient = serviceWith(new SessionClaimPolicy("", null, APPLICATION));
        String token = backboneLike(UUID.randomUUID()).issuer("anyone").audience().clear().add("anything").and().compact();

        assertTrue(lenient.isValid(token));
    }

    @Test
    void rejectsASessionOfAnotherApplicationOrOneThatNamesNone() {
        UUID uid = UUID.randomUUID();
        String otherApplication = backboneLike(uid).claim("aid", UUID.randomUUID().toString()).compact();
        String noApplication = Jwts.builder().claims(Map.of("type", "session-token", "uid", uid.toString()))
                .id(UUID.randomUUID().toString()).issuer(ISSUER).audience().add(AUDIENCE).and()
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key).compact();

        assertFalse(service.isValid(otherApplication));
        assertTrue(service.getVerifiedUid(otherApplication).isEmpty());
        assertFalse(service.isValid(noApplication));
        assertTrue(asked.isEmpty(), "a foreign session is refused before the deny-list is consulted");
    }

    @Test
    void acceptsTheApplicationIdInAnyCase() {
        String upper = backboneLike(UUID.randomUUID()).claim("aid", APPLICATION.toString().toUpperCase()).compact();

        assertTrue(service.isValid(upper));
    }

    @Test
    void theApplicationIdIsMandatory() {
        NullPointerException missing = assertThrows(NullPointerException.class, () -> new SessionClaimPolicy(ISSUER, AUDIENCE, (UUID) null));

        assertTrue(missing.getMessage().contains("umdc.smartkin.application-id"));
    }

    @Test
    void thePolicyIsReadFromTheConfigurationText() {
        SessionClaimPolicy policy = new SessionClaimPolicy(ISSUER, AUDIENCE, APPLICATION.toString());

        assertEquals(APPLICATION, policy.applicationId());
        assertThrows(IllegalArgumentException.class, () -> new SessionClaimPolicy(ISSUER, AUDIENCE, "not-a-uuid"));
    }

    @Test
    void readsTheSubjectOfAGenuineToken() {
        assertEquals("tester", service.getUsernameFromToken(backboneLike(UUID.randomUUID()).compact()));
    }

    @Test
    void generatedTokensBelongToThisApplicationWhateverTheCallerAsksFor() throws Exception {
        String token = service.generateSessionToken("alice",
                Map.of("uid", UUID.randomUUID().toString(), "aid", UUID.randomUUID().toString()));

        assertEquals(APPLICATION.toString(), service.getTokenClaims(token).get("aid"));
    }

    @Test
    void rejectsAMissingOrMalformedUid() {
        String noUid = Jwts.builder().claims(Map.of("type", "session-token")).issuer(ISSUER)
                .audience().add(AUDIENCE).and().expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key).compact();
        String badUid = backboneLike(UUID.randomUUID()).claim("uid", "not-a-uuid").compact();

        assertTrue(service.getVerifiedUid(noUid).isEmpty());
        assertTrue(service.getVerifiedUid(badUid).isEmpty());
    }

    @Test
    void rejectsNullBlankAndGarbageTokens() {
        assertTrue(service.getVerifiedUid(null).isEmpty());
        assertTrue(service.getVerifiedUid("   ").isEmpty());
        assertFalse(service.isValid("not-a-jwt-at-all"));
    }

    @Test
    void generatedTokensRoundTripAndCarryIssuerAudienceAndTheCallersClaims() throws Exception {
        UUID uid = UUID.randomUUID();
        Map<String, String> params = new HashMap<>();
        params.put("uid", uid.toString());
        params.put("type", "forged-by-caller");   // must not override the security-sensitive claims

        String token = service.generateSessionToken("alice", params);

        assertTrue(service.isValid(token));
        assertEquals(uid, service.getVerifiedUid(token).orElseThrow());
        assertEquals("alice", service.getUsernameFromToken(token));
        assertEquals(ISSUER, service.getTokenClaims(token).getIssuer());
        assertTrue(service.getTokenClaims(token).getAudience().contains(AUDIENCE));
        assertNotNull(service.getTokenClaims(token).getId());
    }

    @Test
    void generatedTokensOmitIssuerAndAudienceWhenNotConfigured() throws Exception {
        SessionJwtServiceImpl bare = serviceWith(new SessionClaimPolicy(null, "", APPLICATION));
        String token = bare.generateSessionToken("alice", Map.of("uid", UUID.randomUUID().toString()));

        assertNull(bare.getTokenClaims(token).getIssuer());
        assertNull(bare.getTokenClaims(token).getAudience());
        assertTrue(bare.isValid(token));
    }

    @Test
    void getTokenClaimsWrapsParsingFailuresInTheLibrarysCheckedException() {
        assertThrows(CertificateSecurityException.class, () -> service.getTokenClaims("garbage"));
    }

    @Test
    void aRevokedJtiIsRejectedByIsValidButStillVerifiesLocally() {
        UUID uid = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String token = backboneLike(uid).id(jti).compact();
        revoked.add(jti);

        assertFalse(service.isValid(token));
        assertEquals(uid, service.getVerifiedUid(token).orElseThrow());
        assertTrue(asked.contains(jti));
    }

    @Test
    void theDenyListIsOnlyAskedAboutTokensThatPassedTheLocalChecks() {
        String expired = backboneLike(UUID.randomUUID()).expiration(Date.from(Instant.now().minusSeconds(60))).compact();
        String forged = Jwts.builder().id(UUID.randomUUID().toString())
                .claims(Map.of("type", "session-token", "uid", UUID.randomUUID().toString()))
                .signWith(Keys.hmacShaKeyFor(new byte[64])).compact();

        assertFalse(service.isValid(expired));
        assertFalse(service.isValid(forged));
        assertFalse(service.isValid("garbage"));
        assertTrue(asked.isEmpty(), "no Redis round trip for tokens that are already invalid");
    }

    @Test
    void getVerifiedUidNeverConsultsTheDenyList() {
        service.getVerifiedUid(backboneLike(UUID.randomUUID()).compact());

        assertTrue(asked.isEmpty());
    }

    @Test
    void aTokenWithoutJtiCannotBeCheckedForRevocationSoItIsRejected() {
        String noJti = Jwts.builder().claims(Map.of("type", "session-token", "uid", UUID.randomUUID().toString()))
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key).compact();

        assertFalse(service.isValid(noJti));
        assertTrue(service.getVerifiedUid(noJti).isEmpty());
    }

    @Test
    void anOutageOfTheDenyListFailsClosedInsteadOfAcceptingTheToken() {
        String token = backboneLike(UUID.randomUUID()).compact();
        outage = true;

        assertThrows(SessionValidationUnavailableException.class, () -> service.isValid(token));
        assertTrue(service.getVerifiedUid(token).isPresent(), "the local check does not depend on Redis");
    }
}
