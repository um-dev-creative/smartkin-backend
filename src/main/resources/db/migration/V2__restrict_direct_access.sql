-- DB-4 (decision D-3): the database is reachable only through the SmartKin backend.
--
-- 1. Row Level Security is enabled on every table of the schema. No policy is created for the Supabase API roles (anon, authenticated),
--    so even if the schema were exposed through the Data API (PostgREST) they would see no rows.
-- 2. All privileges on the schema are revoked from PUBLIC and from the Supabase API roles when those exist.
-- 3. A dedicated application role, smartkin_app, gets only DML on the tables (and use of the sequences) plus one permissive policy per table.
--    The role is created without LOGIN: the pipeline or DBA enables it with a password from Vault
--    (alter role smartkin_app with login password '...', see docs/db/smartkin_schema_bootstrap.sql).
-- The table owner (the role that runs Flyway) is not subject to RLS, so migrations and the owner keep working.

do
$$
    begin
        if not exists (select 1 from pg_roles where rolname = 'smartkin_app') then
            create role smartkin_app nologin;
        end if;
    end
$$;

do
$$
    declare
        api_role text;
    begin
        revoke all on schema smartkin from public;
        revoke all on all tables in schema smartkin from public;
        revoke all on all sequences in schema smartkin from public;
        revoke all on all functions in schema smartkin from public;
        foreach api_role in array array ['anon', 'authenticated', 'service_role']
            loop
                if exists (select 1 from pg_roles where rolname = api_role) then
                    execute format('revoke all on schema smartkin from %I', api_role);
                    execute format('revoke all on all tables in schema smartkin from %I', api_role);
                    execute format('revoke all on all sequences in schema smartkin from %I', api_role);
                    execute format('revoke all on all functions in schema smartkin from %I', api_role);
                    -- Objects created later by this owner must not be granted to the API roles either
                    execute format('alter default privileges in schema smartkin revoke all on tables from %I', api_role);
                    execute format('alter default privileges in schema smartkin revoke all on sequences from %I', api_role);
                    execute format('alter default privileges in schema smartkin revoke all on functions from %I', api_role);
                end if;
            end loop;
    end
$$;

alter table achievements enable row level security;
alter table activities enable row level security;
alter table activity_progress enable row level security;
alter table assigned_activities enable row level security;
alter table child enable row level security;
alter table child_achievements enable row level security;
alter table child_rewards enable row level security;
alter table goals enable row level security;
alter table notifications enable row level security;
alter table parent enable row level security;
alter table points enable row level security;
alter table rewards enable row level security;
alter table topics enable row level security;

create policy smartkin_app_all on achievements for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on activities for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on activity_progress for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on assigned_activities for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on child for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on child_achievements for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on child_rewards for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on goals for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on notifications for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on parent for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on points for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on rewards for all to smartkin_app using (true) with check (true);
create policy smartkin_app_all on topics for all to smartkin_app using (true) with check (true);

grant usage on schema smartkin to smartkin_app;
grant select, insert, update, delete on all tables in schema smartkin to smartkin_app;
grant usage, select on all sequences in schema smartkin to smartkin_app;
-- Tables added by later migrations (run by the same owner) are usable by the application role without extra grants
alter default privileges in schema smartkin grant select, insert, update, delete on tables to smartkin_app;
alter default privileges in schema smartkin grant usage, select on sequences to smartkin_app;
