\set ON_ERROR_STOP on
\connect ticket_db
BEGIN;
SET LOCAL ROLE ticket_owner;
SET LOCAL search_path = ticket_schema, pg_catalog;
CREATE DOMAIN nonempty_text AS text CHECK (btrim(VALUE) <> '');

CREATE TABLE event_access_snapshots (
  event_id uuid PRIMARY KEY, organizer_subject nonempty_text NOT NULL,
  event_status nonempty_text NOT NULL, event_starts_at timestamptz NOT NULL, event_ends_at timestamptz NOT NULL,
  source_config_version bigint NOT NULL CHECK (source_config_version > 0),
  received_at timestamptz NOT NULL, CHECK (event_starts_at < event_ends_at)
);
CREATE TABLE issuances (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), order_id uuid NOT NULL UNIQUE,
  event_id uuid NOT NULL REFERENCES event_access_snapshots(event_id), buyer_subject nonempty_text NOT NULL,
  payment_confirmation_id nonempty_text NOT NULL,
  customer_email nonempty_text NOT NULL,
  purchase_snapshot jsonb NOT NULL CHECK (jsonb_typeof(purchase_snapshot) = 'object' AND purchase_snapshot @> '{"schemaVersion":1}' AND purchase_snapshot ? 'items' AND jsonb_typeof(purchase_snapshot->'items') = 'array'),
  input_hash nonempty_text NOT NULL, expected_ticket_count integer NOT NULL CHECK (expected_ticket_count > 0),
  state text NOT NULL CHECK (state IN ('PENDING','COMPLETED','FAILED')),
  created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
  failed_at timestamptz, failure_code nonempty_text,
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  CHECK ((state = 'COMPLETED' AND completed_at IS NOT NULL) OR (state <> 'COMPLETED' AND completed_at IS NULL)),
  CHECK ((state = 'FAILED' AND failed_at IS NOT NULL AND failure_code IS NOT NULL) OR (state <> 'FAILED' AND failed_at IS NULL AND failure_code IS NULL)),
  UNIQUE (id, event_id), UNIQUE (id, order_id)
);
CREATE TABLE tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), issuance_id uuid NOT NULL, event_id uuid NOT NULL,
  order_item_id uuid NOT NULL, ordinal integer NOT NULL CHECK (ordinal > 0),
  holder_subject nonempty_text NOT NULL, ticket_type_id uuid NOT NULL, sector_id uuid, seat_id uuid,
  display_snapshot jsonb NOT NULL CHECK (jsonb_typeof(display_snapshot) = 'object' AND display_snapshot @> '{"schemaVersion":1}'),
  state text NOT NULL CHECK (state IN ('VALID','USED','VOID')),
  qr_token_ciphertext bytea NOT NULL CHECK (octet_length(qr_token_ciphertext) > 0),
  qr_key_version nonempty_text NOT NULL, qr_token_hash nonempty_text NOT NULL UNIQUE,
  used_at timestamptz, used_by nonempty_text, row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  FOREIGN KEY (issuance_id, event_id) REFERENCES issuances(id, event_id),
  CHECK (seat_id IS NULL OR sector_id IS NOT NULL),
  CHECK ((state = 'USED' AND used_at IS NOT NULL AND used_by IS NOT NULL) OR
         (state <> 'USED' AND used_at IS NULL AND used_by IS NULL)),
  UNIQUE (issuance_id, order_item_id, ordinal), UNIQUE (id, issuance_id)
);
CREATE TABLE ticket_deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), issuance_id uuid NOT NULL REFERENCES issuances(id),
  requested_by nonempty_text NOT NULL, channel nonempty_text NOT NULL,
  request_key uuid NOT NULL, request_hash nonempty_text NOT NULL,
  recipient_email nonempty_text NOT NULL,
  requested_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz, outcome nonempty_text,
  failure_code text,
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0), next_attempt_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (issuance_id, requested_by, request_key),
  CHECK ((finished_at IS NULL AND outcome IS NULL) OR (finished_at IS NOT NULL AND outcome IS NOT NULL))
);
CREATE TABLE checkin_attempts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), ticket_id uuid REFERENCES tickets(id),
  request_key uuid NOT NULL, replay_of uuid REFERENCES checkin_attempts(id),
  actor_subject nonempty_text NOT NULL, requested_event_id uuid NOT NULL,
  request_hash nonempty_text NOT NULL,
  requested_at timestamptz NOT NULL DEFAULT now(), result_code nonempty_text NOT NULL,
  succeeded boolean NOT NULL, correlation_id nonempty_text NOT NULL,
  CHECK (NOT succeeded OR ticket_id IS NOT NULL),
  CHECK (replay_of IS NULL OR (replay_of <> id AND NOT succeeded))
);
CREATE UNIQUE INDEX checkin_one_success_per_ticket_uq ON checkin_attempts(ticket_id) WHERE succeeded;
CREATE UNIQUE INDEX checkin_original_request_uq ON checkin_attempts(actor_subject, request_key) WHERE replay_of IS NULL;
CREATE INDEX issuances_buyer_idx ON issuances(buyer_subject, created_at);
CREATE INDEX issuances_event_idx ON issuances(event_id);
CREATE INDEX tickets_issuance_idx ON tickets(issuance_id);
CREATE INDEX deliveries_issuance_idx ON ticket_deliveries(issuance_id, requested_at);
CREATE INDEX checkin_ticket_idx ON checkin_attempts(ticket_id, requested_at);
CREATE INDEX checkin_event_idx ON checkin_attempts(requested_event_id, requested_at);
COMMENT ON TABLE issuances IS 'Retry must compare immutable input and verify exact child count in one local transaction; a root uniqueness constraint alone is insufficient.';
COMMENT ON TABLE tickets IS 'Ciphertext permits QR re-download with an externally stored restoreable key. Token format/key algorithm remain B13; no raw QR in audit.';
COMMENT ON TABLE checkin_attempts IS 'Every submitted check-in including rejection is audited. Actor, event, window eventStart <= now <= eventEnd, ticket state and atomic update require service enforcement.';
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
