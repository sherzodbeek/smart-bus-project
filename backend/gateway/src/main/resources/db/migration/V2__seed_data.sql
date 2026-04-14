-- Gateway service seed data

insert into gateway_buses (bus_id, plate_number, model, capacity, assigned_route, status)
values
  ('B-001', 'XA-1234-BC', 'Mercedes Citaro',      52, 'R-001 Downtown Express',      'ACTIVE'),
  ('B-007', 'XB-5678-DE', 'Volvo 7900 Electric',  45, 'R-003 University Line',        'ACTIVE'),
  ('B-012', 'XC-9012-FG', 'BYD K9',               40, 'R-007 Airport Shuttle',        'MAINTENANCE'),
  ('B-019', 'XD-3456-HI', 'MAN Lion''s City',     48, 'R-012 Midtown Circulator',     'ACTIVE')
on conflict do nothing;

insert into gateway_settings (section, payload)
values
  ('general',      '{"siteName":"SmartBus","siteEmail":"admin@smartbus.com","sitePhone":"+1 (555) 123-4567","timezone":"UTC-05:00 Eastern Time","currency":"USD ($)"}'),
  ('ticket',       '{"ticketPrefix":"SB-","bookingWindow":30,"cancelWindow":24,"maxPassengers":10,"allowRefund":true,"allowTransfer":true,"enableQR":false}'),
  ('notification', '{"emailNotif":true,"smsNotif":true,"delayNotif":true,"dailyReport":false,"weeklyReport":true,"reportEmail":"reports@smartbus.com"}'),
  ('security',     '{"sessionTimeout":30,"maxAttempts":5,"passMinLength":8,"twoFactor":false,"forceSSL":true}')
on conflict (section) do nothing;
