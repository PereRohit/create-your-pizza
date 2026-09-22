# Build plan — CreateYourPizza

**Status:** APPROVED — 2026-09-18 (owner HIFL Approve). **Revise APPROVED 2026-09-21:** §5.1 service-ready Compose smoke — Auth service-ready after **06**; Catalog read-path service-ready after **10** (**11–14** still later). Stories under `docs/stories/`; implement one story at a time (graph in §6).

**Upstream:** [Intent](intent.md) (**APPROVED**) · [Spec / PRD](spec.md) (**APPROVED**) · [Design / TRD](design.md) (**APPROVED**)

**Related:** [Project context](project-context.md) · [HIFL playbook](hifl-playbook.md) · [Handoff](handoff.md) · [Stories](stories/README.md)

**Not in this stage:** Application implementation (that is Stage 5 Build). Stories: [stories/README.md](stories/README.md).

---

## 1. Status and scope

This plan turns APPROVED Design + Spec into an ordered Build: stack, Initializr checklists, Maven layout, story order, tests (mock at ports), **service-ready Compose smoke**, Docker, and Definition of Done.

**In scope for Build plan**

- Stack: Spring Boot **4.1.1**, Maven, JAR, Java **26** (Initializr generate as **25**, then pin 26 in both POMs)
- Independent sibling Maven projects under this repo
- Exact Initializr selections + third-party `pom.xml` entries
- Ordered user-story list (files created only after **Approve**)
- Test plan: mock dependent ports (JUnit + Spring Boot Test)
- **Service-ready smoke:** Compose checkpoints at closing stories before `DONE-` (§5.1); catalog read-path smoke may require the auth stack too
- One root `docker-compose.yml`
- Config in `.properties` files

**Out of scope for Build plan**

- Writing story markdown files (playbook: after Approve)
- Implementing Java, Flyway SQL, or Compose YAML
- Creating `AGENTS.md` or springdoc artifacts (Build stories)
- Full Stage 6 Verify evidence pack (still after all stories)

---

## 2. Stack confirmation (locked)

| Item | Choice |
|------|--------|
| Language / build | **Java** + **Maven** |
| Framework | **Spring Boot 4.1.1** |
| Java | Runtime/compile **26**. On start.spring.io choose **25** (26 is not in the dropdown), then set `<java.version>26</java.version>` in **both** `pom.xml` files |
| Packaging | **Jar** |
| Config | `application.properties` (and profile files, e.g. `application-test.properties`) — not YAML |
| Apps | Two **independent** Maven projects (no parent POM) |
| Maven coordinates | **`groupId` `com.createyourpizza`**. Artifacts: **`auth-service`**, **`catalog-service`**. Packages: **`com.createyourpizza.auth`**, **`com.createyourpizza.catalog`**. |
| Auth DB name | **`auth-db`** (v1 engine: PostgreSQL; Flyway in auth-service) |
| Catalog DB name | **`catalog-db`** (v1 engine: PostgreSQL; Flyway in catalog-service) |
| Cache / locks | **Redis** — catalog-service only |
| JWT | **RS256**; catalog **OAuth2 Resource Server** verifies locally from JWKS URL |
| API docs | SpringDoc OpenAPI per service (Initializr) |
| Lombok | **Yes** — both apps |
| Tests | JUnit 5 + Spring Boot Test; **mock ports** (DB, Redis, JWKS HTTP, PDF renderer) |
| Runtime | One **`docker-compose.yml`** at repo root: `auth-db`, `catalog-db`, `redis`, `auth-service`, `catalog-service` |

**JWT identifiers (locked here — Design left them to Build)**

| Claim | Value |
|-------|--------|
| `iss` | `create-your-pizza-auth` |
| `aud` | `create-your-pizza-catalog` |

Catalog rejects tokens that fail `iss` / `aud` / signature / `exp` / required `scope`.

---

## 3. Folder structure and Maven coordinates

Two **sibling** Maven projects. Industry practice for two services in one git repo. One Compose file at the **repo root** is the usual pairing — not harder than separate repos.

```text
create-your-pizza/                          ← git root
  docs/
  docker-compose.yml                        ← whole stack (Build story 01)
  README.md
  AGENTS.md                                 ← after story 15
  auth-service/
    pom.xml                                 ← groupId com.createyourpizza / artifactId auth-service
    src/main/java/com/createyourpizza/auth/
    src/main/resources/application.properties
    src/test/java/com/createyourpizza/auth/
  catalog-service/
    pom.xml                                 ← groupId com.createyourpizza / artifactId catalog-service
    src/main/java/com/createyourpizza/catalog/
    src/main/resources/application.properties
    src/test/java/com/createyourpizza/catalog/
```

