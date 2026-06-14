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
| 3 — Serving Course Video at Scale: Storage, Transcoding & CDN | https://skillsuites.com/lms-video-content-delivery-cdn/ |
| 4 — Assessments & Real-Time: Auto-Grading, Live Classes & WebSockets | https://skillsuites.com/lms-assessments-realtime-engine/ |
| 5 — Learning Analytics: Events, Streaming & the Transactional Outbox | https://skillsuites.com/lms-events-analytics-pipeline/ |
| 6 — Search, Recommendations & AI Tutoring: Hybrid Search, Cold-Start & RAG | https://skillsuites.com/lms-search-recommendations-personalization/ |
| 7–10 | published one per day |

## Branches

`main` is the latest. Each part is frozen on its own branch so you can check out the exact code:

| Branch | State |
|--------|-------|
| `part-1` | Product & domain: the module structure + the domain model in code |
| `part-2` | Persistence: multi-tenant data model, `@TenantId` + RLS, idempotent enrollment, REST API |
| `part-3` | Media: video assets, an idempotent transcoding pipeline (off the request path), an HLS/ABR ladder, and signed CDN playback URLs |
| `part-4` | Assessment: deterministic auto-grading, exactly-once submission, attempts policy + timed expiry; plus a real-time fan-out tier (broker seam, presence, missed-message replay) |
| `part-5` | Events: transactional outbox (atomic event-with-state), at-least-once relay, idempotent progress-projection consumer, tenant-isolated; enroll emits `enrollment.created` |
| `part-6` | Discovery: keyword/semantic/hybrid catalog search, content-based + cold-start recommendations, zero-downtime alias-swap reindex, hand-rolled tenant isolation in the search store |
| `part-7` … `part-10` | Added as each article ships |

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

## What's in `part-3`

Part 3 adds the **Media** bounded context — course video at scale, where an LMS's cost and reliability
usually go to die. The architectural rule is simple and load-bearing: **multi-gigabyte bytes never touch
the app servers**. Uploads go directly to object storage via a presigned URL; playback bytes come from the
CDN via a signed URL. The app only ever moves small JSON.

