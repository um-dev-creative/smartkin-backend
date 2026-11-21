-- Deletes a user BY USER ID: its verification codes, application_role_user, contacts, the row of general."user" and its person.
-- Plain DELETE statements, one per table. Nothing is created. Run them in this order (the order follows the foreign keys).
--
-- 1) Replace the id in the WHOLE file with "replace all" (it appears in every statement): 00000000-0000-0000-0000-000000000000 -> the user id.
--    If the id is changed in only some statements, the others delete nothing and the last one fails with
--    "violates foreign key constraint ... still referenced from table application_role_user".
-- 2) Run the statements one after the other (script mode in DBeaver/pgAdmin, or psql -f). Each one is independent: if one table does
--    not exist in your database (for example mercury lives in another database) that statement fails and you can run the rest.
-- 3) To see first what would go, run them between BEGIN; and ROLLBACK; (replace ROLLBACK; by COMMIT; to keep the changes).
--
-- The lines that start with "--opt " are tables that point to the user or the person and block the delete only when they have rows
-- (federated_identity, address, identification_document): if the last statement fails with a foreign key error, remove the "--opt " prefix
-- of the lines above it and run them again.

-- 0. check the user first: it must return ONE row (alias and e-mail of the user you are about to delete)
SELECT id, alias, email_account, active, person_id FROM general."user" WHERE id = '00000000-0000-0000-0000-000000000000';

-- 1. verification codes (mercury schema)
DELETE FROM mercury.verification_codes WHERE user_id = '00000000-0000-0000-0000-000000000000';

--opt DELETE FROM general.federated_identity WHERE user_id = '00000000-0000-0000-0000-000000000000';

-- 2. roles of the user in the application, and the role links this user CREATED for other users: those keep their row, only their creator
--    is cleared (creator_id is nullable; deleting them would take the roles of other users away). Without this update the last statement fails
--    with "violates foreign key constraint ... on table application_role_user" when the user created role links.
DELETE FROM general.application_role_user WHERE user_id = '00000000-0000-0000-0000-000000000000';
UPDATE general.application_role_user SET creator_id = NULL WHERE creator_id = '00000000-0000-0000-0000-000000000000';

-- 3. contacts the user owns, and the contacts of its person (kept when another user, of another application, still uses that person)
DELETE FROM general.contact c
 WHERE c.owner_id = '00000000-0000-0000-0000-000000000000'
    OR (c.person_id = (SELECT person_id FROM general."user" WHERE id = '00000000-0000-0000-0000-000000000000')
        AND NOT EXISTS (SELECT 1 FROM general."user" o WHERE o.person_id = c.person_id AND o.id <> '00000000-0000-0000-0000-000000000000'));

--opt DELETE FROM general.address a WHERE a.person_id = (SELECT person_id FROM general."user" WHERE id = '00000000-0000-0000-0000-000000000000') AND NOT EXISTS (SELECT 1 FROM general."user" o WHERE o.person_id = a.person_id AND o.id <> '00000000-0000-0000-0000-000000000000');
--opt DELETE FROM general.identification_document d WHERE d.person_id = (SELECT person_id FROM general."user" WHERE id = '00000000-0000-0000-0000-000000000000') AND NOT EXISTS (SELECT 1 FROM general."user" o WHERE o.person_id = d.person_id AND o.id <> '00000000-0000-0000-0000-000000000000');

-- 4. the user, and its person (kept when another user, of another application, still uses that person)
WITH deleted AS (
    DELETE FROM general."user" WHERE id = '00000000-0000-0000-0000-000000000000' RETURNING person_id
)
DELETE FROM general.person p
 WHERE p.id IN (SELECT person_id FROM deleted)
   AND NOT EXISTS (SELECT 1 FROM general."user" o WHERE o.person_id = p.id AND o.id <> '00000000-0000-0000-0000-000000000000');

-- If the last statement still fails with a foreign key error, this lists EVERY table and column that points to general."user" (and to
-- general.person): run the DELETE (or the UPDATE ... SET NULL) of the table it names first, then repeat the last statement.
--   SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid) AS definition
--     FROM pg_constraint
--    WHERE contype = 'f' AND confrelid IN ('general."user"'::regclass, 'general.person'::regclass);
