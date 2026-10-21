# D-6: MVP API contract

Date: 2026-10-21. Status: accepted. Task: X-2.

The contract is `docs/api/openapi-v1.yaml` (OpenAPI 3.0.3, 25 operations). It is written before the controllers so the frontend can build against it in parallel; each backend ticket (BE-4 to BE-12) implements the operations it owns and must match it.

## Decisions

1. **Contract first, additive only.** The existing `/api/v1/users` and `/api/v1/user-register` operations are not redefined or changed; every new resource has its own path.
2. **Identity from the token.** No operation takes the caller's id as a parameter. The role (parent or child) and the ownership rules come from the session token; a parent reaches only their children, a child only themselves.
3. **A child is a link, not a registration.** The child's account is created with `POST /users` (backbone owns identity). `POST /children` creates the `child` row keyed by that user id and bound to the calling parent.
4. **An activity points at a lesson.** `assigned_activities` references `activities` (PostgreSQL) while lessons and quizzes live in MongoDB, and nothing links them today. BE-7 adds migration `V4` with a nullable `activities.lesson_id`; the contract exposes it as `Activity.lessonId` and `Assignment.lessonId`. Existing rows keep working (the column is nullable).
5. **Grading is server-side.** The lesson payload sent to clients has no `correct` flag on options (`Option` has only `id` and `text`); the server grades `POST .../quiz-attempts` against the lesson version, stores the attempt and publishes `QUIZ_COMPLETED`. Points are booked by the existing consumer (KF-2), not by the endpoint, so the idempotent path stays the only writer of points.
6. **Pagination** (`page`, `size`, `PageMeta`) only on lists that grow without bound: assignments, notifications and point transactions. Small lists (children, topics, lessons, activities, achievements, quiz attempts) return arrays.
7. **Errors** keep the convention of the existing endpoints: the HTTP status plus the `message` header, and a small `ErrorResponse` body. 404 is used both for a missing resource and for one the caller may not see, so ids of other families are not revealed.

## Consequences

- `OpenApiContractTest` keeps the file well formed (resolved references, unique operation ids, no `correct` flag on options, existing user endpoints not redefined).
- Each controller ticket adds a test that the springdoc description of its operations matches the contract.
- Goals, rewards redemption and the reward catalog are not in this version; they are post-MVP.
