# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- RD-1: `SessionJwtServiceImpl.isValid` now also requires a `jti` that backbone-rest has not revoked. **Breaking for deployments:** the check is on by default and the service
  refuses to start without `REVOCATION_REDIS_URL`; set `REVOCATION_ENABLED=false` for local development only.
- DB-2: the datasource is configured in `bootstrap.yml` from `SMARTKIN_DB_URI`, `SMARTKIN_DB_PORT` (default 6543, transaction pooler), `SMARTKIN_DB_NAME` (default `umdevc`),
  `SMARTKIN_DB_USERNAME` and `SMARTKIN_DB_PASSWORD` (Vault or environment), with `prepareThreshold=0` and a Hikari pool of `SMARTKIN_DB_POOL_SIZE` (default 5).
- DB-3: Flyway manages the schema (`spring-boot-starter-flyway`, `flyway-database-postgresql`) on the direct connection (`SMARTKIN_DB_DIRECT_PORT`, default 5432) with
  `FLYWAY_ENABLED` to switch it off; Hibernate no longer touches the schema (`ddl-auto: none`).
- Entities with generated keys use `GenerationType.UUID`; the unmapped enum columns of `child_rewards`, `goals`, `notifications` and `points` are now mapped.
- DB-1: SmartKin no longer maps backbone's `general.user`. `UserEntity` and `UserRepository` were removed; `ParentEntity` and `ChildEntity` use the
  backbone user id as their key and `NotificationEntity` stores `userId` as a plain column. The model now has 13 entities, all in the `smartkin` schema.
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
- RD-2 (backend): `LessonCatalogService.publishedLessons(topicId)` reads the published lessons of a topic through a Redis cache (`RedisLessonCatalogCache`, key
  `smartkin:catalog:topic:<id>`, TTL `CACHE_LESSON_CATALOG_TTL`, 10 minutes by default). The cache fails open and is off when `CACHE_REDIS_URL` is empty
  (`NoLessonCatalogCache`). `evict(topicId)` drops a topic's entry when its lessons change.
- RD-1: token revocation check against backbone-rest's Redis deny-list (`jti:<value>` keys) with Lettuce: `RedisConnector` (lazy, self-healing connection),
  `RedisRevocationChecker` (fail closed, answers cached for `REVOCATION_CACHE_TTL`, 30 s by default), `NoRevocationChecker` and a handler that answers 503
  when the list cannot be consulted. Tests with a Redis 7 Testcontainer.
- MG-1: MongoDB support (`spring-boot-starter-data-mongodb`) and the collections `lessons`, `quiz_attempts` and `interaction_events`, modelled as records in
  `com.umdc.smartkin.mongo.document` with Spring Data repositories. Connection through `SMARTKIN_MONGO_*` variables; UUIDs use the standard representation.
- MG-2: indexes declared on the documents and created at startup (`auto-index-creation`): unique `slug` and `topicId+status` on lessons; unique
  `childId+lessonId+attemptNumber`, `childId+completedAt` and `assignmentId` on quiz attempts; `childId+lessonId+occurredAt` and `attemptId` on events; and a TTL index on
  `interaction_events.expiresAt`. `InteractionEventService` sets `expiresAt` from `prx.interaction-events.retention` (default 90 days).
- Tests: `InteractionEventServiceTest` and `MongoCollectionsContainerTest` (MongoDB 7 in Testcontainers).
- DB-3: `V1__baseline_smartkin_schema.sql` (13 tables, 4 enum types, indexes), the enums `RewardStatus`, `GoalStatus`, `NotificationType` and `TransactionType`, and
  `docs/decisions/D-2-baseline-schema-and-enum-labels.md`.
- `JpaMappingContainerTest` now applies the baseline to PostgreSQL 17 and round-trips every entity, the four enums and the database cascade (6 tests).
- DB-1: `docs/decisions/D-1-supabase-schema-and-user-reference.md` (schema `smartkin` in the existing Supabase project, user ids without foreign keys) and
  `docs/db/smartkin_schema_bootstrap.sql`, which creates the empty schema in the `umdevc` database.
- JV-2: GitHub Actions workflow `.github/workflows/ci.yml` (Temurin 25, `mvn -B verify`, Docker for Testcontainers, JaCoCo report as artifact)
  on pushes and pull requests to `main` and `development`. The Docker base image tag `amazoncorretto:25.0.4-alpine3.24` was checked against the registry.
- JV-6: test suite of 92 tests (JUnit 6, Mockito, MockMvc, Testcontainers): services, authentication, session JWT, controllers and API defaults,
  mappers, Feign interceptors against a local token endpoint, transfer objects and their Jackson 3 contract, and a PostgreSQL 17
  container test that boots the JPA model (13 entities). Line coverage on the gated code is above 99%.
- Unit tests for `EmailMessageProducerServiceImpl` (100% line and branch coverage).
- `CHANGELOG.md`, `docs/USER_GUIDE.md` and `docs/TECHNICAL_GUIDE.md`.
- `CLAUDE.md` with build commands and architecture notes.
