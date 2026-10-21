package com.umdc.smartkin.constant;

import java.util.UUID;

/**
 * A utility class that contains constants used across the SmartKin application.
 * This class is designed to provide a centralized location for commonly used string constants,
 * such as package names, header keys, and messages, to enhance reusability and maintainability.
 *
 * This class cannot be instantiated.
 */
public final class SmartKinAppConstants {
    public static final String ENTITY_PACKAGE = "com.umdc.smartkin.jpa.domain";
    public static final String REPOSITORY_PACKAGE = "com.umdc.smartkin.jpa.repository";
    public static final String MESSAGE_ERROR_HEADER = "message-error";
    public static final String MESSAGE_HEADER = "message";
    public static final String USER_NOT_FOUND_MESSAGE = "User not found.";
    public static final String HTTP_FORBIDDEN = "403";
    public static final String FORBIDDEN_DESCRIPTION = "The caller may not do this";
    public static final String SESSION_TOKEN_DESCRIPTION = "Token session";
    public static final String CALLER_UNKNOWN_MESSAGE = "The session token does not identify a user.";
    public static final String NOT_A_PARENT_MESSAGE = "Only a parent can do this.";
    public static final String CHILD_CANNOT_BE_PARENT_MESSAGE = "A child cannot have a parent profile.";
    public static final String PARENT_PROFILE_NOT_FOUND_MESSAGE = "Parent profile not found.";
    public static final String CHILD_NOT_FOUND_MESSAGE = "Child not found.";
    public static final String CHILD_USER_NOT_FOUND_MESSAGE = "The child user does not exist.";
    public static final String CHILD_IS_CALLER_MESSAGE = "A parent cannot be their own child.";
    public static final String CHILD_ALREADY_LINKED_MESSAGE = "The user already has a parent or a child profile.";
    public static final String DEFAULT_GENDER = "N";
    public static final UUID PHONE_CONTACT_TYPE_ID = UUID.fromString("61ed9501-bee2-4391-8376-91307ae02a48");

    private SmartKinAppConstants() {
        throw new IllegalStateException("Utility class");
    }
}