On Initializr, set **Package name** explicitly (hyphenated artifact ids otherwise become awkward packages):

| Field | auth-service | catalog-service |
|-------|----------------|-----------------|
| Group | `com.createyourpizza` | `com.createyourpizza` |
| Artifact | `auth-service` | `catalog-service` |
| Name | `auth-service` | `catalog-service` |
| Package name | `com.createyourpizza.auth` | `com.createyourpizza.catalog` |
| Packaging | Jar | Jar |
| Java | **25** | **25** |
| Spring Boot | **4.1.1** | **4.1.1** |

Unzip each zip **into** `auth-service/` and `catalog-service/` (do not nest an extra folder). Coordinates in each `pom.xml`:

`auth-service/pom.xml`:

```xml
<groupId>com.createyourpizza</groupId>
<artifactId>auth-service</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>jar</packaging>
<properties>
  <java.version>26</java.version>
</properties>
```

`catalog-service/pom.xml`:

```xml
<groupId>com.createyourpizza</groupId>
<artifactId>catalog-service</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>jar</packaging>
<properties>
  <java.version>26</java.version>
</properties>
```

Build/run (not difficult):

- `mvn -f auth-service/pom.xml test`
- `mvn -f catalog-service/pom.xml test`
- `docker compose up --build` from `create-your-pizza/`

There is **no** root `pom.xml`. Do not `mvn` at the git root expecting a reactor.

---

## 4. Owner Initializr — exact selections

Do **not** select: Docker Compose Support (root compose is written in-repo), OAuth2 Authorization Server, Spring Session, OAuth2 Resource Server **on auth**.

### 4.1 `auth-service` — select on start.spring.io

| UI name | id |
|---------|-----|
| Spring Web | `web` |
| Spring Security | `security` |
| Spring Data JPA | `data-jpa` |
| PostgreSQL Driver | `postgresql` |
| Flyway Migration | `flyway` |
| Validation | `validation` |
| SpringDoc OpenAPI | `springdoc-openapi` |
| Lombok | `lombok` |

### 4.2 `catalog-service` — select on start.spring.io

| UI name | id |
|---------|-----|
| Spring Web | `web` |
| Spring Security | `security` |
| OAuth2 Resource Server | `oauth2-resource-server` |
| Spring Data JPA | `data-jpa` |
| PostgreSQL Driver | `postgresql` |
| Flyway Migration | `flyway` |
| Spring Data Redis (Access+Driver) | `data-redis` |
| Validation | `validation` |
| SpringDoc OpenAPI | `springdoc-openapi` |
| Lombok | `lombok` |

Catalog Resource Server: JWKS URI in `application.properties` (`AUTH_JWKS_URL` / Spring `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`). That is Design local verify. Auth **issues** JWTs; it is not a resource server.

### 4.3 Third-party libraries — add in that service’s `pom.xml`

Spring Boot parent manages versions for Spring artifacts. Pin `<version>` only when the BOM does not.

| Library | Service | How |
|---------|---------|-----|
| **Nimbus JOSE JWT** | `auth-service` | `<dependency>` `com.nimbusds` / `nimbus-jose-jwt` in `auth-service/pom.xml` (RS256 sign + JWKS JSON). Catalog does **not** add it; Resource Server covers verify. |
| **OpenPDF** | `catalog-service` | `<dependency>` `com.github.librepdf` / `openpdf` in `catalog-service/pom.xml` with an explicit `<version>` (not in the Boot BOM). |

BCrypt comes with Spring Security — no extra dependency.

Install Lombok in the IDE (annotation processing) so generated getters compile.

### 4.4 Agent wait

Stories **01** (Compose) and **02** (owner Initializr) have no dependency on each other. Java stories **03** and **07** wait until **02** is `DONE-`.

---

## 5. Tests — mock dependents

Story tests use JUnit + Spring Boot Test and **mocks/fakes** at ports. They must not require Docker. Real PostgreSQL and Redis are for **`docker compose up`**, **§5.1 service-ready smoke**, and Stage 6 Verify.

**How:** depend on small ports (interfaces), not on Postgres or Redis types, in application code. Tests supply fakes/mocks.

| Port (illustrative) | Real adapter | In tests |
|---------------------|--------------|----------|
| User / product / option persistence | JPA repositories | Mockito (or in-memory fake) |
| Catalog write / PDF locks | Redis `SET NX EX` adapter | Fake lock map (in-process) |
| Catalog JSON cache | Redis adapter | Fake map + TTL clock if needed |
| JWKS fetch | HTTP client to auth | Stub JWKS JSON / mock `JwtDecoder` |
| PDF bytes | OpenPDF adapter | Fake renderer returning known bytes |

