package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.ProfileImageService;
import com.umdc.smartkin.client.backbone.to.PostProfileImageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/// Exposes [ProfileImageApi] under `/api/v1/profile/image`.
@RestController
@RequestMapping("/api/v1/profile/image")
public class ProfileImageController implements ProfileImageApi {

    private final ProfileImageService service;

    /// @param service the profile image service
    public ProfileImageController(ProfileImageService service) {
        this.service = service;
    }

    @Override
    public ProfileImageService getService() {
        return service;
    }

    @Override
    public ResponseEntity<PostProfileImageResponse> upload(String token, MultipartFile image) {
        return service.upload(token, image);
    }
}
