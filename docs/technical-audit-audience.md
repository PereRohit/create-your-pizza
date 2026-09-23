# Technical audit audience

**Date:** 2026-09-23  
**Purpose:** Triage the Java / Maven technical audit (50 findings) against this repo’s locked engineering decisions. Decide what is worth fixing, what to defer, and what to ignore. **No code was changed in this session.**

**Participants**

| Role | Who |
|------|-----|
| Chair | Audit author (prior Java/Maven review of both sibling modules) |
| Reviewer | Project-context sub-agent with the full repo, Design / Build-plan / AGENTS.md constraints, and the live sources |

**Rules used in the room**

- Textbook Spring purity that contradicts a locked decision (no parent POM, sibling independence, Jackson 2 envelopes on Boot 4, stdout first-admin, profile-gated PDF trigger, H2 story tests) is **IGNORE**.
- Style or naming that forces a DB migration or OpenAPI/wire break for no runtime gain is **IGNORE**.
- **FIX** only if there is genuine risk: security, correctness, production-JAR contamination, a classpath pin that is on a published CVE range, or a fail-open authz default.
- **DEFER** for real hygiene that is not urgent on a two-module local v1.
- CVE claims must name an advisory or be labelled as a class of bug with no CVE looked up.

**Outcome after two rounds:** **FIX 2 · DEFER 11 · IGNORE 45.** Nothing implemented.

---

## Conversation 1 — full finding triage

Chair presented every audit ID. Reviewer read the live code and locked docs, then answered per ID.

