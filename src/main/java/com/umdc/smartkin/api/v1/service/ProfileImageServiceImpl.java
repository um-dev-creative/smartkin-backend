package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.PostProfileImageResponse;
import com.umdc.smartkin.util.UpstreamFailure;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.MESSAGE_ERROR_HEADER;
import static com.umdc.smartkin.constant.SmartKinAppConstants.PROFILE_IMAGE_MISSING_MESSAGE;

/// Hands the image to backbone, which stores it for the user of the session token (so a caller can only set their own image).
@Service
public class ProfileImageServiceImpl implements ProfileImageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImageServiceImpl.class);
    private static final String BACKBONE_REFUSED = "The image could not be stored, try again later.";

    private final BackboneClient backboneClient;
    private final UUID applicationId;

    /// @param backboneClient the identity service that stores the image
    /// @param applicationId  SmartKin's application id
    public ProfileImageServiceImpl(BackboneClient backboneClient, @Value("${umdc.smartkin.application-id}") UUID applicationId) {
        this.backboneClient = backboneClient;
        this.applicationId = applicationId;
    }

    @Override
    public ResponseEntity<PostProfileImageResponse> upload(String token, MultipartFile image) {
        if (Objects.isNull(image) || image.isEmpty()) {
            return ResponseEntity.badRequest().header(MESSAGE_ERROR_HEADER, PROFILE_IMAGE_MISSING_MESSAGE).build();
        }
        try {
            var stored = backboneClient.saveProfilePhoto(token, applicationId, image.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(stored.getBody());
        } catch (FeignException e) {
            LOGGER.warn("Backbone did not take the profile image (status {})", e.status());
            return ResponseEntity.status(UpstreamFailure.statusToAnswer(e)).header(MESSAGE_ERROR_HEADER, BACKBONE_REFUSED).build();
        } catch (IOException e) {
            LOGGER.warn("The uploaded image could not be read: {}", e.getMessage());
            return ResponseEntity.badRequest().header(MESSAGE_ERROR_HEADER, PROFILE_IMAGE_MISSING_MESSAGE).build();
        }
    }
}
