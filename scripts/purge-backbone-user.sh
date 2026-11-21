#!/usr/bin/env bash
# Deletes everything backbone and mercury keep about a user, or about every user of an application, in ONE transaction:
#
#   mercury.verification_codes        the codes of the user(s)
#   general.federated_identity        social logins of the user(s)        (FK dependent, deleted so the user can go)
#   general.application_role_user     the role(s) of the user(s) in the application
#   general.contact                   the contacts of the person(s) and the ones the user owns
#   general.address                   the addresses of the person(s)       (FK dependent)
#   general.identification_document   the documents of the person(s)      (FK dependent)
#   general."user"                    the user(s)
#   general.person                    the person(s) of those users, ONLY when no other user (another application) still uses the person
#
# It is a DRY RUN unless --apply is given: everything is deleted inside a transaction that is rolled back, and the number of rows per
# table is printed, so you can see what would go. Nothing about SmartKin's own `smartkin` schema is touched (it holds backbone user ids
# as plain UUIDs; delete those rows apart if the user also used the product).
#
#   scripts/purge-backbone-user.sh --user <user-uuid>                 # shows what would be deleted
#   scripts/purge-backbone-user.sh --user <user-uuid> --apply         # asks you to type the id, then deletes
#   scripts/purge-backbone-user.sh --application <app-uuid> --apply --yes   # every user of the application, no question
#
# Connection (never on the command line; psql reads them from the environment)
#   PGHOST      required        PGPORT  default 5432        PGDATABASE  default umdevc
#   PGUSER      required        PGPASSWORD  required
#   PGSSLMODE   e.g. require (Supabase needs TLS)
# Without a local `psql` the script runs it in Docker (POSTGRES_IMAGE, default postgres:17-alpine; DOCKER_NETWORK to join a network).
# mercury.verification_codes is deleted when it exists in the same database; otherwise the script says so and skips it.

set -euo pipefail

usage() {
  sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//' >&2
  exit 2
}

mode=""
target=""
apply=0
assume_yes=0
UUID_RE='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'

while [ $# -gt 0 ]; do
  case "$1" in
    --user | --application)
      [ -z "$mode" ] || { echo "Use only one of --user and --application" >&2; exit 2; }
      mode="${1#--}"
      target="${2:-}"
      shift 2 || { echo "$1 needs an id" >&2; exit 2; }
      ;;
    --apply) apply=1; shift ;;
    --yes) assume_yes=1; shift ;;
    -h | --help) usage ;;
    *) echo "Unknown argument '$1'" >&2; usage ;;
  esac
done

[ -n "$mode" ] || { echo "Give --user <uuid> or --application <uuid>" >&2; usage; }
[[ "$target" =~ $UUID_RE ]] || { echo "'$target' is not a UUID" >&2; exit 2; }
: "${PGHOST:?set PGHOST}"
: "${PGUSER:?set PGUSER}"
: "${PGPASSWORD:?set PGPASSWORD}"
export PGPORT="${PGPORT:-5432}" PGDATABASE="${PGDATABASE:-umdevc}"
image="${POSTGRES_IMAGE:-postgres:17-alpine}"

run_psql() {
  if command -v psql >/dev/null 2>&1; then
    psql "$@"
  else
    docker run --rm -i -e PGHOST -e PGPORT -e PGDATABASE -e PGUSER -e PGPASSWORD -e PGSSLMODE \
      ${DOCKER_NETWORK:+--network "$DOCKER_NETWORK"} "$image" psql "$@"
  fi
}

if [ "$apply" = 1 ] && [ "$assume_yes" = 0 ]; then
  echo "This DELETES the data of the ${mode} ${target} in ${PGDATABASE}@${PGHOST} and cannot be undone." >&2
  read -r -p "Type the id to confirm: " typed
  [ "$typed" = "$target" ] || { echo "Not confirmed, nothing was deleted." >&2; exit 1; }
fi

run_psql -X -q -v ON_ERROR_STOP=1 -v mode="$mode" -v target="$target" -v apply="$apply" -f - <<'SQL'
BEGIN;

CREATE TEMP TABLE t_params ON COMMIT DROP AS SELECT :'mode'::text AS mode, :'target'::uuid AS target;

CREATE TEMP TABLE t_users ON COMMIT DROP AS
SELECT u.id, u.person_id
FROM general."user" u, t_params p
WHERE (p.mode = 'user' AND u.id = p.target) OR (p.mode = 'application' AND u.application_id = p.target);

-- a person that another user (another application) still uses is kept
CREATE TEMP TABLE t_persons ON COMMIT DROP AS
SELECT DISTINCT t.person_id AS id
FROM t_users t
WHERE t.person_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM general."user" o WHERE o.person_id = t.person_id AND o.id NOT IN (SELECT id FROM t_users));

DO $$
DECLARE
  n bigint;
  p t_params%ROWTYPE;
BEGIN
  SELECT * INTO p FROM t_params;
  SELECT count(*) INTO n FROM t_users;
  RAISE NOTICE '% found: % user(s), % person(s) to delete', p.mode, n, (SELECT count(*) FROM t_persons);

  IF to_regclass('mercury.verification_codes') IS NOT NULL THEN
    DELETE FROM mercury.verification_codes
     WHERE user_id IN (SELECT id FROM t_users)
        OR (p.mode = 'user' AND user_id = p.target)
        OR (p.mode = 'application' AND application_id = p.target);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'mercury.verification_codes:       % row(s)', n;
  ELSE
    RAISE NOTICE 'mercury.verification_codes:       table not found in this database, skipped';
  END IF;

  IF to_regclass('general.federated_identity') IS NOT NULL THEN
    DELETE FROM general.federated_identity WHERE user_id IN (SELECT id FROM t_users);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'general.federated_identity:       % row(s)', n;
  END IF;

  DELETE FROM general.application_role_user
   WHERE user_id IN (SELECT id FROM t_users) OR (p.mode = 'user' AND user_id = p.target);
  GET DIAGNOSTICS n = ROW_COUNT;
  RAISE NOTICE 'general.application_role_user:    % row(s)', n;

  DELETE FROM general.contact
   WHERE person_id IN (SELECT id FROM t_persons) OR owner_id IN (SELECT id FROM t_users) OR (p.mode = 'user' AND owner_id = p.target);
  GET DIAGNOSTICS n = ROW_COUNT;
  RAISE NOTICE 'general.contact:                  % row(s)', n;

  IF to_regclass('general.address') IS NOT NULL THEN
    DELETE FROM general.address WHERE person_id IN (SELECT id FROM t_persons);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'general.address:                  % row(s)', n;
  END IF;

  IF to_regclass('general.identification_document') IS NOT NULL THEN
    DELETE FROM general.identification_document WHERE person_id IN (SELECT id FROM t_persons);
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'general.identification_document:  % row(s)', n;
  END IF;

  DELETE FROM general."user" WHERE id IN (SELECT id FROM t_users);
  GET DIAGNOSTICS n = ROW_COUNT;
  RAISE NOTICE 'general.user:                     % row(s)', n;

  DELETE FROM general.person WHERE id IN (SELECT id FROM t_persons);
  GET DIAGNOSTICS n = ROW_COUNT;
  RAISE NOTICE 'general.person:                   % row(s)', n;
END
$$;

\if :apply
  COMMIT;
  \echo 'APPLIED: the rows above were deleted.'
\else
  ROLLBACK;
  \echo 'DRY RUN: nothing was deleted. Add --apply to delete.'
\endif
SQL