Controllers and use-cases stay testable without a database. Every coding story still needs **>80% LoC** of that story’s new/changed code **and** full behaviour coverage of its ACs via mocks/fakes above.

### 5.1 Service-ready Compose smoke (Build checkpoints)

**Purpose:** catch environment and cross-endpoint bugs at **service-ready checkpoints** — auth service-ready after **06**; catalog **read-path** service-ready after **10** (stories **11–14** still later) — without waiting for Stage 6 Verify or running Compose on every story.

**Rule:** on each §5.1 closing story (**06** / **10**), after acceptance criteria and the playbook **candid review** of that story’s code, run the smoke below against **`docker compose up`** from the repo root as the **last task before** renaming to `DONE-`. Record pass/fail in that closing story’s notes (or handoff). If smoke fails, **do not** rename to `DONE-`; fix and re-run until it passes.

- **Auth smoke (story 06, last task before `DONE-`):** required before treating auth as service-ready. Catalog schema (**07**) may still proceed in parallel per §6. **08** must not start until **06** is `DONE-` (which includes this smoke), in addition to graph deps **04** + **07**.
- **Catalog read-path smoke (story 10, last task before `DONE-`):** required before treating catalog as **Catalog read-path service-ready**. **11** must not start until **10** is `DONE-` (which includes this smoke). PDF path (**12→13/14**) may still follow the §6 graph from **09** without waiting on **10**.

| Checkpoint | When (last task before closing-story `DONE-`) | Compose smoke must prove |
|------------|-----------------------------------------------|---------------------------|
| **Auth service-ready** | On **06**, after ACs + candid review of code | `auth-service` + `auth-db` up; bootstrap admin credentials in logs (or known seeded admin); `POST /auth/login` → admin JWT; `POST /auth/register` → PENDING; `POST /auth/users/{id}/approve` (Bearer admin) → api key + secret **once**; `POST /auth/token` → trusted JWT; `GET /auth/.well-known/jwks.json` verifies both JWTs (signature + locked `iss`/`aud`/`roles`/`scope`; trusted has `client_id`; secret **not** in JWT) |
| **Catalog read-path service-ready** | On **10**, after ACs + candid review of code | `catalog-service` + `catalog-db` (+ Redis if already wired) and **auth** stack up; admin login (or trusted token) from auth; `GET /api/products` with Bearer succeeds (envelope + pagination); missing/invalid JWT → **401**; wrong scope / trusted write if exercised → **403** as Design requires |

**Not required at these checkpoints:** full PDF/lock matrix, OpenAPI polish (**15**), or the complete Spec acceptance pack — those remain Stage 6 Verify (and later stories’ mocked tests).

**How to run:** shell/`curl` (or a small script under `scripts/` added in the closing story) against Compose-published ports. Prefer the same env vars as root Compose. Fail the checkpoint on any step failure; do **not** rename the closing story to `DONE-` until smoke passes; fix and re-run (do not ignore).

---

## 6. Story order (create files after Approve)

Filenames under `docs/stories/`. Implement **one story at a time** on a **dedicated git branch** `feat/<story-id>-<max-5-word-summary>` that contains **only** that story’s changes ([playbook](hifl-playbook.md)). Record the in-progress file in [handoff.md](handoff.md). Run the playbook **candid review loop** before renaming to `DONE-` (story graph is the scope fence). Every **coding** story: **>80% LoC** of that story’s new/changed code **and** full behaviour tests of its acceptance criteria (via mocks/fakes above).

An arrow **A → B** means **B starts only after A is `DONE-`**. **01** and **02** may proceed in parallel. Auth (**03–06**) needs **01** and **02**. Catalog schema (**07**) needs **01** and **02**. **06** and **10** include §5.1 service-ready smoke as their **last task before `DONE-`**, so dependents of those stories inherit the smoke gate: **08** waits for **04**, **07**, and **06** `DONE-`; **11** waits for **10** `DONE-`. After **09**, queries/cache (**10→11**) and PDF (**12→13** and **12→14**) may proceed independently. **15** waits for **06**, **13**, and **14**. Numeric order **01 through 15** is a valid total order.

