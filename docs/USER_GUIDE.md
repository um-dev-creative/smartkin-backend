# SmartKin Backend: User Guide

This guide is for people who consume or operate the SmartKin API. It describes what the service does today.

## What the API offers

Base path: `/api/v1`, served over HTTPS (TLS 1.3 only). Interactive documentation is available through the
OpenAPI UI (springdoc) when the service is running.

| Operation | Method and path | Notes |
| --- | --- | --- |
| Create a user | `POST /api/v1/users` | Returns 201; 409 when alias or email already exists |
| Get a user | `GET /api/v1/users/{id}` | |
| Update a user | `PUT /api/v1/users/{userId}` | |
| Delete a user | `DELETE /api/v1/users/{userId}` | |
| Generate a session token | `POST /api/v1/auth/session-token` | Body with `alias` and `password` |
| Confirm a verification code | registration controller | Delegated to mercury |

Authenticated calls carry the session token in the header defined by `SESSION_TOKEN_KEY` (`session-token`).

## What changed for operators in this release

- The service now runs on **Java 25** and **Spring Boot 4.1.1**. Hosts and containers need a Java 25 runtime; the Docker image
  `amazoncorretto:25.0.4-alpine3.24` already includes it.
- The API contract (paths, request and response bodies) did not change.
- Configuration is unchanged, except for two optional variables: `BACKBONE_URL` and `MERCURY_URL` set the base URL of each service
  (default `https://prx-qa.backbone.tst/backbone` and `https://prx-qa.backbone.tst/mercury`). Set them per environment, for example in the
  Config Server or Vault.

- Data: SmartKin now stores its data in the `smartkin` schema of the existing Supabase project, created by database migrations at startup. Configure the
  connection with `SMARTKIN_DB_URI`, `SMARTKIN_DB_USERNAME` and `SMARTKIN_DB_PASSWORD` (see the technical guide for the other variables). User accounts stay in backbone;
  the API does not change.
- MongoDB: lessons, quiz attempts and interaction events will be stored in MongoDB. Set `SMARTKIN_MONGO_HOST`, `SMARTKIN_MONGO_USERNAME` and `SMARTKIN_MONGO_PASSWORD`
  (and optionally `SMARTKIN_MONGO_DATABASE`). Raw interaction events expire after 90 days by default (`SMARTKIN_INTERACTION_EVENTS_RETENTION`). No endpoint changes yet.
- Sessions: a token that backbone has revoked (for example after logout) is now rejected by SmartKin within about 30 seconds. Set `REVOCATION_REDIS_URL` to the Redis that
  backbone uses; the service will not start without it unless `REVOCATION_ENABLED=false`. If that Redis is down, authenticated calls answer **503** until it is back.
- Cache: set `CACHE_REDIS_URL` to speed up the lesson catalog; leave it empty to read straight from MongoDB. A cache outage never breaks the catalog.
- Database access: SmartKin's tables are closed to direct access (Row Level Security). Run the application with the `smartkin_app` database role (enable it with a password, see the technical guide)
  and run migrations with the schema owner via `SMARTKIN_FLYWAY_USERNAME` and `SMARTKIN_FLYWAY_PASSWORD`.
- Events: SmartKin now defines the Kafka topics `smartkin.activity.assigned.v1`, `smartkin.quiz.completed.v1` and `smartkin.achievement.earned.v1` for other services to consume. Ask the platform team to create them
  before the first release that publishes to them; nothing publishes yet.
- Events: SmartKin now also reads its three event topics. Passing a quiz and earning an achievement add points to the child, and new assignments and achievements appear in the child's
  inbox. Records that cannot be processed are retried and then sent to the error topic `smartkin.events.error.v1`, which the platform team must create and monitor (see the technical guide).
  Apply migration V3 (it runs at startup) before the first event arrives.
- Delivery: every pull request now runs the build and tests on Java 25 in GitHub Actions.
- Quality: the build now runs 150 automated tests and fails below 85% coverage. This has no effect on API consumers.
- Fix: confirming a verification code no longer fails with an internal error when mercury rejects it without a `message` header; the
  response keeps mercury's status and an empty `message` header.

## Parents and children (new)

A parent can now keep a profile and link children to it. All calls carry the session token.

| Operation | Method and path | Notes |
| --- | --- | --- |
| Save my parent profile | `PUT /api/v1/parents/me` | Optional phone (max 20), address, picture. Creates the profile the first time |
| Read my parent profile | `GET /api/v1/parents/me` | 404 until it is saved |
| Link a child | `POST /api/v1/children` | Body `childUserId` (an account already created with `POST /users`), optional `gradeLevel` and `avatar`. 409 if that account already has a parent or child profile |
| List my children | `GET /api/v1/children` | Only the caller's children |
| Read a child | `GET /api/v1/children/{childId}` | The parent, or the child themselves. Another family's child answers 404 |
| Edit a child | `PUT /api/v1/children/{childId}` | Parent only; grade level and avatar |

