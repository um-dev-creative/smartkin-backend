package com.umdc.smartkin.client.mercury.to;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/// Body of mercury's `POST /api/v1/messages`: one message for any channel. SmartKin names the template by its `templateDefinedId`; the
/// subject is the template's, so none is sent.
///
/// @param channel           channel code (`email`)
/// @param userId            the user the message is for
/// @param target            the recipient address in that channel
/// @param templateDefinedId id of the template definition in mercury
/// @param applicationId     SmartKin's application
/// @param language          language of the template (`es`, `en`)
/// @param from              sender address
/// @param body              mercury requires a body; the template renders the real one
/// @param sendDate          when to send, `yyyy-MM-dd HH:mm:ss` (UTC)
/// @param params            the template parameters (`vc`, `user_name`)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MercuryMessageRequest(
        String channel,
        UUID userId,
        String target,
        UUID templateDefinedId,
        UUID applicationId,
        String language,
        String from,
        String body,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime sendDate,
        Map<String, Object> params) {

    /// No target and no parameters: they carry personal data and the verification code.
    @Override
    public String toString() {
        return "MercuryMessageRequest{channel=" + channel + ", userId=" + userId + ", templateDefinedId=" + templateDefinedId
                + ", language=" + language + ", sendDate=" + sendDate + ", params=" + (params == null ? 0 : params.size()) + " keys}";
    }
}
