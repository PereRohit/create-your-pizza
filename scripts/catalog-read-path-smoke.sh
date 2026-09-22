#!/usr/bin/env bash
# Build plan §5.1 — Catalog read-path service-ready Compose smoke (story 10).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AUTH_URL="${AUTH_BASE_URL:-http://localhost:8080}"
CATALOG_URL="${CATALOG_BASE_URL:-http://localhost:8081}"
COMPOSE=(docker compose -f "$ROOT/docker-compose.yml")

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }
pass() { echo "SMOKE OK: $*"; }

echo "==> Fresh volumes so auth bootstrap prints credentials and catalog seed is clean"
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
docker network rm create-your-pizza_default >/dev/null 2>&1 || true

echo "==> Bringing up auth-db + auth-service (JWKS must be ready before catalog warmup)"
"${COMPOSE[@]}" up -d --build auth-db auth-service

echo "==> Waiting for auth-service health (JWKS)"
for i in $(seq 1 90); do
	if curl -sf "$AUTH_URL/auth/.well-known/jwks.json" >/dev/null 2>&1; then
		break
	fi
	if [[ "$i" -eq 90 ]]; then
		"${COMPOSE[@]}" logs --tail=80 auth-service || true
		fail "auth-service did not become ready"
	fi
	sleep 2
done
pass "auth-service responding"

echo "==> Reading bootstrap admin from logs"
BOOT_LINE=""
for i in $(seq 1 30); do
	BOOT_LINE="$("${COMPOSE[@]}" logs auth-service 2>&1 | grep 'BOOTSTRAP ADMIN' | tail -1 || true)"
	[[ -n "$BOOT_LINE" ]] && break
	sleep 1
done
[[ -n "$BOOT_LINE" ]] || fail "no BOOTSTRAP ADMIN line in auth-service logs"
ADMIN_USER="$(echo "$BOOT_LINE" | sed -n 's/.*username=\([^ ]*\).*/\1/p')"
ADMIN_PASS="$(echo "$BOOT_LINE" | sed -n 's/.*password=\([^ ]*\).*/\1/p')"
[[ -n "$ADMIN_USER" && -n "$ADMIN_PASS" ]] || fail "could not parse bootstrap credentials: $BOOT_LINE"
pass "bootstrap admin username=$ADMIN_USER"

echo "==> POST /auth/login"
LOGIN_BODY="$(jq -n --arg u "$ADMIN_USER" --arg p "$ADMIN_PASS" '{username:$u,password:$p}')"
LOGIN_HTTP="$(curl -s -o /tmp/catalog-smoke-login.json -w '%{http_code}' -X POST "$AUTH_URL/auth/login" \
	-H 'Content-Type: application/json' \
	--data-binary "$LOGIN_BODY")"
[[ "$LOGIN_HTTP" == "200" ]] || fail "login failed HTTP $LOGIN_HTTP body=$(cat /tmp/catalog-smoke-login.json)"
ADMIN_JWT="$(jq -r '.data.accessToken' /tmp/catalog-smoke-login.json)"
[[ "$ADMIN_JWT" != "null" && -n "$ADMIN_JWT" ]] || fail "login missing accessToken"
pass "admin JWT issued"

echo "==> POST /auth/register + approve + token (trusted JWT)"
REG_HTTP="$(curl -s -o /tmp/catalog-smoke-reg.json -w '%{http_code}' -X POST "$AUTH_URL/auth/register" \
	-H 'Content-Type: application/json' \
	-d '{"displayName":"Smoke Partner"}')"
[[ "$REG_HTTP" == "201" ]] || fail "register failed HTTP $REG_HTTP body=$(cat /tmp/catalog-smoke-reg.json)"
USER_ID="$(jq -r '.data.userId' /tmp/catalog-smoke-reg.json)"
APPROVE_HTTP="$(curl -s -o /tmp/catalog-smoke-approve.json -w '%{http_code}' -X POST "$AUTH_URL/auth/users/$USER_ID/approve" \
	-H "Authorization: Bearer $ADMIN_JWT")"
[[ "$APPROVE_HTTP" == "200" ]] || fail "approve failed HTTP $APPROVE_HTTP body=$(cat /tmp/catalog-smoke-approve.json)"
API_KEY="$(jq -r '.data.apiKey' /tmp/catalog-smoke-approve.json)"
API_SECRET="$(jq -r '.data.apiSecret' /tmp/catalog-smoke-approve.json)"
TOKEN_BODY="$(jq -n --arg k "$API_KEY" --arg s "$API_SECRET" '{apiKey:$k,apiSecret:$s}')"
TOKEN_HTTP="$(curl -s -o /tmp/catalog-smoke-token.json -w '%{http_code}' -X POST "$AUTH_URL/auth/token" \
	-H 'Content-Type: application/json' \
	--data-binary "$TOKEN_BODY")"
