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
- Delivery: every pull request now runs the build and tests on Java 25 in GitHub Actions.
- Quality: the build now runs 92 automated tests and fails below 85% coverage. This has no effect on API consumers.
- Fix: confirming a verification code no longer fails with an internal error when mercury rejects it without a `message` header; the
  response keeps mercury's status and an empty `message` header.

## Running locally

1. Install JDK 25 and Maven 3.9.
2. Export the variables listed in `src/main/resources/bootstrap.yml` (profile, Vault, Config Server, SSL, OAuth clients).
3. Run `mvn spring-boot:run`.

Without those variables the service stops at startup with a message such as
`Profile '${SPRING_BOOT_PROFILE_ACTIVE}' must contain a letter, digit or allowed char`.
