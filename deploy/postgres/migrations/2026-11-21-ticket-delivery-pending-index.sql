\set ON_ERROR_STOP on
-- Run on ticket_db as ticket_owner or a role allowed to SET ROLE ticket_owner.
-- Run in psql without --single-transaction.
SET ROLE ticket_owner;

CREATE INDEX CONCURRENTLY IF NOT EXISTS deliveries_pending_order_idx
  ON ticket_schema.tbl_ticket_deliveries (requested_at, id)
  WHERE finished_at IS NULL;

DO $migration$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_catalog.pg_index
    WHERE indexrelid = 'ticket_schema.deliveries_pending_order_idx'::regclass
      AND indrelid = 'ticket_schema.tbl_ticket_deliveries'::regclass
      AND indisready
      AND indisvalid
  ) THEN
    RAISE EXCEPTION 'deliveries_pending_order_idx is not ready and valid. Remove the invalid index before retrying.';
  END IF;
END;
$migration$;

RESET ROLE;
