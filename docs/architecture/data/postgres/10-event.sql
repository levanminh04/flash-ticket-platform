\set ON_ERROR_STOP on
\connect event_db
BEGIN;
SET LOCAL ROLE event_owner;
SET LOCAL search_path = event_schema, pg_catalog;
CREATE DOMAIN nonempty_text AS text CHECK (btrim(VALUE) <> '');
CREATE DOMAIN currency_code AS text CHECK (VALUE = 'VND');
CREATE DOMAIN money_amount AS numeric(19,0) CHECK (VALUE >= 0 AND VALUE <> 'NaN'::numeric);

CREATE TABLE categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name nonempty_text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE venues (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name nonempty_text NOT NULL,
  address nonempty_text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), organizer_subject nonempty_text NOT NULL,
  category_id uuid REFERENCES categories(id), venue_id uuid REFERENCES venues(id),
  title nonempty_text NOT NULL, description text NOT NULL DEFAULT '',
  sales_mode text NOT NULL CHECK (sales_mode IN ('QUANTITY','SEAT_MAP')),
  status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','PUBLISHED','CANCELLED')),
  starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
  sale_starts_at timestamptz NOT NULL, sale_ends_at timestamptz NOT NULL,
  checkin_starts_at timestamptz NOT NULL, checkin_ends_at timestamptz NOT NULL,
  purchase_limit integer NOT NULL CHECK (purchase_limit > 0),
  fee_rate numeric(9,8) CHECK (fee_rate BETWEEN 0 AND 1),
  approved_by nonempty_text, approved_at timestamptz,
  source_config_version bigint NOT NULL DEFAULT 1 CHECK (source_config_version > 0),
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (id, sales_mode),
  CHECK (starts_at < ends_at AND sale_starts_at < sale_ends_at AND checkin_starts_at < checkin_ends_at),
  CHECK (sale_ends_at <= ends_at),
  CHECK (checkin_starts_at = starts_at AND checkin_ends_at = ends_at),
  CHECK ((approved_by IS NULL AND approved_at IS NULL AND fee_rate IS NULL) OR
         (approved_by IS NOT NULL AND approved_at IS NOT NULL AND fee_rate IS NOT NULL)),
  CHECK (status NOT IN ('APPROVED','PUBLISHED') OR approved_at IS NOT NULL)
);
CREATE TABLE event_images (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL REFERENCES events(id),
  object_reference nonempty_text NOT NULL, display_order integer NOT NULL CHECK (display_order >= 0),
  UNIQUE (event_id, display_order)
);
CREATE TABLE event_layouts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL REFERENCES events(id),
  name nonempty_text NOT NULL,
  canvas_width integer NOT NULL CHECK (canvas_width > 0), canvas_height integer NOT NULL CHECK (canvas_height > 0),
  background_image_reference text,
  layout_data jsonb NOT NULL CHECK (jsonb_typeof(layout_data) = 'object' AND layout_data @> '{"schemaVersion":1}' AND jsonb_typeof(layout_data->'decorations') = 'array' AND layout_data ? 'decorations'),
  UNIQUE (id, event_id), UNIQUE (event_id)
);
CREATE TABLE event_sectors (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL, layout_id uuid NOT NULL,
  name nonempty_text NOT NULL, sector_type text NOT NULL CHECK (sector_type IN ('SEATED','STANDING')),
  configured_capacity bigint NOT NULL CHECK (configured_capacity >= 0),
  code text, display_order integer NOT NULL DEFAULT 0 CHECK (display_order >= 0),
  color_code varchar(7) CHECK (color_code ~ '^#[0-9A-Fa-f]{6}$'),
  geometry jsonb NOT NULL CHECK (jsonb_typeof(geometry) = 'object' AND geometry @> '{"schemaVersion":1}' AND geometry ? 'shape' AND jsonb_typeof(geometry->'shape') = 'object'),
  FOREIGN KEY (layout_id, event_id) REFERENCES event_layouts(id, event_id),
  UNIQUE (id, event_id), UNIQUE (id, event_id, sector_type)
);
CREATE TABLE event_seats (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL, sector_id uuid NOT NULL,
  sector_type text NOT NULL DEFAULT 'SEATED' CHECK (sector_type = 'SEATED'),
  seat_label nonempty_text NOT NULL,
  row_name nonempty_text NOT NULL, seat_number nonempty_text NOT NULL,
  coord_x numeric(10,2) NOT NULL CHECK (coord_x <> 'NaN'::numeric), coord_y numeric(10,2) NOT NULL CHECK (coord_y <> 'NaN'::numeric),
  ticket_type_id uuid, is_hidden boolean NOT NULL DEFAULT false,
  FOREIGN KEY (sector_id, event_id, sector_type) REFERENCES event_sectors(id, event_id, sector_type),
  UNIQUE (sector_id, seat_label), UNIQUE (id, event_id, sector_id)
);
CREATE TABLE ticket_types (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL,
  name nonempty_text NOT NULL, sales_mode text NOT NULL, sector_id uuid,
  color_code varchar(7) CHECK (color_code ~ '^#[0-9A-Fa-f]{6}$'),
  unit_price money_amount NOT NULL CHECK (unit_price > 0), currency currency_code NOT NULL,
  configured_quantity bigint CHECK (configured_quantity >= 0),
  FOREIGN KEY (event_id, sales_mode) REFERENCES events(id, sales_mode),
  FOREIGN KEY (sector_id, event_id) REFERENCES event_sectors(id, event_id),
  CHECK ((sales_mode = 'QUANTITY' AND sector_id IS NULL AND configured_quantity IS NOT NULL) OR
         (sales_mode = 'SEAT_MAP' AND sector_id IS NOT NULL AND configured_quantity IS NULL)),
  UNIQUE (id, event_id), UNIQUE (id, event_id, sector_id)
);
ALTER TABLE event_seats ADD CONSTRAINT seats_ticket_type_same_sector_fk FOREIGN KEY (ticket_type_id, event_id, sector_id) REFERENCES ticket_types(id, event_id, sector_id);
CREATE TABLE promotions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL REFERENCES events(id),
  code nonempty_text NOT NULL, starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
  discount_kind text NOT NULL CHECK (discount_kind IN ('FIXED','PERCENT')),
  fixed_discount money_amount, currency currency_code, discount_rate numeric(9,8),
  maximum_uses bigint NOT NULL CHECK (maximum_uses > 0),
  maximum_uses_per_user integer NOT NULL DEFAULT 1 CHECK (maximum_uses_per_user = 1),
  CHECK (starts_at < ends_at),
  CHECK (code = upper(btrim(code))),
  CHECK (discount_rate IS NULL OR discount_rate * 100 = trunc(discount_rate * 100)),
  CHECK ((discount_kind = 'FIXED' AND fixed_discount IS NOT NULL AND currency IS NOT NULL AND discount_rate IS NULL) OR
         (discount_kind = 'PERCENT' AND fixed_discount IS NULL AND currency IS NULL AND discount_rate IS NOT NULL AND discount_rate > 0 AND discount_rate <= 1)),
  UNIQUE (id, event_id), UNIQUE (event_id, code)
);
-- API canonicalizes codes using Locale.ROOT before validation and persistence.
CREATE TABLE event_cancellation_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL REFERENCES events(id),
  requested_by nonempty_text NOT NULL, requested_at timestamptz NOT NULL DEFAULT now(),
  reason nonempty_text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  reviewed_by nonempty_text, reviewed_at timestamptz, review_reason nonempty_text,
  CHECK ((status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR
         (status <> 'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);
CREATE INDEX events_organizer_idx ON events (organizer_subject, created_at);
CREATE INDEX events_public_idx ON events (starts_at, id) WHERE status = 'PUBLISHED';
CREATE INDEX events_category_idx ON events (category_id);
CREATE INDEX events_venue_idx ON events (venue_id);
CREATE INDEX layouts_event_idx ON event_layouts(event_id);
CREATE INDEX sectors_layout_idx ON event_sectors(layout_id, event_id);
CREATE INDEX ticket_types_event_idx ON ticket_types(event_id);
CREATE INDEX promotions_event_idx ON promotions(event_id, starts_at, ends_at);
CREATE INDEX cancellation_event_idx ON event_cancellation_requests(event_id, requested_at);
CREATE UNIQUE INDEX cancellation_one_pending_uq ON event_cancellation_requests(event_id) WHERE status = 'PENDING';
COMMENT ON TABLE events IS 'B12-v0.1 candidate: lifecycle transitions/configuration immutability require service transactions; no venue capacity invariant.';
COMMENT ON TABLE event_cancellation_requests IS 'History retained; one PENDING/event. Rejected requests can be resubmitted; cancelled event rejects new requests in service transaction.';
-- Runtime DELETE is restricted to editor-owned child tables and DRAFT event rows.
-- Locking the parent serializes DELETE with submission; the API must still authorize organizer ownership.
CREATE FUNCTION guard_draft_child_delete() RETURNS trigger LANGUAGE plpgsql
SET search_path = event_schema, pg_catalog AS $body$
DECLARE current_status text;
BEGIN
  SELECT status INTO current_status FROM events WHERE id = OLD.event_id FOR UPDATE;
  IF current_status IS DISTINCT FROM 'DRAFT' THEN
    RAISE EXCEPTION 'Only DRAFT event configuration can be deleted' USING ERRCODE = '23514';
  END IF;
  RETURN OLD;
END $body$;
CREATE TRIGGER draft_delete_guard BEFORE DELETE ON event_images FOR EACH ROW EXECUTE FUNCTION guard_draft_child_delete();
CREATE TRIGGER draft_delete_guard BEFORE DELETE ON event_layouts FOR EACH ROW EXECUTE FUNCTION guard_draft_child_delete();
CREATE TRIGGER draft_delete_guard BEFORE DELETE ON event_sectors FOR EACH ROW EXECUTE FUNCTION guard_draft_child_delete();
CREATE TRIGGER draft_delete_guard BEFORE DELETE ON event_seats FOR EACH ROW EXECUTE FUNCTION guard_draft_child_delete();
CREATE TRIGGER draft_delete_guard BEFORE DELETE ON ticket_types FOR EACH ROW EXECUTE FUNCTION guard_draft_child_delete();
CREATE TRIGGER draft_delete_guard BEFORE DELETE ON promotions FOR EACH ROW EXECUTE FUNCTION guard_draft_child_delete();
REVOKE ALL ON FUNCTION guard_draft_child_delete() FROM PUBLIC;
CREATE TABLE outbox_messages (
  message_id uuid PRIMARY KEY, aggregate_id uuid NOT NULL,
  aggregate_version bigint NOT NULL CHECK (aggregate_version > 0),
  message_type nonempty_text NOT NULL, schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version = 1),
  payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
  occurred_at timestamptz NOT NULL, correlation_id uuid NOT NULL, causation_id uuid,
  traceparent text, published_at timestamptz,
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  next_attempt_at timestamptz NOT NULL DEFAULT now(), last_error_code text
);
CREATE INDEX outbox_pending_idx ON outbox_messages(next_attempt_at, occurred_at) WHERE published_at IS NULL;
CREATE TABLE inbox_messages (
  consumer_name nonempty_text NOT NULL, message_id uuid NOT NULL,
  payload_hash nonempty_text NOT NULL, processed_at timestamptz NOT NULL DEFAULT now(),
  response_payload jsonb CHECK (jsonb_typeof(response_payload) = 'object'),
  PRIMARY KEY (consumer_name, message_id)
);
COMMENT ON TABLE outbox_messages IS 'Business update and outgoing event/command commit together. Trade-off: relay and duplicate delivery; published_at only after broker confirm. Payload follows B13 named versioned contract, not arbitrary business JSON.';
COMMENT ON TABLE inbox_messages IS 'Deduplication by consumer and message ID in same transaction as effect and response outbox. Compare payload hash on replay; different hash rejects. Trade-off: durable rows/retention; not exactly-once network delivery.';
COMMIT;
