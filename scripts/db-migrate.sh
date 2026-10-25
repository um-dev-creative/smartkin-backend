#!/usr/bin/env bash
# Creates (or brings up to date) the tables of the `smartkin` schema by running the Flyway migrations of
# src/main/resources/db/migration (V1 to V6 today) with the Flyway command line, as the schema owner `smartkin_owner`.
# It applies exactly what the service would apply at start-up (same Flyway version, schema, history table and checksums), so the service
# can start afterwards with nothing pending, or with FLYWAY_ENABLED=false.
#
# Order: 1) docs/db/smartkin_roles.sql  2) this script  3) start the service.
#
#   SMARTKIN_DB_URI=db.example.com \
#   SMARTKIN_FLYWAY_USERNAME=smartkin_owner SMARTKIN_FLYWAY_PASSWORD="$(vault kv get -field=flyway_password <path>)" \
#   ./scripts/db-migrate.sh            # shows what is applied and what is pending (changes nothing)
#   ./scripts/db-migrate.sh validate   # checks the applied migrations against the files
#   ./scripts/db-migrate.sh migrate    # creates the tables
#
# Required
#   SMARTKIN_DB_URI            host of the DIRECT connection (not the transaction pooler: migrations need session-level locks)
#   SMARTKIN_FLYWAY_USERNAME   the schema owner (smartkin_owner); `postgres` is refused unless ALLOW_ADMIN_USER=1
#   SMARTKIN_FLYWAY_PASSWORD   its password. It reaches the container through the environment, never on a command line
# Optional
#   SMARTKIN_DB_DIRECT_PORT    default 5432 (6543, the pooler, is refused)
#   SMARTKIN_DB_NAME           default umdevc
#   SMARTKIN_DB_SCHEMA         default smartkin
#   FLYWAY_IMAGE               default flyway/flyway:12.4.0 (the version Spring Boot 4.1.1 brings)
#   DOCKER_NETWORK             docker network to join, to reach a database that runs in a container
#   JDBC_PARAMS                extra JDBC parameters, e.g. "sslmode=require" (Supabase needs TLS)
#
# Needs Docker. Exit code 0 only when the command succeeded.

# The script uses bash arrays: `sh script` works on macOS (where sh is bash) and breaks on dash (Debian, Ubuntu)
[ -n "${BASH_VERSION:-}" ] || { echo "Run it with bash: ./scripts/db-migrate.sh (not 'sh ...')" >&2; exit 2; }

set -euo pipefail

command_name="${1:-info}"
case "$command_name" in info | validate | migrate) ;; *) echo "Unknown command '$command_name' (use info, validate or migrate)" >&2; exit 2 ;; esac

: "${SMARTKIN_DB_URI:?set SMARTKIN_DB_URI}"
: "${SMARTKIN_FLYWAY_USERNAME:?set SMARTKIN_FLYWAY_USERNAME}"
: "${SMARTKIN_FLYWAY_PASSWORD:?set SMARTKIN_FLYWAY_PASSWORD}"
port="${SMARTKIN_DB_DIRECT_PORT:-5432}"
database="${SMARTKIN_DB_NAME:-umdevc}"
schema="${SMARTKIN_DB_SCHEMA:-smartkin}"
image="${FLYWAY_IMAGE:-flyway/flyway:12.4.0}"

if [ "$port" = "6543" ]; then
  echo "Port 6543 is Supabase's transaction pooler; migrations need the direct connection (5432). Set SMARTKIN_DB_DIRECT_PORT." >&2
  exit 2
fi
if [ "$SMARTKIN_FLYWAY_USERNAME" = "postgres" ] && [ "${ALLOW_ADMIN_USER:-0}" != "1" ]; then
  echo "Refusing to migrate as postgres: the tables would belong to it and smartkin_owner could not change them later." >&2
  echo "Run docs/db/smartkin_roles.sql first and use smartkin_owner (or set ALLOW_ADMIN_USER=1 knowingly)." >&2
  exit 2
fi

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migrations="$repo/src/main/resources/db/migration"
[ -d "$migrations" ] || { echo "Migrations not found in $migrations" >&2; exit 2; }

url="jdbc:postgresql://${SMARTKIN_DB_URI}:${port}/${database}"
[ -n "${JDBC_PARAMS:-}" ] && url="${url}?${JDBC_PARAMS}"

docker_args=(run --rm
  -e FLYWAY_URL="$url"
  -e FLYWAY_USER="$SMARTKIN_FLYWAY_USERNAME"
  -e FLYWAY_PASSWORD
  -v "$migrations:/flyway/sql:ro")
[ -n "${DOCKER_NETWORK:-}" ] && docker_args+=(--network "$DOCKER_NETWORK")

export FLYWAY_PASSWORD="$SMARTKIN_FLYWAY_PASSWORD"
flyway() {
  docker "${docker_args[@]}" "$image" \
    -schemas="$schema" -defaultSchema="$schema" -createSchemas=true -validateOnMigrate=true \
    -locations=filesystem:/flyway/sql -connectRetries=3 "$@"
}

echo ">> ${command_name} on ${SMARTKIN_DB_URI}:${port}/${database} schema ${schema} as ${SMARTKIN_FLYWAY_USERNAME}"
flyway "$command_name"
if [ "$command_name" = "migrate" ]; then
  echo ">> state after migrate"
  flyway info
fi
