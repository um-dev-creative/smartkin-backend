-- =====================================================================================
-- SmartKin: bootstrap of the application schema in Supabase (task DB-1, decision D-1)
--
-- Run ONCE against the `umdevc` database of the Supabase project, with a role that can create schemas
-- (the same database zerobound uses; the Supabase SQL editor and the MCP connect to `postgres`, not to `umdevc`).
--
--   psql "host=<pooler or db host> port=5432 dbname=umdevc user=postgres" -f docs/db/smartkin_schema_bootstrap.sql
--
-- Only the empty schema is created here. Tables, enum types and indexes arrive with the Flyway baseline (task DB-3).
-- SmartKin does not map or reference backbone's `general` schema: user ids are plain UUIDs (see docs/decisions/D-1-*.md).
-- =====================================================================================

begin;

do
$$
    begin
        if current_database() <> 'umdevc' then
            raise exception 'Wrong database: connected to "%" but this script is for "umdevc".', current_database();
        end if;
    end
$$;

create schema if not exists smartkin;

commit;
