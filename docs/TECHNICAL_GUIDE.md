# SmartKin Backend: Technical Guide

## Stack

| Component | Version |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.1 |
| Spring Cloud | 2025.1.3 (Config, Bootstrap, OpenFeign, Vault, Eureka client) |
| springdoc | 3.1.0 (`springdoc-openapi-starter-webmvc-ui`) |
| MapStruct | 1.6.3 |
| PostgreSQL JDBC | 42.7.13 |
| Shared libraries | `com.umdc:commons`, `commons-services`, `security-oauth` 0.0.3 (Repsy) |
| Docker base image | `amazoncorretto:25.0.4-alpine3.24` |

Versions are aligned with `zerobound-rest`, `mercury` and `backbone-rest`. Boot-managed starters carry no explicit version.

## Build and test

```bash
mvn clean package            # jar at target/smartkin-backend.jar
mvn verify                   # tests, PMD, CPD and JaCoCo report (target/site/jacoco/jacoco.xml)
mvn test -Dtest=Class#method # single test
```

The private Repsy repository needs `REPSY_ACCOUNT_USER` and `REPSY_ACCOUNT_PASSWORD`; the `com.umdc` libraries can also come
from the local Maven cache.

## Database

PostgreSQL on Supabase, schema `smartkin`, database `umdevc` (shared project with zerobound). User ids come from backbone and are stored as plain UUIDs
(decision D-1). The schema is created and evolved by Flyway (`src/main/resources/db/migration`, decision D-2); Hibernate uses `ddl-auto: none`, so every
entity change needs a new `V<n>__*.sql`.

| Variable | Default | Purpose |
| --- | --- | --- |
| `SMARTKIN_DB_URI` | none | Host only, e.g. `aws-1-us-east-2.pooler.supabase.com` |
| `SMARTKIN_DB_PORT` | 6543 | Transaction pooler used by the application |
| `SMARTKIN_DB_DIRECT_PORT` | 5432 | Connection used by Flyway (session locks) |
| `SMARTKIN_DB_NAME` | `umdevc` | Database |
| `SMARTKIN_DB_SCHEMA` | `smartkin` | Flyway schema and history table location |
| `SMARTKIN_DB_USERNAME`, `SMARTKIN_DB_PASSWORD` | none | Credentials, from Vault |
| `SMARTKIN_DB_POOL_SIZE` | 5 | Hikari maximum pool size |
| `FLYWAY_ENABLED` | `true` | `false` in production when the pipeline applies migrations |

Create the empty schema with `docs/db/smartkin_schema_bootstrap.sql` (or let Flyway do it through `create-schemas`). Use a dedicated database role for SmartKin.
The config-server file `stage-folder-config/smartkin-backend-*.yml` still carries its own datasource block; the variables above take effect through `bootstrap.yml`,
so remove or align that block when the environment is switched to Supabase.

## MongoDB

MongoDB holds what the relational model should not carry: lesson scene scripts and quizzes, quiz attempts and raw interaction events. Documents are Java records in
`com.umdc.smartkin.mongo.document`; ids that point at PostgreSQL (`topicId`, `assignmentId`) or at backbone (`childId`) are plain UUIDs.

| Collection | Purpose | Indexes |
| --- | --- | --- |
| `lessons` | Scenes, interactions and quiz of a lesson (`status`: DRAFT, PUBLISHED, ARCHIVED) | unique `slug`; `topicId` + `status` |
| `quiz_attempts` | One document per attempt, with answers and score | unique `childId` + `lessonId` + `attemptNumber`; `childId` + `completedAt` desc; `assignmentId` |
| `interaction_events` | Raw taps, drops and choices, kept for analysis | `childId` + `lessonId` + `occurredAt` desc; `attemptId`; TTL on `expiresAt` (0 s) |

`InteractionEventService.record(...)` stamps `expiresAt = now + prx.interaction-events.retention` (`SMARTKIN_INTERACTION_EVENTS_RETENTION`, default 90 days, an assumption to
confirm with the data-protection policy for minors). MongoDB's TTL monitor runs about once a minute, so expiry is not instantaneous.

