package com.umdc.smartkin.client.mercury;

import com.umdc.smartkin.client.mercury.to.MercuryTemplateDefinition;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/// The mercury template (`templateDefinedId`) of each e-mail kind and language, **asked to mercury** and kept in memory, the way zerobound-rest
/// does it: no id is copied into SmartKin's configuration, so a template created, changed or switched off in mercury is seen without touching
/// SmartKin. What each template is for comes from its file name, `sk_<kind>_<language>.ftl` (hyphens or underscores): `sk_verification_es.ftl`
/// is the `verification` e-mail in Spanish and `sk_password_reset_en.ftl` the `password_reset` one in English; any other file is ignored.
///
/// The list is read at the first use, again when it is older than `umdc.smartkin.mail.template-ids.refresh` (5 minutes) and, at most once per
/// `miss-cooldown` (30 seconds), when a lookup misses (a template created a moment ago). A mercury that cannot be asked keeps the last good list.
@Component
public class TemplateIdRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateIdRegistry.class);
    private static final String PREFIX = "sk_";
    /// The template of the verification e-mail also goes by `sk_verification_code_<language>`.
    private static final String VERIFICATION_CODE = "verification_code";
    private static final String VERIFICATION = "verification";

    private final Supplier<List<MercuryTemplateDefinition>> loader;
    private final Clock clock;
    private final Duration refresh;
    private final Duration missCooldown;
    private final AtomicReference<Map<String, UUID>> ids = new AtomicReference<>(Map.of());
    private final AtomicReference<Instant> lastAttempt = new AtomicReference<>();
    private final AtomicReference<Instant> lastLoad = new AtomicReference<>();

    /// Builds the registry that asks mercury with SmartKin's M2M token.
    ///
    /// @param mercuryClient mercury's Feign client
    /// @param mercuryTokens SmartKin's Mercury M2M token
    /// @param clock         the clock the refreshes are measured with
    /// @param applicationId SmartKin's application id
    /// @param refresh       how long a list is trusted
    /// @param missCooldown  how soon a miss may ask mercury again
    @Autowired
    public TemplateIdRegistry(MercuryClient mercuryClient, MercuryTokens mercuryTokens, Clock clock,
                              @Value("${umdc.smartkin.application-id}") UUID applicationId,
                              @Value("${umdc.smartkin.mail.template-ids.refresh:5m}") Duration refresh,
                              @Value("${umdc.smartkin.mail.template-ids.miss-cooldown:30s}") Duration missCooldown) {
        this(() -> mercuryTokens.withToken(token -> mercuryClient.templateDefinitions(token, applicationId)), clock, refresh, missCooldown);
    }

    TemplateIdRegistry(Supplier<List<MercuryTemplateDefinition>> loader, Clock clock, Duration refresh, Duration missCooldown) {
        this.loader = loader;
        this.clock = clock;
        this.refresh = refresh;
        this.missCooldown = missCooldown;
    }

    /// The template of an e-mail kind in a language.
    ///
    /// @param kind     `verification` or `password_reset`
    /// @param language `es`, `en`, ...
    /// @return its `templateDefinedId`, or empty when mercury has no such template for SmartKin (after asking again if the last look is old enough)
    public Optional<UUID> idOf(String kind, String language) {
        var key = key(kind, language);
        var now = clock.instant();
        var loaded = lastLoad.get();
        var stale = loaded == null || !now.isBefore(loaded.plus(refresh));
        var hit = Optional.ofNullable(ids.get().get(key));
        if (hit.isPresent() && !stale) {
            return hit;
        }
        var last = lastAttempt.get();
        if (stale || last == null || !now.isBefore(last.plus(missCooldown))) {
            reload();
            return Optional.ofNullable(ids.get().get(key));
        }
        return hit;
    }

    private void reload() {
        lastAttempt.set(clock.instant());
        var read = new HashMap<String, UUID>();
        try {
            for (var definition : loader.get()) {
                var kind = kindOf(definition.location(), definition.language());
                if (kind.isEmpty() || definition.templateDefinedId() == null) {
                    continue;
                }
                var key = key(kind.get(), definition.language());
                if (read.putIfAbsent(key, definition.templateDefinedId()) != null) {
                    LOGGER.warn("mercury has more than one template for {}; keeping the first ({})", key, definition.location());
                }
            }
        } catch (FeignException | IllegalStateException e) {
            LOGGER.warn("mercury's templates could not be read ({}: {}); keeping the last ones", e.getClass().getSimpleName(), e.getMessage());
            return;
        }
        var before = ids.getAndSet(Map.copyOf(read));
        lastLoad.set(clock.instant());
        if (!before.equals(read)) {
            LOGGER.info("Templates from mercury: {} usable ({} before)", read.size(), before.size());
        }
    }

    /// What a template file is for: the kind of e-mail, read from `sk_<kind>_<language>.<ext>`.
    static Optional<String> kindOf(String location, String language) {
        if (location == null || location.isBlank() || language == null || language.isBlank()) {
            return Optional.empty();
        }
        var name = location.substring(location.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        var dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replace('-', '_');
        if (!name.startsWith(PREFIX)) {
            return Optional.empty();
        }
        name = name.substring(PREFIX.length());
        var suffix = "_" + language.strip().toLowerCase(Locale.ROOT);
        if (!name.endsWith(suffix)) {
            return Optional.empty();
        }
        name = name.substring(0, name.length() - suffix.length());
        return name.isEmpty() ? Optional.empty() : Optional.of(VERIFICATION_CODE.equals(name) ? VERIFICATION : name);
    }

    private static String key(String kind, String language) {
        return kind.toLowerCase(Locale.ROOT) + "/" + language.strip().toLowerCase(Locale.ROOT);
    }
}
