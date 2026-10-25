# D-3: Database access only through the backend

Date: 2026-10-18. Status: accepted. Task: DB-4.

## Decision

Clients never read or write the `smartkin` schema directly. Only the SmartKin backend does, and it does so with a restricted role.

1. **RLS on every table.** `V2__restrict_direct_access.sql` enables Row Level Security on the 13 tables. No policy exists for `anon` or `authenticated`, so those roles see no rows even if
   a table were granted to them by mistake.
2. **No privileges for others.** All privileges on the schema are revoked from `PUBLIC` and, when they exist, from `anon`, `authenticated` and `service_role`, including default privileges for
   future objects.
3. **A dedicated role.** `smartkin_app` (created without `LOGIN`) gets `USAGE` on the schema, DML on the tables, `USAGE`/`SELECT` on sequences and one permissive policy per table. It cannot create,
   truncate or drop anything. Default privileges extend the same grants to tables added by later migrations run by the same owner.
4. **Owner vs. application.** The schema owner (the role that runs Flyway) is not subject to RLS (`FORCE ROW LEVEL SECURITY` is not used) and keeps running migrations. The application connects as
   `smartkin_app`; Flyway uses `SMARTKIN_FLYWAY_USERNAME`/`SMARTKIN_FLYWAY_PASSWORD`, which fall back to the application credentials.

## Why policies are `using (true)`

The backend authorizes each request itself (backbone roles, parent-child ownership) and talks to one database role. RLS here is a perimeter, not per-user filtering: it closes every other door. Per-user
policies would require passing the end user's identity into the database, which the architecture does not do.

## Operational steps (not automated)

- Create the roles with `docs/db/smartkin_roles.sql` (it creates `smartkin_owner` for Flyway and gives `smartkin_app` login, with passwords from Vault; idempotent), then set `SMARTKIN_FLYWAY_USERNAME`/`_PASSWORD` to the owner and `SMARTKIN_DB_USERNAME`/`_PASSWORD` to `smartkin_app`.
- In the Supabase dashboard, keep `smartkin` out of *Settings > API > Exposed schemas* (the default exposes only `public` and `graphql_public`). This setting is not in the repository, so it must be checked by hand.
- `V2` needs a Flyway user that can `create role`; where it cannot, create `smartkin_app` first and V2 will reuse it.

## Consequences

- A leaked Data API key (anon or service) cannot reach SmartKin data.
- Migrations that add tables must also `enable row level security` and add the `smartkin_app` policy; otherwise the table is readable only by the owner, and the application fails visibly on it.
- The mercury and backbone databases are unaffected.
