# Architecture notes (Part 1)

A modular-monolith LMS. The bounded contexts are top-level modules under `com.scholr.lms`; each module
keeps a small public surface and a private `internal`. The boundaries are enforced by ArchUnit
(`src/test/java/com/scholr/lms/architecture/ModularityTest.java`), which fails the build on a violation.

## Module map

| Module (`com.scholr.lms.*`) | Bounded context | Owns |
|-----------------------------|-----------------|------|
| `identity`   | Identity & Organization | tenants, users, memberships, roles |
| `catalog`    | Catalog & Curriculum    | courses, modules, lessons, versioning |
| `enrollment` | Enrollment              | cohorts, seats, enrollments |
| `learning`   | Learning & Progress     | activity, completion (the source of truth) |
| `assessment` | Assessment              | quizzes, attempts, grading |
| `shared`     | Shared kernel           | `TenantId` and other cross-context value objects |

## The consistency rule

- **Inside an aggregate** (e.g. `Cohort`): strong, transactional consistency. The seat invariant
  ("never oversell a cohort") is enforced in `Cohort.enroll(...)`, in one DB transaction.
- **Across contexts**: eventual consistency, carried by domain events (in-process now; a durable event
  backbone with the transactional outbox pattern in Part 5).

## Reference architecture diagram

See the article: clients → API gateway → modular-monolith core over PostgreSQL + Redis, with media/CDN,
event backbone, search, real-time tier and analytics as capabilities to be added in later parts, all
wrapped by the cross-cutting concerns (security, observability, cost) in Part 10.
