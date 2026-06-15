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
| 7 — Payments, Billing & Subscriptions: Idempotent Webhooks & Entitlements | https://skillsuites.com/lms-payments-billing-subscriptions/ |
| 8 — Interoperability: LTI 1.3, SCORM & xAPI | https://skillsuites.com/lms-interoperability-lti-scorm-xapi/ |
| 9 — The Experience Layer: Frontend, Accessibility (WCAG 2.2), i18n & Offline | https://skillsuites.com/lms-frontend-accessibility-i18n/ |
| 10 — Productionizing: Security, Testing, CI/CD, Scaling, SRE & Cost (Capstone) | https://skillsuites.com/lms-production-security-testing-sre/ |
| 11 — The Working Platform: Login, RBAC & a Server-Rendered UI | https://skillsuites.com/lms-login-rbac-thymeleaf-ui/ |
| 12 — The Instructor Workspace: Course Authoring, Lessons & Cohort Rosters | https://skillsuites.com/lms-instructor-course-authoring/ |
| 13 — The Student Learning Flow: Catalog, Enrolment, Player & Auto-Graded Assessments | https://skillsuites.com/lms-student-learning-flow/ |
| 14 — The Admin Console: Catalog Oversight, Org Reports & Billing | https://skillsuites.com/lms-admin-console/ |

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
| `part-7` | Billing: subscription state machine, separate entitlements, idempotent webhook processing (dedup by PSP event id), Stripe-agnostic gateway port, reconciliation; V5 migration with RLS |
| `part-8` | Interop: xAPI translation to an LRS (fed by Part 5 events), LTI 1.3 launch validation + AGS grade passback, XXE-hardened SCORM manifest parsing, reliable idempotent SCORM completion capture |
| `part-9` | Sync: offline-first conflict resolution — grow-only-set union for completed lessons + last-write-wins position cursor, idempotent multi-device merge |
| `part-10` | Production: Actuator probes, a committed docker-compose deploy, a hardened non-root container — the system, deployable |
| `part-11` | The working platform: Spring Security login + enforced RBAC, tenant-from-identity, Thymeleaf role dashboards, admin enroll flow, seeded demo tenant |
| `part-12` | The instructor workspace: course & lesson authoring, a guarded draft→publish lifecycle, and tenant-scoped cohort rosters (an explicit by-id join across the Enrollment and Identity contexts) |
| `part-13` | The student learning flow: published-course catalogue, idempotent enrol-by-course, a lesson player with progress tracking, auto-graded assessments, and completion certificates — progress modeled as a fold over idempotent completion facts |
| `part-14` | The admin console: catalogue oversight, organisation-wide reports computed live as folds over tenant-scoped facts (no analytics warehouse), and a billing view over the Part 7 subscription engine — completing the working-platform arc |

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

## What's in `part-7`

Part 7 adds the **Billing** context — payments as a distributed-consistency problem, solved.

- **Idempotent webhook processing** (`billing/BillingService.handleWebhook` + `domain/ProcessedWebhook.java`)
  — a payment processor delivers at least once, so a duplicate webhook is deduped by the processor's event
  id and becomes a no-op. The same idempotent-consumer pattern as Part 5, now guarding money.
- **A subscription state machine** (`billing/domain/Subscription.java` + `SubscriptionStatus.java`) — guarded
  TRIALING → ACTIVE → PAST_DUE → CANCELED transitions with a `@Version` optimistic lock, so an out-of-order
  or concurrent webhook can't corrupt it. PAST_DUE retains access during the dunning grace window.
- **Entitlements, separate from subscriptions** (`billing/domain/Entitlement.java`) — the small hot record
  read on every access check; billing events flip its `active` flag, access control only reads it.
- **A processor-agnostic port** (`billing/PaymentGateway.java`, `FakePaymentGateway.java`) — Stripe plugs in
  here; the domain never imports a PSP SDK, so the whole flow is testable without a network call.
- **Reconciliation as the real source of truth** (`billing/BillingService.reconcile`) — webhooks can be lost
  or arrive out of order, so a periodic job diffs the database against the processor and repairs drift.
- **`V5__billing.sql`** mirrors the earlier parts: `tenant_id` + index + Row-Level Security on every billing
  table; money is stored as integer minor units (cents), never a float.
- **The proof** — `BillingTest` asserts webhook idempotency, the state machine, grace-window access,
  reconciliation repairing drift, and tenant isolation.

## What's in `part-8`

Part 8 adds the **Interop** context — the standards (LTI 1.3, SCORM, xAPI) that make an LMS sellable to
institutions, built as bridging seams (no JPA of its own).

- **xAPI as a translation of your event stream** (`interop/XapiTranslator.java` + the
  `LearningRecordStore` port / `InMemoryLrs.java`) — internal events become actor-verb-object statements
  in a Learning Record Store; your Part 5 stream *is* your LRS feed. Tenant-filtered by hand (no RLS in
  the LRS).
- **LTI 1.3 launch validation** (`interop/LtiLaunchValidator.java`) — verify the signed launch (HMAC
  stand-in for RS256/JWKS) and check expiry **before** trusting any claim; reject tampered/expired launches.
