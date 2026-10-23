# Security review of the MVP (X-5)

Date: 2026-10-23. Scope: smartkin-backend (all MVP endpoints, events, database) and smartkin-frontend (Angular app and Express BFF).
Method: reading the code, the tests and the repository settings, plus the dependency alerts GitHub reports. **It is not a penetration test**: nothing was run against a live environment, and the security library (PRX), Vault, TLS and Kafka were not exercised (that needs the QA environment of BE-2/X-1).

## Verdict

Nothing found is a reason to stop the MVP, but **two items must be closed before real families use it** (S-1 and S-2), and S-3 (secrets in logs) was serious and is already fixed. Details below.

| # | Finding | Severity | Status |
| --- | --- | --- | --- |
| S-1 | The caller's identity is the `uid` read from the session token **without checking its signature** in the controllers | High until verified | **Open: verify in QA** |
| S-2 | A parent can link **any existing user** as their child without that user's consent | High (product) | **Open: decision needed** |
| S-3 | The BFF wrote tokens, passwords and all Vault values to its logs | High | **Fixed** (smartkin-frontend PR #22) |
| S-4 | A Vault token is in the git history of smartkin-frontend (commit `2eb6965`) | High | **Open: rotate (FE-2)** |
| S-5 | 24 open dependency alerts in smartkin-frontend (2 critical, 11 high); 0 in smartkin-backend | Medium | **Open: re-check after the upgrade chain is merged** |
| S-6 | Session tokens are stored unencrypted in Redis (BFF) and in the browser's `sessionStorage` | Medium | Accepted with conditions |
| S-7 | Kafka events are not transactional (no outbox) and the topics' access control is not defined here | Medium | Accepted, documented |
| S-8 | Several lists are not paged (children, topics, activities, achievements, quiz attempts) | Low | Accepted |
| S-9 | Linking a child answers differently for "does not exist" and "already linked", which lets a parent probe user ids | Low | Accepted |

## Open items

### S-1: identity comes from an unverified token payload
`JwtUtil.getUidFromToken` decodes the middle part of the session token and returns its `uid`; the controllers use it as the caller (parent profile, ownership of children, assignments, progress, quiz, points, notifications). That is safe **only if** the PRX security library rejects every request whose token is not validly signed, expired or revoked, **before** the controller runs. The unit and integration tests cannot show it, because none of them starts that library (they call the services directly).
**To close it (X-1/X-4 in QA):** with a token whose payload has the `uid` of another real user but a wrong signature, an unsigned token (`alg: none`), an expired one and a revoked one (`jti` in the Redis deny-list), call at least `GET /parents/me`, `GET /children`, `GET /notifications` and `POST /assignments`. **Every call must answer 401** (503 if the revocation list is down) and must not reach the service. If any answers 2xx or 403 with data, it is a critical vulnerability: the identity must then be read from the validated `Claims` (`SessionJwtService.getTokenClaims`) instead of from the raw payload.

### S-2: linking a child needs no consent from the child's account
`POST /children` links the user id the parent writes. The only checks are that the user exists in backbone, is not the parent, and has no profile yet. A parent who knows or guesses a valid user id can link an unrelated account, then see its points and achievements, assign activities and send it notifications. User ids are random UUIDs, so guessing is unlikely, but ids appear in other places (shared screenshots, support tickets), and an account created for another purpose can be captured.
**Options (product decision):** (a) an invitation code the child's account redeems; (b) the child accepts the link in their own session before it takes effect; (c) the parent creates the child's account from SmartKin (no outside user can be claimed). Until one is chosen, document it and restrict who can use the endpoint.

### S-4 and S-5
S-4 needs the Vault token to be rotated (it cannot be undone by deleting the file; purging history is optional). For S-5, the alerts are on the default branch of smartkin-frontend, before the Angular/Node/Express upgrade chain (PRs #7 to #22) is merged; re-run Dependabot after the merge and fix what remains, starting with `form-data` and `tar` (critical). The backend has none.

## Accepted, with conditions

- **S-6.** Tokens are bearer credentials: whoever reads them can act as the user until they expire or are revoked. The BFF keeps them in Redis (RD-2) and the app in `sessionStorage`. Conditions: Redis must use `rediss://` with a password and be reachable only from the BFF; the app must not contain HTML injection sinks (checked: none of the new screens uses `innerHTML`, `bypassSecurityTrust*` or `document.write`; Angular escapes the rest); keep token lifetimes short.
- **S-7.** If publishing an event fails after the commit, the assignment or attempt exists but the notification, points or achievement points are missing (D-4, BE-7, BE-9, BE-11 describe it). Nothing is lost silently: it is logged and counted (`smartkin.events.*`, BE-14). Restrict who can produce to the `smartkin.*` topics: any producer could create points, which is a broker-level control outside this repository.
- **S-8.** The lists that are not paged are small by nature at MVP scale. Add paging if a parent can have many children or the catalog grows past a few hundred activities.
- **S-9.** The 400/409 difference on `POST /children` discloses whether a user id exists. Ids are unguessable UUIDs, so it is low risk; S-2's invitation flow would remove it.

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
