package com.umdc.smartkin.client.mercury;

import com.umdc.smartkin.client.mercury.to.MercuryTemplateDefinition;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateIdRegistryTest {

    private static final UUID VERIFICATION_ES = UUID.randomUUID();
    private static final UUID RESET_ES = UUID.randomUUID();
    private static final UUID RESET_EN = UUID.randomUUID();

    /// A clock the test moves by hand.
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Supplier<List<MercuryTemplateDefinition>>> source = new AtomicReference<>();

    private TemplateIdRegistry registry() {
        return new TemplateIdRegistry(() -> {
            calls.incrementAndGet();
            return source.get().get();
        }, clock, Duration.ofMinutes(5), Duration.ofSeconds(30));
    }

    private static MercuryTemplateDefinition def(String location, String language, UUID id) {
        return new MercuryTemplateDefinition(id, location, language);
    }

    private static List<MercuryTemplateDefinition> standard() {
        return List.of(def("sk_verification_es.ftl", "es", VERIFICATION_ES), def("templates/sk-password-reset-es.ftl", "es", RESET_ES),
                def("sk_password_reset_en.ftl", "en", RESET_EN), def("zb_verification_es.ftl", "es", UUID.randomUUID()));
    }

    @Test
    void findsEachTemplateByTheKindAndLanguageOfItsFileName() {
        source.set(TemplateIdRegistryTest::standard);
        var registry = registry();

        assertEquals(Optional.of(VERIFICATION_ES), registry.idOf("verification", "es"));
        assertEquals(Optional.of(RESET_ES), registry.idOf("password_reset", "es"));
        assertEquals(Optional.of(RESET_EN), registry.idOf("password_reset", "en"));
        assertEquals(Optional.empty(), registry.idOf("verification", "en"));
    }

    @Test
    void theVerificationCodeNameIsTheVerificationTemplate() {
        source.set(() -> List.of(def("sk_verification_code_es.ftl", "es", VERIFICATION_ES)));

        assertEquals(Optional.of(VERIFICATION_ES), registry().idOf("verification", "es"));
    }

    @Test
    void ignoresFilesThatAreNotSmartKinTemplatesOfThatLanguage() {
        source.set(() -> List.of(def("zb_verification_es.ftl", "es", UUID.randomUUID()), def("sk_verification_en.ftl", "es", UUID.randomUUID()),
                def(null, "es", UUID.randomUUID()), def("sk_verification_es.ftl", null, UUID.randomUUID()), def("sk_es.ftl", "es", UUID.randomUUID()),
                def("sk_verification_es.ftl", "es", null)));

        assertEquals(Optional.empty(), registry().idOf("verification", "es"));
    }

    @Test
    void keepsTheFirstOfTwoTemplatesForTheSameKindAndLanguage() {
        var other = UUID.randomUUID();
        source.set(() -> List.of(def("sk_verification_es.ftl", "es", VERIFICATION_ES), def("sk_verification_code_es.ftl", "es", other)));

        assertEquals(Optional.of(VERIFICATION_ES), registry().idOf("verification", "es"));
    }

    @Test
    void doesNotAskMercuryAgainWhileTheListIsFresh() {
        source.set(TemplateIdRegistryTest::standard);
        var registry = registry();

        registry.idOf("verification", "es");
        registry.idOf("password_reset", "es");

        assertEquals(1, calls.get());
    }

    @Test
    void asksAgainWhenTheListIsOlderThanTheRefreshTime() {
        source.set(TemplateIdRegistryTest::standard);
        var registry = registry();
        registry.idOf("verification", "es");

        clock.advance(Duration.ofMinutes(6));
        registry.idOf("verification", "es");

        assertEquals(2, calls.get());
    }

    @Test
    void aMissAsksAgainOnlyAfterTheCooldownSoANewTemplateIsSeenQuickly() {
        source.set(() -> List.of(def("sk_verification_es.ftl", "es", VERIFICATION_ES)));
        var registry = registry();
        assertEquals(Optional.empty(), registry.idOf("password_reset", "es"));

        source.set(TemplateIdRegistryTest::standard);
        assertEquals(Optional.empty(), registry.idOf("password_reset", "es"));
        assertEquals(1, calls.get());

        clock.advance(Duration.ofSeconds(31));
        assertEquals(Optional.of(RESET_ES), registry.idOf("password_reset", "es"));
        assertEquals(2, calls.get());
    }

    @Test
    void aMercuryThatCannotBeAskedKeepsTheLastGoodList() {
        source.set(TemplateIdRegistryTest::standard);
        var registry = registry();
        registry.idOf("verification", "es");

        source.set(() -> {
            throw new IllegalStateException("mercury is down");
        });
        clock.advance(Duration.ofMinutes(6));

        assertEquals(Optional.of(VERIFICATION_ES), registry.idOf("verification", "es"));
    }

    @Test
    void aMercuryThatIsDownFromTheStartAnswersEmpty() {
        source.set(() -> {
            throw new IllegalStateException("mercury is down");
        });

        assertTrue(registry().idOf("verification", "es").isEmpty());
    }

    @Test
    void feignFailuresAreAlsoKept() {
        source.set(TemplateIdRegistryTest::standard);
        var registry = registry();
        registry.idOf("verification", "es");

        source.set(() -> {
            throw com.umdc.smartkin.support.TestData.feign(503);
        });
        clock.advance(Duration.ofMinutes(6));

        assertEquals(Optional.of(VERIFICATION_ES), registry.idOf("verification", "es"));
    }

    @Test
    void theProductionConstructorAsksMercuryWithItsTokenForTheApplication() {
        var client = org.mockito.Mockito.mock(MercuryClient.class);
        var tokens = org.mockito.Mockito.mock(MercuryTokens.class);
        var application = UUID.randomUUID();
        org.mockito.Mockito.when(tokens.withToken(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            @SuppressWarnings("unchecked")
            java.util.function.Function<String, Object> function = call.getArgument(0);
            return function.apply("token");
        });
        org.mockito.Mockito.when(client.templateDefinitions("token", application)).thenReturn(standard());

        var registry = new TemplateIdRegistry(client, tokens, clock, application, Duration.ofMinutes(5), Duration.ofSeconds(30));

        assertEquals(Optional.of(VERIFICATION_ES), registry.idOf("verification", "es"));
    }
}
