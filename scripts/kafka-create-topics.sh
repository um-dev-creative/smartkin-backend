#!/usr/bin/env bash
# Creates the Kafka topics smartkin-backend uses (BE-3) and checks they exist. Safe to run again: existing topics are left as they are.
#
#   KAFKA_BOOTSTRAP_SERVERS=broker1:9093,broker2:9093 \
#   KAFKA_CLIENT_PROPERTIES=$HOME/kafka/smartkin-client.properties \
#   ./scripts/kafka-create-topics.sh              # creates and verifies
#   ./scripts/kafka-create-topics.sh --dry-run    # prints what it would do
#
# Topics (same names and overrides as the service: prx.kafka.topics.* and KAFKA_ERROR_TOPIC)
#   smartkin.activity.assigned.v1    TOPIC_ACTIVITY_ASSIGNED     a parent assigned an activity
#   smartkin.quiz.completed.v1       TOPIC_QUIZ_COMPLETED        a child finished a quiz
#   smartkin.achievement.earned.v1   TOPIC_ACHIEVEMENT_EARNED    an achievement was awarded
#   smartkin.events.error.v1         KAFKA_ERROR_TOPIC           records that failed every retry (dead letters)
#
# Required
#   KAFKA_BOOTSTRAP_SERVERS   host:port list of the brokers
# Optional
#   KAFKA_CLIENT_PROPERTIES   path of a client.properties file (security.protocol, SASL/SSL settings, truststore). It may hold secrets and
#                             is never printed. Without it the connection is plaintext. The files it names (ca.pem, a keystore...) may
#                             be written with their path on YOUR machine, as long as they sit in the same folder as the properties file:
#                             Kafka runs in a container, so the script copies the file, rewrites that folder to /etc/kafka-client/
#                             (where it mounts the folder read-only) and removes the copy when it ends. A path that already starts
#                             with /etc/kafka-client/ is left alone. See docs/ops/kafka-client.aiven.example.properties
#   PARTITIONS                default 3
#   REPLICATION_FACTOR        default 3 (it cannot exceed the number of brokers: use 1 on a single-node cluster)
#   KAFKA_IMAGE               default apache/kafka:4.2.0; the CLI of the image is used unless KAFKA_TOPICS_BIN is set
#   KAFKA_TOPICS_BIN          path of a local kafka-topics.sh to use instead of Docker
#   DOCKER_NETWORK            docker network to join, to reach a broker that runs in a container
#
# Grant the service principal read on the three event topics and write on all four (ACLs are the broker's, not created here).
# Exit code 0 only when all four topics exist with the expected number of partitions.

# The script uses bash arrays: `sh script` works on macOS (where sh is bash) and breaks on dash (Debian, Ubuntu)
[ -n "${BASH_VERSION:-}" ] || { echo "Run it with bash: ./scripts/kafka-create-topics.sh (not 'sh ...')" >&2; exit 2; }

set -euo pipefail

dry_run=0
[ "${1:-}" = "--dry-run" ] && dry_run=1

: "${KAFKA_BOOTSTRAP_SERVERS:?set KAFKA_BOOTSTRAP_SERVERS}"
partitions="${PARTITIONS:-3}"
replication="${REPLICATION_FACTOR:-3}"
image="${KAFKA_IMAGE:-apache/kafka:4.2.0}"
topics=(
  "${TOPIC_ACTIVITY_ASSIGNED:-smartkin.activity.assigned.v1}"
  "${TOPIC_QUIZ_COMPLETED:-smartkin.quiz.completed.v1}"
  "${TOPIC_ACHIEVEMENT_EARNED:-smartkin.achievement.earned.v1}"
  "${KAFKA_ERROR_TOPIC:-smartkin.events.error.v1}"
)

staged_dir=""
cleanup() { [ -z "$staged_dir" ] || rm -rf "$staged_dir"; }
trap cleanup EXIT

# Runs ONCE in the main shell (never inside $(...), whose changes would be lost and whose temp folder would never be removed).
# Kafka runs in a container, so the properties file is copied with the folder it lives in rewritten to the folder the container sees.
mount_args=()
command_config=()
prepare_properties() {
  [ -n "${KAFKA_CLIENT_PROPERTIES:-}" ] || return 0
  [ -f "$KAFKA_CLIENT_PROPERTIES" ] || { echo "KAFKA_CLIENT_PROPERTIES not found: $KAFKA_CLIENT_PROPERTIES" >&2; exit 2; }
  if [ -n "${KAFKA_TOPICS_BIN:-}" ]; then
    command_config=(--command-config "$KAFKA_CLIENT_PROPERTIES")   # a local CLI sees the same paths as you do
    return 0
  fi
  local dir physical file from_dir from_physical
  dir="$(cd "$(dirname "$KAFKA_CLIENT_PROPERTIES")" && pwd)"
  physical="$(cd "$(dirname "$KAFKA_CLIENT_PROPERTIES")" && pwd -P)"
  file="$(basename "$KAFKA_CLIENT_PROPERTIES")"
  staged_dir="$(mktemp -d)"
  chmod 700 "$staged_dir"
  # host folder -> container folder, for the folder as typed and for its resolved (symlink-free) form
  from_dir="$(printf '%s' "$dir/" | sed 's/[][\\.*^$|]/\\&/g')"
  from_physical="$(printf '%s' "$physical/" | sed 's/[][\\.*^$|]/\\&/g')"
  sed -e "s|${from_dir}|/etc/kafka-client/|g" -e "s|${from_physical}|/etc/kafka-client/|g" "$dir/$file" > "$staged_dir/client.properties"
  chmod 600 "$staged_dir/client.properties"
  mount_args=(-v "$dir:/etc/kafka-client:ro" -v "$staged_dir:/etc/kafka-client-conf:ro")
  command_config=(--command-config "/etc/kafka-client-conf/client.properties")
}

cli() {
  if [ -n "${KAFKA_TOPICS_BIN:-}" ]; then
    "$KAFKA_TOPICS_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" ${command_config[@]+"${command_config[@]}"} "$@"
    return
  fi
  local args=(run --rm)
  [ -n "${DOCKER_NETWORK:-}" ] && args+=(--network "$DOCKER_NETWORK")
  docker "${args[@]}" ${mount_args[@]+"${mount_args[@]}"} "$image" /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" ${command_config[@]+"${command_config[@]}"} "$@"
}

prepare_properties

echo ">> brokers ${KAFKA_BOOTSTRAP_SERVERS}: ${#topics[@]} topics, ${partitions} partitions, replication factor ${replication}"
for topic in "${topics[@]}"; do
  if [ "$dry_run" = "1" ]; then
    echo "   would create ${topic}"
  else
    cli --create --if-not-exists --topic "$topic" --partitions "$partitions" --replication-factor "$replication"
  fi
done
[ "$dry_run" = "1" ] && exit 0

echo ">> verifying"
failed=0
for topic in "${topics[@]}"; do
  description="$(cli --describe --topic "$topic" 2>/dev/null || true)"
  found="$(printf '%s\n' "$description" | sed -n 's/.*PartitionCount: *\([0-9]*\).*/\1/p' | head -n 1)"
  if [ -z "$found" ]; then
    echo "   MISSING  ${topic}"; failed=1
  elif [ "$found" -lt "$partitions" ]; then
    echo "   FEWER    ${topic}: ${found} partitions, expected at least ${partitions}"; failed=1
  else
    echo "   ok       ${topic} (${found} partitions)"
  fi
done
[ "$failed" = "0" ] || { echo "Some topics are missing or have fewer partitions than expected." >&2; exit 1; }
echo ">> all ${#topics[@]} topics exist"