[[ "$TOKEN_HTTP" == "200" ]] || fail "token exchange failed HTTP $TOKEN_HTTP body=$(cat /tmp/catalog-smoke-token.json)"
TRUSTED_JWT="$(jq -r '.data.accessToken' /tmp/catalog-smoke-token.json)"
[[ -n "$TRUSTED_JWT" && "$TRUSTED_JWT" != "null" ]] || fail "token missing accessToken"
pass "trusted JWT issued"

echo "==> Bringing up catalog-db + redis + catalog-service"
"${COMPOSE[@]}" up -d --build catalog-db redis catalog-service

echo "==> Waiting for catalog-service (unauthenticated GET /api/products → 401)"
for i in $(seq 1 90); do
	UNAUTH_HTTP="$(curl -s -o /tmp/catalog-smoke-unauth.json -w '%{http_code}' "$CATALOG_URL/api/products" || true)"
	if [[ "$UNAUTH_HTTP" == "401" ]]; then
		break
	fi
	if [[ "$i" -eq 90 ]]; then
		"${COMPOSE[@]}" logs --tail=80 catalog-service || true
		fail "catalog-service did not become ready (last HTTP $UNAUTH_HTTP body=$(cat /tmp/catalog-smoke-unauth.json 2>/dev/null || true))"
	fi
	sleep 2
done
pass "unauthenticated GET /api/products → 401"

echo "==> GET /api/products with admin Bearer (envelope + pagination)"
LIST_HTTP="$(curl -s -o /tmp/catalog-smoke-list.json -w '%{http_code}' "$CATALOG_URL/api/products" \
	-H "Authorization: Bearer $ADMIN_JWT")"
[[ "$LIST_HTTP" == "200" ]] || fail "admin list failed HTTP $LIST_HTTP body=$(cat /tmp/catalog-smoke-list.json)"
STATUS="$(jq -r '.status' /tmp/catalog-smoke-list.json)"
[[ "$STATUS" == "200" ]] || fail "expected envelope status 200, got $STATUS"
jq -e '.pagination.current != null and .pagination.next != null and .pagination.total != null' /tmp/catalog-smoke-list.json >/dev/null \
	|| fail "missing pagination sibling: $(cat /tmp/catalog-smoke-list.json)"
jq -e '.data | type == "array"' /tmp/catalog-smoke-list.json >/dev/null \
	|| fail "data is not a flat array: $(cat /tmp/catalog-smoke-list.json)"
pass "admin GET /api/products envelope + pagination"

echo "==> GET /api/products with trusted Bearer"
TRUSTED_HTTP="$(curl -s -o /tmp/catalog-smoke-trusted.json -w '%{http_code}' "$CATALOG_URL/api/products" \
	-H "Authorization: Bearer $TRUSTED_JWT")"
[[ "$TRUSTED_HTTP" == "200" ]] || fail "trusted list failed HTTP $TRUSTED_HTTP body=$(cat /tmp/catalog-smoke-trusted.json)"
pass "trusted GET /api/products → 200"

echo "==> missing/invalid JWT → 401"
INVALID_HTTP="$(curl -s -o /tmp/catalog-smoke-badjwt.json -w '%{http_code}' "$CATALOG_URL/api/products" \
	-H "Authorization: Bearer not-a-jwt")"
[[ "$INVALID_HTTP" == "401" ]] || fail "expected 401 for invalid JWT, got $INVALID_HTTP body=$(cat /tmp/catalog-smoke-badjwt.json)"
pass "invalid JWT → 401"

echo "==> trusted write → 403"
WRITE_HTTP="$(curl -s -o /tmp/catalog-smoke-write.json -w '%{http_code}' -X POST "$CATALOG_URL/api/products" \
	-H "Authorization: Bearer $TRUSTED_JWT" \
	-H "Content-Type: application/json" \
	-d '{"productName":"Nope","productType":"simple","productCategory":"veg","productPrice":1}')"
[[ "$WRITE_HTTP" == "403" ]] || fail "expected 403 for trusted write, got $WRITE_HTTP body=$(cat /tmp/catalog-smoke-write.json)"
pass "trusted POST /api/products → 403"

echo
echo "CATALOG READ-PATH SERVICE-READY SMOKE: PASS"