```mermaid
flowchart TB
  s01["01 compose-config"]
  s02["02 OWNER maven-initializr"]

  subgraph auth ["auth-service"]
    s03["03 auth-schema-bootstrap"]
    s04["04 auth-jwks-jwt"]
    s05["05 login-register-token"]
    s06["06 auth-admin-users plus smoke"]
    s03 --> s04 --> s05 --> s06
  end

  subgraph catalog ["catalog-service"]
    s07["07 catalog-schema-seed"]
    s08["08 catalog-jwt-jwks"]
    s09["09 catalog-writes"]
    s10["10 catalog-queries plus smoke"]
    s11["11 catalog-redis-cache"]
    s12["12 pdf-job-locks"]
    s13["13 public-pdf"]
    s14["14 test-pdf-trigger"]
    s07 --> s08 --> s09
    s09 --> s10 --> s11
    s09 --> s12
    s07 --> s12
    s12 --> s13
    s12 --> s14
  end

  s15["15 openapi-agents"]

  s01 --> s03
  s02 --> s03
  s01 --> s07
  s02 --> s07
  s04 --> s08
  s06 --> s08
  s06 --> s15
  s13 --> s15
  s14 --> s15
```

| File | Depends on | Outcome |
|------|------------|---------|
| `01-compose-config.md` | — | Root Compose: `auth-db`, `catalog-db`, `redis`, both apps (stubs OK until **02**); volumes; env; **MUST** properties |
| `02-OWNER-maven-initializr.md` | — | **Owner** Initializr both sibling Maven apps; pin Java 26; coordinates per §3–4 |
| `03-auth-schema-bootstrap.md` | 01, 02 | Flyway auth tables; first admin stdout if zero admins |
| `04-auth-jwks-jwt.md` | 03 | RS256; JWKS; issue JWT; Nimbus **task** |
| `05-auth-login-register-token.md` | 04 | login, register PENDING, `/auth/token` |
| `06-auth-admin-users.md` | 05 | admins, paginated users, approve/deny/revoke, no self-delete; **§5.1 auth smoke last task before `DONE-`** |
| `07-catalog-schema-seed.md` | 01, 02 | Flyway catalog + seed; dirty=true |
| `08-catalog-jwt-jwks.md` | 04, 07, **06** (`DONE-` includes auth smoke) | Resource Server + JWKS URL |
| `09-catalog-writes.md` | 08 | Admin CRUD; dirty; write lock; 503 on PDF lock |
| `10-catalog-queries.md` | 09 | List/get filters pagination pizza-spec; **§5.1 catalog read-path smoke last task before `DONE-`** |
| `11-catalog-redis-cache.md` | **10** (`DONE-` includes catalog read-path smoke) | Redis-first catalog keys TTL 3m |
| `12-pdf-job-locks.md` | 09, 07 | PDF job; locks; OpenPDF **task** |
| `13-public-pdf.md` | 12 | Public raw PDF GET + version |
| `14-test-pdf-trigger.md` | 12 | Test-profile generate route |
| `15-openapi-agents.md` | 06, 13, 14 | springdoc + `AGENTS.md` |

Stories **03–06** are auth-service; **07–14** catalog-service plus **15** both. Do not start **09** before **08**. Do not start a story until every incoming arrow in the graph is `DONE-` (smoke is folded into **06** / **10** `DONE-`, not a separate node).

---

## 7. Implementation notes (do not rediscover)

- **No reinventing the wheel:** Prefer Spring Boot / Security / Data / Hibernate / Lombok / JDK and Build-plan libraries over hand-written boilerplate. Prefer annotations and injection over custom constructors, getters, id/timestamp callbacks, and wrappers that duplicate framework behaviour.
- Envelope + pagination as Spec/Design; docs omit empty keys — **coding may still emit** success `error: ""`.
- DTOs invented at coding time; wire JSON locked. Lombok is allowed on DTOs/entities.
- Principal type from **URL** (`/auth/register` vs `/auth/admins` vs bootstrap).
- Catalog **never** opens `auth-db`.
- Redis keys and lock algorithm: Design §6–7 exactly (`finally` DEL + TTL).
- Option kinds only `CRUST_SIZE` \| `CRUST_TYPE` \| `TOPPING`.
- Consumer types: `simple` / `combo` / `pizza-base` / `pizza-spec`.
- `maxPrice` is **strictly less than** on product **and** option prices.
- `category` filter excludes pizza-spec unless `type=pizza-spec` (then ignore category).
- Default list sort: `created_at` ascending across the union; filter wins.
- Outstanding JWTs valid until `exp` after revoke.

**MUST properties** (`application.properties`)

| Property | Default |
|----------|---------|
| `app.pdf.interval` | 5 minutes |
| `app.cache.catalog-ttl` | 3 minutes |
| `app.jwt.ttl` | 30 minutes |
| `app.lock.pdf-ttl` | 120 seconds |
| `app.lock.write-ttl` | 30 seconds |

