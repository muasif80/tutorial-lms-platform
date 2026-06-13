# Scholr — a Production-Grade LMS (reference implementation)

The companion codebase for the **"Architecting a Production-Grade LMS"** series. It grows one part at
a time, branch by branch, so that by the end you don't just understand a production LMS — you have a
working first version you can run.

It's built as a **Spring Boot modular monolith over PostgreSQL**: one deployable, one database, with the
domain partitioned into hard-bounded modules that can be extracted into services only when real scale
demands it. (Stack rationale and the full design are in Part 1.)

## Series

| Part | Article |
|------|---------|
| 1 — Product, Domain & Architecture | https://skillsuites.com/lms-architecture-product-domain-design/ |
| 2 — Data Model, Multi-Tenancy & APIs | https://skillsuites.com/lms-backend-data-model-multi-tenancy/ |
| 3–10 | published one per day |

## Branches

`main` is the latest. Each part is frozen on its own branch so you can check out the exact code:

| Branch | State |
|--------|-------|
| `part-1` | Product & domain: the module structure + the domain model in code |
| `part-2` | Persistence: multi-tenant data model, `@TenantId` + RLS, idempotent enrollment, REST API |
| `part-3` … `part-10` | Added as each article ships |

## What's in `part-2`

Part 2 turns the design into a **persistent, multi-tenant backend**:

- **Pool multi-tenancy** — a shared schema with a `tenant_id` on every tenant-scoped table, with isolation
  enforced in two independent layers: Hibernate `@TenantId` (the app can't forget the filter) **and**
  PostgreSQL **Row-Level Security** (the database refuses to leak). See `shared/MultiTenancyConfig.java`
  and `db/migration/V1__init.sql`.
- The **core data model** as JPA entities — identity is global (`app_users`), membership and roles are
  per-tenant (`memberships`), and `courses` / `cohorts` / `enrollments` are tenant-scoped.
- The **transactional, idempotent seat invariant** — the `Cohort` aggregate gains a `@Version` optimistic
  lock, and `EnrollmentService.enroll(...)` is find-or-create so a retried request never double-enrolls
  or oversells (`enrollment/`).
- A small **REST API** (`web/`) over the three contexts, with the tenant carried per request.
- An integration test (`MultiTenancyAndEnrollmentTest`) that **proves** tenant isolation, idempotency, and
  the seat cap on a real persistence stack — alongside the Part 1 ArchUnit boundary checks.

> Tests run on in-memory H2 (PostgreSQL mode); the RLS policy itself is Postgres-only and ships in the
> Flyway migration for real deployments. `mvn verify` runs the whole proof.

## Build & run

```bash
# with Docker (no local Java needed):
docker build -t scholr-lms .
docker run --rm -p 8080:8080 scholr-lms
# then: curl localhost:8080/health   and   curl localhost:8080/contexts

# or with local Maven + Java 21:
mvn verify          # compiles + runs the unit and architecture tests
mvn spring-boot:run
```

## License

MIT © 2026 Muhammad Asif. Independent educational reference architecture.
