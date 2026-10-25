#!/usr/bin/env bash
# QA smoke test of smartkin-backend (X-4, and the S-1 checks of docs/SECURITY_REVIEW_MVP.md).
#
# It needs a running service, `curl` and `jq`. It does not create users: pass the session tokens of users that already exist.
#
#   BASE_URL=https://smartkin.umdc-qa.tst:8166/smartkin-backend \
#   PARENT_TOKEN=... CHILD_TOKEN=... ./scripts/qa-smoke.sh
#
# Required
#   BASE_URL            service root, without /api/v1 (include the context path if the service has one)
#   PARENT_TOKEN        session-token of a user who is or can become a parent
#   CHILD_TOKEN         session-token of a user with NO parent or child profile yet (a fresh account); the script makes it a child
# Optional
#   OTHER_PARENT_TOKEN  session-token of a second parent: checks that another family's child answers 404
#   EXPIRED_TOKEN       a token past its expiry          (S-1: must answer 401)
#   REVOKED_TOKEN       a token whose session was closed (S-1: must answer 401)
#   CURL_OPTS           extra curl flags, for example "--cacert certs/smartkin/prx-internal-ca.crt" or "-k" on a throwaway QA
#
# Exit code 0 only when every check passed.

set -u
: "${BASE_URL:?set BASE_URL}"
: "${PARENT_TOKEN:?set PARENT_TOKEN}"
: "${CHILD_TOKEN:?set CHILD_TOKEN}"
API="${BASE_URL%/}/api/v1"
# shellcheck disable=SC2206
CURL=(curl -sS -o /tmp/smartkin-smoke.body -w '%{http_code}' --max-time 20 ${CURL_OPTS:-})

passed=0
failed=0

# call METHOD PATH TOKEN [JSON]  -> prints the HTTP status; the body stays in /tmp/smartkin-smoke.body
call() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  local args=(-X "$method" "$API$path" -H 'Accept: application/json')
  [ -n "$token" ] && args+=(-H "session-token: $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  "${CURL[@]}" "${args[@]}" 2>/dev/null || echo 000
}

# expect "description" "accepted statuses (space separated)" METHOD PATH TOKEN [JSON]
expect() {
  local description="$1" accepted="$2"
  shift 2
  local status
  status="$(call "$@")"
  if [[ " $accepted " == *" $status "* ]]; then
    passed=$((passed + 1)); printf '  ok    %-62s %s\n' "$description" "$status"
  else
    failed=$((failed + 1)); printf '  FAIL  %-62s got %s, wanted %s\n' "$description" "$status" "$accepted"
  fi
}

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
part() { printf '%s' "$1" | cut -d. -f"$2"; }

echo "== Service"
live="$("${CURL[@]}" "${BASE_URL%/}/actuator/health/liveness" 2>/dev/null || echo 000)"
if [ "$live" = "200" ]; then
  passed=$((passed + 1)); printf '  ok    %-62s %s\n' "liveness probe answers 200" "$live"
else
  failed=$((failed + 1)); printf '  FAIL  %-62s got %s, wanted 200\n' "liveness probe answers 200" "$live"
fi

echo "== S-1: the API must refuse a token it cannot trust (every call must be 401)"
head_p="$(part "$PARENT_TOKEN" 1)"; payload_p="$(part "$PARENT_TOKEN" 2)"
payload_c="$(part "$CHILD_TOKEN" 2)"
none_header="$(printf '{"alg":"none","typ":"JWT"}' | b64url)"
# Parallel arrays instead of an associative array, so it also runs on the bash 3.2 that macOS ships
KINDS=(tampered-signature unsigned-alg-none payload-of-another-user)
TOKENS=(
  "$head_p.$payload_p.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
  "$none_header.$payload_p."
  "$head_p.$payload_c.$(part "$PARENT_TOKEN" 3)"
)
[ -n "${EXPIRED_TOKEN:-}" ] && { KINDS+=(expired); TOKENS+=("$EXPIRED_TOKEN"); }
[ -n "${REVOKED_TOKEN:-}" ] && { KINDS+=(revoked); TOKENS+=("$REVOKED_TOKEN"); }
for i in "${!KINDS[@]}"; do
  kind="${KINDS[$i]}"; bad="${TOKENS[$i]}"
  expect "$kind: GET /parents/me"       "401" GET /parents/me "$bad"
  expect "$kind: GET /children"         "401" GET /children "$bad"
  expect "$kind: GET /notifications"    "401" GET /notifications "$bad"
  expect "$kind: POST /assignments"     "401" POST /assignments "$bad" '{"childId":"00000000-0000-0000-0000-000000000000","activityId":"00000000-0000-0000-0000-000000000000"}'
