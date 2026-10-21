package com.umdc.smartkin.api.v1.to;

import jakarta.validation.constraints.Size;

/// Body of `PUT /parents/me`: the optional contact data of a parent profile.
///
/// @param phoneNumber    phone number, up to 20 characters
/// @param address        postal address
/// @param profilePicture picture reference, up to 255 characters
public record ParentProfileRequest(
        @Size(max = 20) String phoneNumber,
        String address,
        @Size(max = 255) String profilePicture) {
}
