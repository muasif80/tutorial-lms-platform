-- Part 12 schema: lessons. A course (Part 2) is now authored as an ordered list of lessons. Tenant-scoped
-- and isolated like every other table: tenant_id + index + Hibernate @TenantId + PostgreSQL Row-Level
-- Security. Referenced to its course by id.

create table lessons (
    id        uuid primary key,
    tenant_id uuid not null references organizations (id),
    course_id uuid not null references courses (id),
    title     varchar(512) not null,
    position  int not null default 0,
    body      varchar(4000)
);

create index idx_lessons_tenant on lessons (tenant_id);
create index idx_lessons_course on lessons (course_id, position);

alter table lessons enable row level security;

create policy tenant_isolation on lessons
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
