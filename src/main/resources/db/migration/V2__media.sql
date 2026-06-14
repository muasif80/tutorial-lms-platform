-- Part 3 schema: the Media context. One source video per asset, an adaptive-bitrate
-- ladder of renditions, and the transcode jobs that produce them. Every table is
-- tenant-scoped and isolated exactly like Part 1's tables: a tenant_id column, an index
-- on it, Hibernate @TenantId at the app layer, and PostgreSQL Row-Level Security below as
-- defense in depth. Nothing in the media pipeline is global.

create table video_assets (
    id         uuid primary key,
    tenant_id  uuid not null references organizations (id),
    course_id  uuid not null references courses (id),
    source_key varchar(1024) not null,
    status     varchar(16) not null,
    version    bigint not null default 0
);

create table video_renditions (
    id           uuid primary key,
    tenant_id    uuid not null references organizations (id),
    asset_id     uuid not null references video_assets (id),
    height       int  not null check (height >= 1),
    bitrate_kbps int  not null check (bitrate_kbps >= 1),
    playlist_key varchar(1024) not null,
    unique (tenant_id, asset_id, height)   -- makes "package the 720p rung" idempotent
);

create table transcode_jobs (
    id              uuid primary key,
    tenant_id       uuid not null references organizations (id),
    asset_id        uuid not null references video_assets (id),
    idempotency_key varchar(255) not null,
    state           varchar(16) not null,
    attempts        int not null default 0,
    unique (tenant_id, idempotency_key)    -- makes "enqueue transcode" idempotent
);

create index idx_video_assets_tenant     on video_assets (tenant_id);
create index idx_video_renditions_tenant on video_renditions (tenant_id);
create index idx_transcode_jobs_tenant   on transcode_jobs (tenant_id);
create index idx_video_renditions_asset  on video_renditions (asset_id);
create index idx_transcode_jobs_asset    on transcode_jobs (asset_id);

-- Defense in depth, mirroring V1: even a query that "forgets" the tenant filter
-- cannot read another tenant's media. The app sets the GUC `app.tenant_id` per
-- transaction; the policy makes a cross-tenant leak structurally impossible.
alter table video_assets     enable row level security;
alter table video_renditions enable row level security;
alter table transcode_jobs   enable row level security;

create policy tenant_isolation on video_assets
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on video_renditions
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on transcode_jobs
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
