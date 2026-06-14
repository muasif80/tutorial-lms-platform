-- Part 11 schema: the Auth context. One global credentials table that Spring Security authenticates
-- against. It is deliberately NOT tenant-scoped and has NO Row-Level Security: login must resolve a
-- user by email before any tenant context exists, so this table is readable without a tenant. The
-- authenticated principal then carries the user's tenant_id, which pins the request's tenant context —
-- that is how isolation becomes driven by the logged-in identity rather than a trusted header.

create table credentials (
    id            uuid primary key,
    email         varchar(320) not null unique,
    password_hash varchar(100) not null,
    role          varchar(16)  not null,
    tenant_id     uuid not null references organizations (id),
    app_user_id   uuid not null references app_users (id),
    display_name  varchar(256) not null
);

create index idx_credentials_tenant on credentials (tenant_id);
