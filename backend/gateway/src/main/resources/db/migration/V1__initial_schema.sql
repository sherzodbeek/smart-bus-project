-- Gateway service initial schema
-- Replaces schema.sql spring.sql.init approach; Flyway owns all DDL from this point.

create table if not exists gateway_users (
  id                   bigserial     primary key,
  full_name            varchar(200)  not null,
  email                varchar(200)  not null unique,
  password_hash        varchar(255)  not null,
  role                 varchar(32)   not null,
  phone                varchar(64),
  address              varchar(255),
  language             varchar(32)   not null default 'English',
  status               varchar(32)   not null default 'ACTIVE',
  email_notifications  boolean       not null default true,
  sms_alerts           boolean       not null default true,
  push_notifications   boolean       not null default false,
  created_at           timestamptz   not null default now(),
  updated_at           timestamptz   not null default now()
);

create index if not exists idx_gateway_users_email
  on gateway_users (email);

create table if not exists gateway_contact_messages (
  id         bigserial     primary key,
  full_name  varchar(200)  not null,
  email      varchar(200)  not null,
  subject    varchar(200)  not null,
  message    text          not null,
  status     varchar(32)   not null default 'NEW',
  created_at timestamptz   not null default now()
);

create index if not exists idx_gateway_contact_messages_email
  on gateway_contact_messages (email);

create table if not exists gateway_buses (
  id             bigserial     primary key,
  bus_id         varchar(64)   not null unique,
  plate_number   varchar(64)   not null unique,
  model          varchar(200)  not null,
  capacity       integer       not null,
  assigned_route varchar(200)  not null,
  status         varchar(32)   not null,
  created_at     timestamptz   not null default now(),
  updated_at     timestamptz   not null default now()
);

create table if not exists gateway_settings (
  section    varchar(64)  primary key,
  payload    text         not null,
  updated_at timestamptz  not null default now()
);

create table if not exists gateway_ticket_documents (
  booking_reference varchar(64)   not null,
  owner_email       varchar(200)  not null,
  file_name         varchar(255)  not null,
  content_type      varchar(128)  not null,
  content           bytea         not null,
  size_bytes        bigint        not null,
  created_at        timestamptz   not null default now(),
  primary key (booking_reference, owner_email)
);
