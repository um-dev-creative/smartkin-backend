-- Deletes the users, contacts, application_role_user, verification_codes and persons of ONE user or of EVERY user of an application.
-- It is a single SQL statement (data-modifying CTEs): no tables are created, no procedure, no temporary object. Paste it in psql,
-- DBeaver, pgAdmin or the Supabase editor.
--
-- 1) Set the two values in the CTE "params" (first block):
--      mode  'user'         -> id is a user id (general."user".id)
--            'application'  -> id is an application id: every user of that application goes
-- 2) TRY IT FIRST inside a transaction you roll back; the last row tells how many rows each table loses:
--        BEGIN;
--        <this statement>;
--        ROLLBACK;
--    To delete for real run the statement alone (auto-commit) or finish with COMMIT; instead of ROLLBACK;. It cannot be undone.
--
-- It deletes, all in the same statement (foreign keys are checked when the statement ends, so the order does not matter):
--   mercury.verification_codes (also the ones of an id that no longer exists; in 'application' mode every code of the application),
--   general.application_role_user, general.contact (of the person and the ones the user owns), general."user", general.person
--   (only when no other user, of another application, still uses that person), and the tables that point to them and would block the
--   delete: general.federated_identity, general.address, general.identification_document.
-- Result: one row with the number of rows deleted per table.

WITH params AS (
    SELECT 'user'::text                                AS mode,   -- 'user' or 'application'
           '00000000-0000-0000-0000-000000000000'::uuid AS id      -- the user id or the application id
),
users AS (
    SELECT u.id, u.person_id
      FROM general."user" u, params p
     WHERE (p.mode = 'user' AND u.id = p.id) OR (p.mode = 'application' AND u.application_id = p.id)
),
persons AS (
    SELECT DISTINCT u.person_id AS id
      FROM users u
     WHERE u.person_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM general."user" o WHERE o.person_id = u.person_id AND o.id NOT IN (SELECT id FROM users))
),
d_codes AS (
    DELETE FROM mercury.verification_codes c USING params p
     WHERE c.user_id IN (SELECT id FROM users)
        OR (p.mode = 'user' AND c.user_id = p.id)
        OR (p.mode = 'application' AND c.application_id = p.id)
    RETURNING 1
),
d_federated AS (
    DELETE FROM general.federated_identity WHERE user_id IN (SELECT id FROM users) RETURNING 1
),
d_roles AS (
    DELETE FROM general.application_role_user r USING params p
     WHERE r.user_id IN (SELECT id FROM users) OR (p.mode = 'user' AND r.user_id = p.id)
    RETURNING 1
),
d_contacts AS (
    DELETE FROM general.contact c USING params p
     WHERE c.person_id IN (SELECT id FROM persons)
        OR c.owner_id IN (SELECT id FROM users)
        OR (p.mode = 'user' AND c.owner_id = p.id)
    RETURNING 1
),
d_addresses AS (
    DELETE FROM general.address WHERE person_id IN (SELECT id FROM persons) RETURNING 1
),
d_documents AS (
    DELETE FROM general.identification_document WHERE person_id IN (SELECT id FROM persons) RETURNING 1
),
d_users AS (
    DELETE FROM general."user" WHERE id IN (SELECT id FROM users) RETURNING 1
),
d_persons AS (
    DELETE FROM general.person WHERE id IN (SELECT id FROM persons) RETURNING 1
)
SELECT (SELECT count(*) FROM d_codes)     AS verification_codes,
       (SELECT count(*) FROM d_federated) AS federated_identity,
       (SELECT count(*) FROM d_roles)     AS application_role_user,
       (SELECT count(*) FROM d_contacts)  AS contact,
       (SELECT count(*) FROM d_addresses) AS address,
       (SELECT count(*) FROM d_documents) AS identification_document,
       (SELECT count(*) FROM d_users)     AS "user",
       (SELECT count(*) FROM d_persons)   AS person;