Privacy between families is enforced on every call: a parent only ever sees and edits their own children, a child only sees themselves and cannot edit anything, and asking for a child of another family answers **404** exactly as if it did not exist. A user with neither a parent nor a child profile gets 403.

Errors return the HTTP status and the reason in the `message-error` header. Nothing existing changes. A user whose verification code is not completed gets 403 here.

## Assignments (new)

A parent gives an activity to one of their children, and both can follow it. All calls carry the session token.

| Operation | Method and path | Notes |
| --- | --- | --- |
| Assign an activity | `POST /api/v1/assignments` | Body `childId`, `activityId`, optional `dueDate` (must be in the future). Parent only, and only for their own children. 409 if the child already has that activity assigned and not completed |
| List assignments | `GET /api/v1/assignments` | A parent sees all their children's (add `?childId=` for one); a child sees their own. Newest first, paged with `page` (from 0) and `size` (1 to 100, default 20) |
| Read an assignment | `GET /api/v1/assignments/{assignmentId}` | Includes the child's progress once they start. An assignment of another family answers 404 |

Assigning an activity also sends the child a notification in their inbox. If the notification service is down at that moment the assignment is still created and only the notification is missing.

## Catalog (new)

Any signed-in user can browse what a child can learn. All calls are `GET` and carry the session token.

| Operation | Path | Notes |
| --- | --- | --- |
| Topics | `/api/v1/topics` | Ordered by name |
| Lessons of a topic | `/api/v1/topics/{topicId}/lessons` | Published lessons only; 404 if the topic does not exist |
| A lesson | `/api/v1/lessons/{lessonId}` | Scenes and quiz, ready to play. The quiz never says which option is correct (the server grades the answers); drafts and archived lessons answer 404 |
| Activities | `/api/v1/activities` and `/api/v1/activities?topicId=...` | Active activities a parent can assign; each says which lesson it plays (`lessonId`, empty for older activities) |
| An activity | `/api/v1/activities/{activityId}` | 404 when it does not exist or is inactive |

**Operators:** this release adds database migration V4 (a nullable `lesson_id` column on `activities`). It runs at startup with the Flyway account (`SMARTKIN_FLYWAY_USERNAME`) and does not change or remove any data.

## API contract for the MVP

The operations the MVP will offer are described in `docs/api/openapi-v1.yaml`: link a child to a parent, browse topics, lessons and activities, assign an activity, report progress, submit a quiz,
see points and achievements, and read the notification inbox. This release only publishes the description; **none of these endpoints exists yet**, and the current ones (users, registration, session token) work as before.
Each endpoint will be available as its ticket is delivered. The quiz answers are graded by the server and the lessons never tell the client which option is correct.

## Running in a container

1. Put the TLS keystores in `certs/smartkin/` (`smartkin.jks`, `umdc-truststore.jks`; git-ignored) **before** `mvn clean package`: they are copied into the jar.
2. Copy `secrets/local.env.example` to `secrets/local.env` and set `VAULT_TOKEN`. `config/local.env` already points at the QA Vault and Config Server; everything else (Supabase, Atlas, Redis, Kafka, the backbone and mercury clients) comes from Vault.
3. Once per Docker host, create the shared network: `docker network create --driver bridge --subnet 172.22.0.0/16 --gateway 172.22.0.1 nginx_umdc-net`.
4. `mvn -B clean package && docker compose build && docker compose up -d`. The service listens on HTTPS 8166, has the fixed IP `172.22.0.12`, and `docker compose ps` shows its health. Put `api.umdc-qa.tst/smartkin-backend/` in nginx in front of it.
5. Native image: **experimental, do not deploy yet**. `Dockerfile.native` builds, but the binary does not start (see the technical guide). Use the JVM image from step 4.

The container stops gracefully on `docker stop` (up to 30 s). A new Vault token or Config Server change needs a restart.

## Running locally

1. Install JDK 25 and Maven 3.9.
2. Export the variables listed in `src/main/resources/bootstrap.yml` (profile, Vault, Config Server, SSL, OAuth clients).
3. Run `mvn spring-boot:run`.

Without those variables the service stops at startup with a message such as
`Profile '${SPRING_BOOT_PROFILE_ACTIVE}' must contain a letter, digit or allowed char`.
