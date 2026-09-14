\set ON_ERROR_STOP on
\connect booking_db
BEGIN;
SET LOCAL ROLE booking_owner;
SET LOCAL search_path = booking_schema, pg_catalog;
CREATE DOMAIN nonempty_text AS text CHECK (btrim(VALUE) <> '');
CREATE DOMAIN currency_code AS text CHECK (VALUE = 'VND');
CREATE DOMAIN money_amount AS numeric(19,0) CHECK (VALUE >= 0 AND VALUE <> 'NaN'::numeric);

CREATE TABLE event_sales_snapshots (
  event_id uuid PRIMARY KEY, organizer_subject nonempty_text NOT NULL,
  sales_mode text NOT NULL CHECK (sales_mode IN ('QUANTITY','SEAT_MAP')),
  event_status nonempty_text NOT NULL, sale_starts_at timestamptz NOT NULL, sale_ends_at timestamptz NOT NULL,
  purchase_limit integer NOT NULL CHECK (purchase_limit > 0),
  configuration_snapshot jsonb NOT NULL CHECK (jsonb_typeof(configuration_snapshot) = 'object' AND configuration_snapshot @> '{"schemaVersion":1}'),
  source_config_version bigint NOT NULL CHECK (source_config_version > 0), received_at timestamptz NOT NULL,
  CHECK (sale_starts_at < sale_ends_at), UNIQUE (event_id, sales_mode)
);
CREATE TABLE orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL, buyer_subject nonempty_text NOT NULL,
  sales_mode text NOT NULL, sector_id uuid,
  lifecycle_state text NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK (lifecycle_state IN ('PENDING_PAYMENT','ISSUING','COMPLETED','CANCELLED','EXPIRED','ISSUANCE_FAILED')),
  customer_email nonempty_text NOT NULL CHECK (customer_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'),
  payment_frozen_at timestamptz, payment_snapshot_version bigint CHECK (payment_snapshot_version > 0),
  frozen_purchase_snapshot jsonb CHECK (jsonb_typeof(frozen_purchase_snapshot) = 'object' AND frozen_purchase_snapshot @> '{"schemaVersion":1}'),
  accepted_charge_id uuid UNIQUE, accepted_at timestamptz,
  subtotal money_amount NOT NULL, discount money_amount NOT NULL DEFAULT 0, total money_amount NOT NULL CHECK (total > 0),
  currency currency_code NOT NULL, expires_at timestamptz NOT NULL,
  source_config_version bigint NOT NULL CHECK (source_config_version > 0),
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (event_id, sales_mode) REFERENCES event_sales_snapshots(event_id, sales_mode),
  CHECK ((sales_mode = 'QUANTITY' AND sector_id IS NULL) OR (sales_mode = 'SEAT_MAP' AND sector_id IS NOT NULL)),
  CHECK (discount <= subtotal AND total = subtotal - discount), CHECK (created_at < expires_at),
  CHECK ((payment_frozen_at IS NULL) = (payment_snapshot_version IS NULL)),
  CHECK ((payment_frozen_at IS NULL) = (frozen_purchase_snapshot IS NULL)),
  CHECK (payment_frozen_at IS NULL OR (created_at <= payment_frozen_at AND payment_frozen_at < expires_at)),
  CHECK ((accepted_charge_id IS NULL) = (accepted_at IS NULL)),
  CHECK (accepted_at IS NULL OR (payment_frozen_at IS NOT NULL AND accepted_at < expires_at)),
  CHECK (lifecycle_state NOT IN ('ISSUING','COMPLETED','ISSUANCE_FAILED') OR accepted_charge_id IS NOT NULL),
  UNIQUE (id, event_id), UNIQUE (id, event_id, buyer_subject), UNIQUE (id, event_id, sector_id), UNIQUE (id, currency),
  UNIQUE (id, sales_mode), UNIQUE (id, expires_at)
);
CREATE TABLE order_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), order_id uuid NOT NULL, event_id uuid NOT NULL,
  ticket_type_id uuid NOT NULL, sector_id uuid, sales_mode text NOT NULL, ticket_type_name nonempty_text NOT NULL,
  quantity integer NOT NULL CHECK (quantity > 0), unit_price money_amount NOT NULL CHECK (unit_price > 0),
  line_subtotal money_amount NOT NULL, currency currency_code NOT NULL,
  selection_snapshot jsonb NOT NULL CHECK (jsonb_typeof(selection_snapshot) = 'object' AND selection_snapshot @> '{"schemaVersion":1}'),
  FOREIGN KEY (order_id, event_id) REFERENCES orders(id, event_id),
  FOREIGN KEY (order_id, event_id, sector_id) REFERENCES orders(id, event_id, sector_id),
  FOREIGN KEY (order_id, currency) REFERENCES orders(id, currency),
  FOREIGN KEY (order_id, sales_mode) REFERENCES orders(id, sales_mode),
  CHECK ((sales_mode = 'QUANTITY' AND sector_id IS NULL) OR (sales_mode = 'SEAT_MAP' AND sector_id IS NOT NULL)),
  CHECK (line_subtotal = quantity * unit_price),
  UNIQUE (id, order_id, event_id), UNIQUE (order_id, ticket_type_id)
);
CREATE TABLE reservations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), order_id uuid NOT NULL UNIQUE, event_id uuid NOT NULL,
  buyer_subject nonempty_text NOT NULL,
  state text NOT NULL CHECK (state IN ('HELD','COMMITTED','RELEASED')),
  expires_at timestamptz NOT NULL, row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (order_id, event_id, buyer_subject) REFERENCES orders(id, event_id, buyer_subject),
  FOREIGN KEY (order_id, expires_at) REFERENCES orders(id, expires_at),
  UNIQUE (id, event_id), UNIQUE (id, order_id, event_id), UNIQUE (id, event_id, buyer_subject), UNIQUE (id, expires_at)
);
CREATE TABLE ticket_type_inventory (
  ticket_type_id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES event_sales_snapshots(event_id),
  configured_capacity bigint NOT NULL CHECK (configured_capacity >= 0),
  held bigint NOT NULL DEFAULT 0 CHECK (held >= 0), purchased bigint NOT NULL DEFAULT 0 CHECK (purchased >= 0),
  source_config_version bigint NOT NULL CHECK (source_config_version > 0), row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  CHECK (purchased <= configured_capacity AND held <= configured_capacity - purchased), UNIQUE (ticket_type_id, event_id)
);
CREATE TABLE sector_inventory (
  sector_id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES event_sales_snapshots(event_id),
  sector_type text NOT NULL CHECK (sector_type IN ('SEATED','STANDING')),
  configured_capacity bigint NOT NULL CHECK (configured_capacity >= 0),
  held bigint NOT NULL DEFAULT 0 CHECK (held >= 0), purchased bigint NOT NULL DEFAULT 0 CHECK (purchased >= 0),
  source_config_version bigint NOT NULL CHECK (source_config_version > 0), row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  CHECK (purchased <= configured_capacity AND held <= configured_capacity - purchased),
  UNIQUE (sector_id, event_id), UNIQUE (sector_id, event_id, sector_type)
);
ALTER TABLE orders ADD CONSTRAINT orders_sector_fk FOREIGN KEY (sector_id, event_id) REFERENCES sector_inventory(sector_id, event_id);
CREATE TABLE seat_inventory (
  seat_id uuid PRIMARY KEY, event_id uuid NOT NULL, sector_id uuid NOT NULL,
  ticket_type_id uuid, is_hidden boolean NOT NULL DEFAULT false,
  sector_type text NOT NULL DEFAULT 'SEATED' CHECK (sector_type = 'SEATED'), seat_label nonempty_text NOT NULL,
  state text NOT NULL DEFAULT 'AVAILABLE' CHECK (state IN ('AVAILABLE','HELD','PURCHASED','UNAVAILABLE')),
  current_reservation_id uuid, reservation_generation bigint NOT NULL DEFAULT 0 CHECK (reservation_generation >= 0),
  source_config_version bigint NOT NULL CHECK (source_config_version > 0), row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  FOREIGN KEY (sector_id, event_id, sector_type) REFERENCES sector_inventory(sector_id, event_id, sector_type),
  FOREIGN KEY (current_reservation_id, event_id) REFERENCES reservations(id, event_id),
  CHECK ((state IN ('HELD','PURCHASED') AND current_reservation_id IS NOT NULL AND reservation_generation > 0) OR
         (state IN ('AVAILABLE','UNAVAILABLE') AND current_reservation_id IS NULL)),
  CHECK (NOT is_hidden OR state = 'UNAVAILABLE'),
  CHECK (state = 'UNAVAILABLE' OR ticket_type_id IS NOT NULL),
  UNIQUE (seat_id, event_id), UNIQUE (seat_id, event_id, sector_id), UNIQUE (sector_id, seat_label)
);
CREATE TABLE order_item_seats (
  order_item_id uuid NOT NULL, order_id uuid NOT NULL, event_id uuid NOT NULL, sector_id uuid NOT NULL, seat_id uuid NOT NULL,
  PRIMARY KEY (order_item_id, seat_id), UNIQUE (order_id, seat_id),
  FOREIGN KEY (order_item_id, order_id, event_id) REFERENCES order_items(id, order_id, event_id),
  FOREIGN KEY (order_id, event_id, sector_id) REFERENCES orders(id, event_id, sector_id),
  FOREIGN KEY (seat_id, event_id, sector_id) REFERENCES seat_inventory(seat_id, event_id, sector_id)
);
CREATE TABLE reservation_allocations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), reservation_id uuid NOT NULL, order_id uuid NOT NULL,
  order_item_id uuid NOT NULL, event_id uuid NOT NULL,
  resource_kind text NOT NULL CHECK (resource_kind IN ('TICKET_TYPE','STANDING_SECTOR','SEAT')),
  ticket_type_id uuid, sector_id uuid, seat_id uuid,
  quantity integer NOT NULL CHECK (quantity > 0),
  state text NOT NULL CHECK (state IN ('HELD','COMMITTED','RELEASED')),
  FOREIGN KEY (reservation_id, order_id, event_id) REFERENCES reservations(id, order_id, event_id),
  FOREIGN KEY (order_item_id, order_id, event_id) REFERENCES order_items(id, order_id, event_id),
  FOREIGN KEY (ticket_type_id, event_id) REFERENCES ticket_type_inventory(ticket_type_id, event_id),
  FOREIGN KEY (sector_id, event_id) REFERENCES sector_inventory(sector_id, event_id),
  FOREIGN KEY (seat_id, event_id) REFERENCES seat_inventory(seat_id, event_id),
  CHECK ((resource_kind = 'TICKET_TYPE' AND ticket_type_id IS NOT NULL AND sector_id IS NULL AND seat_id IS NULL) OR
         (resource_kind = 'STANDING_SECTOR' AND ticket_type_id IS NULL AND sector_id IS NOT NULL AND seat_id IS NULL) OR
         (resource_kind = 'SEAT' AND ticket_type_id IS NULL AND sector_id IS NULL AND seat_id IS NOT NULL AND quantity = 1))
);
CREATE UNIQUE INDEX allocations_ticket_type_uq ON reservation_allocations(reservation_id, order_item_id, ticket_type_id) WHERE resource_kind = 'TICKET_TYPE';
CREATE UNIQUE INDEX allocations_sector_uq ON reservation_allocations(reservation_id, order_item_id, sector_id) WHERE resource_kind = 'STANDING_SECTOR';
CREATE UNIQUE INDEX allocations_seat_uq ON reservation_allocations(reservation_id, seat_id) WHERE resource_kind = 'SEAT';
CREATE TABLE purchase_limits (
  event_id uuid NOT NULL REFERENCES event_sales_snapshots(event_id), buyer_subject nonempty_text NOT NULL,
  configured_limit integer NOT NULL CHECK (configured_limit > 0),
  held integer NOT NULL DEFAULT 0 CHECK (held >= 0), purchased integer NOT NULL DEFAULT 0 CHECK (purchased >= 0),
  source_config_version bigint NOT NULL CHECK (source_config_version > 0), row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  PRIMARY KEY (event_id, buyer_subject), CHECK (purchased <= configured_limit AND held <= configured_limit - purchased)
);
CREATE TABLE promotion_inventory (
  promotion_id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES event_sales_snapshots(event_id),
  configured_limit bigint NOT NULL CHECK (configured_limit > 0), per_user_limit integer NOT NULL DEFAULT 1 CHECK (per_user_limit = 1),
  held bigint NOT NULL DEFAULT 0 CHECK (held >= 0), used bigint NOT NULL DEFAULT 0 CHECK (used >= 0),
  starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL,
  configuration_snapshot jsonb NOT NULL CHECK (jsonb_typeof(configuration_snapshot) = 'object' AND configuration_snapshot @> '{"schemaVersion":1}'),
  source_config_version bigint NOT NULL CHECK (source_config_version > 0), row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  CHECK (starts_at < ends_at), CHECK (used <= configured_limit AND held <= configured_limit - used), UNIQUE (promotion_id, event_id)
);
CREATE TABLE promotion_usages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), promotion_id uuid NOT NULL, event_id uuid NOT NULL,
  reservation_id uuid NOT NULL, buyer_subject nonempty_text NOT NULL,
  generation bigint NOT NULL CHECK (generation > 0),
  state text NOT NULL CHECK (state IN ('HELD','USED','RELEASED')),
  discount money_amount NOT NULL, currency currency_code NOT NULL, expires_at timestamptz NOT NULL,
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  FOREIGN KEY (promotion_id, event_id) REFERENCES promotion_inventory(promotion_id, event_id),
  FOREIGN KEY (reservation_id, event_id, buyer_subject) REFERENCES reservations(id, event_id, buyer_subject),
  FOREIGN KEY (reservation_id, expires_at) REFERENCES reservations(id, expires_at),
  UNIQUE (reservation_id, generation)
);
CREATE UNIQUE INDEX promotion_one_active_per_reservation_uq ON promotion_usages(reservation_id) WHERE state IN ('HELD','USED');
CREATE UNIQUE INDEX promotion_one_active_per_buyer_uq ON promotion_usages(promotion_id, buyer_subject) WHERE state IN ('HELD','USED');
CREATE INDEX orders_buyer_idx ON orders(buyer_subject, created_at);
CREATE INDEX orders_event_idx ON orders(event_id, created_at);
CREATE INDEX orders_expiry_idx ON orders(expires_at);
CREATE INDEX reservations_expiry_idx ON reservations(expires_at) WHERE state = 'HELD';
CREATE INDEX allocations_item_idx ON reservation_allocations(order_item_id, order_id, event_id);
CREATE INDEX seats_reservation_idx ON seat_inventory(current_reservation_id) WHERE current_reservation_id IS NOT NULL;
CREATE INDEX promotion_usage_buyer_idx ON promotion_usages(promotion_id, buyer_subject, state);
COMMENT ON COLUMN orders.lifecycle_state IS 'S-01/GOV-136. One order row progresses through the lifecycle; legal transitions and inventory updates are one Booking transaction.';
COMMENT ON TABLE reservation_allocations IS 'Candidate: local FK identity only; mode, resource-to-item match, aggregate totals and exact-once release require transactional service validation.';
COMMENT ON TABLE sector_inventory IS 'SEATED capacity is a configuration bound; seat rows own availability. Do not debit both seat and sector counters.';
CREATE TABLE idempotency_requests (
  actor_subject nonempty_text NOT NULL, operation nonempty_text NOT NULL, request_key uuid NOT NULL,
  request_hash nonempty_text NOT NULL, resource_id uuid NOT NULL,
  response_status integer NOT NULL CHECK (response_status BETWEEN 200 AND 299),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (actor_subject, operation, request_key)
);
COMMENT ON TABLE idempotency_requests IS 'Create-order replay returns original order rather than creating another hold. Record success in order transaction; conflicting payload returns conflict. Trade-off: storage and retention, not a replacement for reservation state guards.';
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