| Variable | Default | Purpose |
| --- | --- | --- |
| `SMARTKIN_MONGO_HOST` | none | Cluster host (Atlas SRV host, or `host:port` with `SMARTKIN_MONGO_SCHEME=mongodb`) |
| `SMARTKIN_MONGO_SCHEME` | `mongodb+srv` | URI scheme |
| `SMARTKIN_MONGO_DATABASE` | `smartkin` | Database |
| `SMARTKIN_MONGO_USERNAME`, `SMARTKIN_MONGO_PASSWORD` | none | Credentials from Vault; URL-encode special characters |

Index creation at startup needs a user with `createIndex` rights on the database; the unique indexes will fail to build if duplicate data already exists.

## Continuous integration

`.github/workflows/ci.yml` runs `mvn -B verify` on JDK 25 (Temurin) for pushes and pull requests to `main` and `development`, and uploads
`target/site/jacoco/` as the `backend-coverage` artifact. Optional repository secrets `REPSY_ACCOUNT_USER` and `REPSY_ACCOUNT_PASSWORD` are passed
as environment variables for the private Repsy repository. The workflow does not build the Docker image because the certificates copied by the
`Dockerfile` (`src/main/resources/*.crt`, `keystore.jks`) are not in the repository.

## Coverage policy

- Every task must reach **85%** line and branch coverage on the classes and methods it adds or changes.
- `jacoco.minimum.coverage` is 0.85 and the package-level JaCoCo check fails the build (`mvn verify`).
- The check skips code with no logic of its own: `*Application`, `config/**`, `jpa/domain/**` (entities), `jpa/repository/**` and the generated
  `mapper/*Impl` classes. The entity model is verified by `JpaMappingContainerTest` instead.
- Last measurement (JV-6): 99.5% lines, 99.0% branches and 99.1% methods on the gated code.

## Tests

| Test | What it covers |
| --- | --- |
| `UserServiceImplTest`, `UserRegisterServiceImplTest`, `AuthServiceImplTest` | Business flows with mocked Feign clients, mappers and Kafka producer |
| `SessionJwtServiceImplTest`, `JwtUtilTest` | Token signing, claims and parsing |
| `UserControllersTest` | Delegation, route mapping with MockMvc and the `*Api` default methods |
| `MappersTest` | MapStruct mappings and their default methods |
| `FeignConfigurersTest` | Bearer token interceptors against a local HTTP server that plays the token endpoint |
| `TransferObjectsTest`, `EmailMessageProducerServiceImplTest` | `toString` and the Jackson 3 contract, including the snake_case payload sent to mercury |
| `MongoCollectionsContainerTest`, `InteractionEventServiceTest` | Documents, repositories, unique and TTL indexes on MongoDB 7 (Testcontainers) and the retention stamp |
| `JpaMappingContainerTest` | Flyway baseline on a PostgreSQL 17 Testcontainer, CRUD round trips of the 13 entities, the 4 enums and the cascade |

Docker must be running for `JpaMappingContainerTest`.
Shared builders live in `src/test/java/com/umdc/smartkin/support/TestData.java`.

## Spring Boot 4 migration notes

- `@EntityScan` lives in `org.springframework.boot.persistence.autoconfigure`.
- Jackson 3 uses `tools.jackson.*` (`ObjectMapper`, `JacksonException`, unchecked). Jackson annotations stay in `com.fasterxml.jackson.annotation`.
- javac no longer finds annotation processors on the classpath: `maven-compiler-plugin` lists `mapstruct-processor` and
  `spring-boot-configuration-processor` in `annotationProcessorPaths`.
- Surefire passes JaCoCo's `${argLine}` plus the Mockito agent; ASM 9.10.1 is required to read Java 25 bytecode.

## Feign client URLs

`@FeignClient(url = "${prx.clients.backbone.url}")` and `${prx.clients.mercury.url}` are resolved from `bootstrap.yml`, where
`BACKBONE_URL` and `MERCURY_URL` override the QA defaults. `FeignClientUrlConfigTest` checks the placeholders, the defaults and the override.

## Known gaps

- The service starts only with the full environment (Vault, Config Server, SSL bundle, OAuth clients); startup with a complete
  environment was not verified in this task.
- The Docker image was not built in this task.
- The enum labels (D-2) and the baseline DDL are derived from the entities and need confirmation against any existing `smartkin` schema in `umdevc`.
- Flyway and the Supabase connection were exercised only against a Testcontainers PostgreSQL, not against Supabase.
