# D-5: Event consumers, idempotency and the error topic

Date: 2026-10-18. Status: accepted. Tasks: KF-2, KF-3.

## Decisions

1. **SmartKin consumes its own events** to derive points, child totals and inbox notifications, so services that finish a quiz or grant an achievement only publish a fact (D-4).
2. **Idempotent by existence check plus unique index.** Kafka is at-least-once. Each effect is skipped if it exists; `V3` adds `uq_points_source (source_type, source_id, transaction_type)` and a partial
   `uq_notifications_related (user_id, type, related_entity_id)` so a race fails with a constraint error instead of double-booking (the retry then finds the effect and skips it).
   Not a processed-events table: the business keys already identify the effect.
3. **Points rule:** a passed quiz earns its `score`; a failed one earns nothing. An achievement earns its `pointsValue`. This is an assumption to confirm with product.
4. **One error topic** (`smartkin.events.error.v1`) for all event topics instead of `<topic>.DLT` per topic: fewer topics for the platform team to create and one place to monitor. The original topic
   is in the `kafka_dlt-original-topic` header.
5. **Retry in the consumer thread** (blocking, exponential, default 4 attempts, about 7s) rather than retry topics. Volume is low and per-child ordering matters; non-blocking retry topics can replace
   this if a poison record ever delays a partition too long. Malformed records are not retried.
6. **Emails to mercury** stay on the existing `EmailMessageProducerService`; the consumers do not send them because they hold only the child's id, not an email address.

## Consequences

- Creating the error topic and alerting on it is an operations task.
- The V3 indexes fail to apply if `points` or `notifications` already hold duplicates of those keys (not the case on a fresh `smartkin` schema).
