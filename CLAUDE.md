# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

SmartKin backend: a Spring Boot 4.1 / Java 25 REST API (Maven, package `com.umdc.smartkin`). It depends on in-house libraries (`com.umdc:commons`, `commons-services`, `security-oauth`) hosted on a private Repsy Maven repo, so builds need `REPSY_ACCOUNT_USER` and `REPSY_ACCOUNT_PASSWORD` set in the environment (or the libraries in `~/.m2`).

## Commands

- Build: `mvn clean package` (produces `target/smartkin-backend.jar`; `finalName` is fixed)
- Run all tests + quality gates: `mvn test` (PMD `check`/`cpd-check` with `ruleset.xml` and the JaCoCo `check` are bound to the `test` phase and fail the build; JaCoCo minimum is 85% and the package-level check fails the build)
- Single test: `mvn test -Dtest=ClassName#method`
- Docker image is built from the pre-built jar in `target/` (`Dockerfile`, exposes 8166, imports several `.crt` files from `src/main/resources` into the JVM cacerts)

Task rules: changes are made on a branch derived from `development`, pushed to the remote and integrated through a PR to `development`; never merged locally (see "Delivery workflow" in `docs/TECHNICAL_GUIDE.md`). 85% coverage on touched code, update `CHANGELOG.md`, `docs/USER_GUIDE.md` and `docs/TECHNICAL_GUIDE.md`. PMD uses the `sonar.exclusions` property in `pom.xml`; JaCoCo has its own class-pattern exclusions (config, entities, repositories, generated mapper implementations, Application). Integration tests need Docker (Testcontainers).

## Configuration

All config is in `src/main/resources/bootstrap.yml` and is almost entirely `${ENV_VAR}` placeholders. Values come from the environment, Vault (`spring-cloud-starter-vault-config`, kv backend `UMDC`), and a Spring Cloud Config server (`CNFS_URI`). The app uses the Spring Cloud bootstrap context and serves HTTPS only (TLS 1.3 via the `smartkin-security` SSL bundle). Spring `SecurityAutoConfiguration` is excluded; auth is handled by the PRX security library. The app will not start locally without the full set of env vars/Vault.

## Architecture

The service is a thin orchestration layer: it owns almost no business logic of its own and delegates user identity and verification to two other PRX services via Feign.

- `api/v1/controller`: each resource has an `*Api` interface and a `*Controller` class. The Api interface carries all the Spring MVC mappings, OpenAPI annotations, and default method bodies that delegate to `getService()`. The controller just implements the interface and supplies the service. Add endpoints and docs in the interface.
- `api/v1/service`: `UserService`/`UserServiceImpl` and `UserRegisterService`/`UserRegisterServiceImpl`. Interfaces have default methods; implementations are the Spring beans.
- `api/v1/to`: request/response transfer objects for this API.
- `client/backbone` (`BackboneClient`): Feign client to the "backbone" service, the system of record for users, tokens and profile images. Its DTOs are in `client/backbone/to`.
- `client/mercury` (`MercuryClient`): Feign client to the "mercury" service, which handles verification codes (confirm-code flow).
- `interceptor/*FeignConfigurer`: per-client Feign configuration, matching the `prx.auth.clients` entries (`backbone`, `mercury`) in `bootstrap.yml`.
- `security/SessionJwtServiceImpl`: implementation of the PRX security library's `SessionJwtService`, as in zerobound-rest. The session is the **backbone session token** the BFF got at sign-in, sent as is in `session-token`; SmartKin issues no tokens of its own (the library's `/auth` controllers are excluded from the scan). It requires the backbone signature (`APP_TOKEN_SECRET`, shared with backbone-rest), `type`, `exp`, `jti`, a UUID `uid`, `aid` = the SmartKin application (`umdc.smartkin.application-id`), `iss`/`aud` (`SessionClaimPolicy`) and a `jti` not revoked by backbone. `security/verification` keeps accounts that were created active and never verified out of the API by asking mercury (`VerificationStatus`). Calls to Mercury need Mercury's M2M token, which `client/mercury/MercuryTokens` gets by trading the Backbone M2M token of SmartKin's managed client (`umdc.clients.mercury.m2m.*`) at mercury's `POST /api/v1/auth/token`; there is no alias/password towards mercury (see `docs/TECHNICAL_GUIDE.md`, "Authentication towards mercury").
- `mapper`: MapStruct mappers between API TOs and client DTOs. Use constants from `constant/SmartKinAppConstants` rather than literals.
- `kafka`: producer for `EmailMessageTO` messages (`EmailMessageProducerService`, config in `KafkaProducerConfig`).
- `jpa/domain` and `jpa/repository`: JPA entities and Spring Data repositories (PostgreSQL) for the domain model: users, parents, children, activities, assigned activities and progress, goals, rewards, achievements, points, notifications, topics. Datasource setup is in `config/DataBaseConfig`.

Feign error handling convention: catch `FeignException` and relay `e.status()` plus the `MESSAGE_HEADER` response header to the caller, rather than throwing.

## Style

Javadoc is written in both `/** */` and Markdown `///` styles; each package has a `package-info.java`. Branches follow `sak-<n>-<description>`; changes are made on a branch derived from `development`, pushed to the remote and integrated through a PR to `development`. Never merged locally.
