#!/usr/bin/env bash
# Build plan §5.1 — Auth service-ready Compose smoke (story 06).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${AUTH_BASE_URL:-http://localhost:8080}"
COMPOSE=(docker compose -f "$ROOT/docker-compose.yml")

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }
pass() { echo "SMOKE OK: $*"; }

echo "==> Fresh auth-db volume so bootstrap prints credentials"
"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
docker network rm create-your-pizza_default >/dev/null 2>&1 || true

echo "==> Bringing up auth-db + auth-service"
"${COMPOSE[@]}" up -d --build auth-db auth-service

echo "==> Waiting for auth-service health (JWKS)"
for i in $(seq 1 90); do
	if curl -sf "$BASE_URL/auth/.well-known/jwks.json" >/dev/null 2>&1; then
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
BOOT_LINE="$("${COMPOSE[@]}" logs auth-service 2>&1 | grep 'BOOTSTRAP ADMIN' | tail -1 || true)"
[[ -n "$BOOT_LINE" ]] || fail "no BOOTSTRAP ADMIN line in auth-service logs"
ADMIN_USER="$(echo "$BOOT_LINE" | sed -n 's/.*username=\([^ ]*\).*/\1/p')"
ADMIN_PASS="$(echo "$BOOT_LINE" | sed -n 's/.*password=\([^ ]*\).*/\1/p')"
[[ -n "$ADMIN_USER" && -n "$ADMIN_PASS" ]] || fail "could not parse bootstrap credentials: $BOOT_LINE"
pass "bootstrap admin username=$ADMIN_USER"

echo "==> POST /auth/login"
LOGIN_BODY="$(jq -n --arg u "$ADMIN_USER" --arg p "$ADMIN_PASS" '{username:$u,password:$p}')"
LOGIN_HTTP="$(curl -s -o /tmp/auth-smoke-login.json -w '%{http_code}' -X POST "$BASE_URL/auth/login" \
	-H 'Content-Type: application/json' \
	--data-binary "$LOGIN_BODY")"
[[ "$LOGIN_HTTP" == "200" ]] || fail "login failed HTTP $LOGIN_HTTP body=$(cat /tmp/auth-smoke-login.json)"
ADMIN_JWT="$(jq -r '.data.accessToken' /tmp/auth-smoke-login.json)"
[[ "$ADMIN_JWT" != "null" && -n "$ADMIN_JWT" ]] || fail "login missing accessToken"
pass "admin JWT issued"

echo "==> POST /auth/register"
REG_HTTP="$(curl -s -o /tmp/auth-smoke-reg.json -w '%{http_code}' -X POST "$BASE_URL/auth/register" \
	-H 'Content-Type: application/json' \
	-d '{"displayName":"Smoke Partner"}')"
[[ "$REG_HTTP" == "201" ]] || fail "register failed HTTP $REG_HTTP body=$(cat /tmp/auth-smoke-reg.json)"
USER_ID="$(jq -r '.data.userId' /tmp/auth-smoke-reg.json)"
STATUS="$(jq -r '.data.status' /tmp/auth-smoke-reg.json)"
[[ "$STATUS" == "PENDING" ]] || fail "expected PENDING, got $STATUS"
pass "registered PENDING userId=$USER_ID"

echo "==> POST /auth/users/{id}/approve"
APPROVE_HTTP="$(curl -s -o /tmp/auth-smoke-approve.json -w '%{http_code}' -X POST "$BASE_URL/auth/users/$USER_ID/approve" \
	-H "Authorization: Bearer $ADMIN_JWT")"
[[ "$APPROVE_HTTP" == "200" ]] || fail "approve failed HTTP $APPROVE_HTTP body=$(cat /tmp/auth-smoke-approve.json)"
API_KEY="$(jq -r '.data.apiKey' /tmp/auth-smoke-approve.json)"
API_SECRET="$(jq -r '.data.apiSecret' /tmp/auth-smoke-approve.json)"
[[ -n "$API_KEY" && "$API_KEY" != "null" ]] || fail "approve missing apiKey"
[[ -n "$API_SECRET" && "$API_SECRET" != "null" ]] || fail "approve missing apiSecret"
pass "approve returned apiKey + apiSecret once"

