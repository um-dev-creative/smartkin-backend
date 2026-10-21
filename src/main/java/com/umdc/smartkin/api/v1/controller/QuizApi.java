package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.QuizAttemptService;
import com.umdc.smartkin.api.v1.to.QuizAttemptRequest;
import com.umdc.smartkin.api.v1.to.QuizAttemptResponse;
import com.umdc.smartkin.constant.SmartKinAppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/// Quiz API: the attempts a child makes on the quiz of an assigned lesson.
@Tag(name = "quiz", description = "The quiz API")
public interface QuizApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the quiz attempt service
    default QuizAttemptService getService() {
        return new QuizAttemptService() {};
    }

    /// Lists the attempts of an assignment.
    ///
    /// @param token        the session token
    /// @param assignmentId the assignment's id
    /// @return the attempts
    @Operation(operationId = "listQuizAttempts", summary = "Quiz attempts of an assignment")
    @ApiResponses({
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_OK, description = "Attempts"),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/{assignmentId}/quiz-attempts", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<QuizAttemptResponse>> listQuizAttempts(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID assignmentId) {
        return getService().list(token, assignmentId);
    }

    /// Submits the answers of a quiz attempt.
    ///
    /// @param token        the session token
    /// @param assignmentId the assignment's id
    /// @param request      the answers
    /// @return the graded attempt
    @Operation(operationId = "submitQuizAttempt", summary = "Submit the answers of a quiz attempt (the assigned child only)",
            description = "The server grades the answers against the lesson version, stores the attempt and publishes `QUIZ_COMPLETED`; points are booked by the "
                    + "event consumer when the quiz is passed. Answers to unknown questions are rejected. 409 when `maxAttempts` is reached.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Attempt graded"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_FORBIDDEN, description = SmartKinAppConstants.FORBIDDEN_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = SmartKinAppConstants.HTTP_NOT_FOUND, description = SmartKinAppConstants.NOT_FOUND_DESCRIPTION, content = @Content),
            @ApiResponse(responseCode = "409", description = "The request conflicts with the current state", content = @Content)
    })
    @PostMapping(value = "/{assignmentId}/quiz-attempts", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<QuizAttemptResponse> submitQuizAttempt(
            @Parameter(description = SmartKinAppConstants.SESSION_TOKEN_DESCRIPTION, required = true) @RequestHeader(SESSION_TOKEN_KEY) String token,
            @NotNull @PathVariable UUID assignmentId,
            @Valid @RequestBody QuizAttemptRequest request) {
        return getService().submit(token, assignmentId, request);
    }
}
