package com.umdc.smartkin.api.v1.controller;

import com.umdc.security.annotation.SkipSessionValidation;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// The sign-up is the only user operation open without a session (the visitor has none yet); the interceptor of
/// security-oauth looks the annotation up with `AnnotationUtils.findAnnotation` on the handler method.
class UserSessionValidationTest {

    @Test
    void creatingAUserDoesNotNeedASession() {
        assertNotNull(AnnotationUtils.findAnnotation(handler("createUserPost"), SkipSessionValidation.class));
    }

    @Test
    void everyOtherUserOperationStillNeedsASession() {
        List<Method> others = Arrays.stream(UserController.class.getDeclaredMethods())
                .filter(method -> !"createUserPost".equals(method.getName()) && java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .toList();
        others.forEach(method -> assertNull(AnnotationUtils.findAnnotation(method, SkipSessionValidation.class), method.getName()));
    }

    private static Method handler(String name) {
        return Arrays.stream(UserController.class.getDeclaredMethods()).filter(method -> name.equals(method.getName())).findFirst().orElseThrow();
    }
}
