-- When an item was withdrawn. A pack's version changes on publish and on withdraw, so phones
-- that already downloaded it fetch the corrected pack.
ALTER TABLE public.content_item ADD COLUMN withdrawn_at TIMESTAMPTZ;
