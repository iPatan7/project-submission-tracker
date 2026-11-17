#!/usr/bin/env bash
# Smoke test after a deployment: sends one submission through the whole flow via nginx.
set -euo pipefail
BASE=${1:-http://localhost:8181}
JSON='Content-Type: application/json'
AUTH="Authorization: Bearer ${FACULTY_TOKEN:?FACULTY_TOKEN is not set}"

echo "1) health check"
curl -fsS "$BASE/health"; echo

echo "2) new submission"
resp=$(curl -fsS -X POST "$BASE/api/submissions" -H "$JSON" \
  -d '{"uid":"24MCC20081","studentName":"Mohammad Iqbal Patan","title":"Project Submission Tracker","repoUrl":"https://github.com/iPatan7/project-submission-tracker"}')
echo "$resp"
id=$(echo "$resp" | sed -E 's/.*"id":([0-9]+).*/\1/')

echo "3) status change without the faculty token must give 401"
code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE/api/submissions/$id/status" -H "$JSON" -d '{"status":"APPROVED"}')
echo "HTTP $code"
[ "$code" = "401" ]

echo "4) faculty starts review"
curl -fsS -X PUT "$BASE/api/submissions/$id/status" -H "$JSON" -H "$AUTH" -d '{"status":"UNDER_REVIEW"}'; echo

echo "5) faculty approves"
curl -fsS -X PUT "$BASE/api/submissions/$id/status" -H "$JSON" -H "$AUTH" -d '{"status":"APPROVED"}' | grep '"status":"APPROVED"'

echo "6) wrong input must give 400"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/submissions" -H "$JSON" -d '{"uid":"abc"}')
echo "HTTP $code"
[ "$code" = "400" ]

echo "7) approved work cannot be changed again (409)"
code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE/api/submissions/$id/status" -H "$JSON" -H "$AUTH" -d '{"status":"REJECTED"}')
echo "HTTP $code"
[ "$code" = "409" ]

total=$(curl -fsS "$BASE/api/submissions" | grep -o '"id":' | wc -l)
echo "submissions stored: $total"
echo "SMOKE TEST PASSED"
