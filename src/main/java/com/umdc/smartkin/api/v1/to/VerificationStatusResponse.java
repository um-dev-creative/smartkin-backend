package com.umdc.smartkin.api.v1.to;

/// Whether the signed-in user confirmed the e-mail code. The session is backbone's token, which does not say it, so the BFF asks this at
/// sign-in and when the page loads to send an account that was created active and never verified to the verification screen.
///
/// @param verified `true` once the e-mail code was confirmed
public record VerificationStatusResponse(boolean verified) {
}
