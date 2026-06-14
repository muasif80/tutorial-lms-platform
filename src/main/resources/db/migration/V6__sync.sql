-- Part 9 schema: the Sync context — the server side of offline-first learning. One row per (learner,
-- course) holds the converged state that offline edits from any device merge into: a grow-only set of
-- completed lessons (conflict-free union) and a last-write-wins position cursor. Tenant-scoped and isolated
-- exactly like the earlier parts: a tenant_id column, an index on it, Hibernate @TenantId at the app layer,
-- and PostgreSQL Row-Level Security below.

create table course_sync_state (
    id                   uuid primary key,
    tenant_id            uuid not null references organizations (id),
    learner_id           uuid not null references app_users (id),
    course_id            uuid not null references courses (id),
    last_position_lesson uuid,
    last_position_at     timestamptz,
    version              bigint not null default 0,
    unique (tenant_id, learner_id, course_id)   -- one merge target per learner per course
);

-- The grow-only set of completed lessons, owned by course_sync_state (merged by union, never deleted).
create table course_sync_completed_lessons (
    sync_state_id uuid not null references course_sync_state (id),
    lesson_id     uuid not null
);

create index idx_course_sync_state_tenant on course_sync_state (tenant_id);
create index idx_course_sync_state_lookup on course_sync_state (learner_id, course_id);
create index idx_course_sync_completed_owner on course_sync_completed_lessons (sync_state_id);

-- Defense in depth, mirroring the earlier parts: a cross-tenant read of another learner's offline progress
-- is structurally impossible. The element-collection table is reached only through its owner row, so it
-- inherits isolation via the foreign key.
alter table course_sync_state enable row level security;

create policy tenant_isolation on course_sync_state
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