- **AGS grade passback** (`interop/LtiPlatform.java` + `InteropService.passbackGrade`) — report a score
  back to the platform's gradebook.
- **XXE-hardened SCORM manifest parsing** (`interop/ScormManifestParser.java`) — SCORM packages are
  untrusted input, so the parser disables DTDs and external entities before reading a byte.
- **Reliable, idempotent SCORM completion capture** (`InteropService.commitScormCompletion`) — the
  war-story fix: a stable derived statement id means a doubled runtime commit is captured exactly once.
- **The proof** — `InteropTest` asserts launch validation, tampered/expired rejection, grade passback,
  XXE rejection, and idempotent completion capture.

## What's in `part-9`

Part 9 adds the **Sync** context — the server side of offline-first learning, where the experience
layer's hardest distributed problem (offline state + conflict resolution) is solved.

- **Conflict-free merge** (`sync/domain/CourseSyncState.java`) — completed lessons are a grow-only set
  (G-Set CRDT) merged by union, which is commutative, associative, and idempotent, so two devices that
  each made different progress offline both win and re-syncing a batch changes nothing; the single
  position cursor is a last-write-wins register resolved by timestamp.
- **An idempotent sync endpoint** (`sync/SyncService.java` + `web/SyncController.java`) — a client can
  safely retry a sync it isn't sure landed, the same discipline that protected enrollment, submission,
  events, and payments, now applied to a flaky mobile connection.
- **`V6__sync.sql`** — `tenant_id` + index + Row-Level Security, so a cross-tenant read of another
  learner's offline progress is structurally impossible.
- **The proof** — `OfflineSyncTest` asserts multi-device set union, last-write-wins ordering (out-of-order
  arrival still picks the latest), idempotent re-sync, and tenant isolation.

> The rest of Part 9 (frontend architecture, the design system, WCAG 2.2 AA accessibility, i18n/RTL,
> Core Web Vitals, and the PWA) is front-end and covered in the article; the offline-sync engine is the
> part that lives in this backend.

## What's in `part-10` (capstone)

Part 10 makes the platform **deployable and production-shaped**, and assembles the full reference architecture.

- **Production observability** — Spring Boot **Actuator** exposes `/actuator/health` with Kubernetes-style
  **liveness and readiness probes** (`application.yml`), so an orchestrator restarts a wedged instance and
  routes traffic only to one that's ready (DB connected, migrations applied).
- **One-command deploy** — a committed **`docker-compose.yml`** brings up PostgreSQL + the app together;
  the app waits on the database's health check, Postgres data lives in a named volume, and Flyway runs the
  migrations on startup. `.env.example` documents the config.
- **A hardened container** — the `Dockerfile` is a multi-stage build that runs as a **non-root user** and
  carries a `HEALTHCHECK` against the readiness probe.
- **A live demo console** — the app serves a lightweight browser console at `/` that drives the real API
  (create tenants, watch isolation, idempotent enroll, conflict-free offline sync). *Not* the production
  frontend (that's Part 9's subject) — a console so you can see the backend work.

## What's in `part-11` (series extension — a working platform)

Part 11 turns the backend into a **usable, multi-role platform** with a server-rendered UI.

- **Real login** — Spring Security form login (`auth/SecurityConfig.java`) against a global, tenant-less
  `Credential` (BCrypt), loaded via `auth/AppUserDetailsService`.
- **Enforced RBAC** — `/admin/**`, `/instructor/**`, `/learn/**` gated by role; a wrong-role request gets
  a 403. Roles map to authorities in `auth/UserPrincipal`.
- **Tenant from the identity** — `auth/TenantPrincipalFilter` pins the tenant from the authenticated user
  (no more trusted `X-Tenant-Id`); isolation is now driven by who is logged in.
- **Role dashboards + admin enroll flow** — `web/ui/UiController` + Thymeleaf templates; the admin can
  add/enroll instructors and students.
- **Seeded demo tenant** — `config/DataSeeder` creates Acme University + admin/instructor/student logins
  (password `scholr`) + courses/cohorts/enrollments on first boot, so it is usable immediately.
- **Swagger** at `/swagger-ui.html`.

Run it: `docker compose up -d --build`, then open `http://localhost:8080/login` and sign in as
`admin@acme.test` / `instructor@acme.test` / `student@acme.test` (password `scholr`).

## What's in `part-12` (series extension — the instructor workspace)

Part 12 makes the **instructor role** real: authoring content end to end, all tenant-scoped, all built on
Part 11's login + RBAC + tenant-from-identity.

- **Course & lesson authoring** — a new `catalog/domain/Lesson.java` (tenant-scoped via `@TenantId`,
  referencing its course by id), with `CatalogService` gaining `createCourse`, `addLesson`, `lessons`, and
  `lessonCount`. Lesson `position` is computed on the server (`count + 1`), never sent by the client.
- **A guarded draft→publish lifecycle** — `CatalogService.publish` loads the course, calls the aggregate's
  `Course.publish()` transition, and saves it in one transaction. Publishing is idempotent and lives in one
  place, so future rules (e.g. "needs a lesson to go live") have a single home — not a boolean setter.
