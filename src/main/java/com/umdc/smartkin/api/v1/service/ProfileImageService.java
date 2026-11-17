package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.client.backbone.to.PostProfileImageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/// Profile image of the signed-in user.
public interface ProfileImageService {

    /// Stores the image as the profile image of the caller.
    ///
    /// @param token the session token of the caller
    /// @param image the image file
    /// @return 201 with the reference of the stored image
    default ResponseEntity<PostProfileImageResponse> upload(String token, MultipartFile image) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
