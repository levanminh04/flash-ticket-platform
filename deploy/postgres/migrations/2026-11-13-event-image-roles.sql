-- Run on event_db with permission to SET ROLE event_owner.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL ROLE event_owner;

ALTER TABLE event_schema.tbl_event_images
  ADD COLUMN image_role text;

WITH first_images AS (
  SELECT DISTINCT ON (event_id) id
  FROM event_schema.tbl_event_images
  ORDER BY event_id, display_order, id
)
UPDATE event_schema.tbl_event_images AS image
SET image_role = CASE WHEN first_images.id IS NULL THEN 'GALLERY' ELSE 'BANNER' END,
    display_order = CASE WHEN first_images.id IS NULL THEN image.display_order ELSE 0 END
FROM event_schema.tbl_event_images AS old_image
LEFT JOIN first_images ON first_images.id = old_image.id
WHERE image.id = old_image.id;

-- Replace the legacy unique key, which remains named after the pre-prefix table.
ALTER TABLE event_schema.tbl_event_images
  DROP CONSTRAINT event_images_event_id_display_order_key,
  ALTER COLUMN image_role SET DEFAULT 'GALLERY',
  ALTER COLUMN image_role SET NOT NULL,
  ADD CONSTRAINT event_image_role_check CHECK (image_role = 'GALLERY' OR (image_role = 'BANNER' AND display_order = 0)),
  ADD CONSTRAINT event_image_role_order_key UNIQUE (event_id, image_role, display_order);

COMMIT;
