-- =====================================================================================
-- SmartKin: database roles (decision D-3)
--
-- Creates the two login roles the service needs and hands the schema to the right one. Run it ONCE per database, before the first
-- deployment, and again to rotate a password or to repair an environment whose tables belong to another role. It is idempotent.
--
--   smartkin_owner  Owns the schema and its tables. Flyway connects as this role (SMARTKIN_FLYWAY_USERNAME / _PASSWORD), because
--                   migrations need DDL. It is not a superuser and cannot create roles or databases. As the owner it is not subject
--                   to Row Level Security, so migrations keep working.
--   smartkin_app    What the application connects as (SMARTKIN_DB_USERNAME / _PASSWORD). Only SELECT/INSERT/UPDATE/DELETE on the
--                   tables and USAGE/SELECT on the sequences: no DDL, no TRUNCATE, no role management. Row Level Security applies
--                   to it (it has no BYPASSRLS) through the `smartkin_app_all` policy each migration creates.
--
-- Run as a role that can create roles (postgres on Supabase) against the `umdevc` database, not `postgres`:
--
--   psql "host=<db host> port=5432 dbname=umdevc user=postgres" -v ON_ERROR_STOP=1 \
--        -v expected_database=umdevc \
--        -v owner_password="$(vault kv get -field=flyway_password <path>)" \
--        -v app_password="$(vault kv get -field=app_password <path>)" \
--        -f docs/db/smartkin_roles.sql
--
-- The passwords are psql variables, so they never appear in this file or in the shell history. On Supabase's pooler the user name has
-- the project suffix (smartkin_app.<project-ref>); the roles themselves are created without it.
-- Also create the schema with docs/db/smartkin_schema_bootstrap.sql, or let this script do it (it creates it if missing).
-- =====================================================================================

\set ON_ERROR_STOP on

\if :{?owner_password}
\else
    do $$ begin raise exception 'owner_password is required: psql -v owner_password=... (see the header of this file)'; end $$;
\endif
\if :{?app_password}
\else
    do $$ begin raise exception 'app_password is required: psql -v app_password=... (see the header of this file)'; end $$;
\endif

-- Optional guard against running in the wrong database (the Supabase SQL editor connects to `postgres`, not to `umdevc`)
\if :{?expected_database}
    select current_database() = :'expected_database' as right_database \gset
    \if :right_database
    \else
        do $$ begin raise exception 'Wrong database: the connection is not to the database named by expected_database'; end $$;
    \endif
\endif

begin;

-- 1. Roles. Created if missing (V2 may have created smartkin_app as NOLOGIN), then always aligned, which also rotates the passwords.
select 'create role smartkin_owner' where not exists (select 1 from pg_roles where rolname = 'smartkin_owner') \gexec
select 'create role smartkin_app'   where not exists (select 1 from pg_roles where rolname = 'smartkin_app') \gexec

alter role smartkin_owner with login password :'owner_password'
    nosuperuser nocreatedb nocreaterole noreplication nobypassrls;
alter role smartkin_app with login password :'app_password'
    nosuperuser nocreatedb nocreaterole noreplication nobypassrls;

-- The role running this script must be able to act as the owner to hand objects over (PostgreSQL 16 grants that to a role's creator
-- only when it creates it, hence the explicit membership).
select format('grant smartkin_owner to %I', current_user)
 where not pg_has_role(current_user, 'smartkin_owner', 'member') \gexec

select format('grant connect on database %I to smartkin_owner, smartkin_app', current_database()) \gexec

-- 2. The schema belongs to the owner; nobody else may use it by default.
create schema if not exists smartkin authorization smartkin_owner;
alter schema smartkin owner to smartkin_owner;
revoke all on schema smartkin from public;
grant usage on schema smartkin to smartkin_app;

-- 3. Objects that already exist (an environment migrated by another role, e.g. postgres) move to the owner. A fresh database has none.
do
$$
    declare
        object record;
    begin
        for object in
            select c.relname, c.relkind
            from pg_class c
                     join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'smartkin'
              and c.relkind in ('r', 'p', 'v', 'm', 'S')
              and pg_get_userbyid(c.relowner) <> 'smartkin_owner'
              -- a sequence that belongs to a column follows its table
              and not exists (select 1 from pg_depend d where d.objid = c.oid and d.deptype in ('a', 'i') and c.relkind = 'S')
            loop
                execute format('alter %s smartkin.%I owner to smartkin_owner',
                               case object.relkind when 'S' then 'sequence' when 'v' then 'view' when 'm' then 'materialized view' else 'table' end,
                               object.relname);
            end loop;
        for object in
            select t.typname
            from pg_type t
                     join pg_namespace n on n.oid = t.typnamespace
            where n.nspname = 'smartkin' and t.typtype = 'e' and pg_get_userbyid(t.typowner) <> 'smartkin_owner'
            loop
                execute format('alter type smartkin.%I owner to smartkin_owner', object.typname);
            end loop;
    end
$$;

commit;

-- 4. What smartkin_app may do, granted by the owner so that it also covers tables created later by its migrations.
begin;
set local role smartkin_owner;

grant select, insert, update, delete on all tables in schema smartkin to smartkin_app;
grant usage, select on all sequences in schema smartkin to smartkin_app;
alter default privileges for role smartkin_owner in schema smartkin grant select, insert, update, delete on tables to smartkin_app;
alter default privileges for role smartkin_owner in schema smartkin grant usage, select on sequences to smartkin_app;

commit;

-- 5. What was done, for the log (no passwords)
select rolname, rolcanlogin as can_login, rolsuper as superuser, rolcreaterole as create_role, rolcreatedb as create_db,
       rolbypassrls as bypass_rls
from pg_roles
where rolname in ('smartkin_owner', 'smartkin_app')
order by rolname;

select n.nspname as schema, pg_get_userbyid(n.nspowner) as owner
from pg_namespace n
where n.nspname = 'smartkin';

-- Next: point SMARTKIN_FLYWAY_USERNAME / SMARTKIN_FLYWAY_PASSWORD at smartkin_owner and SMARTKIN_DB_USERNAME / SMARTKIN_DB_PASSWORD at
-- smartkin_app (both from Vault, never in config/local.env). Keep `smartkin` out of Supabase's exposed API schemas.
