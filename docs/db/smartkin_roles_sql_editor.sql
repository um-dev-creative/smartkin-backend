-- =====================================================================================
-- SmartKin: database roles (decision D-3), version for SQL EDITORS
--
-- The same result as docs/db/smartkin_roles.sql, for clients that are NOT psql: DataGrip, DBeaver, pgAdmin, IntelliJ's console, the
-- Supabase SQL editor. Those clients do not understand psql's \if, \set, \gexec or -v, so this file is ONE plain `do` block with no
-- meta-commands. (Prefer smartkin_roles.sql with psql when you can: its passwords never touch the file.)
--
--   smartkin_owner  Owns the schema and its tables. Flyway connects as this role (SMARTKIN_FLYWAY_USERNAME / _PASSWORD).
--   smartkin_app    What the service connects as (SMARTKIN_DB_USERNAME / _PASSWORD): SELECT/INSERT/UPDATE/DELETE and sequence use only.
--
-- HOW TO RUN
--   1. Connect to the `umdevc` database (not `postgres`) as a role that can create roles (postgres on Supabase).
--   2. Replace the two passwords below. They must not stay as CHANGE_ME_*; the block stops if they do.
--   3. Run the whole file as ONE statement (select all, execute). It is safe to run again; that rotates the passwords.
--   4. Put the passwords in Vault, then clear them from this file. NEVER commit it with real passwords. The password text is part of
--      the statement, so it can reach the client's query history and the server log (log_statement): clear the history.
-- =====================================================================================

do
$roles$
declare
    -- >>> EDIT THESE THREE LINES <<<
    owner_password    constant text := 'CHANGE_ME_OWNER_PASSWORD';
    app_password      constant text := 'CHANGE_ME_APP_PASSWORD';
    expected_database constant text := 'umdevc';   -- '' skips the check
    object record;
begin
    if owner_password like 'CHANGE_ME%' or app_password like 'CHANGE_ME%' then
        raise exception 'Edit owner_password and app_password at the top of this block before running it';
    end if;
    if expected_database <> '' and current_database() <> expected_database then
        raise exception 'Wrong database: connected to "%" but expected "%"', current_database(), expected_database;
    end if;

    -- 1. Roles. Created if missing (V2 may have created smartkin_app as NOLOGIN) with the defaults, which are already NOSUPERUSER,
    -- NOCREATEDB, NOCREATEROLE, NOREPLICATION and NOBYPASSRLS. ALTER ROLE must not NAME those attributes: a role that is not a
    -- superuser (Supabase's postgres) may not even write NOSUPERUSER, NOREPLICATION or NOBYPASSRLS ("Only roles with the SUPERUSER
    -- attribute may change the SUPERUSER attribute"). So they are checked, not set.
    if not exists (select 1 from pg_roles where rolname = 'smartkin_owner') then
        create role smartkin_owner;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'smartkin_app') then
        create role smartkin_app;
    end if;
    for object in
        select rolname from pg_roles
        where rolname in ('smartkin_owner', 'smartkin_app')
          and (rolsuper or rolcreaterole or rolcreatedb or rolreplication or rolbypassrls)
        loop
            raise exception 'The role % already has privileges it must not have (superuser, createrole, createdb, replication or bypassrls). Remove them as a superuser: alter role % nosuperuser nocreaterole nocreatedb noreplication nobypassrls',
                object.rolname, object.rolname;
        end loop;
    -- LOGIN and the password are the only things changed (this also rotates the passwords)
    execute format('alter role smartkin_owner with login password %L', owner_password);
    execute format('alter role smartkin_app with login password %L', app_password);

    -- The role running this block must be able to act as the owner to hand objects over. PostgreSQL 16+ gives the creator of a role only
    -- ADMIN OPTION on it, not the right to SET ROLE to it, so that is granted explicitly.
    if not coalesce(case when current_setting('server_version_num')::int >= 160000
                         then pg_has_role(current_user, 'smartkin_owner', 'set')
                         else pg_has_role(current_user, 'smartkin_owner', 'member') end, false) then
        if current_setting('server_version_num')::int >= 160000 then
            execute format('grant smartkin_owner to %I with set true', current_user);
        else
            execute format('grant smartkin_owner to %I', current_user);
        end if;
    end if;
    -- Needs to own the database; PUBLIC can connect by default, so a refusal is not fatal.
    begin
        execute format('grant connect on database %I to smartkin_owner, smartkin_app', current_database());
    exception when insufficient_privilege then
        raise notice 'Skipped GRANT CONNECT on %: you do not own it, and PUBLIC can connect by default', current_database();
    end;

    -- 2. The schema belongs to the owner; nobody else may use it by default.
    create schema if not exists smartkin authorization smartkin_owner;
    alter schema smartkin owner to smartkin_owner;
    revoke all on schema smartkin from public;
    grant usage on schema smartkin to smartkin_app;

    -- 3. Objects that already exist (an environment migrated by another role, e.g. postgres) move to the owner. A fresh database has none.
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

    -- 4. What smartkin_app may do, granted by the owner so that it also covers tables created later by its migrations.
    set local role smartkin_owner;
    grant select, insert, update, delete on all tables in schema smartkin to smartkin_app;
    grant usage, select on all sequences in schema smartkin to smartkin_app;
    alter default privileges for role smartkin_owner in schema smartkin grant select, insert, update, delete on tables to smartkin_app;
    alter default privileges for role smartkin_owner in schema smartkin grant usage, select on sequences to smartkin_app;
    reset role;
end
$roles$;

-- Check (run it separately): both roles can log in and none is a superuser, can create roles or databases, or bypass RLS.
-- select rolname, rolcanlogin, rolsuper, rolcreaterole, rolcreatedb, rolbypassrls from pg_roles where rolname in ('smartkin_owner', 'smartkin_app');
--
-- Next: SMARTKIN_FLYWAY_USERNAME / _PASSWORD = smartkin_owner and SMARTKIN_DB_USERNAME / _PASSWORD = smartkin_app (both from Vault, never in
-- config/local.env). Create the tables with scripts/db-migrate.sh or by starting the service. Keep `smartkin` out of Supabase's exposed API schemas.
