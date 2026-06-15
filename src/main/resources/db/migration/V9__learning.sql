-- Part 13 schema: lesson completions. Progress is a fold over these idempotent facts (lessons completed
-- / total lessons), so there is no separate counter to keep in sync. Tenant-scoped and isolated like every
-- other table: tenant_id + index + Hibernate @TenantId + PostgreSQL Row-Level Security. The unique
-- constraint on (tenant_id, learner_id, lesson_id) is what makes "mark complete" idempotent.

create table lesson_completions (
    id           uuid primary key,
    tenant_id    uuid not null references organizations (id),
    learner_id   uuid not null references app_users (id),
    course_id    uuid not null references courses (id),
    lesson_id    uuid not null references lessons (id),
    completed_at timestamptz not null,
    constraint uq_completion_learner_lesson unique (tenant_id, learner_id, lesson_id)
);

create index idx_completions_tenant on lesson_completions (tenant_id);
create index idx_completions_learner_course on lesson_completions (learner_id, course_id);

alter table lesson_completions enable row level security;

create policy tenant_isolation on lesson_completions
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
