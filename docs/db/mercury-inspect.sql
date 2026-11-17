-- Read-only inspection of mercury's reference data, to decide the values of
--   scripts/smartkin/bootstrap-reference-data.sql and scripts/smartkin/seed-templates.sql
-- It only SELECTs. Run it as ONE statement with a role that can read the schemas `mercury` and `general`, and paste the whole result.
-- Passwords, tokens and user data are not read: only ids, names, flags and counts.
select section, a, b, c, d from (
    select 1 as o, 'schemas' as section, nspname as a, null as b, null as c, null as d
    from pg_namespace where nspname in ('mercury', 'general')
  union all
    select 2, 'template_types', id::text, name, active::text, null from mercury.template_types
  union all
    select 3, 'severity_type', id::text, name, active::text, null from mercury.severity_type
  union all
    select 4, 'frequency_type', id::text, name, active::text, null from mercury.frequency_type
  union all
    select 5, 'message_status_type', name, active::text, null, null from mercury.message_status_type
  union all
    select 6, 'verification-like templates', t.location, t.template_type_id::text, tt.name, t.application_id::text
    from mercury.templates t left join mercury.template_types tt on tt.id = t.template_type_id
    where t.location ilike '%verification%'
  union all
    select 7, 'all templates (count by application and type)', t.application_id::text, t.template_type_id::text, count(*)::text, null
    from mercury.templates t group by t.application_id, t.template_type_id
  union all
    select 8, 'who owns the definitions (owner_id candidates)', d.user_id::text, d.application_id::text, d.frequency_type::text, count(*)::text
    from mercury.template_defined d group by d.user_id, d.application_id, d.frequency_type
  union all
    select 9, 'applications (every column)', to_jsonb(a)::text, null, null, null from general.application a
  union all
    select 10, 'row level security on', c.relnamespace::regnamespace::text || '.' || c.relname, c.relrowsecurity::text, null, null
    from pg_class c where c.relnamespace::regnamespace::text in ('mercury', 'general')
      and c.relname in ('template_types', 'severity_type', 'frequency_type', 'message_status_type', 'templates', 'template_defined', 'application')
) x order by o, a;
