\set ON_ERROR_STOP on
-- Run on event_db as event_owner or a role allowed to SET ROLE event_owner.
-- Run in psql without --single-transaction.
SET ROLE event_owner;

CREATE INDEX CONCURRENTLY IF NOT EXISTS seats_event_idx
  ON event_schema.tbl_event_seats (event_id);

DO $migration$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_catalog.pg_index i
    JOIN pg_catalog.pg_class c ON c.oid = i.indexrelid
    JOIN pg_catalog.pg_am a ON a.oid = c.relam
    WHERE i.indexrelid = 'event_schema.seats_event_idx'::regclass
      AND i.indrelid = 'event_schema.tbl_event_seats'::regclass
      AND i.indisready
      AND i.indisvalid
      AND a.amname = 'btree'
      AND i.indnatts = 1
      AND i.indpred IS NULL
      AND pg_catalog.pg_get_indexdef(i.indexrelid, 1, false) = 'event_id'
  ) THEN
    RAISE EXCEPTION 'seats_event_idx is invalid or does not match the expected event_id index.';
  END IF;
END;
$migration$;

RESET ROLE;
