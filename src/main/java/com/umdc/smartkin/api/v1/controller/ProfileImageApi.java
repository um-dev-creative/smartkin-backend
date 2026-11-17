package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ProfileImageService;
import com.umdc.smartkin.client.backbone.to.PostProfileImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;
import static com.umdc.smartkin.constant.SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION;

/// The profile image of the signed-in user, stored in backbone.
@Tag(name = "profile-image", description = "The profile image of the signed-in user")
public interface ProfileImageApi {

    /// Provides the service.
    ///
    /// @return the profile image service
    default ProfileImageService getService() {
        return new ProfileImageService() {};
    }

    /// Stores the profile image of the caller.
    ///
    /// @param token the session token of the caller
    /// @param image the image file (part `imageData`)
    /// @return the reference of the stored image
    @Operation(summary = "Upload the profile image", description = "Stores the image as the profile image of the caller.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "The image was stored"),
        @ApiResponse(responseCode = "400", description = "The image is missing or empty"),
        @ApiResponse(responseCode = "502", description = "Backbone did not take the image")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<PostProfileImageResponse> upload(
            @Parameter(description = SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @RequestPart("imageData") MultipartFile image) {
        return getService().upload(token, image);
    }
}