echo "==> POST /auth/token"
TOKEN_BODY="$(jq -n --arg k "$API_KEY" --arg s "$API_SECRET" '{apiKey:$k,apiSecret:$s}')"
TOKEN_HTTP="$(curl -s -o /tmp/auth-smoke-token.json -w '%{http_code}' -X POST "$BASE_URL/auth/token" \
	-H 'Content-Type: application/json' \
	--data-binary "$TOKEN_BODY")"
[[ "$TOKEN_HTTP" == "200" ]] || fail "token exchange failed HTTP $TOKEN_HTTP body=$(cat /tmp/auth-smoke-token.json)"
TRUSTED_JWT="$(jq -r '.data.accessToken' /tmp/auth-smoke-token.json)"
[[ -n "$TRUSTED_JWT" && "$TRUSTED_JWT" != "null" ]] || fail "token missing accessToken"
echo "$TRUSTED_JWT" | grep -qF "$API_SECRET" && fail "apiSecret leaked into trusted JWT string" || true
pass "trusted JWT issued"

echo "==> GET JWKS and verify both JWTs"
JWKS_JSON="$(curl -sf "$BASE_URL/auth/.well-known/jwks.json")" || fail "JWKS fetch failed"

node --input-type=module - "$JWKS_JSON" "$ADMIN_JWT" "$TRUSTED_JWT" "$API_KEY" "$API_SECRET" <<'NODE'
import { createPublicKey, createVerify } from 'node:crypto';

const [jwksRaw, adminJwt, trustedJwt, apiKey, apiSecret] = process.argv.slice(2);
const jwks = JSON.parse(jwksRaw);

function b64urlToBuf(s) {
  const pad = '='.repeat((4 - (s.length % 4)) % 4);
  return Buffer.from((s + pad).replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

function parseJwt(token) {
  const [h, p, s] = token.split('.');
  if (!h || !p || !s) throw new Error('malformed jwt');
  return {
    header: JSON.parse(b64urlToBuf(h).toString('utf8')),
    payload: JSON.parse(b64urlToBuf(p).toString('utf8')),
    signingInput: `${h}.${p}`,
    signature: b64urlToBuf(s),
  };
}

function verify(token, expect) {
  const { header, payload, signingInput, signature } = parseJwt(token);
  const jwk = jwks.keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new Error(`no jwk for kid=${header.kid}`);
  const key = createPublicKey({ key: jwk, format: 'jwk' });
  const ok = createVerify('RSA-SHA256').update(signingInput).verify(key, signature);
  if (!ok) throw new Error('signature invalid');
  if (payload.iss !== 'create-your-pizza-auth') throw new Error(`bad iss ${payload.iss}`);
  if (!(Array.isArray(payload.aud) ? payload.aud : [payload.aud]).includes('create-your-pizza-catalog')) {
    throw new Error(`bad aud ${payload.aud}`);
  }
  for (const [k, v] of Object.entries(expect)) {
    const actual = payload[k];
    if (Array.isArray(v)) {
      const list = Array.isArray(actual) ? actual : [actual];
      for (const item of v) {
        if (!list.includes(item)) throw new Error(`missing ${k}=${item}`);
      }
    } else if (actual !== v) {
      throw new Error(`${k}: expected ${v}, got ${actual}`);
    }
  }
  const serialized = JSON.stringify(payload);
  if (serialized.includes(apiSecret) || token.includes(apiSecret)) {
    throw new Error('apiSecret present in JWT');
  }
}

verify(adminJwt, {
  roles: ['ADMIN'],
  scope: 'catalog:read catalog:write menu:read',
});
verify(trustedJwt, {
  roles: ['TRUSTED_SYSTEM'],
  scope: 'catalog:read menu:read',
  client_id: apiKey,
});
console.log('JWKS signature + claims verified for admin and trusted JWTs');
NODE

pass "JWKS verified both JWTs (iss/aud/roles/scope; trusted client_id; secret not in JWT)"
echo
echo "AUTH SERVICE-READY SMOKE: PASS"
