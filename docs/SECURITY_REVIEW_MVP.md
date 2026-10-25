# Security review of the MVP (X-5)

Date: 2026-10-23. Scope: smartkin-backend (all MVP endpoints, events, database) and smartkin-frontend (Angular app and Express BFF).
Method: reading the code, the tests and the repository settings, plus the dependency alerts GitHub reports. **It is not a penetration test**: nothing was run against a live environment, and the security library (PRX), Vault, TLS and Kafka were not exercised (that needs the QA environment of BE-2/X-1).

## Verdict

Nothing found is a reason to stop the MVP, but **one item must be closed before real families use it** (S-1); S-2 (linking without consent) and S-3 (secrets in logs) were serious and are already fixed. Details below.

| # | Finding | Severity | Status |
| --- | --- | --- | --- |
| S-1 | The caller's identity is the `uid` read from the session token **without checking its signature** in the controllers | High until verified | **Open: verify in QA** |
| S-2 | A parent can link **any existing user** as their child without that user's consent | High (product) | **Fixed** (invitation code or parent-created account; see the section below) |
| S-3 | The BFF wrote tokens, passwords and all Vault values to its logs | High | **Fixed** (smartkin-frontend PR #22) |
| S-4 | A Vault token is in the git history of smartkin-frontend (commit `2eb6965`) | High | **Open: rotate (FE-2)** |
| S-5 | 24 open dependency alerts in smartkin-frontend (2 critical, 11 high); 0 in smartkin-backend | Medium | **Fixed in code** (smartkin-frontend PR #24: `pnpm audit` 33 advisories to 0); closes when the stack is merged and Dependabot re-scans |
| S-6 | Session tokens are stored unencrypted in Redis (BFF) and in the browser's `sessionStorage` | Medium | Accepted with conditions |
| S-7 | Kafka events are not transactional (no outbox) and the topics' access control is not defined here | Medium | Accepted, documented |
| S-8 | Several lists are not paged (children, topics, activities, achievements, quiz attempts) | Low | Accepted |
| S-9 | Linking a child answers differently for "does not exist" and "already linked", which lets a parent probe user ids | Low | **Fixed** with S-2 (the endpoint is gone) |

## Open items

### S-1: identity comes from an unverified token payload
`JwtUtil.getUidFromToken` decodes the middle part of the session token and returns its `uid`; the controllers use it as the caller (parent profile, ownership of children, assignments, progress, quiz, points, notifications). That is safe **only if** the PRX security library rejects every request whose token is not validly signed, expired or revoked, **before** the controller runs. The unit and integration tests cannot show it, because none of them starts that library (they call the services directly).
**Local evidence (2026-10-25, branch sak-41):** the library's `SecurityConfig` is no longer scanned and `SessionTokenSecurityConfig` plus the library's `SessionJwtInterceptor` authenticate `/api/v1/**`. On the running JVM jar, a token signed with another key, an unsigned (`alg: none`) one, an expired one and no token all answered 401, while a valid token reached the repository. Redis revocation and the deployed PRX library are still to be checked in QA.
**To close it (X-1/X-4 in QA):** run `scripts/qa-smoke.sh` (see `docs/QA_RUNBOOK_MVP.md`), which makes these calls. By hand: with a token whose payload has the `uid` of another real user but a wrong signature, an unsigned token (`alg: none`), an expired one and a revoked one (`jti` in the Redis deny-list), call at least `GET /parents/me`, `GET /children`, `GET /notifications` and `POST /assignments`. **Every call must answer 401** (503 if the revocation list is down) and must not reach the service. If any answers 2xx or 403 with data, it is a critical vulnerability: the identity must then be read from the validated `Claims` (`SessionJwtService.getTokenClaims`) instead of from the raw payload.

### S-2: linking a child needed no consent (fixed)
`POST /children` linked the user id the parent typed, so a parent who knew a valid id could capture an unrelated account. **Fixed:** the endpoint was removed. A user now joins a family only by (a) presenting a one-time invitation code the parent created (`POST /children/invitations/accept`), or (b) because the parent created that user's account (`POST /children/accounts`, child under 18). Codes are 8 random characters, single use, valid 7 days, stored only as SHA-256, and every invalid code answers the same 404. A race between two accepts by the same user (two parents' codes at once) used to let the second one overwrite the first parent through `merge`; creating a profile is now an insert, so the second accept fails with 409 (`ChildEntity` is `Persistable`; `ChildInvitationContainerTest`). Residual risk: there is no per-caller rate limit on `accept` (31^8 combinations and a 7-day life make guessing impractical, but a gateway limit is advisable) and a parent who shares the code with the wrong person lets that person join (revoke it from the list).

### S-4 and S-5
S-5 is addressed in smartkin-frontend PR #24 (stacked on the upgrade chain): `bcrypt` (unused, brought `tar`) was removed, `node-vault-client` (its `request` chain has no fixed release) was replaced by a small `fetch` reader, and the rest were updated or pinned with `overrides`; `pnpm audit` reports 0. The GitHub alerts are on the default branch, so they clear only after the chain is merged. The same PR stops the BFF printing the Vault token in debug mode.

S-4 needs the Vault token to be rotated (it cannot be undone by deleting the file; purging history is optional). For S-5, the alerts are on the default branch of smartkin-frontend, before the Angular/Node/Express upgrade chain (PRs #7 to #22) is merged; re-run Dependabot after the merge and fix what remains, starting with `form-data` and `tar` (critical). The backend has none.

## Accepted, with conditions

- **S-6.** Tokens are bearer credentials: whoever reads them can act as the user until they expire or are revoked. The BFF keeps them in Redis (RD-2) and the app in `sessionStorage`. Conditions: Redis must use `rediss://` with a password and be reachable only from the BFF; the app must not contain HTML injection sinks (checked: none of the new screens uses `innerHTML`, `bypassSecurityTrust*` or `document.write`; Angular escapes the rest); keep token lifetimes short.
- **S-7.** If publishing an event fails after the commit, the assignment or attempt exists but the notification, points or achievement points are missing (D-4, BE-7, BE-9, BE-11 describe it). Nothing is lost silently: it is logged and counted (`smartkin.events.*`, BE-14). Restrict who can produce to the `smartkin.*` topics: any producer could create points, which is a broker-level control outside this repository.
- **S-8.** The lists that are not paged are small by nature at MVP scale. Add paging if a parent can have many children or the catalog grows past a few hundred activities.
- **S-9.** Fixed with S-2: `POST /children` no longer exists, and `accept` answers 404 for every invalid code.

## Verified (what is in good shape)

| Area | Evidence |
| --- | --- |
| A parent reaches only their own children; a child only themselves; another family's data answers 404 so ids are not revealed | `AccessPolicy` and its matrix test; `CrossFamilyAccessContainerTest` on real PostgreSQL covers profiles, assignments, progress, quiz, points, achievements and notifications |
| The right answers never leave the server | The lesson types have no `correct` or `feedback`; `CatalogMapperTest` serializes a lesson and asserts neither appears; grading is `QuizGrader` on the server |
| The database is closed to direct access | Row Level Security on every table and the restricted role `smartkin_app` (D-3, V2) with a test that the API roles are locked out |
| Inputs are bounded and validated | Request records with `@Valid` and size limits; pages capped at 100; `maxAttempts` per assignment; a due date must be in the future |
| Revoked tokens are refused | `SessionJwtServiceImpl.isValid` checks the `jti` in Redis and fails closed with 503 (RD-1) |
| No privilege taken from the client | Roles are deduced on the server from the profile rows; the frontend role guards only decide which screen to show |
| No secrets in the repository's code | Configuration comes from environment, Vault and Config Server; the history issue is S-4 |
| Backend dependencies | 0 open Dependabot alerts |
| Errors do not leak internals | Failures answer a status and a fixed reason in `message-error`; backbone errors are relayed by status only |

## What this review did not cover
The PRX security library itself, Vault, the TLS bundle and Kafka/Redis/MongoDB/Supabase deployment settings (network, credentials, ACLs); load or abuse testing (rate limits on the new endpoints were not reviewed); mobile or browser-specific hardening such as Content-Security-Policy headers of the web server in front of the BFF.
