# ADR 0001 — Modular monolith over microservices (to start)

- **Status:** Accepted
- **Date:** 2026-06-08

## Context

We are building a multi-tenant LMS with several bounded contexts (Identity & Org, Catalog, Enrollment,
Learning & Progress, Assessment, Certification, Billing, Notifications). The team is small and the system
is new, so the cost of operating a distributed system — network calls, distributed transactions, and the
operational burden of many services — is not yet justified, and the highest risk is shipping a
*distributed monolith* (all the cost of microservices, none of the benefit).

## Decision

Build a **modular monolith**: a single Spring Boot deployable over a single PostgreSQL database, with the
bounded contexts implemented as **hard-bounded modules**. Each module exposes a small public surface
(`api` / `domain`) and hides its implementation (`internal`). Cross-module access to `internal` packages
is forbidden and **enforced in CI with ArchUnit**. Strong consistency lives inside an aggregate and a
single DB transaction; eventual consistency carries facts across contexts (via domain events, made
durable in Part 5).

## Consequences

- **Positive:** operational simplicity (one deploy, one DB), single-transaction invariants (e.g. the
  cohort seat invariant), fast local development, and clean seams to extract a service later exactly
  where real scaling pressure appears.
- **Negative:** the whole app scales and deploys as a unit until a context is extracted; module
  discipline must be actively enforced (hence the ArchUnit tests) or the boundaries erode.
- **Revisit when:** a specific context (media, real-time) needs independent scaling or an independent
  deploy cadence — then extract it along its existing module boundary.