| Issue | Points talked about | Decision |
|-------|---------------------|----------|
| **H1** Dual Jackson 2 + 3 on the compile classpath | Chair: Boot 4.1.1 already brings `tools.jackson`; both POMs pin `jackson-databind` 2.21.1; `CatalogQueryService` news its own Jackson 2 mapper. Reviewer: POM comments lock Jackson 2 for envelopes / Redis / security JSON because Boot 4 MVC is Jackson 3; mappers are isolated; no `activateDefaultTyping`. Removing Jackson 2 would be a product rewrite, not a hygiene fix. | **IGNORE** — locked dual-stack. Version pin is a separate issue (M3). |
| **H2** Service layer depends on `web.dto` | Chair: `AuthService` / catalog services return web types. Reviewer: Design left DTO shape to coding; there is no shared kernel module; extracting a domain DTO layer is churn with no runtime gain in a two-module v1. | **IGNORE** |
| **H3** Service exceptions extend `ResponseStatusException` | Chair: HTTP status baked into `*.service`. Reviewer: handlers already wrap them in `ApiEnvelope`; splitting a domain exception tree is later cleanup, not a correctness bug. | **IGNORE** |
| **H4** Public list API is `List<Object>` | Chair: no sealed type; cache deserializes by `productType` string. Reviewer: Design §3.4 is a flat union of products + pizza-spec on one list; `List<Object>` is an honest model of that wire. | **IGNORE** |
| **H5** `TestPdfTriggerController` in `src/main`, `@Profile({"test","dev"})`; security always `permitAll`s the path | Chair: production JAR contamination; unauthenticated generate. Reviewer: Design §5.6 requires this endpoint, no auth, test/dev only; Compose does not set `spring.profiles.active`; `TestPdfTriggerAbsentOnDefaultProfileTest` asserts 404 on default. The class must live in main so `dev`/`test` can see it. A `permitAll` matcher with no bean is a 404, not an open generator. Fail-open is M27, not H5. | **IGNORE** — do not put `dev` on Compose. |
| **H6** No SLF4J; `AdminBootstrap` prints credentials to stdout | Chair: no logger in 153 Java files; secret on stdout. Reviewer: Design / AGENTS.md lock “print first admin to stdout; read Compose logs.” SLF4J does not change the secret-in-logs fact. | **IGNORE** — treat auth-service logs as secret. |
| **H7** `ProductType` lowercase constants persisted as STRING | Chair: violates Java enum convention; rename is a migration. Reviewer: DB `product_type` is `simple` \| `combo` \| `pizza` by Design; `@Enumerated(STRING)` matches the column. | **IGNORE** |
| **M1** No aggregator parent / `dependencyManagement` | Chair: duplicated pins will drift. Reviewer: Build plan / AGENTS.md lock **no parent POM**, independent siblings, `mvn -f`. A reactor POM is the finding the repo forbade. | **IGNORE** |
| **M2** Duplicated compiler / Lombok plugin block | Chair: ~35 identical plugin lines. Reviewer: sibling independence; dedup requires the parent that is forbidden. | **IGNORE** |
| **M3** Hardcoded versions vs Boot BOM | Chair: jackson 2.21.1 vs BOM 2.21.5; nimbus / openpdf / springdoc also pinned. Reviewer: nimbus / openpdf / springdoc are **not** in the Boot BOM and their pins are fine. The **only** bad pin is Jackson 2.21.1 overriding the patched Jackson 2 BOM (CVE-2026-54512 family). Smallest fix: bump `jackson-databind` and `jackson-datatype-jsr310` to **2.21.5** (or drop `<version>`). Not an incident — no default typing — but the pin is wrong. | **FIX** — Jackson 2 pin only. |
| **M4** No enforcer / checkstyle / PMD / SpotBugs / JaCoCo / Spotless | Chair: no quality plugins. Reviewer: v1 Definition of Done is stories + Compose smoke, not a quality-gate suite. | **DEFER** — after CI exists and someone will read the reports. |
| **M5** No CI workflow | Chair: no `.github/workflows`. Reviewer: process gap, not a code defect. | **DEFER** |
| **M6** Docker `-DskipTests` | Chair: image build can ship a red suite. Reviewer: story tests are host `mvn test`, not the image; normal image practice. Becomes a hole only while CI is still absent. | **DEFER** — pair with M5. |
| **M7** catalog-service missing `.dockerignore` | Chair: auth has one; catalog `COPY . .` sends `target/` and IDE junk. Reviewer: `mvn package` still rebuilds the JAR; this is context size, not a dirty image. | **DEFER** — copy auth’s file when Docker is next touched. |
| **M8** Docker no Maven dependency-layer cache | Chair: full copy then `mvnw package`. Reviewer: rebuild time only. | **DEFER** |
| **M9** Two Maven wrappers | Chair: drift risk. Reviewer: each sibling is a complete Initializr app; required by no-parent. | **IGNORE** |
| **M10** Empty Initializr POM metadata | Chair: empty `<name/>`, `<scm/>`, `<licenses/>`. Reviewer: cosmetic leftover. | **IGNORE** |
| **M11** Duplicate `ApiEnvelope` + `Pagination` | Chair: byte-identical copies. Reviewer: no shared module is a locked product decision. | **IGNORE** |
| **M12** Duplicate exception handlers; validation drops field errors | Chair: near-copies; no field-level errors. Reviewer: duplication = siblings; envelope is `{status,message,error}` with no `errors[]` in Spec/OpenAPI (same as L11). | **IGNORE** |
| **M13** `writeEnvelope` + static `ObjectMapper` copied three times | Chair: auth `SecurityConfig`, `AdminJwtAuthenticationFilter`, catalog `SecurityConfig`. Reviewer: boilerplate, not a v1 risk. | **IGNORE** |
| **M14** `OptionEntity` names the JPA stereotype | Chair: sibling is `Product`, not `ProductEntity`. Reviewer: table is `option_entities` (Design); rename is import churn. | **IGNORE** |
| **M15** Inconsistent enum / persistence styles | Chair: lowercase `ProductType`, converter `ProductCategory`, UPPER `OptionKind` / `UserRole`. Reviewer: each style matches its column; unifying forces converters or migrations. | **IGNORE** |
| **M16** Mixed DTO styles | Chair: records vs mutable beans vs `@Value @Builder`. Reviewer: writes need setters for validation; reads are immutable. Style only. | **IGNORE** |
| **M17** Write DTOs use `String` instead of enums | Chair: `productType` / `productCategory` are strings. Reviewer: wire values include `pizza-base` / `pizza-spec` / `non-veg`, which are **not** `ProductType` constants; services already parse. Enum fields would need custom deserializers or an OpenAPI break. | **IGNORE** |
| **M18** `OptionResponse` reuses `product*` field names | Chair: `productId` on an option row. Reviewer: Design §3.4 requires pizza-spec to use those names. | **IGNORE** — locked wire. |
| **M19** `CatalogResponseMapper` queries `ComboItemRepository` (N+1) | Chair: mapper + persistence; list can N+1. Reviewer: real N+1; seed has **one** combo; irrelevant at v1 size. | **DEFER** — fetch-join / `@EntityGraph` if combo count grows. |
| **M20** `JwksController` injects a repository; returns raw `Map` | Chair: no service; untyped JWKS. Reviewer: Design JWKS is `{ "keys": [ public JWK objects ] }`; `VerificationKey.getPublicJwk()` is already that map. | **IGNORE** |
| **M21** `CatalogSeedExpectations` in main sources | Chair: test contract in the fat JAR. Reviewer: only referenced from tests; not a secret or an endpoint. | **DEFER** — move under `src/test` when convenient. |
| **M22** `@ConfigurationPropertiesScan` only on auth | Chair: inconsistent Boot style. Reviewer: catalog binds via `@EnableConfigurationProperties` and a `@Bean` factory; that is the working pattern, not a missing scan. | **IGNORE** |
| **M23** Lock holder ignored / Redis `release` is blind `DELETE` | Chair: interface takes `holder`; in-memory ignores it; Redis `DEL`s without compare. Reviewer: Design §6 is `SET NX EX` + `DEL` in `finally` + TTL; `release(String key)` has no holder by contract. Compare-and-delete is a different lock. | **IGNORE** |
| **M24** `CatalogBusyException` vs `ResponseStatusException` split | Chair: inconsistent taxonomy. Reviewer: busy needs **503 + `Retry-After: 60` + locked copy**; RSE handlers cannot add that header cleanly. | **IGNORE** — intentional. |
| **M25** No slice tests; Flyway disabled in `mvn test` | Chair: no `@WebMvcTest` / `@DataJpaTest`; H2 `create-drop` never runs V1/V2. Reviewer: Build plan §5 forbids Docker in story tests; Compose smoke **is** the real-DDL gate. The H2 ≠ Postgres gap is real and accepted. | **DEFER** — Testcontainers only after that rule and CI change. |
| **M26** `CatalogQueryService` private `ObjectMapper` | Chair: sidesteps Spring’s mapper. Reviewer: must be Jackson 2 for cache JSON; Boot HTTP mapper is Jackson 3. Same locked dual-stack as H1. | **IGNORE** |
| **M27** `anyRequest().permitAll()`; catalog always permits `/test/pdf/generate` | Chair: fail-open for future routes; test path always permitted. Reviewer: the test-path matcher is Design-locked (see H5). The **real** issue is fail-open: a new `/internal/**` controller would ship public. No actuator today. Smallest fix: `anyRequest().denyAll()` in both chains; keep existing public matchers and the test-path matcher. | **FIX** — fail-open only. Leave the Design-locked test path. |
| **M28** Catalog registers the same security entry point twice | Chair: oauth2 pair and `exceptionHandling` pair. Reviewer: resource-server paths need the oauth2 pair; the outer pair is leftover, not a bypass. | **IGNORE** |
| **M29** `0.0.1-SNAPSHOT` with no version strategy | Chair: weak for releases. Reviewer: Build plan locks the Initializr snapshot. | **IGNORE** |
| **M30** Lombok `annotationProcessorPaths` omit `<version>` | Chair: brittle. Reviewer: Boot parent manages Lombok; this is the Initializr pattern and it compiles. | **IGNORE** |
| **M31** JDK 26 not enforced via Enforcer | Chair: no `requireJavaVersion`. Reviewer: `<java.version>26</java.version>` already steers the compiler; Enforcer is part of M4 later. | **DEFER** — with M4. |
| **M32** `TrustedClientCredentials` EAGER `OneToOne` | Chair: common JPA flag. Reviewer: token exchange always needs `credentials.getUser()`; 1:1 unique `user_id`. LAZY would add a query that always runs. | **IGNORE** |
| **M33** `config` depends on `web.ApiEnvelope` | Chair: inverted layer. Reviewer: the filter / entry-point **is** the HTTP boundary. | **IGNORE** |
| **L1** `HELP.md` leftovers | Chair: Initializr Getting Started in both modules. Reviewer: noise. | **IGNORE** |
| **L2** `.gitignore` Gradle + unused `.jacoco` | Chair: Maven-only repo. Reviewer: harmless Initializr gitignore. | **IGNORE** |
| **L3** No `.editorconfig` / `package-info` | Chair: style scaffolding missing. Reviewer: not a defect. | **IGNORE** |
| **L4** Minimal Javadoc | Chair: public types undocumented. Reviewer: useful comments already sit on ports; not a defect. | **IGNORE** |
| **L5** `ComboItemId` `Serializable` without `serialVersionUID` | Chair: textbook warning. Reviewer: JPA composite key, not a Java serialization wire. | **IGNORE** |
| **L6** `Product.optionsEnabled` `Boolean` vs `active` `boolean` | Chair: inconsistent nullability. Reviewer: Design: `options_enabled` is null/false for simple/combo; `active` is required. Wrapper is the domain. | **IGNORE** |
| **L7** `AdminBootstrap` `@AllArgsConstructor` | Chair: rest of code uses `@RequiredArgsConstructor`. Reviewer: both fields are `final`; same bytecode. | **IGNORE** |
| **L8** Controllers import nested service records | Chair: `UserListPage` / `CatalogListPage`. Reviewer: fine for v1; promoting them is H2 in reverse. | **IGNORE** |
| **L9** JWT `Date` vs `Instant` | Chair: mixed temporal APIs. Reviewer: Nimbus `JWTClaimsSet` is `Date`; `Date.from(now)` is the correct adapter. | **IGNORE** |
| **L10** `Collectors.toList()` vs `stream().toList()` | Chair: one pre-16 call site. Reviewer: style. | **IGNORE** |
| **L11** Validation handler drops field details | Chair: `BindingResult` unused. Reviewer: locked envelope has no field map; adding one is an OpenAPI change. | **IGNORE** |
| **L12** Inconsistent test class naming | Chair: `*Test` vs `*IntegrationTest`. Reviewer: does not change coverage. | **IGNORE** |
| **L13** `TestPdfTriggerControllerTest` stubs with ten nulls | Chair: anonymous subclass of `@RequiredArgsConstructor` type. Reviewer: ugly; behavior is covered by web/profile tests. | **IGNORE** |
| **L14** `CachingRemoteJwkSource` in `config` | Chair: belongs under `security` / `jwt`. Reviewer: it **is** the Resource Server JWKS adapter. | **IGNORE** |
| **L15** `SigningKeyService` mixed responsibilities | Chair: runner + keygen + persist + getter. Reviewer: Design: private key is process-only; fine for v1 single-key boot. | **DEFER** — split when rotation lands. |
| **L16** `spring-boot-maven-plugin` has no local config | Chair: no layers / `mainClass`. Reviewer: parent defaults are enough for `java -jar`. | **IGNORE** |
| **L17** No `.mvn/jvm.config` | Chair: no wrapper JVM flags. Reviewer: optional developer convenience. | **IGNORE** |
| **L18** Default JDBC credentials in `application.properties` | Chair: `auth`/`auth`, `catalog`/`catalog` committed. Reviewer: env-overridable; Compose publishes 5432/5433 with the same pair; Redis 6379 has **no AUTH**. Correct for local v1. Not acceptable as a shared-host / prod compose. | **DEFER** — when a non-local environment exists. |

