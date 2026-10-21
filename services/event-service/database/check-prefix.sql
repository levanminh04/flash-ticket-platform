\set ON_ERROR_STOP on
-- Run as event_admin in event_db. All synthetic fixture changes roll back.
BEGIN;
SET LOCAL ROLE event_owner;
SET LOCAL search_path = event_schema, pg_catalog;
DO $check$
DECLARE
  event_key uuid := gen_random_uuid();
  image_key uuid := gen_random_uuid();
BEGIN
  INSERT INTO tbl_events(id, organizer_subject, title, sales_mode, starts_at, ends_at,
    sale_starts_at, sale_ends_at, checkin_starts_at, checkin_ends_at, purchase_limit)
  VALUES (event_key, 'prefix-check', 'Prefix check', 'QUANTITY',
    '2030-01-02 00:00Z', '2030-01-03 00:00Z', '2030-01-01 00:00Z', '2030-01-02 00:00Z',
    '2030-01-02 00:00Z', '2030-01-03 00:00Z', 5);
  INSERT INTO tbl_event_images(id, event_id, object_reference, display_order)
  VALUES (image_key, event_key, 'prefix-check', 0);
  DELETE FROM tbl_event_images WHERE id = image_key;
  IF FOUND IS NOT TRUE THEN RAISE EXCEPTION 'DRAFT deletion failed'; END IF;

  INSERT INTO tbl_event_images(id, event_id, object_reference, display_order)
  VALUES (image_key, event_key, 'prefix-check', 0);
  UPDATE tbl_events SET status = 'PENDING_APPROVAL' WHERE id = event_key;
  BEGIN
    DELETE FROM tbl_event_images WHERE id = image_key;
    RAISE EXCEPTION 'Expected non-DRAFT deletion to fail';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
  IF NOT EXISTS (SELECT FROM tbl_event_images WHERE id = image_key) THEN
    RAISE EXCEPTION 'Guard failed to preserve the image';
  END IF;
  RAISE NOTICE 'PASS: renamed Event trigger allows DRAFT and rejects non-DRAFT deletion';
END $check$;
ROLLBACK;