Plus datasource URLs, Redis URL, JWKS URL (catalog), `iss`/`aud` matching §2.

**Postgres connection pool (HikariCP)** — Spring Boot auto-configures Hikari as the JDBC pool; both services set explicit `spring.datasource.hikari.*` (env-overridable). No custom `DataSource` `@Bean`. Pool lives in each JVM, not in the Postgres container.

| Property | Default | Notes |
|----------|---------|--------|
| `spring.datasource.hikari.pool-name` | `auth-pool` / `catalog-pool` | Per service |
| `spring.datasource.hikari.maximum-pool-size` | `10` | Override via `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` |
| `spring.datasource.hikari.minimum-idle` | `2` | Override via `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` |
| `spring.datasource.hikari.connection-timeout` | `30000` (ms) | |
| `spring.datasource.hikari.idle-timeout` | `600000` (ms) | |
| `spring.datasource.hikari.max-lifetime` | `1800000` (ms) | |

Compose may set the same overrides on app services (auth-service already does for max pool / min idle). Keep sum of all clients’ `maximum-pool-size` under Postgres `max_connections`.

---

## 8. Test plan (behaviour; mocks OK)

Cover Design §9 inside the story that introduces the behaviour.

| Area | Must prove |
|------|------------|
| Bootstrap | Admin created only when zero admins; credentials on stdout; second start does not mint another |
| Trusted | Register PENDING; `/auth/token` fails until approve; secret once on approve; deny; revoke blocks new tokens |
| Same reads | Admin login JWT and trusted token JWT both `GET /api/products` |
| Writes | Trusted 403 on catalog writes; Admin CRUD; cannot DELETE self (403) |
| Users list | `GET /auth/users` paginated like catalog |
| Listing | `type=pizza-spec` all options; untyped list can include them; filters; size 10; max 100 clamp; flat `data`; `pagination.next=-1` |
| JWT path | Catalog uses JWKS HTTP / Resource Server only |
| PDF | **vN**; **options available** when flag true; pizza-spec own space; latest Redis; `?version=` historical; unknown 404; version **not** bumped on product write |
| Locks | Write during PDF lock → 503; job **skips** if write lock; dirty stays true (fake lock is enough) |
| Cache | Catalog cache TTL from config |
| Options | Per-row `price` on list and PDF |
| Test trigger | Profile-gated; same skip/lock rules |

---

## 9. Definition of Done (Build, after stories)

- [ ] All `docs/stories/` coding stories `DONE-` with tests as required, each after the playbook candid review loop
- [ ] **§5.1** auth service-ready smoke passed as last task of **06** (before `DONE-`); Catalog read-path service-ready smoke passed as last task of **10** (before `DONE-`)

- [ ] `docker compose up` from repo root brings `auth-db`, `catalog-db`, `redis`, both apps
- [ ] First admin visible in **auth-service logs**
- [ ] Sample catalog + options in `catalog-db`; first PDF job can produce **v1**
- [ ] Swagger UI per service; OpenAPI importable
- [ ] `AGENTS.md` at repo root
- [ ] No `/auth/validate`; no shared database; no `system_status`

Then Stage 6: draft [docs/verify.md](verify.md).

---

## 10. What NOT to do

- Do not git-commit unless the owner confirms
- Do not scaffold Java before story **02** is `DONE-` (owner Initializr)
- Do not add a parent POM
- Do not put two stories on one git branch; use `feat/<story-id>-<max-5-word-summary>`
- Do not rename a story `DONE-` before the playbook candid review loop; do not treat later stories as review findings
- Do not reinvent Spring, Hibernate, Lombok, or Build-plan library features as hand-written boilerplate when a dependency or annotation already does the job
- Do not select Docker Compose Support on Initializr
- Do not implement customer register/login
- Do not put API secrets in JWT
- Do not share one database across auth and catalog
- Do not call `/auth/validate` or read `auth-db` from catalog
- Do not add JWT denylist
- Do not derive combo price from simples
- Do not queue skipped PDF runs
- Do not increment PDF version on admin writes
- Do not skip §5.1 auth service-ready smoke on **06** or Catalog read-path service-ready smoke on **10** (last task before `DONE-`), and do not require Compose for every story’s `mvn test`


---

## 11. Gate

Build plan is **APPROVED** (2026-09-18). **Revise APPROVED 2026-09-21** — §5.1 Auth service-ready (after **06**) and Catalog read-path service-ready (after **10**).

**Build:** follow [stories](stories/README.md) and the §6 graph (including §5.1 smokes folded into **06** / **10** `DONE-`). **01** and **02** may proceed in parallel. **02** is the owner Initializr enabler.
