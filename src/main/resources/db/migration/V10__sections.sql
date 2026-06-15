-- Part 15 schema: lesson sections. A lesson is now an ordered list of sections, each authored in a block
-- editor and stored as both the editor's block JSON (the editable source) and server-rendered, sanitized
-- HTML (what learners see, so the student view needs no JavaScript). Tenant-scoped and isolated like every
-- other table: tenant_id + index + Hibernate @TenantId + PostgreSQL Row-Level Security.

create table sections (
    id            uuid primary key,
    tenant_id     uuid not null references organizations (id),
    course_id     uuid not null references courses (id),
    lesson_id     uuid not null references lessons (id),
    title         varchar(512) not null,
    position      int not null default 0,
    content_json  text,
    rendered_html text,
    updated_at    timestamptz not null
);

create index idx_sections_tenant on sections (tenant_id);
create index idx_sections_lesson on sections (lesson_id, position);

alter table sections enable row level security;

create policy tenant_isolation on sections
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
