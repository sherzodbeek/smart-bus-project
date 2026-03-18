create table if not exists gateway_users (
  id bigserial primary key,
  full_name varchar(200) not null,
  email varchar(200) not null unique,
  password_hash varchar(255) not null,
  role varchar(32) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table if exists gateway_users add column if not exists phone varchar(64);
alter table if exists gateway_users add column if not exists address varchar(255);
alter table if exists gateway_users add column if not exists language varchar(32) not null default 'English';
alter table if exists gateway_users add column if not exists status varchar(32) not null default 'ACTIVE';
alter table if exists gateway_users add column if not exists email_notifications boolean not null default true;
alter table if exists gateway_users add column if not exists sms_alerts boolean not null default true;
alter table if exists gateway_users add column if not exists push_notifications boolean not null default false;

create table if not exists gateway_contact_messages (
  id bigserial primary key,
  full_name varchar(200) not null,
  email varchar(200) not null,
  subject varchar(200) not null,
  message text not null,
  status varchar(32) not null default 'NEW',
  created_at timestamptz not null default now()
);

create table if not exists gateway_buses (
  id bigserial primary key,
  bus_id varchar(64) not null unique,
  plate_number varchar(64) not null unique,
  model varchar(200) not null,
  capacity integer not null,
  assigned_route varchar(200) not null,
  status varchar(32) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists gateway_settings (
  section varchar(64) primary key,
  payload text not null,
  updated_at timestamptz not null default now()
);

create table if not exists gateway_ticket_documents (
  booking_reference varchar(64) not null,
  owner_email varchar(200) not null,
  file_name varchar(255) not null,
  content_type varchar(128) not null,
  content bytea not null,
  size_bytes bigint not null,
  created_at timestamptz not null default now(),
  primary key (booking_reference, owner_email)
);

insert into gateway_buses (bus_id, plate_number, model, capacity, assigned_route, status)
values
  ('B-001', 'XA-1234-BC', 'Mercedes Citaro', 52, 'R-001 Downtown Express', 'ACTIVE'),
  ('B-007', 'XB-5678-DE', 'Volvo 7900 Electric', 45, 'R-003 University Line', 'ACTIVE'),
  ('B-012', 'XC-9012-FG', 'BYD K9', 40, 'R-007 Airport Shuttle', 'MAINTENANCE'),
  ('B-019', 'XD-3456-HI', 'MAN Lion''s City', 48, 'R-012 Midtown Circulator', 'ACTIVE')
on conflict do nothing;

insert into gateway_settings (section, payload)
values
  ('general', '{"siteName":"SmartBus","siteEmail":"admin@smartbus.com","sitePhone":"+1 (555) 123-4567","timezone":"UTC-05:00 Eastern Time","currency":"USD ($)"}'),
  ('ticket', '{"ticketPrefix":"SB-","bookingWindow":30,"cancelWindow":24,"maxPassengers":10,"allowRefund":true,"allowTransfer":true,"enableQR":false}'),
  ('notification', '{"emailNotif":true,"smsNotif":true,"delayNotif":true,"dailyReport":false,"weeklyReport":true,"reportEmail":"reports@smartbus.com"}'),
  ('security', '{"sessionTimeout":30,"maxAttempts":5,"passMinLength":8,"twoFactor":false,"forceSSL":true}')
on conflict (section) do nothing;