**Conversation 1 challenges (reviewer → chair)**

| Point | Reviewer argument | Accepted? |
|-------|-------------------|-----------|
| H1 / M26 over-rated | Dual Jackson is a written POM comment, not a conflict waiting to explode. | Yes — H1/M26 stay IGNORE. |
| H5 over-rated as a production hole | Default profile is proven 404; Compose never activates `dev`. | Yes — H5 IGNORE. |
| M1 / M2 / M9 / M11 over-rated | Restatements of “no parent POM / sibling independence.” | Yes — all IGNORE. |
| M3 under-rated if filed as BOM hygiene | 2.21.1 is on a published CVE range **and** overrides the BOM. | Yes — M3 FIX. |
| M27 under-rated if bundled with the test trigger | Fail-open `anyRequest().permitAll()` is the only authz landmine worth changing this week. | Yes — M27 FIX. |
| M25 over-rated as an `mvn test` bug | Build plan forbids Docker in story tests; Compose smoke is the DDL gate. | Yes — M25 DEFER. |
| M23 over-rated | Blind `DEL` + unused holder **is** Design §6. | Yes — M23 IGNORE. |
| L18 / Redis-without-AUTH under-rated if filed as style | Fine locally; first thing to change for a shared-host deploy. | Yes — L18 DEFER, not FIX now. |

