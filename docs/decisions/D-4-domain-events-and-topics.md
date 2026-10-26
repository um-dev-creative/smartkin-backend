# D-4: Domain events and Kafka topics

Date: 2026-10-18. Status: accepted. Task: KF-1.

## Decisions

1. **One topic per event type**, named `smartkin.<subject>.<verb>.v<schema>`; the version in the name changes only for breaking payload changes.

| Event type | Topic (default) | Payload record |
| --- | --- | --- |
| `ACTIVITY_ASSIGNED` | `smartkin.activity.assigned.v1` | `ActivityAssignedPayload` |
| `QUIZ_COMPLETED` | `smartkin.quiz.completed.v1` | `QuizCompletedPayload` |
| `ACHIEVEMENT_EARNED` | `smartkin.achievement.earned.v1` | `AchievementEarnedPayload` |

   Names can be overridden with `umdc.kafka.topics.activity-assigned`, `.quiz-completed` and `.achievement-earned`.
2. **Envelope.** Every message is JSON: `eventId` (UUID), `type`, `schemaVersion` (int), `occurredAt` (ISO-8601 UTC), `subjectId` (the child's backbone user id) and `payload`.
3. **Key = child id.** One child's events land in one partition and keep their order. Consumers dedupe by `eventId` (delivery is at least once).
4. **Compatible changes** (new optional payload fields) keep the version; consumers must ignore unknown fields. Removing or retyping a field means a new topic `.v2` published alongside `.v1` until consumers move.
5. **Email stays separate.** Verification and notification emails still go to mercury's topic as `EmailMessageTO` (`umdc.producer.mercury.topic`). The domain events are internal facts; KF-2 consumers
   (points, achievements, in-app inbox) and any mercury integration subscribe to them.

## Payloads (schema version 1)

| Event | Fields |
| --- | --- |
| `ACTIVITY_ASSIGNED` | `assignmentId`, `childId`, `parentId`, `activityId`, `activityTitle`, `dueDate` (nullable) |
| `QUIZ_COMPLETED` | `attemptId`, `childId`, `lessonId`, `assignmentId`, `attemptNumber`, `score`, `maxScore`, `percentage`, `passed`, `completedAt` |
| `ACHIEVEMENT_EARNED` | `childAchievementId`, `childId`, `achievementId`, `achievementName`, `pointsValue`, `earnedAt` |

## Limits and open points

- Publishing is not transactional with PostgreSQL or MongoDB: a crash between commit and send loses the event. A transactional outbox would close the gap; it is not part of KF-1.
- Topics are not created by the application. Create them with the partition count, replication factor and retention your cluster policy sets (retention should cover the longest consumer outage);
  auto-creation works only if the broker allows it.
- Nothing publishes these events yet; the services that assign activities, grade quizzes and award achievements will call `SmartKinEventPublisher`.
