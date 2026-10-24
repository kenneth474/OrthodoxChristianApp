#!/usr/bin/env bash
# smoke-test.sh: quick checks that a running copy of the app really works,
# the way it runs online (in its container, with PostgreSQL).
#
# The "Build and test" workflow (ci.yml) starts the container and runs:
#     bash .github/scripts/smoke-test.sh http://localhost:10000
#
# A "smoke test" is the electrician's first check: switch it on and see
# whether smoke comes out. It doesn't test everything (the Java and React
# tests do that); it tests that the pieces fit together: the container, the
# PostgreSQL database, the React app inside the .jar, and the https settings.
#
# It creates a test account, so only point it at a throwaway copy of the app.

# Stop at the first failing command, and treat unset variables as mistakes.
set -euo pipefail

BASE="${1:-http://localhost:8080}"
COOKIES="$(mktemp)"   # like a browser's cookie storage, so we stay signed in
PHOTO="$(mktemp)"

pass() { echo "✓ $1"; }
fail() { echo "✗ $1"; exit 1; }

# Fetches this session's CSRF token and prints "Header-Name: token", ready to
# send with the next change (see SecurityConfig.java for why it's needed).
csrf_header() {
  curl -fsS -b "$COOKIES" -c "$COOKIES" "$BASE/api/auth/csrf" | jq -r '"\(.headerName): \(.token)"'
}

# 1. The React app is inside the .jar and served at /
curl -fsS "$BASE/" | grep -q '<div id="root">' || fail "home page"
pass "home page (the React app)"

# 2. Screen addresses work when opened directly (SpaController)
curl -fsS "$BASE/bible/PSA/50" | grep -q '<div id="root">' || fail "opening /bible/PSA/50 directly"
pass "opening /bible/PSA/50 directly"

# 3. The Bible text was packed into the .jar and loads
curl -fsS "$BASE/api/bible/PSA/50" | jq -e '.verses | length > 10' > /dev/null || fail "Bible chapter"
pass "Bible chapter (Psalm 50)"

# 4. Behind https (as on Render): the sign-in cookie is https-only, and the
#    browser is told to always use https (HSTS). Render adds the
#    X-Forwarded-Proto header; here we add it ourselves.
HEADERS="$(curl -fsS -o /dev/null -D - -H 'X-Forwarded-Proto: https' "$BASE/api/auth/csrf")"
echo "$HEADERS" | grep -qi '^set-cookie: JSESSIONID=.*; *secure' || fail "https-only sign-in cookie"
echo "$HEADERS" | grep -qi '^strict-transport-security:' || fail "HSTS header"
pass "https-only sign-in cookie and HSTS"

# 5. Create an account: saved in PostgreSQL. 201 = "Created".
EMAIL="smoke-$(date +%s)@example.com"
STATUS="$(curl -sS -o /dev/null -w '%{http_code}' -b "$COOKIES" -c "$COOKIES" \
  -H "$(csrf_header)" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Xq7#mLp2!vRt9z\",\"displayName\":\"Smoke Test\"}" \
  "$BASE/api/auth/register")"
[ "$STATUS" = "201" ] || fail "creating an account (status $STATUS)"
curl -fsS -b "$COOKIES" "$BASE/api/account" | jq -e --arg email "$EMAIL" '.email == $email' > /dev/null \
  || fail "signed in after creating the account"
pass "creating an account and staying signed in"

# 6. Staying signed in: the long-lasting "stay-signed-in" cookie alone, with
#    no session (as after the server restarts), signs you in again
#    (StaySignedIn.java). In curl's cookie file, column 6 is a cookie's name
#    and column 7 its value.
STAY="$(awk '$6 == "stay-signed-in" { print $7 }' "$COOKIES")"
[ -n "$STAY" ] || fail "the stay-signed-in cookie was set"
curl -fsS -H "Cookie: stay-signed-in=$STAY" "$BASE/api/account" \
  | jq -e --arg email "$EMAIL" '.email == $email' > /dev/null || fail "signed in again by the stay-signed-in cookie"
pass "signed in again with only the stay-signed-in cookie"

# 7. A choice added after the database was made can be saved: PostgreSQL's
#    lists of allowed choices are removed at startup (DatabaseFixes.java).
curl -fsS -X PUT -b "$COOKIES" -c "$COOKIES" -H "$(csrf_header)" -H 'Content-Type: application/json' \
  -d '{"textSize":"SMALL","theme":"SYSTEM","style":"ATHOS"}' "$BASE/api/account/appearance" \
  | jq -e '.textSize == "SMALL"' > /dev/null || fail "saving the Small text size"
pass "saving the Small text size"

# 8. Upload a profile photo (the app's own icon) and read it back: checks
#    that pictures are stored in and read from PostgreSQL correctly.
curl -fsS -o /dev/null -b "$COOKIES" -c "$COOKIES" -H "$(csrf_header)" \
  -F "photo=@frontend/public/icons/icon-512.png;type=image/png" "$BASE/api/account/photo" \
  || fail "uploading a profile photo"
TYPE="$(curl -fsS -o "$PHOTO" -w '%{content_type}' -b "$COOKIES" "$BASE/api/account/photo")"
[ "$TYPE" = "image/jpeg" ] && [ -s "$PHOTO" ] || fail "reading the profile photo back (got $TYPE)"
pass "profile photo saved and read back"

# 9. Today's calendar comes from the Orthocal website. If that site is down
#    it's not our container's fault, so only warn instead of failing.
if curl -fsS "$BASE/api/today" | jq -e '.title' > /dev/null; then
  pass "today's calendar (from Orthocal)"
else
  echo "! today's calendar didn't load (is orthocal.info down?), not counted as a failure"
fi

echo "All smoke tests passed."
