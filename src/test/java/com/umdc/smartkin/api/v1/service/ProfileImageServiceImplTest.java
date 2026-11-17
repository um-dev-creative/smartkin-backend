package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.controller.ProfileImageController;
import com.umdc.smartkin.client.backbone.BackboneClient;
import com.umdc.smartkin.client.backbone.to.PostProfileImageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.umdc.smartkin.support.TestData.APPLICATION_ID;
import static com.umdc.smartkin.support.TestData.feign;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceImplTest {

    @Mock
    private BackboneClient backboneClient;
    private ProfileImageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProfileImageServiceImpl(backboneClient, APPLICATION_ID);
    }

    @Test
    void storesTheImageForTheUserOfTheToken() {
        var image = new MockMultipartFile("imageData", "a.png", "image/png", new byte[]{1, 2});
        when(backboneClient.saveProfilePhoto("t", APPLICATION_ID, new byte[]{1, 2}))
                .thenReturn(ResponseEntity.ok(new PostProfileImageResponse("ref-1")));

        var response = service.upload("t", image);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("ref-1", response.getBody().ref());
    }

    @Test
    void refusesAMissingOrEmptyImage() {
        assertEquals(HttpStatus.BAD_REQUEST, service.upload("t", null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, service.upload("t", new MockMultipartFile("imageData", new byte[0])).getStatusCode());
        verifyNoInteractions(backboneClient);
    }

    @Test
    void relaysBackboneFailuresWithoutBlamingTheCallerForOurCredential() {
        var image = new MockMultipartFile("imageData", "a.png", "image/png", new byte[]{1});
        when(backboneClient.saveProfilePhoto(any(), any(), any())).thenThrow(feign(401)).thenThrow(feign(413));

        assertEquals(HttpStatus.BAD_GATEWAY, service.upload("t", image).getStatusCode());
        assertEquals(HttpStatus.CONTENT_TOO_LARGE, service.upload("t", image).getStatusCode());
    }

    @Test
    void answersBadRequestWhenTheFileCannotBeRead() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getBytes()).thenThrow(new IOException("gone"));

        assertEquals(HttpStatus.BAD_REQUEST, service.upload("t", broken).getStatusCode());
    }

    @Test
    void theControllerDelegatesAndTheApiDefaultsAnswerNotImplemented() {
        var answer = ResponseEntity.status(HttpStatus.CREATED).<PostProfileImageResponse>build();
        var image = new MockMultipartFile("imageData", new byte[]{1});
        var delegate = mock(ProfileImageService.class);
        when(delegate.upload("t", image)).thenReturn(answer);
        var controller = new ProfileImageController(delegate);

        assertEquals(answer, controller.upload("t", image));
        assertEquals(delegate, controller.getService());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, new com.umdc.smartkin.api.v1.controller.ProfileImageApi() { }.upload("t", image).getStatusCode());
    }
}
