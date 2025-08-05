package com.umdc.smartkin.constant;

/**
 * A utility class that contains constants used across the SmartKin application.
 * This class is designed to provide a centralized location for commonly used string constants,
 * such as package names, header keys, and messages, to enhance reusability and maintainability.
 *
 * This class cannot be instantiated.
 */
public final class SmartKinAppConstants {
    public static final String ENTITY_PACKAGE = "com.umdc.smartkin.jpa.entity";
    public static final String REPOSITORY_PACKAGE = "com.umdc.smartkin.jpa.repository";
    public static final String MESSAGE_ERROR_HEADER = "message-error";
    public static final String MESSAGE_HEADER = "message";
    public static final String USER_NOT_FOUND_MESSAGE = "User not found.";

    private SmartKinAppConstants() {
        throw new IllegalStateException("Utility class");
    }
}
