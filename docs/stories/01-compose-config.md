# 01 — Compose and config

**Status:** ready  
**Type:** enabler  
**Depends on:** — (may run in parallel with `02-OWNER-maven-initializr.md`)  
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

## Tasks

- [ ] Add root `docker-compose.yml` (`auth-db`, `catalog-db`, `redis`, both app services)
- [ ] Volumes for both DBs and Redis
- [ ] MUST properties with locked defaults; `.properties` not YAML
- [ ] App services may stay stubs until **02** is `DONE-`

## Notes

Graph: first enabler, parallel with **02**. Unblocks **03** and **07** together with **02**.
