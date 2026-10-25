package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Size;

/// Body of `POST /children/invitations`: the data the child profile will get when the invitation is accepted.
///
/// @param gradeLevel school grade, up to 50 characters
/// @param avatar     avatar reference, up to 255 characters
public record InvitationRequest(@Size(max = 50) String gradeLevel, @Size(max = 255) String avatar) {
}
