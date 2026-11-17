package com.umdc.smartkin.api.v1.service;

import com.umdc.smartkin.api.v1.to.GetUserResponse;
import com.umdc.smartkin.api.v1.to.PutUserRequest;
import com.umdc.smartkin.api.v1.to.UserCreateRequest;
import com.umdc.smartkin.api.v1.to.UserCreateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/// Service interface for user-related operations.
public interface UserService {


    /// Creates a new user.
    ///
    /// @param userCreateRequest the request object containing user details
    /// @return a ResponseEntity containing the response object and HTTP status
    /// @see UserCreateRequest
    /// @see UserCreateResponse
    /// @see ResponseEntity
    /// @see HttpStatus
    default ResponseEntity<UserCreateResponse> create(UserCreateRequest userCreateRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Finds a user by ID.
    ///
    /// @param id the ID of the user to find
    /// @return a ResponseEntity containing the response object and HTTP status
    /// @see GetUserResponse
    /// @see ResponseEntity
    /// @see HttpStatus
    /// @see UUID
    default ResponseEntity<GetUserResponse> findUser(String token, UUID id) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Updates a user by ID.
    ///
    /// @param token the session token of the caller, who must be the user
    /// @param userId the ID of the user to update
    /// @param request the patch user request
    /// @return a ResponseEntity containing the patch user response
    default ResponseEntity<Void> update(String token, UUID userId, PutUserRequest request) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Deletes a user by user ID and application ID.
    ///
    /// @param token the session token of the caller, who must be the user
    /// @param userId the ID of the user to delete
    /// @return a ResponseEntity with appropriate status
    /// @see ResponseEntity
    /// @see UUID
    default ResponseEntity<Void> deleteUserByUserAndApplication(String token, UUID userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    /// Deletes a user without asking who the caller is. Only for the service's own clean-up of an account it has just created (a child
    /// whose profile failed); never reachable from an endpoint.
    ///
    /// @param userId the ID of the user to delete
    /// @return a ResponseEntity with appropriate status
    default ResponseEntity<Void> deleteUser(UUID userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

}
