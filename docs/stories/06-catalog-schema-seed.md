# 06 — Catalog schema and seed

**Status:** ready  
**Depends on:** `01-compose-config.md` + owner Initializr `catalog-service`  
**Links:** [Intent](../intent.md) · [Spec](../spec.md) · [Design](../design.md) · [Build plan](../build-plan.md)

## Story

As an operator, I want `catalog-db` schema and sample Simple/Combo/Pizza plus option entities so that the catalog and first PDF job have data.

## Acceptance criteria

- [ ] Flyway creates `products`, `combo_items`, `option_entities`, `menu_pdf`, `catalog_meta` per Design
- [ ] No `system_status` table; no lock columns on `catalog_meta`
- [ ] Seed: simples, at least one combo with members, at least one pizza; option entities FR-4a–c with **per-row prices** and locked `is_base` names
- [ ] `catalog_meta` id=1, `dirty=true` so first generate can produce **v1**
- [ ] No first-admin SQL in catalog
- [ ] `groupId` `com.createyourpizza`, `artifactId` `catalog-service`, `<java.version>26</java.version>`
- [ ] Package `com.createyourpizza.catalog`

## Tests (if coding)

- Unit tests with **>80% LoC** coverage of code added/changed for this story
- Full **behaviour** coverage of seed mapping / meta defaults (mock DB or Flyway-independent assertions)

## Notes

Unblocks **07** (with 03) and **11** (with 08).
