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
select 'SB-101', from_location.id, to_location.id, '08:00 AM', '09:15 AM', 12.50, 24
from schedule_locations from_location, schedule_locations to_location
where from_location.name = 'Downtown Terminal' and to_location.name = 'Airport Station'
on conflict do nothing;

insert into schedule_routes (route_code, from_location_id, to_location_id, departure_time, arrival_time, unit_price, seats_available)
select 'SB-202', from_location.id, to_location.id, '09:00 AM', '09:35 AM', 8.00, 25
from schedule_locations from_location, schedule_locations to_location
where from_location.name = 'Central Park' and to_location.name = 'University Campus'
on conflict do nothing;

insert into schedule_routes (route_code, from_location_id, to_location_id, departure_time, arrival_time, unit_price, seats_available)
select 'SB-303', from_location.id, to_location.id, '12:15 PM', '12:55 PM', 10.00, 14
from schedule_locations from_location, schedule_locations to_location
where from_location.name = 'Riverside Mall' and to_location.name = 'Tech Park'
on conflict do nothing;
