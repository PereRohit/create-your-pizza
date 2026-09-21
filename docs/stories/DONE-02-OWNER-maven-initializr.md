# 02 — Owner Initializr (both Maven apps)

**Status:** done  
**Type:** enabler (owner)  
**Depends on:** — (may run in parallel with `01-compose-config.md`)  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md) §3–4

## Story

As the project owner, I want both Spring Boot Maven apps created via Spring Initializr in this repo so that later stories have real `auth-service` and `catalog-service` trees to code in.

## Acceptance criteria

- [x] `auth-service/` and `catalog-service/` exist at repo root (sibling folders, no parent POM)
- [x] Each was generated with Maven, JAR, Spring Boot **4.1.1**, Java **25** on start.spring.io, then `<java.version>26</java.version>` in both POMs
- [x] `groupId` `com.createyourpizza`; artifacts `auth-service` and `catalog-service`; packages `com.createyourpizza.auth` and `com.createyourpizza.catalog`
- [x] Initializr selections match [build-plan.md](../build-plan.md) §4.1 and §4.2 (including Lombok; **not** Docker Compose Support)
- [x] Nimbus and OpenPDF are **not** required on this story (tasks on **04** and **12**)

## Tests (if coding)

- Enabler / environment: `mvn -f auth-service/pom.xml -q -DskipTests validate` and the same for `catalog-service` succeed after the pin to Java 26

## Tasks

- [x] **Owner:** generate `auth-service` zip; unzip into `auth-service/` (no nested extra folder)
- [x] **Owner:** generate `catalog-service` zip; unzip into `catalog-service/`
- [x] Pin `<java.version>26</java.version>` in both POMs (agent OK after trees exist)
- [x] Confirm coordinates and package names (agent OK after trees exist)

## Notes

Filename token **`OWNER`**: this story cannot be `DONE-` without the owner. Do not use `OWNER` for commit-ask, HIFL gates, or Definition of Ready. Unblocks **03** and **07**. Git branch: `feat/02-maven-initializr` (`OWNER` is not part of the branch summary).

Landed on `feat/02-maven-initializr`: sibling Maven trees (Boot 4.1.1, Java 26 pin); story-01 `application.properties` kept (plus `spring.application.name`); Maven Wrapper `.mvn/` regenerated (unzip was missing it). `mvn … validate` OK for both.
