# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- BE-13: `EndToEndFlowContainerTest`, the full MVP slice on real PostgreSQL (Flyway V1 to V5), MongoDB and Kafka with the production services, publisher, consumers and achievement evaluator: link a child, browse the catalog, assign an activity (notification through Kafka), report progress, fail and then pass the server-graded quiz, and receive the quiz points, the achievement, its points and its notification asynchronously; assigning again after completion, 404 for another family, and no event in the error topic. It does not start the web layer, the security library, Vault, TLS or Redis (needs the QA environment of BE-2).
- BE-14: error topic monitoring. `ErrorTopicMonitor` wraps the consumer error handling and records `smartkin.events.delivery_failed` (per failed attempt), `smartkin.events.dead_lettered` (records sent to the error topic, tagged by topic and cause) and `smartkin.events.dead_letter_failed` (the error topic itself could not be written; the error propagates so the offset is not committed), plus `ERROR` logs starting with `DEAD_LETTERED` / `DEAD_LETTER_FAILED`. Retry and dead-letter behavior is unchanged. `KafkaConsumerConfig.smartKinErrorHandler` now receives a `MeterRegistry`. The alert rule and a metrics endpoint/registry remain with the platform team.
- BE-12: notification inbox API. `GET /api/v1/notifications[?unread=&page=&size=]`, `GET /api/v1/notifications/unread-count`, `POST /api/v1/notifications/{notificationId}/read` and `POST /api/v1/notifications/read-all` (`listNotifications`, `getUnreadNotificationCount`, `markNotificationRead`, `markAllNotificationsRead`). Every user reaches only their own notifications (the `uid` of the token is the `user_id`); another user's notification answers 404; marking as read is idempotent and keeps the first `readAt`; mark-all is a single `update` limited to the caller. New `NotificationMapper`, `UnreadCountResponse` and repository methods `findByUserId`, `findByUserIdAndIsReadFalse`, `countByUserIdAndIsReadFalse`, `findByIdAndUserId` and `markAllRead`. All 25 operations of the MVP contract are now implemented.
- BE-11: achievement rules. Achievements are earned by a JSON rule in `achievements.requirements` with three types: `QUIZZES_PASSED` (distinct lessons passed), `ACTIVITIES_COMPLETED` (distinct activities completed) and `POINTS_REACHED` (point balance), each with a `threshold`. `AchievementEvaluator` (own `REQUIRES_NEW` transaction) awards each achievement once per child with `insert ... on conflict do nothing` and publishes `ACHIEVEMENT_EARNED` after the commit; `AchievementTrigger` runs it after a passed `QUIZ_COMPLETED` and after `ACHIEVEMENT_EARNED` (once the points are booked, so `POINTS_REACHED` and cascades work) and after a child completes an activity by reporting 100, never failing the caller. Invalid rules are logged and skipped. Migration `V5__achievement_rules.sql` adds the unique index on `child_achievements (child_id, achievement_id)` and seeds three placeholder achievements. `SmartKinEventListener` and `ProgressServiceImpl` now receive an `AchievementTrigger`; `DomainEventDispatcher` also publishes `ACHIEVEMENT_EARNED` and exposes `afterCommit`.
- BE-10: points and achievements API. `GET /api/v1/children/{childId}/points[?page=&size=]` and `GET /api/v1/children/{childId}/achievements` (`getChildPoints`, `listChildAchievements`). Readable by the child and by the linked parent (another family's child answers 404, a sibling 403); read-only, since points and achievements are written by the event consumers. Points come as the balance plus a page of movements, newest first; achievements newest first with the achievement data loaded in one query. New `RewardsMapper` and repository methods `PointRepository.findByChildId` and `ChildAchievementRepository.findByChildIdOrderByEarnedAtDesc`.
- BE-9: quiz attempts API. `POST /api/v1/assignments/{assignmentId}/quiz-attempts` and `GET .../quiz-attempts` (`submitQuizAttempt`, `listQuizAttempts`). The server grades the answers with `QuizGrader` (a question is correct only for the exact set of correct options, no partial credit; percentage rounded down; passed at `passingPercentage`), stores the attempt in MongoDB with the lesson version, keeps the best score in the progress, completes the assignment when the quiz is passed and publishes `QUIZ_COMPLETED` after the commit. Only the assigned child may submit; `maxAttempts` per assignment answers 409; unknown questions or options answer 400. The attempt is inserted first and deleted again if the progress cannot be written. New `ProgressRecorder`, `QuizAttemptMapper` and repository methods `countByAssignmentId` / `findByAssignmentIdOrderByAttemptNumber`.
- `DomainEventDispatcher`: the publish-after-commit logic of `ACTIVITY_ASSIGNED` (BE-7) moved to a shared component (also used by `QUIZ_COMPLETED`); behavior unchanged.
- BE-8: progress API. `GET/PUT /api/v1/assignments/{assignmentId}/progress` and `PUT /api/v1/assignments/{assignmentId}/feedback` (`getProgress`, `updateProgress`, `putParentFeedback`). Only the assigned child reports progress (first report sets `startTime`, the percentage never goes down, 100 sets `completionTime` once and frees the activity to be assigned again); only the assigning parent leaves feedback; before the child starts, progress reads as 0%. Updates lock the assignment (`findForUpdateById`, `PESSIMISTIC_WRITE`) so concurrent first reports cannot violate the unique key of `activity_progress`. Anyone who cannot reach the child gets 404.
- BE-7: assignments API. `POST /api/v1/assignments`, `GET /api/v1/assignments[?childId=&page=&size=]` and `GET /api/v1/assignments/{assignmentId}` (`assignActivity`, `listAssignments`, `getAssignment`). A parent assigns an active activity only to their own children (optional future `dueDate`; 409 while the child has the same activity open); parents list all their children's assignments, children their own, newest first and paged; other families' assignments answer 404. `ACTIVITY_ASSIGNED` is published after the database commit and never blocks or fails the assignment. New `ClockConfig` (`Clock` bean), generic `PageResponse<T>`, `AssignmentMapper` and repository methods `findByChildId`, `findByChildParentId`, `existsOpenAssignment` and progress lookups.
- BE-6: catalog API. `GET /api/v1/topics`, `/topics/{topicId}/lessons`, `/lessons/{lessonId}`, `/activities[?topicId=]` and `/activities/{activityId}` (`listTopics`, `listTopicLessons`, `getLesson`, `listActivities`, `getActivity`). Topic lessons use the Redis-cached `LessonCatalogService`; only `PUBLISHED` lessons and `is_active` activities are served (404 otherwise). The lesson response types have no `correct` flag on options and no question feedback, so answers cannot leak. Migration `V4__activity_lesson_link.sql` adds the nullable `smartkin.activities.lesson_id` (plain id, no foreign key; existing activities keep a `null` lesson) and `ActivityEntity.lessonId`.
- BE-5: ownership rules in one place. `AccessPolicy` (`security.access`) decides who may reach which child (`viewChild`: the child or the linked parent; `manageChild`: only the linked parent) and returns `OWNER`, `SELF`, `NOT_VISIBLE` (404, hides other families' ids) or `FORBIDDEN` (403) together with the loaded child. `ChildServiceImpl` and `ParentServiceImpl` use it with no change in their answers; later endpoints (assignments, progress, quiz, points) will too. Tests: `AccessPolicyTest` and `CrossFamilyAccessContainerTest` (two families on PostgreSQL).
- BE-4: parent and child profiles. `GET/PUT /api/v1/parents/me` and `GET/POST /api/v1/children`, `GET/PUT /api/v1/children/{childId}` (operations `getMyParentProfile`, `putMyParentProfile`, `listMyChildren`, `linkChild`, `getChild`, `updateChild` of the contract). The caller is the `uid` of the session token: a user with a `parent` row is a parent, one with a `child` row a child. A parent reaches only their own children; a child of another family answers 404. Linking checks that the user exists in backbone, is not the caller and has no parent or child profile yet (409 otherwise, also on a concurrent link). New `ParentService`/`ChildService`, `ParentMapper`/`ChildMapper` and `ChildRepository.findAllByParentId`. `ControllerContractConformanceTest` compares every implemented controller with `docs/api/openapi-v1.yaml`.
- X-2: MVP API contract `docs/api/openapi-v1.yaml` (OpenAPI 3.0.3, 25 operations: parents and children, catalog, assignments and progress, quiz attempts, points and achievements, notifications) and decision record `docs/decisions/D-6-mvp-api-contract.md`. It is documentation only: no controller or behavior changes. `OpenApiContractTest` keeps it well formed (resolved references, unique operation ids, no `correct` flag on quiz options, existing user endpoints not redefined).
- `docs/BACKLOG_MVP.md`: analysis of smartkin-backend and smartkin-frontend and the unified MVP backlog (tickets BE-n, FE-n and X-n) with the execution status of each ticket.

### Added
- Container images aligned with mercury, backbone-rest and zerobound-rest. `Dockerfile` (JVM) is now two-stage with Spring Boot layers, a `docker-entrypoint.sh` (`exec java`, `-XX:+UseCompactObjectHeaders`, `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError`, `JAVA_OPTS`), a non-root user and a `HEALTHCHECK` on `/actuator/health/liveness`; the internal CA is an optional BuildKit secret. `Dockerfile.native` (experimental: it builds but the binary does not start yet, see the technical guide) builds a GraalVM native image (Debian builder, Maven repository cache mount, `umdc-local` fallback for a Repsy outage, `NATIVE_QUICK_BUILD`) with its own `Dockerfile.native.dockerignore`, `config/native.env` and a `native` Maven profile; `NativeRuntimeHints` registers the reflection, resource and proxy hints the dependencies lack.
- `docker-compose.yml`, `config/local.env` and `secrets/local.env.example`: the same two-file split (versioned bootstrap identity, git-ignored `VAULT_TOKEN`), `extra_hosts` for the `*.umdc-qa.tst` names, static IP `172.22.0.12` on `nginx_umdc-net`, port 8166, health check, 30 s stop grace period, `no-new-privileges`, read-only root with a `/tmp` tmpfs, and `SMARTKIN_TAG` to run the native image.
- `management.endpoints`: only `health` is exposed, with the liveness/readiness probes; `HealthProbeSecurityConfig` opens `/actuator/health/**` without a token (the security library answered 401 to everything else).
- `pom.xml` copies `certs/smartkin/` (git-ignored: `smartkin.jks`, `umdc-truststore.jks`) onto the classpath, like backbone-rest, so the keystores of the TLS bundle travel inside the jar.

### Changed
- X-2 contract amendment: errors are the HTTP status plus the `message-error` header with an empty body (the existing convention) instead of a JSON `ErrorResponse`; the `ErrorResponse` schema was removed before any controller used it.
- The old `Dockerfile` imported five `.crt` files and a keystore from `src/main/resources` that are not in the repository, so it could not be built from a fresh checkout; those imports are gone.
- `bootstrap.yml` now sets `umdc.security.keystore` / `truststore` (the RestTemplate of the security library, whose absence threw a `NullPointerException` at start) and `eureka.client.tls`, from the same `SSL_*` variables as the TLS bundle.

### Fixed
- `bootstrap.yml` used the old `prx.*` property names for what the com.umdc libraries bind, so the application context could not start: `AuthProperties.getClients()` was null and `BackboneFeignConfigurer` threw a `NullPointerException`. The settings now live under the names the libraries read: `umdc.security.auth.clients`, `umdc.security.jwt`, `umdc.api.endpoint`, `umdc.backbone.base-url`, `umdc.management.clientRoles` and `umdc.logging.trace.enabled` (the JWT secret and expiration, the backbone and mercury clients and `AUTH_ROLE_ID` take the same environment variables as before). A test binds them from `bootstrap.yml`. Found while smoke-testing the container image (the JV-3 library move did not rename them and no test started the whole context).

### Added
- KF-2: SmartKin consumes its own domain events (`SmartKinEventListener`, group `smartkin-backend`). `ACTIVITY_ASSIGNED` creates an inbox notification for the child;
  `QUIZ_COMPLETED` books the score as points when the quiz was passed; `ACHIEVEMENT_EARNED` books the achievement's points and notifies the child. Points also raise
  `child.total_points`. Effects are idempotent (existence checks plus the unique indexes of migration `V3__idempotent_event_effects.sql`).
  Email requests to mercury keep going through `EmailMessageProducerService` to `prx.producer.mercury.topic`.
- KF-3: consumer retries and error topic. A failing record is retried with exponential back-off (`KAFKA_CONSUMER_MAX_ATTEMPTS` 4 in total, 1s start, x2, 30s cap) and then
  published to `smartkin.events.error.v1` (`KAFKA_ERROR_TOPIC`) with the exception in its headers. Malformed records (JSON or payload errors) skip the retries.
  `KAFKA_CONSUMER_ENABLED=false` turns the listeners off.

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
- KF-1: domain event contract and publisher. `SmartKinEvent<T>` envelope, `EventType` (`ACTIVITY_ASSIGNED`, `QUIZ_COMPLETED`, `ACHIEVEMENT_EARNED`), payload records in
  `com.umdc.smartkin.kafka.event.payload`, `KafkaTopicsProperties` (`prx.kafka.topics.*`, defaults `smartkin.<subject>.<verb>.v1`) and `SmartKinEventPublisher`
  (JSON, keyed by the child id). Decision record `docs/decisions/D-4-domain-events-and-topics.md`; tests include a round trip through Kafka 4.0 in Testcontainers.
- DB-4: `V2__restrict_direct_access.sql` enables Row Level Security on the 13 tables, revokes every privilege from `PUBLIC`, `anon`, `authenticated` and `service_role`, and creates the
  restricted application role `smartkin_app` (DML only, no DDL). Decision record `docs/decisions/D-3-database-access-only-through-the-backend.md`;
  `DirectAccessRestrictionContainerTest` proves the application role works, cannot change the schema, and that the API roles are locked out even after an accidental grant.
- `SMARTKIN_FLYWAY_USERNAME` and `SMARTKIN_FLYWAY_PASSWORD` let migrations run as the schema owner while the application uses `smartkin_app`.
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