- **Cohort rosters across two contexts** — `web/ui/InstructorController` joins tenant-scoped **Enrollment**
  (`cohortsForCourse`, `rosterForCohort`) to **global Identity** (`IdentityService.findUser`) *by id*. The
  controller owns the join; the entities never hold a cross-context JPA association — that by-id rule is what
  keeps the module boundary hard and tenant isolation auditable (and it's enforced by the ArchUnit test).
- **Server-rendered Thymeleaf views** — `templates/instructor/{courses,course,cohorts}.html`, every mutation
  a plain HTML form using Post/Redirect/Get so a browser refresh can't duplicate content. Works without JS.
- **`V8__lessons.sql`** — the lessons table with `tenant_id` + index + PostgreSQL Row-Level Security, mirroring
  every other migration in the series.

Run it: sign in as `instructor@acme.test` / `scholr`, then **My Courses** → create a course, add lessons,
publish; **Cohorts & Roster** shows the enrolled students by name.

## What's in `part-13` (series extension — the student learning flow)

Part 13 makes the **student role** real: discover, enrol, learn, get graded, and earn a certificate —
composed almost entirely from machinery built in earlier parts.

- **A published-course catalogue** — `CatalogService.publishedCourses()` (a `findByPublishedTrue…` query)
  shows only courses an instructor has published; drafts stay hidden by construction.
- **Enrol by course, idempotently** — `EnrollmentService.enrollInCourse` resolves a cohort (first with a
  free seat, or a new default one) and delegates to Part 2's idempotent, seat-capped `enroll`, so a
  double-clicked "Enrol" never double-enrols.
- **A new `learning` context** — `learning/domain/LessonCompletion.java` (tenant-scoped, unique on
  `(tenant, learner, lesson)`) + `LearningService`. **Progress is a fold over idempotent completion facts**
  (`completed / total`, computed on read), not a maintained counter — so it can't drift under double-clicks,
  retries, or offline re-sync. The completion certificate is the same derived state at 100%. `V9__learning.sql`
  adds the table with RLS.
- **The course player & auto-graded assessment** — `web/ui/StudentController` drives catalogue → enrol →
  player (mark lessons complete) → quiz → result → progress. Starting a quiz resumes an in-progress attempt;
  submitting is exactly-once (Part 4's `AutoGrader` + `Attempt.submit`); the answer key never reaches the
  browser. Post/Redirect/Get throughout; works without JavaScript.
- **A richer demo seed** — `DataSeeder` now publishes four courses with lessons and seeds an auto-graded
  quiz, all through the model's own public operations, so the whole flow works on first boot.

Run it: sign in as `student@acme.test` / `scholr` → **Catalog** to enrol, **My Learning** to open a course,
mark lessons complete, take the quiz, and watch **Progress & Certificates** fill in.

## What's in `part-14` (series extension — the admin console, arc complete)

Part 14 gives the **admin role** the organisation-wide console that rolls up everything the instructor and
student workspaces produce — completing the working-platform arc (Parts 11–14).

- **`web/ui/AdminController`** — three role-gated screens:
  - `/admin/courses` — every course (published *and* draft) with lessons, cohorts, enrolled/capacity, and
    assessment counts, assembled from four contexts via their public APIs.
  - `/admin/reports` — an organisation rollup computed **live** (no analytics store): role counts, catalogue
    health, cohort capacity and seat fill rate, and total lesson completions.
  - `/admin/billing` — plans, subscriptions (joined to learner and plan names **by id**), and active
    entitlements — a read-only window onto the Part 7 subscription engine (payments off in the demo).
- **`BillingService`** gains tenant-scoped read views (`allPlans`, `allSubscriptions`, `findPlan`,
  `activeEntitlementCount`); **`LearningService.totalCompletions`** backs the engagement metric.
- **`DataSeeder`** seeds two plans and two subscriptions so the billing console has real rows.
- The admin sees everything **within** its tenant and nothing outside it: `@TenantId` + RLS apply to admin
  queries too, so the broad role never crosses the isolation boundary.

The design point: **the admin console is a rollup, not a second source of truth.** Every figure is summed
on read from the same operational tables the other roles write, so it's always current and never drifts.
When live aggregation eventually gets slow, the migration path is the event-driven read models from Part 5.

Run it: sign in as `admin@acme.test` / `scholr` → **Reports**, **Courses**, **Billing**.

## Deploy &amp; run the whole system

```bash
git clone https://github.com/muasif80/tutorial-lms-platform.git
cd tutorial-lms-platform
cp .env.example .env                 # optional: set DB_PASSWORD etc.
docker compose up -d --build         # Postgres + the app, schema auto-migrated
curl localhost:8080/actuator/health  # {"status":"UP"} when ready
open  http://localhost:8080/         # the demo console
```

Scale the stateless app horizontally with `docker compose up -d --scale app=3`; in production the same
image runs on Kubernetes (the probes map straight to the orchestrator) behind a load balancer, with a
managed, replicated PostgreSQL. See Part 10 for the full production topology and the complete tech stack.

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
