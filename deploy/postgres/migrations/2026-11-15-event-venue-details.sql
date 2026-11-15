-- Run on event_db with permission to SET ROLE event_owner.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL ROLE event_owner;

ALTER TABLE event_schema.tbl_venues
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS image_url text,
  ADD COLUMN IF NOT EXISTS google_maps_url text;

COMMIT;
