-- Deletes everything backbone and mercury keep about a user, or about every user of an application, in ONE atomic statement.
-- Plain SQL (one DO block): paste it in psql, DBeaver, pgAdmin or the Supabase SQL editor. No psql meta-commands.
--
-- HOW TO USE: change the three values under "PARAMETERS" and run the whole script.
--   v_mode   'user'         -> v_id is the id of ONE user (general."user".id)
--            'application'  -> v_id is an application id: EVERY user of that application goes
--   v_apply  false (default) -> DRY RUN: it deletes inside the block, reports the rows per table and then rolls everything back
--            true            -> it really deletes (irreversible; take a backup first, and try the dry run before)
-- The result is shown as the message of a notice (apply = true) or of the error "DRY RUN ... rolled back" (apply = false).
--
-- WHAT GOES, in the order of the foreign keys:
--   mercury.verification_codes        the codes of the user(s)   (also the codes of an id that no longer exists in backbone, and, in
--                                     'application' mode, every code of that application)
--   general.federated_identity        social logins of the user(s)         (FK dependent, deleted so the user can go)
--   general.application_role_user     the role(s) of the user(s)
--   general.contact                   the contacts of the person(s) and the ones the user owns (owner_id)
--   general.address                   the addresses of the person(s)       (FK dependent)
--   general.identification_document   the documents of the person(s)      (FK dependent)
--   general."user"                    the user(s)
--   general.person                    the person(s), ONLY when no other user (another application) still uses the person
-- Tables that do not exist in the database (for example mercury in another database) are skipped. The `smartkin` schema is not touched.

DO
$purge$
DECLARE
    -- ===== PARAMETERS =====================================================================================================
    v_mode  text    := 'user';                                    -- 'user' or 'application'
    v_id    uuid    := '00000000-0000-0000-0000-000000000000';    -- the user id or the application id
    v_apply boolean := false;                                     -- false = dry run (rolled back), true = delete for real
    -- ======================================================================================================================
    n         bigint;
    v_users   uuid[];
    v_persons uuid[];
    v_report  text := '';
BEGIN
    IF v_mode NOT IN ('user', 'application') THEN
        RAISE EXCEPTION 'v_mode must be ''user'' or ''application'', not %', v_mode;
    END IF;
    IF v_id = '00000000-0000-0000-0000-000000000000' THEN
        RAISE EXCEPTION 'Set v_id to the user id or the application id first';
    END IF;

    SELECT coalesce(array_agg(u.id), '{}')
      INTO v_users
      FROM general."user" u
     WHERE (v_mode = 'user' AND u.id = v_id) OR (v_mode = 'application' AND u.application_id = v_id);

    -- a person that another user (another application) still uses is kept
    SELECT coalesce(array_agg(DISTINCT t.person_id), '{}')
      INTO v_persons
      FROM general."user" t
     WHERE t.id = ANY (v_users)
       AND t.person_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM general."user" o WHERE o.person_id = t.person_id AND o.id <> ALL (v_users));

    v_report := format('%s %s: %s user(s), %s person(s) to delete%s', v_mode, v_id, cardinality(v_users), cardinality(v_persons), E'\n');

    IF to_regclass('mercury.verification_codes') IS NOT NULL THEN
        DELETE FROM mercury.verification_codes
         WHERE user_id = ANY (v_users)
            OR (v_mode = 'user' AND user_id = v_id)
            OR (v_mode = 'application' AND application_id = v_id);
        GET DIAGNOSTICS n = ROW_COUNT;
        v_report := v_report || format('  mercury.verification_codes:      %s%s', n, E'\n');
    ELSE
        v_report := v_report || E'  mercury.verification_codes:      table not found, skipped\n';
    END IF;

    IF to_regclass('general.federated_identity') IS NOT NULL THEN
        DELETE FROM general.federated_identity WHERE user_id = ANY (v_users);
        GET DIAGNOSTICS n = ROW_COUNT;
        v_report := v_report || format('  general.federated_identity:      %s%s', n, E'\n');
    END IF;

    DELETE FROM general.application_role_user WHERE user_id = ANY (v_users) OR (v_mode = 'user' AND user_id = v_id);
    GET DIAGNOSTICS n = ROW_COUNT;
    v_report := v_report || format('  general.application_role_user:   %s%s', n, E'\n');

    DELETE FROM general.contact
     WHERE person_id = ANY (v_persons) OR owner_id = ANY (v_users) OR (v_mode = 'user' AND owner_id = v_id);
    GET DIAGNOSTICS n = ROW_COUNT;
    v_report := v_report || format('  general.contact:                 %s%s', n, E'\n');

    IF to_regclass('general.address') IS NOT NULL THEN
        DELETE FROM general.address WHERE person_id = ANY (v_persons);
        GET DIAGNOSTICS n = ROW_COUNT;
        v_report := v_report || format('  general.address:                 %s%s', n, E'\n');
    END IF;

    IF to_regclass('general.identification_document') IS NOT NULL THEN
        DELETE FROM general.identification_document WHERE person_id = ANY (v_persons);
        GET DIAGNOSTICS n = ROW_COUNT;
        v_report := v_report || format('  general.identification_document: %s%s', n, E'\n');
    END IF;

    DELETE FROM general."user" WHERE id = ANY (v_users);
    GET DIAGNOSTICS n = ROW_COUNT;
    v_report := v_report || format('  general.user:                    %s%s', n, E'\n');

    DELETE FROM general.person WHERE id = ANY (v_persons);
    GET DIAGNOSTICS n = ROW_COUNT;
    v_report := v_report || format('  general.person:                  %s%s', n, E'\n');

    IF v_apply THEN
        RAISE NOTICE E'APPLIED: the rows below were deleted.\n%', v_report;
    ELSE
        -- an error rolls back the whole block: nothing stays deleted
        RAISE EXCEPTION E'DRY RUN, rolled back. This is what v_apply = true would delete:\n%', v_report;
    END IF;
END
$purge$;