done
expect "no token: GET /children is refused" "400 401" GET /children ""

echo "== Family: invitation, accept, single use (S-2)"
expect "parent creates the profile" "200" PUT /parents/me "$PARENT_TOKEN" '{}'
status="$(call POST /children/invitations "$PARENT_TOKEN" '{"gradeLevel":"5","avatar":"smoke"}')"
code="$(jq -r '.code // empty' /tmp/smartkin-smoke.body 2>/dev/null)"
if [ "$status" = "201" ] && [ -n "$code" ]; then
  passed=$((passed + 1)); printf '  ok    %-62s %s\n' "parent creates an invitation (code shown once)" "$status"
else
  failed=$((failed + 1)); printf '  FAIL  %-62s got %s\n' "parent creates an invitation" "$status"
fi
expect "the invitation list never shows the code" "200" GET /children/invitations "$PARENT_TOKEN"
if jq -e 'any(.[]; .code != null)' /tmp/smartkin-smoke.body >/dev/null 2>&1; then
  failed=$((failed + 1)); echo "  FAIL  the invitation list exposes a code"
fi
expect "a wrong code answers 404" "404" POST /children/invitations/accept "$CHILD_TOKEN" '{"code":"ZZZZ-ZZZZ"}'
expect "the child accepts the code" "201 409" POST /children/invitations/accept "$CHILD_TOKEN" "{\"code\":\"$code\"}"
expect "the same code cannot be used again" "404 409" POST /children/invitations/accept "$CHILD_TOKEN" "{\"code\":\"$code\"}"
expect "a parent cannot accept an invitation" "409" POST /children/invitations/accept "$PARENT_TOKEN" "{\"code\":\"$code\"}"

echo "== Slice: catalog, assignment, notifications"
expect "parent lists the children" "200" GET /children "$PARENT_TOKEN"
child_id="$(jq -r '.[0].childId // empty' /tmp/smartkin-smoke.body 2>/dev/null)"
expect "parent lists the topics" "200" GET /topics "$PARENT_TOKEN"
expect "parent lists the activities" "200" GET /activities "$PARENT_TOKEN"
activity_id="$(jq -r '.[0].activityId // empty' /tmp/smartkin-smoke.body 2>/dev/null)"
if [ -n "$child_id" ] && [ -n "$activity_id" ]; then
  expect "parent assigns an activity" "201 409" POST /assignments "$PARENT_TOKEN" "{\"childId\":\"$child_id\",\"activityId\":\"$activity_id\"}"
  expect "the child sees the assignment" "200" GET /assignments "$CHILD_TOKEN"
  expect "the child reads the inbox" "200" GET /notifications "$CHILD_TOKEN"
  expect "the child reads their points" "200" GET "/children/$child_id/points" "$CHILD_TOKEN"
  [ -n "${OTHER_PARENT_TOKEN:-}" ] && {
    expect "another family's parent profile" "200" PUT /parents/me "$OTHER_PARENT_TOKEN" '{}'
    expect "another family's child answers 404" "404" GET "/children/$child_id" "$OTHER_PARENT_TOKEN"
  }
else
  failed=$((failed + 1)); echo "  FAIL  no child or no activity to continue the slice (seed an active activity with a published lesson)"
fi

echo
echo "passed $passed, failed $failed"
[ "$failed" -eq 0 ]
