# 01 — Compose and config

**Status:** ready  
**Depends on:** —  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an operator, I want one root Docker Compose file and locked config properties so that the whole stack (`auth-db`, `catalog-db`, `redis`, both apps) comes up locally with the required defaults.

## Acceptance criteria

- [ ] Repo root `docker-compose.yml` defines services: `auth-db`, `catalog-db`, `redis`, `auth-service`, `catalog-service`
- [ ] Each database has a volume; Redis has a volume
- [ ] Apps may be stubs until Initializr trees exist; Compose still names both app services
- [ ] Env/config includes datasource URLs, Redis URL, catalog JWKS URL placeholder
- [ ] These properties exist with defaults: `app.pdf.interval` = 5 minutes, `app.cache.catalog-ttl` = 3 minutes, `app.jwt.ttl` = 30 minutes, `app.lock.pdf-ttl` = 120 seconds, `app.lock.write-ttl` = 30 seconds
- [ ] Config is `.properties` files, not YAML application config
- [ ] JWT `iss` = `create-your-pizza-auth`, `aud` = `create-your-pizza-catalog` documented in properties or constants notes

## Tests (if coding)

- Environment/setup story: no Java behaviour tests required until app stubs exist
- If any Compose helper scripts are added, cover their behaviour

## Notes

Graph: first node. Unblocks **02** (needs Initializr auth) and **06** (needs Initializr catalog).
