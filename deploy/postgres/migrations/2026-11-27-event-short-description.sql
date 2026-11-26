\set ON_ERROR_STOP on
\connect event_db
BEGIN;
SET LOCAL ROLE event_owner;
ALTER TABLE event_schema.tbl_events
  ADD COLUMN IF NOT EXISTS short_description text NOT NULL DEFAULT ''
  CHECK (char_length(short_description) <= 300);
COMMIT;
