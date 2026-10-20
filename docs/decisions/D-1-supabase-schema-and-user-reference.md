# D-1: Supabase location and user references

Date: 2026-10-18. Status: accepted. Task: DB-1.

## Decisions

1. **Location.** SmartKin uses the schema `smartkin` of the existing Supabase project (`lanmata's Project`, region us-east-2,
   PostgreSQL 17), in the `umdevc` database that zerobound already uses. No new Supabase project is created.
2. **Users.** SmartKin does not share `general.user` with backbone. Backbone owns identity; SmartKin stores the backbone user id as a
   plain `UUID` and has no foreign key to backbone tables.

## Context

- zerobound follows the same pattern: schema `zerobound`, `user_id uuid` columns, and the `general` schema left out of its DDL.
- On 2026-10-18 the Supabase project held only the Supabase system schemas (`auth`, `storage`, ...) and an empty `zerobound` schema in the
  `postgres` database; there was no `general` or `smartkin` schema.
- SmartKin reaches users only through backbone's REST API (Feign), so a direct mapping of `general.user` gave it no behaviour.

## Consequences

- `UserEntity` and `UserRepository` were removed. `ParentEntity.id` and `ChildEntity.id` hold the backbone user id (no `@MapsId`), and
  `NotificationEntity` has a `userId` column instead of a relation.
- Referential integrity with backbone is not enforced by the database; deletions are coordinated through the API and events.
- Cost stays flat, but SmartKin shares the project's connection limits, backups and credentials boundary with zerobound. Use a dedicated
  database role for SmartKin (task DB-2) and revisit a separate project if the load or the isolation requirements grow.
- The schema is created with `docs/db/smartkin_schema_bootstrap.sql`, run by a person with access to `umdevc`. It was not applied from this
  repository's tooling because the Supabase MCP connects to `postgres`, not to `umdevc`.