- **The Media aggregate** (`media/domain/`) — a `VideoAsset` with a guarded lifecycle
  (`UPLOADED → TRANSCODING → READY/FAILED`, with a `@Version` optimistic lock so a duplicate transcoder
  callback can't double-process it), a `VideoRendition` ladder (the adaptive-bitrate / HLS rungs), and a
  `TranscodeJob`. Cross-aggregate references are by id (UUID), never by JPA association — the series rule.
- **Transcoding off the request path, idempotently** (`MediaService.enqueueTranscode`) — the upload handler
  only persists a job row and returns; a worker drains the queue out of band. The job is keyed on a
  per-tenant `idempotency_key`, so a duplicated upload callback or a client retry resolves to the same job
  instead of paying for a second (expensive) transcode. Recording renditions is idempotent too, via a
  `(tenant_id, asset_id, height)` unique constraint — the same defense Part 2 used for idempotent enroll.
- **Signed CDN playback URLs** (`media/SignedUrlIssuer`) — short-lived, HMAC-signed, tamper-proof URLs bound
  to a path and an expiry. A leaked URL is useless within minutes and a learner cannot forge one without the
  signing key, which never leaves the server. This is the access-control boundary for paid video.
- **`V2__media.sql`** mirrors V1 exactly: a `tenant_id` column, an index on it, and PostgreSQL Row-Level
  Security on every new table, so a cross-tenant media leak is structurally impossible, not merely unlikely.
- **`MediaTest`** proves tenant isolation on assets, idempotent enqueue, the full ABR ladder being packaged
  (and not doubled on a duplicate callback), and that the signed URL genuinely rejects tampering, expiry,
  path-swapping, and a wrong signing key.

> Tests run on in-memory H2 (PostgreSQL mode); the RLS policy itself is Postgres-only and ships in the
> Flyway migration for real deployments. `mvn verify` runs the whole proof.

## What's in `part-4`

Part 4 adds the **Assessment** context and a **real-time** tier — where correctness meets scale and
real-time meets scale.

- **A deterministic auto-grading engine** (`assessment/AutoGrader.java`) — a pure function (no Spring,
  no DB, no clock, no randomness) that scores single-choice (all-or-nothing), multiple-choice
  (proportional partial credit, floored at zero), and short-text (normalized exact match). Reproducible
  on any node, so a grade is defensible; judgment items are never faked. Specced by `AutoGraderTest`.
- **Exactly-once submission** (`assessment/domain/Attempt.java` + `AssessmentService.java`) — a one-way
  `IN_PROGRESS → SUBMITTED/EXPIRED` transition guarded so it grades only once, plus a `@Version`
  optimistic lock. A duplicate submit (double-click, retry, queue redelivery) returns the
  already-recorded grade instead of regrading — the Part 2 idempotent-write discipline applied to exams.
- **Attempts policy + timed expiry** (`Assessment.java`) — the server, not the client, decides whether
  another attempt is allowed; "start" resumes an in-progress attempt rather than burning the budget; a
  submit past the deadline is graded on saved answers and marked `EXPIRED` (never lost, never extended).
  Proven by `TimedAttemptTest`.
- **`V3__assessment.sql`** mirrors V1/V2: `tenant_id` + index + PostgreSQL Row-Level Security on every
  new table, so a cross-tenant leak stays structurally impossible.
- **A real-time fan-out tier** (`realtime/`) — a `MessageBroker` port (the seam Redis pub/sub plugs into),
  an in-memory implementation for tests, and a `LiveClassRoom` with presence and sequence-numbered
  messages so a reconnecting client replays exactly what it missed. The design point: stateless gateways
  + broker fan-out scale a 50k-person class; no node owns the room.
- **`MediaTest`-style proof** — `AssessmentAndRealtimeTest` asserts deterministic grading, idempotent
  submission, the server-side attempts policy, tenant isolation, and the fan-out + replay contract.

## What's in `part-5`

Part 5 adds the **Events** context — the reliable backbone every analytics feature is downstream of.

- **The transactional outbox** (`events/domain/OutboxEvent.java` + `events/OutboxWriter.java`) — the
  writer has no transaction of its own, so it joins the caller's: the event row is inserted in the
  *same* DB transaction as the state change. `EnrollmentService.enroll` now emits `enrollment.created`
  this way, closing the dual-write gap where database and event stream could diverge.
- **An at-least-once relay** (`events/OutboxRelay.java`) ships unpublished events through an
  `EventPublisher` port (Kafka plugs in here; `InMemoryEventPublisher` is the default), ship-then-mark
  so a crash re-ships rather than loses.
- **An idempotent consumer** (`events/ProgressProjection.java` + `events/domain/ProcessedEvent.java`) —
  records each event id before acting and skips ids already seen, turning at-least-once delivery into
  exactly-once *effects*. Progress is modeled as a fold over idempotent facts, so replay and redelivery
  reproduce identical numbers.
- **A rebuildable read model** (`events/domain/LearnerProgress.java`) — a projection, eventually
  consistent, never the source of truth; delete it and replay the log to reconstruct it.
- **`V4__events.sql`** mirrors V1–V3: `tenant_id` + index + Row-Level Security on the outbox, dedup, and
  progress tables, so the whole pipeline is tenant-isolated.
- **The proof** — `EventsPipelineTest` asserts the atomic outbox write, the at-least-once relay, an
  idempotent consumer that won't double-count a redelivered event, and correct progress from the stream.

## What's in `part-6`

Part 6 adds the **Discovery** context — search, recommendations, and the AI-tutor retrieval layer —
built as a search-store seam (OpenSearch in production), not Postgres.

- **Keyword / semantic / hybrid search** (`discovery/SearchService.java` + `SearchMode.java`) — lexical
  term matching, cosine similarity over embeddings, and a reranked blend of both, times domain ranking
  signals (popularity, completion rate).
- **The embedding seam** (`discovery/TextVectorizer.java`) — a deterministic stand-in; a real embedding
  model plugs into this one component, exactly like the broker/publisher seams in Parts 4–5.
- **The search-index port + zero-downtime reindex** (`discovery/SearchIndex.java`,
  `InMemorySearchIndex.java`, `IndexingService.java`) — searches hit a stable alias; a full reindex
  builds a new physical index and atomically repoints the alias, so readers never see a half-built index.
- **Recommendations + cold-start** (`discovery/RecommendationService.java`) — content-based "more like
  this" (works from day one) with a popularity fallback for learners with no history.
- **Hand-rolled tenant isolation** — the search store has no Row-Level Security, so every document carries
  its `tenantId` and every query filters on the current tenant. Leaving Postgres means re-earning isolation.
- **The proof** — `DiscoverySearchTest` asserts the three search modes, recommendations + cold-start,
  tenant isolation, and the zero-downtime alias swap.

## Build & run

```bash
# with Docker (no local Java needed):
docker build -t scholr-lms .
docker run --rm -p 8080:8080 scholr-lms
# then: curl localhost:8080/health   and   curl localhost:8080/contexts

# or with local Maven + Java 21:
mvn verify          # compiles + runs the unit, persistence, and architecture tests
mvn spring-boot:run
```

## License

MIT © 2026 Muhammad Asif. Independent educational reference architecture.
