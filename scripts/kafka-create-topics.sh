#!/usr/bin/env bash
# Creates the Kafka topics smartkin-backend uses (BE-3) and checks they exist. Safe to run again: existing topics are left as they are.
#
#   KAFKA_BOOTSTRAP_SERVERS=broker1:9093,broker2:9093 \
#   KAFKA_CLIENT_PROPERTIES=$HOME/kafka/smartkin-client.properties \
#   ./scripts/kafka-create-topics.sh              # creates and verifies
#   ./scripts/kafka-create-topics.sh --dry-run    # prints what it would do
#
# Topics (same names and overrides as the service: umdc.kafka.topics.* and BOOTSTRAP_ERROR_TOPIC)
#   smartkin.activity.assigned.v1    TOPIC_ACTIVITY_ASSIGNED     a parent assigned an activity
#   smartkin.quiz.completed.v1       TOPIC_QUIZ_COMPLETED        a child finished a quiz
#   smartkin.achievement.earned.v1   TOPIC_ACHIEVEMENT_EARNED    an achievement was awarded
#   smartkin.events.error.v1         BOOTSTRAP_ERROR_TOPIC           records that failed every retry (dead letters)
#
# Required
#   KAFKA_BOOTSTRAP_SERVERS   host:port list of the brokers
# Optional
#   KAFKA_CLIENT_PROPERTIES   path of a client.properties file (security.protocol, SASL/SSL settings, truststore). It may hold secrets: it
#                             is mounted read-only into the container and never printed. Without it the connection is plaintext
#   PARTITIONS                default 3
#   REPLICATION_FACTOR        default 3 (it cannot exceed the number of brokers: use 1 on a single-node cluster)
#   KAFKA_IMAGE               default apache/kafka:4.2.0; the CLI of the image is used unless KAFKA_TOPICS_BIN is set
#   KAFKA_TOPICS_BIN          path of a local kafka-topics.sh to use instead of Docker
#   DOCKER_NETWORK            docker network to join, to reach a broker that runs in a container
#
# Grant the service principal read on the three event topics and write on all four (ACLs are the broker's, not created here).
# Exit code 0 only when all four topics exist with the expected number of partitions.

set -euo pipefail

dry_run=0
[ "${1:-}" = "--dry-run" ] && dry_run=1

: "${KAFKA_BOOTSTRAP_SERVERS:?set KAFKA_BOOTSTRAP_SERVERS}"
partitions="${PARTITIONS:-2}"
replication="${REPLICATION_FACTOR:-3}"
image="${KAFKA_IMAGE:-apache/kafka:4.2.0}"
topics=(
  "${TOPIC_ACTIVITY_ASSIGNED:-smartkin.activity.assigned.v1}"
  "${TOPIC_QUIZ_COMPLETED:-smartkin.quiz.completed.v1}"
  "${TOPIC_ACHIEVEMENT_EARNED:-smartkin.achievement.earned.v1}"
  "${BOOTSTRAP_ERROR_TOPIC:-smartkin.events.error.v1}"
)

cli() {
  if [ -n "${KAFKA_TOPICS_BIN:-}" ]; then
    local extra=()
    [ -n "${KAFKA_CLIENT_PROPERTIES:-}" ] && extra=(--command-config "$KAFKA_CLIENT_PROPERTIES")
    "$KAFKA_TOPICS_BIN" --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" ${extra[@]+"${extra[@]}"} "$@"
    return
  fi
  local args=(run --rm)
  [ -n "${DOCKER_NETWORK:-}" ] && args+=(--network "$DOCKER_NETWORK")
  local extra=()
  if [ -n "${KAFKA_CLIENT_PROPERTIES:-}" ]; then
    [ -f "$KAFKA_CLIENT_PROPERTIES" ] || { echo "KAFKA_CLIENT_PROPERTIES not found: $KAFKA_CLIENT_PROPERTIES" >&2; exit 2; }
    local dir file
    dir="$(cd "$(dirname "$KAFKA_CLIENT_PROPERTIES")" && pwd)"
    file="$(basename "$KAFKA_CLIENT_PROPERTIES")"
    args+=(-v "$dir:/etc/kafka-client:ro")
    extra=(--command-config "/etc/kafka-client/$file")
  fi
  docker "${args[@]}" "$image" /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" ${extra[@]+"${extra[@]}"} "$@"
}

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
