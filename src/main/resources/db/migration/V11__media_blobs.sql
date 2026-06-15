-- Part 15 schema: media blobs (demo image store for the lesson block editor). Tenant-scoped + RLS like
-- everything else. This is the demo stand-in for Part 3's presigned-upload / signed-CDN pipeline — only
-- small images land here; video/audio/PDF/documents are referenced by URL, never stored in the app.

create table media_blobs (
    id           uuid primary key,
    tenant_id    uuid not null references organizations (id),
    content_type varchar(255) not null,
    filename     varchar(512) not null,
    data_b64     text not null,            -- base64-encoded image bytes (demo store)
    created_at   timestamptz not null
);

create index idx_media_blobs_tenant on media_blobs (tenant_id);

alter table media_blobs enable row level security;

create policy tenant_isolation on media_blobs
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
