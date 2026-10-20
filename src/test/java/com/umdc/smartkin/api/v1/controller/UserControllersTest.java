package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.UserRegisterService;
import com.umdc.smartkin.api.v1.service.UserService;
import com.umdc.smartkin.api.v1.to.ConfirmCodeRequest;
import com.umdc.smartkin.api.v1.to.GetUserResponse;
import com.umdc.smartkin.api.v1.to.PutUserRequest;
import com.umdc.smartkin.api.v1.to.UserCreateRequest;
import com.umdc.smartkin.api.v1.to.UserCreateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static com.umdc.smartkin.support.TestData.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllersTest {

    @Mock
    private UserService userService;
    @Mock
    private UserRegisterService userRegisterService;

    @Test
    void userControllerDelegatesEveryOperationToTheService() {
        var controller = new UserController(userService);
        var createRequest = mock(UserCreateRequest.class);
        var putRequest = mock(PutUserRequest.class);
        ResponseEntity<UserCreateResponse> created = ResponseEntity.status(HttpStatus.CREATED).build();
        ResponseEntity<GetUserResponse> found = ResponseEntity.ok().build();
        ResponseEntity<Void> updated = ResponseEntity.noContent().build();
        ResponseEntity<Void> deleted = ResponseEntity.noContent().build();
        when(userService.create(createRequest)).thenReturn(created);
        when(userService.findUser("token", USER_ID)).thenReturn(found);
        when(userService.update(USER_ID, putRequest)).thenReturn(updated);
        when(userService.deleteUserByUserAndApplication(USER_ID)).thenReturn(deleted);

        assertEquals(created, controller.createUserPost(createRequest));
        assertEquals(found, controller.userGet("token", USER_ID));
        assertEquals(updated, controller.update(USER_ID, putRequest));
        assertEquals(deleted, controller.deleteUserByUserId(USER_ID));
    }

    @Test
    void userRegisterControllerDelegatesToTheService() {
        var controller = new UserRegisterController(userRegisterService);
        var request = new ConfirmCodeRequest(USER_ID, "1234-5678");
        ResponseEntity<Void> ok = ResponseEntity.ok().build();
        when(userRegisterService.confirmCode("bkd", request)).thenReturn(ok);

        assertEquals(ok, controller.confirmCode("bkd", request));
    }

    @Test
    void routesAreMappedUnderApiV1() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
        when(userService.findUser("token", USER_ID)).thenReturn(ResponseEntity.notFound().build());
        when(userService.deleteUserByUserAndApplication(USER_ID)).thenReturn(ResponseEntity.noContent().build());

        mvc.perform(get("/api/v1/users/{id}", USER_ID).header("session-token", "token")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/users/{id}", USER_ID)).andExpect(status().isNoContent());
        verify(userService).deleteUserByUserAndApplication(USER_ID);
    }

    @Test
    void apiDefaultsAnswerNotImplementedWhenNoServiceIsProvided() {
        UserApi userApi = new UserApi() { };
        UserRegisterApi registerApi = new UserRegisterApi() { };

        assertEquals(HttpStatus.NOT_IMPLEMENTED, userApi.createUserPost(mock(UserCreateRequest.class)).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, userApi.userGet("token", UUID.randomUUID()).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, userApi.update(USER_ID, mock(PutUserRequest.class)).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, userApi.deleteUserByUserId(USER_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, registerApi.confirmCode("bkd", new ConfirmCodeRequest(USER_ID, "1")).getStatusCode());
    }

    @Test
    void apiDefaultsUseTheServiceProvidedByTheImplementation() {
        UserApi userApi = new UserApi() {
            @Override
            public UserService getService() {
                return userService;
            }
        };
        UserRegisterApi registerApi = new UserRegisterApi() {
            @Override
            public UserRegisterService getService() {
                return userRegisterService;
            }
        };
        var createRequest = mock(UserCreateRequest.class);
        var putRequest = mock(PutUserRequest.class);
        var confirm = new ConfirmCodeRequest(USER_ID, "1");

        userApi.createUserPost(createRequest);
        userApi.userGet("token", USER_ID);
        userApi.update(USER_ID, putRequest);
        userApi.deleteUserByUserId(USER_ID);
        registerApi.confirmCode("bkd", confirm);

        verify(userService).create(createRequest);
        verify(userService).findUser("token", USER_ID);
        verify(userService).update(USER_ID, putRequest);
        verify(userService).deleteUserByUserAndApplication(USER_ID);
        verify(userRegisterService).confirmCode("bkd", confirm);
    }

    @Test
    void serviceDefaultsAnswerNotImplemented() {
        UserService users = new UserService() { };
        UserRegisterService register = new UserRegisterService() { };

        assertEquals(HttpStatus.NOT_IMPLEMENTED, users.create(null).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, users.findUser("t", USER_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, users.update(USER_ID, null).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, users.deleteUserByUserAndApplication(USER_ID).getStatusCode());
        assertEquals(HttpStatus.NOT_IMPLEMENTED, register.confirmCode("t", null).getStatusCode());
    }
}