---

## Conversation 2 — CVE pass and challenges

Chair brought independent advisory lookups (2026-09-23) and six challenge questions. Reviewer confirmed or pushed back. Chair also grepped the repo: no `activateDefaultTyping` / `@JsonTypeInfo`; no `spring-boot-admin` / actuator; catalog production PDF code does not call `XmlParser` / `HtmlParser` (`PdfReader` is test-only on bytes the app just generated).

### Challenge questions

| Q | Chair asked | Reviewer answered | Decision |
|---|-------------|-------------------|----------|
| A | Does `denyAll()` on `anyRequest()` break `/error`, JWKS, login, register, token, `GET /api/menu.pdf`, or error dispatch? | Those routes already have explicit `permitAll`. Test-trigger matcher stays. Only possible hitch: unhandled exception dispatch to `/error` can become 403; add `requestMatchers("/error").permitAll()` if a smoke shows 403-on-500. Unknown paths become 403 instead of 404 — a side effect, not a reason to drop the fix. | **M27 stays FIX.** |
| B | Confirm we are **not** removing the Design-locked PDF trigger; only closing fail-open. | Confirmed. H5 IGNORE. Leave `POST /test/pdf/generate` `permitAll`. | **H5 IGNORE, M27 FIX (fail-open only).** |
| C | Promote M7 (catalog `.dockerignore`) to FIX as a 5-line copy? | Push back. Context size, not a dirty JAR. Same bucket as `HELP.md`. Do it as a chore when Docker is next edited. | **M7 stays DEFER.** |
| D | Bump Jackson 2.21.1 → 2.21.5 only. Do **not** collapse H1 into “remove Jackson 2.” | Confirmed. Boot parent BOM is `jackson-2-bom.version` **2.21.5** and `jackson-bom.version` **3.1.5**. The explicit 2.21.1 pin **overrides** the patched Jackson 2 line. Jackson 3 is already patched. | **H1 IGNORE · M3 FIX (pin only).** |
| E | Keep L18 / Redis-without-AUTH as DEFER; do not change Compose now. | Confirmed. Local Compose is the intended v1 runtime. | **L18 stays DEFER.** |
| F | Any other flip after the CVE pass? | No. Boot 4.1.1 is not itself CVE-vulnerable here (custom `SecurityConfig`, no actuator). Admin-Server CVE does not apply. | **Counts unchanged.** |

