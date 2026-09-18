# 02 — Auth schema and first-admin bootstrap

**Status:** ready  
**Depends on:** `01-compose-config.md` + owner Initializr `auth-service`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an operator, I want auth-service to own `auth-db` schema and create the first admin when none exist so that the store can log in without a SQL first-admin seed.

## Acceptance criteria

- [ ] Flyway in `auth-service` creates `users`, `trusted_client_credentials`, `verification_keys` per Design
- [ ] On startup: if count of `role = ADMIN` is 0, insert one `ADMIN` / `ACTIVE` and print username + password to stdout
- [ ] If at least one admin exists, do not create another bootstrap admin
- [ ] No first-admin SQL seed in Compose
- [ ] `groupId` `com.createyourpizza`, `artifactId` `auth-service`, `<java.version>26</java.version>`

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage: bootstrap when zero admins; skip when ≥1 admin; credentials appear on stdout (capture/mock)

## Notes

Package `com.createyourpizza.auth`. Mock persistence in tests.
