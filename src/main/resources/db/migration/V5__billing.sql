-- Part 7 schema: the Billing context. Plans, subscriptions (a state machine), entitlements (the access
-- record), and the webhook dedup table that makes processing idempotent. Money is stored as integer minor
-- units (cents), never a float. Every table is tenant-scoped and isolated exactly like the earlier parts:
-- a tenant_id column, an index on it, Hibernate @TenantId at the app layer, and PostgreSQL Row-Level
-- Security below as defense in depth — a cross-tenant billing leak would be a uniquely bad bug.

create table billing_plans (
    id              uuid primary key,
    tenant_id       uuid not null references organizations (id),
    name             varchar(256) not null,
    entitlement_key  varchar(256) not null,
    billing_interval varchar(16) not null,           -- 'interval' is reserved in Postgres/H2
    price_cents      bigint not null check (price_cents >= 0)
);

create table subscriptions (
    id           uuid primary key,
    tenant_id    uuid not null references organizations (id),
    learner_id   uuid not null references app_users (id),
    plan_id      uuid not null references billing_plans (id),
    provider_ref varchar(255) not null,
    status       varchar(16) not null,
    version      bigint not null default 0
);

create table entitlements (
    id                    uuid primary key,
    tenant_id             uuid not null references organizations (id),
    learner_id            uuid not null references app_users (id),
    entitlement_key       varchar(256) not null,
    active                boolean not null default false,
    source_subscription_id uuid,
    -- one entitlement per (tenant, learner, key): the access check is a single-row lookup, and the
    -- unique key keeps webhook re-processing from creating duplicate grants
    unique (tenant_id, learner_id, entitlement_key)
);

create table processed_webhooks (
    provider_event_id varchar(255) primary key,   -- the PSP's event id — the idempotency key
    tenant_id         uuid not null references organizations (id),
    processed_at      timestamptz not null
);

create index idx_billing_plans_tenant   on billing_plans (tenant_id);
create index idx_subscriptions_tenant   on subscriptions (tenant_id);
create index idx_entitlements_tenant    on entitlements (tenant_id);
create index idx_processed_webhooks_tenant on processed_webhooks (tenant_id);
create index idx_subscriptions_provider on subscriptions (provider_ref);
create index idx_entitlements_lookup    on entitlements (learner_id, entitlement_key);

alter table billing_plans     enable row level security;
alter table subscriptions     enable row level security;
alter table entitlements      enable row level security;
alter table processed_webhooks enable row level security;

create policy tenant_isolation on billing_plans
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on subscriptions
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on entitlements
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
create policy tenant_isolation on processed_webhooks
    using (tenant_id = current_setting('app.tenant_id', true)::uuid);