### Decision confirmations (challenged IDs only)

| Issue | Before | After | Points talked about |
|-------|--------|-------|---------------------|
| H1 | IGNORE | IGNORE | Repo grep: no default typing / `@JsonTypeInfo`. Dual Jackson stays locked. Do not remove Jackson 2. |
| M3 | FIX | FIX | 2.21.1 overrides BOM 2.21.5 and sits on CVE-2026-54512. Bump databind + jsr310 only. |
| H5 | IGNORE | IGNORE | Design-locked trigger. Default/Compose still 404. |
| M27 | FIX | FIX | Fail-open only. Public matchers already cover the live API. Optional `/error` matcher if smoke requires it. |
| M7 | DEFER | DEFER | Five-line dockerignore is a chore, not the FIX bar. |
| L18 | DEFER | DEFER | Not asking to add Redis AUTH or change published ports in this pass. |

No other IDs flipped.

---

## Conversation 2 — package CVEs and third-party attack surfaces

Chair presented advisory IDs. Reviewer mapped each to **this** code (version + whether the attack class is reachable).

| Library / surface | Version in repo | Advisory / class of attack | Points talked about | Decision |
|-------------------|-----------------|----------------------------|---------------------|----------|
| `jackson-databind` (Jackson 2) | **2.21.1** pinned (overrides Boot Jackson 2 BOM 2.21.5) | **CVE-2026-54512** / GHSA-j3rv-43j4-c7qm — `PolymorphicTypeValidator` bypass via generic type parameters. Fixed in 2.21.4 / 2.21.5 / 3.1.4. Needs polymorphic typing. | Pin is in the affected range. Chair + reviewer grep: no `activateDefaultTyping`, no `@JsonTypeInfo`. Jackson 2 mappers write first-party envelopes and Redis JSON the app produced. **Not reachable as used.** Still a bad pin. | **FIX M3** — bump to 2.21.5. Not an incident. |
| `tools.jackson` (Jackson 3) | Boot 4.1.1 BOM **3.1.5** | Same CVE family until 3.1.4 | 3.1.5 is after the patch. MVC DTOs are concrete types, no type-id fields. | **IGNORE** |
| Spring Boot parent | **4.1.1** | Snyk: 0 direct CVEs on 4.1.1. **CVE-2026-40976** (default web security ineffective) is 4.0.0–4.0.5 and requires *no* custom `SecurityConfig` + actuator-autoconfigure without health. | This repo has custom `SecurityConfig` in both apps and **no** actuator. | **IGNORE** — Boot 4.1.1 itself is not CVE-vulnerable here. |
| Spring Boot **Admin Server** | **not a dependency** | **CVE-2026-62242** SSRF on Admin Server &lt; 4.1.2 | Easy to confuse with Boot 4.1.1. Repo has no `de.codecentric` / `spring-boot-admin`. | **IGNORE** — does not apply. |
| `nimbus-jose-jwt` | **10.9.1** (auth) | Alg confusion / `none` / key injection (class). **CVE-2025-53864** nested-JSON DoS on &lt;10.0.2. Sonatype/Snyk: 0 vulns on 10.9.1. | Issue/verify is **RS256 only**. Catalog `NimbusJwtDecoder` + default validators + **iss/aud**. JWKS URL is config (`AUTH_JWKS_URL`), not taken from the token. | **IGNORE** |
| OpenPDF | **2.0.3** | XXE class on `XmlParser` / `HtmlParser`. BDU-2025-16210 file-read cited on **2.0.4** HTML attributes. | Production renderer **only generates** (`Document` + `PdfWriter` + `Paragraph`). `PdfReader` is test-only on bytes just written. No user PDF/HTML upload. | **IGNORE** |
| springdoc-openapi | **3.1.0 test scope** | GHSA-rhhx-6j8h-8cvw live `/v3/api-docs` DoS on 3.1.0 | Not on the runtime classpath. Test props set `springdoc.api-docs.enabled=false`. Apps do not serve Swagger UI. | **DEFER** bump to 3.1.1 on the next OpenAPI regen. Not a runtime risk. |
| Flyway + checked-in SQL | Boot-managed + `V1__*.sql` / `V2__catalog_seed.sql` | Injection if migrations interpolate input | Static SQL only. No dynamic SQL. H2 tests skip Flyway (M25). | **IGNORE** for injection. Real-DDL coverage stays DEFER (M25). |
| Redis / Lettuce | Boot Data Redis; Compose `redis:7-alpine` | Unauthenticated Redis; command injection via user-controlled keys | Compose publishes **6379** with **no `--requirepass`**. Keys are app-built (`create-your-pizza/…`) via `StringRedisTemplate`, not a command parser. In-memory fallback if Redis is missing (locked). | **DEFER** AUTH for non-local. Not command injection. |
| PostgreSQL + HikariCP | runtime driver; pool 10/2 | Default credentials; connection abuse | Local defaults `auth`/`auth`, `catalog`/`catalog`; Compose publishes **5432/5433**. | **DEFER** with L18. |
| H2 | test scope | Console / remote H2 | Test-only. `spring.h2.console.enabled` is not set. | **IGNORE** |
| Embedded Tomcat / spring-webmvc | Boot 4.1.1 | Typical servlet CVE class | CSRF off is expected for stateless JWT. **No CVE looked up** for this Boot line beyond the Boot 4.1.1 check above. | **IGNORE** |
| `BCryptPasswordEncoder` | Spring Security default | Weak work factor / plaintext compare | Used for admin passwords and trusted secrets. `matches()` on login/token. Secrets never in JWT. | **IGNORE** |
| Lombok | `optional` + annotation processor | Compile-only surprise on runtime | Not a runtime library. | **IGNORE** |
| `anyRequest().permitAll()` | both `SecurityConfig`s | Fail-open for future routes | Closed controller set today; latent for the next controller. | **FIX M27** |
| `POST /test/pdf/generate` | catalog, `@Profile({"test","dev"})` | Unauthenticated PDF generation | Open **only** if `test`/`dev` is active. Default/Compose: bean absent, 404. | **IGNORE** as designed. Do not activate `dev` on Compose. |
| `AdminBootstrap` stdout password | auth startup | Secret in logs | Real and locked. Random 18-byte password, printed once if zero admins. | **IGNORE** (Design). Treat logs as secret. |
| Compose default DB/Redis + host ports | `docker-compose.yml` | Default-credential / open cache on the host | Real on the developer machine. Fine for local v1. | **DEFER** (L18) until a hardened / prod compose exists. |

