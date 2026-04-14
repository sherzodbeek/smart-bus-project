-- Payment service initial schema

create table if not exists payment_records (
  transaction_id    varchar(64)               primary key,
  booking_reference varchar(32)               not null,
  customer_email    varchar(255)              not null,
  amount            double precision          not null,
  status            varchar(32)               not null,
  reason            varchar(256),
  created_at        timestamp with time zone  not null
);

create index if not exists idx_payment_records_booking_reference
  on payment_records (booking_reference);

create index if not exists idx_payment_records_customer_email
  on payment_records (customer_email);

create index if not exists idx_payment_records_status
  on payment_records (status);
