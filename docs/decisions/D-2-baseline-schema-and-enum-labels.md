# D-2: Flyway baseline and enum labels

Date: 2026-10-18. Status: proposed (labels to be confirmed by the product owner). Tasks: DB-2, DB-3.

## Decisions

1. **Flyway owns the schema.** `src/main/resources/db/migration/V1__baseline_smartkin_schema.sql` creates the 13 tables, 4 enum types and indexes
   of the `smartkin` schema; Hibernate runs with `ddl-auto: none`. Migrations run on the direct connection (port 5432); the application
   uses the transaction pooler (port 6543, `prepareThreshold=0`).
2. **The baseline is derived from the entities**, because no DDL for `smartkin` existed in the repository, in git history or in the Supabase
   project (the `umdevc` database was not reachable from the available tools). If a `smartkin` schema already exists in `umdevc`, it must be
   compared with V1 before the first run (see *Applying it*).
3. **Enum labels** were unmapped in the entities (`TODO [Reverse Engineering]` blocks). They are now Java enums stored by name in PostgreSQL enums:

| Type | Labels | Default |
| --- | --- | --- |
| `reward_status` | PENDING, APPROVED, DELIVERED, REJECTED | PENDING |
| `goal_status` | ACTIVE, COMPLETED, CANCELLED | ACTIVE |
| `notification_type` | ASSIGNMENT, REMINDER, ACHIEVEMENT, REWARD, GOAL, SYSTEM | none |
| `transaction_type` | EARN, SPEND | none |

   The labels are an assumption: the original defaults (`'pending'`, `'active'`) were the only evidence. Add values later with
   `alter type ... add value` in a new migration; removing or renaming one needs a data migration.
4. **Generated keys.** Tables with generated ids use `gen_random_uuid()` and the entities use `GenerationType.UUID`. `parent_id` and
   `child_id` are assigned (backbone user ids, decision D-1).

## Applying it

- New or empty `smartkin` schema: start the application with `FLYWAY_ENABLED=true`, or run Flyway from the pipeline against port 5432.
- Existing `smartkin` schema with the same tables: set `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=1` once, so
  Flyway registers V1 without running it; compare column types and enum labels first.
- Production: set `FLYWAY_ENABLED=false` and apply migrations from the pipeline, as zerobound does.
