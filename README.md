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
| 2–10 | published one per day |

## Branches

`main` is the latest. Each part is frozen on its own branch so you can check out the exact code:

| Branch | State |
|--------|-------|
| `part-1` | Product & domain: the module structure + the domain model in code |
| `part-2` … `part-10` | Added as each article ships |

## What's in `part-1`

This part is design-first, so the code establishes the **skeleton**, not yet the database:

- The **bounded contexts as enforced modules** (`identity`, `catalog`, `enrollment`, `learning`, `assessment`, `shared`).
- The **domain model** for the Enrollment context, including the `Cohort` aggregate that **protects the
  seat invariant** ("never oversell a cohort") — the canonical example from the article.
- An **ArchUnit test** that fails the build if a module reaches into another module's `internal` package,
  and a slices rule that keeps the modules free of cycles.
- A health/contexts endpoint, an ADR, and the architecture notes.

> Persistence (PostgreSQL, multi-tenancy, RLS) arrives in **Part 2**.

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
