-- Part 2 schema. Organizations are the tenant registry (id == tenant id).
-- Tenant-scoped tables carry tenant_id; isolation is enforced two ways:
--   (1) app-level by Hibernate @TenantId (auto WHERE tenant_id = ...), and
--   (2) defense-in-depth by PostgreSQL Row-Level Security below.

create table organizations (
    id   uuid primary key,
    name varchar(255) not null
);

create table app_users (
    id    uuid primary key,
    email varchar(320) not null unique,
    name  varchar(255) not null
);

create table memberships (
    id        uuid primary key,
    tenant_id uuid not null references organizations (id),
    user_id   uuid not null references app_users (id),
    role      varchar(32) not null,
    unique (tenant_id, user_id)
);

create table courses (
    id        uuid primary key,
    tenant_id uuid not null references organizations (id),
    title     varchar(255) not null,
    published boolean not null default false
);

create table cohorts (
    id             uuid primary key,
    tenant_id      uuid not null references organizations (id),
    course_id      uuid not null references courses (id),
    capacity       int  not null check (capacity >= 1),
    enrolled_count int  not null default 0,
    version        bigint not null default 0
);

create table enrollments (
    id         uuid primary key,
    tenant_id  uuid not null references organizations (id),
    cohort_id  uuid not null references cohorts (id),
    learner_id uuid not null references app_users (id),
    unique (tenant_id, cohort_id, learner_id)   -- makes "enroll" idempotent
);

create index idx_memberships_tenant on memberships (tenant_id);
create index idx_courses_tenant     on courses (tenant_id);
create index idx_cohorts_tenant     on cohorts (tenant_id);
create index idx_enrollments_tenant on enrollments (tenant_id);

-- Defense in depth: Row-Level Security. The app sets the GUC `app.tenant_id`
-- per transaction; even a query that "forgets" the tenant filter cannot read
-- another tenant's rows. This is what makes a cross-tenant leak structurally
-- impossible rather than merely unlikely.
alter table memberships enable row level security;
alter table courses     enable row level security;
alter table cohorts     enable row level security;
alter table enrollments enable row level security;

create policy tenant_isolation on memberships
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on courses
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on cohorts
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on enrollments
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
