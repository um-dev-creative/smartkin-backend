#!/usr/bin/env bash
# Tests scripts/purge-backbone-user.sql against a throwaway PostgreSQL in Docker (needs Docker; about 20 seconds). It sets the three
# parameters of the script with sed and checks: the dry run changes nothing and reports the rows, 'user' deletes one user and its rows, a
# person shared with another application survives, orphan verification codes go, 'application' deletes every user of the application,
# and a bad mode or the default id is refused.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
sqlfile="$here/purge-backbone-user.sql"
image="${POSTGRES_IMAGE:-postgres:17-alpine}"
db="purge-sql-test-$$"
fail=0

cleanup() { docker rm -f "$db" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$db" -e POSTGRES_PASSWORD=pw -e POSTGRES_DB=umdevc "$image" >/dev/null
for _ in $(seq 1 40); do docker exec "$db" pg_isready -U postgres -d umdevc >/dev/null 2>&1 && break; sleep 1; done
sleep 2

sql() { docker exec -i "$db" psql -U postgres -d umdevc -X -q -t -A "$@"; }
count() { sql -c "SELECT count(*) FROM $1"; }
# runs the script with the parameters set; prints the output, exit status 0 = ran, 1 = error (a dry run ends with an error on purpose)
run() { # mode id apply
  sed -e "s/v_mode  text    := 'user';/v_mode  text    := '$1';/" \
      -e "s/v_id    uuid    := '00000000-0000-0000-0000-000000000000';/v_id    uuid    := '$2';/" \
      -e "s/v_apply boolean := false;/v_apply boolean := $3;/" "$sqlfile" | sql -v ON_ERROR_STOP=1 -f - 2>&1
}
check() { if [ "$2" = "$3" ]; then echo "ok   - $1"; else echo "FAIL - $1 (expected $2, got $3)"; fail=1; fi; }

A1=11111111-1111-1111-1111-111111111111; A2=22222222-2222-2222-2222-222222222222
U1=aaaaaaaa-0000-0000-0000-000000000001; U2=aaaaaaaa-0000-0000-0000-000000000002; U3=aaaaaaaa-0000-0000-0000-000000000003
P1=bbbbbbbb-0000-0000-0000-000000000001; P2=bbbbbbbb-0000-0000-0000-000000000002; P3=bbbbbbbb-0000-0000-0000-000000000003
ORPHAN=cccccccc-0000-0000-0000-000000000009

sql -v ON_ERROR_STOP=1 <<SQL
CREATE SCHEMA general; CREATE SCHEMA mercury;
CREATE TABLE general.person (id uuid PRIMARY KEY);
CREATE TABLE general."user" (id uuid PRIMARY KEY, person_id uuid REFERENCES general.person(id), application_id uuid NOT NULL);
CREATE TABLE general.application_role_user (user_id uuid REFERENCES general."user"(id), role_id uuid, application_id uuid);
CREATE TABLE general.contact (id uuid PRIMARY KEY, person_id uuid REFERENCES general.person(id), owner_id uuid);
CREATE TABLE general.address (id uuid PRIMARY KEY, person_id uuid REFERENCES general.person(id));
CREATE TABLE general.identification_document (id uuid PRIMARY KEY, person_id uuid REFERENCES general.person(id));
CREATE TABLE general.federated_identity (id uuid PRIMARY KEY, user_id uuid REFERENCES general."user"(id));
CREATE TABLE mercury.verification_codes (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid, application_id uuid);
INSERT INTO general.person VALUES ('$P1'), ('$P2'), ('$P3');
INSERT INTO general."user" VALUES ('$U1', '$P1', '$A1'), ('$U2', '$P2', '$A1'), ('$U3', '$P1', '$A2');
INSERT INTO general.application_role_user VALUES ('$U1', gen_random_uuid(), '$A1'), ('$U2', gen_random_uuid(), '$A1'), ('$U3', gen_random_uuid(), '$A2');
INSERT INTO general.contact VALUES (gen_random_uuid(), '$P1', NULL), (gen_random_uuid(), '$P2', NULL), (gen_random_uuid(), NULL, '$U2');
INSERT INTO general.address VALUES (gen_random_uuid(), '$P2');
INSERT INTO general.identification_document VALUES (gen_random_uuid(), '$P2');
INSERT INTO general.federated_identity VALUES (gen_random_uuid(), '$U2');
INSERT INTO mercury.verification_codes (user_id, application_id) VALUES ('$U1', '$A1'), ('$U2', '$A1'), ('$U3', '$A2'), ('$ORPHAN', '$A1');
SQL

echo "--- dry run"
out="$(run user "$U2" false || true)"
check "dry run says it is a dry run" 1 "$(grep -c 'DRY RUN, rolled back' <<<"$out")"
check "dry run reports the user" 1 "$(grep -c 'general.user: *1' <<<"$out")"
check "dry run keeps the users" 3 "$(count 'general."user"')"
check "dry run keeps the codes" 4 "$(count mercury.verification_codes)"

echo "--- one user"
out="$(run user "$U2" true)"
check "apply says applied" 1 "$(grep -c 'APPLIED' <<<"$out")"
check "user U2 deleted" 0 "$(count "general.\"user\" WHERE id = '$U2'")"
check "other users kept" 2 "$(count 'general."user"')"
check "its role deleted" 2 "$(count general.application_role_user)"
check "its person deleted" 0 "$(count "general.person WHERE id = '$P2'")"
check "its contacts deleted (by person and by owner)" 1 "$(count general.contact)"
check "its address and document deleted" 00 "$(count general.address)$(count general.identification_document)"
check "its federated identity deleted" 0 "$(count general.federated_identity)"
check "its code deleted, the rest kept" 3 "$(count mercury.verification_codes)"

echo "--- a person shared with another application survives"
run user "$U1" true >/dev/null
check "user U1 deleted" 0 "$(count "general.\"user\" WHERE id = '$U1'")"
check "shared person P1 kept (U3 still uses it)" 1 "$(count "general.person WHERE id = '$P1'")"
check "P1's contact kept" 1 "$(count general.contact)"

echo "--- orphan code of a user that does not exist"
run user "$ORPHAN" true >/dev/null
check "orphan code deleted" 0 "$(count "mercury.verification_codes WHERE user_id = '$ORPHAN'")"

echo "--- whole application"
run application "$A2" true >/dev/null
check "users of the application deleted" 0 "$(count 'general."user"')"
check "now unshared person P1 deleted" 0 "$(count "general.person WHERE id = '$P1'")"
check "no codes left" 0 "$(count mercury.verification_codes)"
check "unrelated person P3 untouched" 1 "$(count "general.person WHERE id = '$P3'")"

echo "--- bad parameters"
check "a bad mode is refused" 1 "$(run nobody "$A1" true | grep -c "v_mode must be")"
check "the default id is refused" 1 "$(sed "s/v_mode  text    := 'user';/v_mode  text    := 'user';/" "$sqlfile" | sql -v ON_ERROR_STOP=1 -f - 2>&1 | grep -c 'Set v_id')"

[ "$fail" = 0 ] && echo "ALL OK" || { echo "FAILED"; exit 1; }
