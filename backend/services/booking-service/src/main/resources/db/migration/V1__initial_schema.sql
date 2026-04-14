-- Booking service initial schema

create table if not exists booking_process_instances (
  booking_reference       varchar(32)               primary key,
  customer_name           varchar(255)              not null,
  customer_email          varchar(255)              not null,
  from_stop               varchar(255)              not null,
  to_stop                 varchar(255)              not null,
  trip_date               varchar(32)               not null,
  trip_type               varchar(32)               not null,
  passengers              integer                   not null,
  route_code              varchar(64),
  departure_time          varchar(64),
  arrival_time            varchar(64),
  total_amount            double precision,
  payment_transaction_id  varchar(64),
  notification_id         varchar(64),
  current_state           varchar(64)               not null,
  last_error              varchar(512),
  created_at              timestamp with time zone  not null,
  updated_at              timestamp with time zone  not null
);

create index if not exists idx_booking_process_instances_customer_email
  on booking_process_instances (customer_email);

create index if not exists idx_booking_process_instances_current_state
  on booking_process_instances (current_state);
