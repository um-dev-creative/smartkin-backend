package com.umdc.smartkin.api.v1.controller;

import com.umdc.smartkin.api.v1.service.CatalogService;
import com.umdc.smartkin.api.v1.to.ActivityResponse;
import com.umdc.smartkin.api.v1.to.LessonResponse;
import com.umdc.smartkin.api.v1.to.TopicResponse;
import com.umdc.smartkin.catalog.LessonSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

import static com.umdc.smartkin.constant.SmartKinAppConstants.HTTP_NOT_FOUND;
import static com.umdc.smartkin.constant.SmartKinAppConstants.HTTP_OK;
import static com.umdc.smartkin.constant.SmartKinAppConstants.NOT_FOUND_DESCRIPTION;

/// Catalog API: topics, published lessons and active activities. Read-only; any authenticated user may call it.
@Tag(name = "catalog", description = "The catalog API")
public interface CatalogApi {

    /// Provides the service that backs the operations.
    ///
    /// @return the catalog service
    default CatalogService getService() {
        return new CatalogService() {};
    }

    /// Lists the topics of the catalog.
    ///
    /// @return the topics
    @Operation(operationId = "listTopics", summary = "Topics of the catalog")
    @ApiResponses(@ApiResponse(responseCode = HTTP_OK, description = "Topics"))
    @GetMapping(value = "/topics", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<TopicResponse>> listTopics() {
        return getService().topics();
    }

    /// Lists the published lessons of a topic.
    ///
    /// @param topicId the topic's id
    /// @return the lessons
    @Operation(operationId = "listTopicLessons", summary = "Published lessons of a topic (served from the Redis-cached catalog)")
    @ApiResponses({
            @ApiResponse(responseCode = HTTP_OK, description = "Lessons"),
            @ApiResponse(responseCode = HTTP_NOT_FOUND, description = NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/topics/{topicId}/lessons", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<LessonSummary>> listTopicLessons(@NotNull @PathVariable UUID topicId) {
        return getService().topicLessons(topicId);
    }

    /// Returns a published lesson ready to play.
    ///
    /// @param lessonId the lesson's id
    /// @return the lesson
    @Operation(operationId = "getLesson", summary = "Lesson content for playback",
            description = "The quiz options never include which one is correct; correctness is only known to the server when it grades an attempt.")
    @ApiResponses({
            @ApiResponse(responseCode = HTTP_OK, description = "Lesson"),
            @ApiResponse(responseCode = HTTP_NOT_FOUND, description = NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/lessons/{lessonId}", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<LessonResponse> getLesson(@NotNull @PathVariable UUID lessonId) {
        return getService().lesson(lessonId);
    }

    /// Lists the active activities.
    ///
    /// @param topicId restrict to one topic (optional)
    /// @return the activities
    @Operation(operationId = "listActivities", summary = "Active activities, optionally of one topic")
    @ApiResponses(@ApiResponse(responseCode = HTTP_OK, description = "Activities"))
    @GetMapping(value = "/activities", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<List<ActivityResponse>> listActivities(@RequestParam(required = false) UUID topicId) {
        return getService().activities(topicId);
    }

    /// Returns an active activity.
    ///
    /// @param activityId the activity's id
    /// @return the activity
    @Operation(operationId = "getActivity", summary = "An activity")
    @ApiResponses({
            @ApiResponse(responseCode = HTTP_OK, description = "Activity"),
            @ApiResponse(responseCode = HTTP_NOT_FOUND, description = NOT_FOUND_DESCRIPTION, content = @Content)
    })
    @GetMapping(value = "/activities/{activityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    default ResponseEntity<ActivityResponse> getActivity(@NotNull @PathVariable UUID activityId) {
        return getService().activity(activityId);
    }
}
