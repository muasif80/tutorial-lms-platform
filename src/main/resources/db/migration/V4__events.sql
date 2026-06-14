-- Part 5 schema: the Events context — the reliable backbone every analytics feature is downstream of.
-- The transactional outbox (outbox_events) is written in the same transaction as business state; a
-- relay/CDC ships unpublished rows to the broker. processed_events is the consumer-side dedup record
-- that makes consumers idempotent. learner_progress is a read-model projection rebuilt from the stream.
-- Every table is tenant-scoped and isolated exactly like the earlier parts: a tenant_id column, an
-- index on it, Hibernate @TenantId at the app layer, and PostgreSQL Row-Level Security below.

create table outbox_events (
    id           uuid primary key,
    tenant_id    uuid not null references organizations (id),
    type         varchar(128) not null,
    aggregate_id uuid not null,
    payload      varchar(4000) not null,
    occurred_at  timestamptz not null,
    published    boolean not null default false
);

create table processed_events (
    event_id     uuid primary key,
    tenant_id    uuid not null references organizations (id),
    processed_at timestamptz not null
);

create table learner_progress (
    id                uuid primary key,
    tenant_id         uuid not null references organizations (id),
    learner_id        uuid not null references app_users (id),
    course_id         uuid not null references courses (id),
    lessons_completed int  not null default 0,
    enrolled          boolean not null default false,
    unique (tenant_id, learner_id, course_id)   -- the projection upsert key; blocks double-counting
);

create index idx_outbox_events_tenant     on outbox_events (tenant_id);
create index idx_processed_events_tenant  on processed_events (tenant_id);
create index idx_learner_progress_tenant  on learner_progress (tenant_id);
-- the relay drains unpublished rows oldest-first; this index keeps that scan cheap
create index idx_outbox_unpublished       on outbox_events (published, occurred_at);
create index idx_outbox_aggregate         on outbox_events (aggregate_id);

-- Defense in depth, mirroring V1–V3: even a query that "forgets" the tenant filter cannot read
-- another tenant's events, dedup records, or progress. The app sets the GUC `app.tenant_id` per
-- transaction; these policies make a cross-tenant leak structurally impossible.
alter table outbox_events    enable row level security;
alter table processed_events enable row level security;
alter table learner_progress enable row level security;

create policy tenant_isolation on outbox_events
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on processed_events
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on learner_progress
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
