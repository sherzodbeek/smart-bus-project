-- Phase IV: store ML route recommendation results per customer.
-- One row per recommended route per request; requests are grouped by customer_email + created_at.

create table if not exists gateway_recommendations (
  id             bigserial          primary key,
  customer_email varchar(200)       not null,
  route_code     varchar(64)        not null,
  hybrid_score   double precision   not null,
  cf_score       double precision   not null,
  cb_score       double precision   not null,
  reason         varchar(64)        not null,
  confidence     varchar(32)        not null,
  model_version  varchar(32)        not null,
  is_cold_start  boolean            not null default false,
  created_at     timestamptz        not null default now()
);

create index if not exists idx_gateway_recommendations_email
  on gateway_recommendations (customer_email);

create index if not exists idx_gateway_recommendations_created_at
  on gateway_recommendations (created_at desc);
