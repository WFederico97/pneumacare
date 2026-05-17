INSERT INTO teams (name, city, country, created_at) VALUES
  ('River Plate',    'Buenos Aires', 'AR', now()),
  ('Boca Juniors',   'Buenos Aires', 'AR', now()),
  ('Racing Club',    'Avellaneda',   'AR', now()),
  ('Independiente',  'Avellaneda',   'AR', now()),
  ('San Lorenzo',    'Buenos Aires', 'AR', now()),
  ('Atletico Madrid','Madrid',       'ES', now()),
  ('FC Barcelona',   'Barcelona',    'ES', now()),
  ('Real Madrid',    'Madrid',       'ES', now()),
  ('Manchester City','Manchester',   'GB', now()),
  ('Arsenal',        'London',       'GB', now())
ON CONFLICT DO NOTHING;

