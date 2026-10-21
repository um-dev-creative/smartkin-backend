package com.umdc.smartkin.api.v1.to;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/// A parent profile; `parentId` is the parent's backbone user id.
///
/// @param parentId       the parent's backbone user id
/// @param phoneNumber    phone number
/// @param address        postal address
/// @param profilePicture picture reference
@Schema(name = "ParentProfile")
public record ParentProfileResponse(UUID parentId, String phoneNumber, String address, String profilePicture) {
}
