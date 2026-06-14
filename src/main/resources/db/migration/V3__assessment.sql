-- Part 4 schema: the Assessment context. Assessments and their question banks, plus learner
-- attempts. Every top-level table is tenant-scoped and isolated exactly like the earlier parts:
-- a tenant_id column, an index on it, Hibernate @TenantId at the app layer, and PostgreSQL
-- Row-Level Security below as defense in depth. The element-collection tables (options and the
-- answer key) are owned by `questions` and reached only through it, so they inherit isolation
-- via their foreign key rather than carrying their own tenant_id.

create table assessments (
    id                 uuid primary key,
    tenant_id          uuid not null references organizations (id),
    course_id          uuid not null references courses (id),
    title              varchar(512) not null,
    max_attempts       int  not null default 0 check (max_attempts >= 0),
    time_limit_seconds int  not null default 0 check (time_limit_seconds >= 0)
);

create table questions (
    id            uuid primary key,
    tenant_id     uuid not null references organizations (id),
    assessment_id uuid not null references assessments (id),
    type          varchar(32) not null,
    prompt        text not null,
    points        int  not null check (points >= 0)
);

-- Question option labels (ordered) and the server-only answer key. Owned by `questions`.
create table question_options (
    question_id uuid not null references questions (id),
    label       varchar(1024) not null
);

create table question_answer_key (
    question_id  uuid not null references questions (id),
    answer_value varchar(1024) not null
);

create table attempts (
    id            uuid primary key,
    tenant_id     uuid not null references organizations (id),
    assessment_id uuid not null references assessments (id),
    learner_id    uuid not null references app_users (id),
    attempt_no    int  not null check (attempt_no >= 1),
    status        varchar(16) not null,
    started_at    timestamptz not null,
    deadline_at   timestamptz,
    submitted_at  timestamptz,
    score         int,
    max_score     int,
    version       bigint not null default 0,
    -- makes "start this learner's Nth attempt" idempotent and caps double-counting
    unique (tenant_id, assessment_id, learner_id, attempt_no)
);

create index idx_assessments_tenant   on assessments (tenant_id);
create index idx_questions_tenant      on questions (tenant_id);
create index idx_attempts_tenant       on attempts (tenant_id);
create index idx_questions_assessment  on questions (assessment_id);
create index idx_question_options_q    on question_options (question_id);
create index idx_question_answerkey_q  on question_answer_key (question_id);
create index idx_attempts_assessment   on attempts (assessment_id);
create index idx_attempts_learner      on attempts (learner_id);

-- Defense in depth, mirroring V1/V2: even a query that "forgets" the tenant filter cannot read
-- another tenant's assessments, questions, or attempts. The app sets the GUC `app.tenant_id`
-- per transaction; these policies make a cross-tenant leak structurally impossible.
alter table assessments enable row level security;
alter table questions   enable row level security;
alter table attempts    enable row level security;

create policy tenant_isolation on assessments
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on questions
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on attempts
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
