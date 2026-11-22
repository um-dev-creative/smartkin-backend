package com.umdc.smartkin.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The versions in the startup banner come from a Maven-filtered file (`build-versions.properties`), as in backbone-rest: an unfiltered
/// `@...@` would print as is, and a banner placeholder without a default would print the raw `${...}` when the file is missing.
class BannerVersionsTest {

    @Test
    void theFilteredFileHasRealVersions() throws IOException {
        var props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("build-versions.properties")) {
            assertNotNull(in, "build-versions.properties must be on the classpath");
            props.load(in);
        }
        for (String key : new String[]{"umdc.build.spring-cloud-version", "umdc.build.umdc-libs-version"}) {
            String value = props.getProperty(key);
            assertTrue(value != null && !value.isBlank(), key + " is empty");
            assertFalse(value.contains("@"), key + " was not filtered: " + value);
        }
    }

    @Test
    void theBannerPrintsTheApplicationAndBothVersionsWithADefault() throws IOException {
        String banner = Files.readString(Path.of("src/main/resources/banner.txt"));
        assertTrue(banner.contains("${app.name}::${app.version}"));
        assertTrue(banner.contains("${spring-boot.version}"));
        assertTrue(banner.contains("${umdc.build.spring-cloud-version:n/a}"));
        assertTrue(banner.contains("${umdc.build.umdc-libs-version:n/a}"));
    }

    @Test
    void theApplicationLoadsTheFileAsOptional() throws IOException {
        assertTrue(Files.readString(Path.of("src/main/resources/application.yml")).contains("optional:classpath:build-versions.properties"));
    }
}