---

## Final register

| Decision | IDs |
|----------|-----|
| **FIX now** (2) | **M3** bump Jackson 2 `2.21.1` → `2.21.5` (databind + jsr310). **M27** change both security chains from `anyRequest().permitAll()` to `anyRequest().denyAll()`; keep existing public matchers and the Design-locked test-trigger matcher; add `/error` only if smoke shows 403-on-500. |
| **DEFER** (11) | M4 quality plugins · M5 CI · M6 Docker skipTests · M7 catalog `.dockerignore` · M8 Docker dep-layer cache · M19 combo N+1 · M21 seed expectations in main · M25 Flyway-in-`mvn-test` / Testcontainers · M31 Enforcer (with M4) · L15 signing-key split · L18 / Redis AUTH for non-local |
| **IGNORE** (45) | H1 H2 H3 H4 H5 H6 H7 · M1 M2 M9 M10 M11 M12 M13 M14 M15 M16 M17 M18 M20 M22 M23 M24 M26 M28 M29 M30 M32 M33 · L1 L2 L3 L4 L5 L6 L7 L8 L9 L10 L11 L12 L13 L14 L16 L17 |

**Do not implement from this file.** If a later ticket is opened, start with M3 and M27 only.

**Related:** prior audit canvas (IDE, not a repo artifact) · locked stack in [AGENTS.md](../AGENTS.md) · Design [design.md](design.md) · Build plan [build-plan.md](build-plan.md)
