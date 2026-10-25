package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

/// The account a parent created for their child.
///
/// @param child the child profile, already linked to the parent
/// @param alias the user name the child signs in with
/// @param email the e-mail address the verification code was sent to
@Schema(name = "ChildAccount")
public record ChildAccountResponse(ChildResponse child, String alias, String email) {
}
