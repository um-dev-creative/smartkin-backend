# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- JV-6: the JaCoCo package-level check blocks the build again (`haltOnFailure` removed). Its exclusions are now real class patterns
  (application class, `config`, JPA entities and repositories, generated MapStruct implementations) instead of the Sonar glob list.
- `UserRegisterServiceImpl.confirmCode` no longer throws a `NullPointerException` when mercury fails without a `message` header; it
  relays the status with an empty message.
- Surefire runs with `SPRING_BOOT_PROFILE_ACTIVE=test` and Vault, Config Server and bootstrap disabled, so slice tests can start.
- JV-5: the backbone and mercury Feign base URLs come from `prx.clients.backbone.url` and `prx.clients.mercury.url`
  (environment variables `BACKBONE_URL` and `MERCURY_URL`) instead of being hard-coded; the defaults keep the previous QA URLs.
- JV-1: platform upgraded from Java 21 / Spring Boot 3.5.4 to Java 25 / Spring Boot 4.1.1, with Spring Cloud 2025.1.3,
  springdoc 3.1.0 and PostgreSQL JDBC 42.7.13, aligned with zerobound-rest, mercury and backbone-rest.
- JV-3: shared libraries moved from `com.prx` (`prx-commons` 0.0.4, `commons-services` 0.0.1, `security-oauth` 0.0.1)
  to `com.umdc` (`commons`, `commons-services`, `security-oauth` 0.0.3); imports renamed from `com.prx.*` to `com.umdc.*`.
- JV-4: removed the legacy `springdoc-openapi-ui` 1.8.0, `eureka-client-jersey3`, `snakeyaml`, `spring-boot-starter-web-services`,
  `springdoc-openapi-maven-plugin`, the JUnit vintage engine, and the version pins on Boot-managed starters and Spring Kafka.
- `EmailMessageProducerServiceImpl` now uses Jackson 3 (`tools.jackson`), the version shipped with Spring Boot 4.
- `EntityScan` import moved to `org.springframework.boot.persistence.autoconfigure`.
- The compiler plugin declares the MapStruct and configuration-metadata annotation processors explicitly (javac no longer
  discovers them on the classpath).
- Docker base image changed to `amazoncorretto:25.0.4-alpine3.24`; certificates are imported with `keytool -cacerts`.
- JaCoCo minimum coverage raised from 80% to 85% (`jacoco.minimum.coverage`); the package-level check is report-only
  (`haltOnFailure=false`) until the legacy packages have tests.

### Added
- JV-6: test suite of 92 tests (JUnit 6, Mockito, MockMvc, Testcontainers): services, authentication, session JWT, controllers and API defaults,
  mappers, Feign interceptors against a local token endpoint, transfer objects and their Jackson 3 contract, and a PostgreSQL 17
  container test that boots the JPA model (14 entities). Line coverage on the gated code is above 99%.
- Unit tests for `EmailMessageProducerServiceImpl` (100% line and branch coverage).
- `CHANGELOG.md`, `docs/USER_GUIDE.md` and `docs/TECHNICAL_GUIDE.md`.
- `CLAUDE.md` with build commands and architecture notes.
