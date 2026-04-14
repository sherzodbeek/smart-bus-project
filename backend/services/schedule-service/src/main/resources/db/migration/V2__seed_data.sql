-- Schedule service seed data

insert into schedule_locations (name)
values
  ('Downtown Terminal'),
  ('Airport Station'),
  ('Central Park'),
  ('University Campus'),
  ('Riverside Mall'),
  ('Tech Park')
on conflict do nothing;

insert into schedule_routes (route_code, from_location_id, to_location_id, departure_time, arrival_time, unit_price, seats_available)
select 'SB-101', f.id, t.id, '08:00 AM', '09:15 AM', 12.50, 24
from schedule_locations f, schedule_locations t
where f.name = 'Downtown Terminal' and t.name = 'Airport Station'
on conflict do nothing;

insert into schedule_routes (route_code, from_location_id, to_location_id, departure_time, arrival_time, unit_price, seats_available)
select 'SB-202', f.id, t.id, '09:00 AM', '09:35 AM', 8.00, 25
from schedule_locations f, schedule_locations t
where f.name = 'Central Park' and t.name = 'University Campus'
on conflict do nothing;

insert into schedule_routes (route_code, from_location_id, to_location_id, departure_time, arrival_time, unit_price, seats_available)
select 'SB-303', f.id, t.id, '12:15 PM', '12:55 PM', 10.00, 14
from schedule_locations f, schedule_locations t
where f.name = 'Riverside Mall' and t.name = 'Tech Park'
on conflict do nothing;
