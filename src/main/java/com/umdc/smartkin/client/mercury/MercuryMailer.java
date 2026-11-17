package com.umdc.smartkin.client.mercury;

import com.umdc.smartkin.client.mercury.to.MercuryMessageRequest;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/// Hands the e-mails of SmartKin to mercury over HTTPS (`POST /api/v1/messages`, channel `email`), the way zerobound-rest does. Mercury
/// validates the message, stores it durably (`OPENED`), answers at once and sends, retries and archives it on its own: SmartKin keeps no
/// queue and no broker is involved. The `Idempotency-Key` makes a retry safe (mercury answers the first message instead of sending a
/// second e-mail). The code travels only inside the message; mercury keeps it, counts the attempts and expires it.
@Component
public class MercuryMailer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MercuryMailer.class);
    private static final String CHANNEL_EMAIL = "email";
    private static final String BODY = "verification";
    private static final String KEY_PREFIX = "REGISTER-CODE:";
    private static final String RESEND_KEY_PREFIX = "RESEND-CODE:";
    private static final String RESET_KEY_PREFIX = "RESET-CODE:";
    private static final long SECONDS_PER_MINUTE = 60;
    private static final int GROUP_BOUND = 10_000;

    private final MercuryClient mercuryClient;
    private final MercuryTokens mercuryTokens;
    private final UUID applicationId;
    private final UUID templateDefinedId;
    private final String passwordResetTemplate;
    private final String language;
    private final String from;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /// Builds the mailer.
    ///
    /// @param mercuryClient     mercury's Feign client
    /// @param mercuryTokens     SmartKin's Mercury M2M token
    /// @param clock             the clock `sendDate` is read from
    /// @param applicationId     SmartKin's application id
    /// @param templateDefinedId the mercury template definition of the verification e-mail (`sk_verification_<language>`)
    /// @param passwordResetTemplate the mercury template definition of the password reset e-mail (`sk_password_reset_<language>`); empty = not configured
    /// @param language          language of that template
    /// @param from              sender address
    public MercuryMailer(MercuryClient mercuryClient, MercuryTokens mercuryTokens, Clock clock,
                         @Value("${umdc.smartkin.application-id}") UUID applicationId,
                         @Value("${umdc.verification.code.template.id}") UUID templateDefinedId,
                         @Value("${umdc.password-reset.template.id:}") String passwordResetTemplate,
                         @Value("${umdc.smartkin.mail.language:es}") String language,
                         @Value("${umdc.smartkin.mail.from:support@latinhub.info}") String from) {
        this.mercuryClient = mercuryClient;
        this.mercuryTokens = mercuryTokens;
        this.clock = clock;
        this.applicationId = applicationId;
        this.templateDefinedId = templateDefinedId;
        this.passwordResetTemplate = passwordResetTemplate;
        this.language = language;
        this.from = from;
    }

    /// Sends the verification code of a new account.
    ///
    /// @param userId   the new user
    /// @param email    where the code goes
    /// @param fullName shown in the e-mail (`user_name`)
    /// @throws MailDeliveryException when mercury cannot be reached, refuses the token or does not accept the message
    public void sendVerificationCode(UUID userId, String email, String fullName) {
        send(KEY_PREFIX + userId, userId, email, fullName);
    }

    /// Sends a new verification code to an existing account (the visitor lost the first one or spent its attempts); mercury expires the
    /// pending code when the new one is created. The key repeats inside the same minute, so a double click sends one e-mail.
    ///
    /// @param userId   the user
    /// @param email    where the code goes
    /// @param fullName shown in the e-mail (`user_name`)
    /// @throws MailDeliveryException when mercury cannot be reached, refuses the token or does not accept the message
    public void resendVerificationCode(UUID userId, String email, String fullName) {
        long minute = clock.instant().getEpochSecond() / SECONDS_PER_MINUTE;
        send(RESEND_KEY_PREFIX + userId + ":" + minute, userId, email, fullName);
    }

    /// Sends the code that lets the owner of an account choose a new password. It uses its own template, so the e-mail does not talk about
    /// verifying the account. The key repeats inside the same minute, so a double click sends one e-mail.
    ///
    /// @param userId   the user
    /// @param email    where the code goes
    /// @param fullName shown in the e-mail (`user_name`)
    /// @throws MailDeliveryException when the template is not configured, mercury cannot be reached, refuses the token or does not accept the message
    public void sendPasswordResetCode(UUID userId, String email, String fullName) {
        if (passwordResetTemplate == null || passwordResetTemplate.isBlank()) {
            throw new MailDeliveryException("the password reset template is not configured (PRX_PASSWORD_RESET_TEMPLATE_ID)");
        }
        long minute = clock.instant().getEpochSecond() / SECONDS_PER_MINUTE;
        send(RESET_KEY_PREFIX + userId + ":" + minute, userId, email, fullName, UUID.fromString(passwordResetTemplate.trim()));
    }

    private void send(String idempotencyKey, UUID userId, String email, String fullName) {
        send(idempotencyKey, userId, email, fullName, templateDefinedId);
    }

    private void send(String idempotencyKey, UUID userId, String email, String fullName, UUID template) {
        var request = new MercuryMessageRequest(CHANNEL_EMAIL, userId, email, template, applicationId, language, from, BODY,
                LocalDateTime.now(clock.withZone(ZoneOffset.UTC)), Map.of("vc", newCode(), "user_name", fullName));
        try {
            var response = mercuryTokens.withToken(token -> mercuryClient.sendMessage(token, idempotencyKey, request));
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new MailDeliveryException("mercury answered " + response.getStatusCode().value());
            }
            LOGGER.info("E-mail with a code for user {} queued in mercury", userId);
        } catch (FeignException e) {
            throw new MailDeliveryException("mercury did not take the verification e-mail (status " + e.status() + ")", e);
        } catch (IllegalStateException e) {
            throw new MailDeliveryException("mercury is not configured: " + e.getMessage(), e);
        }
    }

    /// Two groups of four digits, `####-####`, from a cryptographic generator.
    private String newCode() {
        return String.format("%04d-%04d", random.nextInt(GROUP_BOUND), random.nextInt(GROUP_BOUND));
    }
}
