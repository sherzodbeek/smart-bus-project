create table if not exists notification_deliveries (
  id                bigserial                 primary key,
  booking_reference varchar(32)               not null,
  recipient         varchar(255)              not null,
  route_code        varchar(64)               not null,
  status            varchar(32)               not null,
  delivered_at      timestamp with time zone  not null
);

create index if not exists idx_notification_deliveries_booking_reference
  on notification_deliveries (booking_reference);
