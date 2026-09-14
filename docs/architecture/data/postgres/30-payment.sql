\set ON_ERROR_STOP on
\connect payment_db
BEGIN;
SET LOCAL ROLE payment_owner;
SET LOCAL search_path = payment_schema, pg_catalog;
CREATE DOMAIN nonempty_text AS text CHECK (btrim(VALUE) <> '');
CREATE DOMAIN currency_code AS text CHECK (VALUE = 'VND');
CREATE DOMAIN money_amount AS numeric(19,0) CHECK (VALUE >= 0 AND VALUE <> 'NaN'::numeric);

CREATE TABLE event_finance_snapshots (
  event_id uuid PRIMARY KEY, organizer_subject nonempty_text,
  event_ends_at timestamptz, fee_rate numeric(9,8) CHECK (fee_rate BETWEEN 0 AND 1),
  source_fee_version bigint CHECK (source_fee_version > 0),
  cancellation_id uuid UNIQUE, cancelled_at timestamptz,
  received_at timestamptz NOT NULL,
  CHECK ((cancellation_id IS NULL) = (cancelled_at IS NULL)),
  CHECK ((organizer_subject IS NULL AND event_ends_at IS NULL AND fee_rate IS NULL AND source_fee_version IS NULL AND cancellation_id IS NOT NULL) OR
         (organizer_subject IS NOT NULL AND event_ends_at IS NOT NULL AND fee_rate IS NOT NULL AND source_fee_version IS NOT NULL))
);
COMMENT ON TABLE event_finance_snapshots IS 'Local immutable fee configuration and monotonic cancellation marker, even when cancellation arrives before finance configuration. Avoids Event DB joins and creating a payout before reconciliation. Trade-off: one small projection per event; missing fee blocks payout, never defaults to zero.';

CREATE TABLE order_payment_snapshots (
  order_id uuid PRIMARY KEY, event_id uuid NOT NULL, buyer_subject nonempty_text NOT NULL,
  expected_amount money_amount NOT NULL CHECK (expected_amount > 0), currency currency_code NOT NULL, expires_at timestamptz NOT NULL,
  source_order_version bigint NOT NULL CHECK (source_order_version > 0),
  purchase_snapshot jsonb NOT NULL CHECK (jsonb_typeof(purchase_snapshot) = 'object' AND purchase_snapshot @> '{"schemaVersion":1}' AND purchase_snapshot ? 'items' AND jsonb_typeof(purchase_snapshot->'items') = 'array'),
  snapshot_hash nonempty_text NOT NULL, received_at timestamptz NOT NULL,
  UNIQUE (order_id, event_id), UNIQUE (order_id, expected_amount, currency)
);
CREATE TABLE payment_attempts (
  id uuid PRIMARY KEY, order_id uuid NOT NULL,
  provider nonempty_text NOT NULL, merchant_account nonempty_text NOT NULL,
  expected_amount money_amount NOT NULL, currency currency_code NOT NULL,
  started_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz,
  state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','UNKNOWN','SUCCEEDED','FAILED')),
  request_key uuid NOT NULL, request_hash nonempty_text NOT NULL,
  payment_url text,
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  FOREIGN KEY (order_id, expected_amount, currency) REFERENCES order_payment_snapshots(order_id, expected_amount, currency),
  CHECK ((finished_at IS NULL AND state IN ('PENDING','UNKNOWN')) OR (finished_at IS NOT NULL AND state IN ('SUCCEEDED','FAILED'))),
  CHECK (finished_at IS NULL OR started_at <= finished_at),
  UNIQUE (id, order_id, provider, merchant_account), UNIQUE (order_id, request_key)
);
CREATE UNIQUE INDEX payment_one_unfinished_attempt_uq ON payment_attempts(order_id) WHERE finished_at IS NULL;
CREATE TABLE charges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), attempt_id uuid NOT NULL, order_id uuid NOT NULL,
  provider nonempty_text NOT NULL, merchant_account nonempty_text NOT NULL, provider_charge_id nonempty_text NOT NULL,
  amount money_amount NOT NULL, currency currency_code NOT NULL,
  charged_at timestamptz NOT NULL, recorded_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (attempt_id, order_id, provider, merchant_account)
    REFERENCES payment_attempts(id, order_id, provider, merchant_account),
  UNIQUE (provider, merchant_account, provider_charge_id),
  UNIQUE (id, order_id), UNIQUE (id, order_id, amount, currency)
);
CREATE TABLE payment_confirmations (
  order_id uuid PRIMARY KEY REFERENCES order_payment_snapshots(order_id),
  charge_id uuid NOT NULL UNIQUE, amount money_amount NOT NULL, currency currency_code NOT NULL,
  confirmed_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (charge_id, order_id, amount, currency) REFERENCES charges(id, order_id, amount, currency),
  FOREIGN KEY (order_id, amount, currency) REFERENCES order_payment_snapshots(order_id, expected_amount, currency),
  UNIQUE (order_id, charge_id)
);
CREATE TABLE refunds (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), charge_id uuid NOT NULL UNIQUE, order_id uuid NOT NULL,
  amount money_amount NOT NULL, currency currency_code NOT NULL,
  reason text NOT NULL CHECK (reason IN ('LATE_PAYMENT','DUPLICATE_PAYMENT','TICKET_ISSUANCE_FAILED','EVENT_CANCELLED')),
  state text NOT NULL CHECK (state IN ('PENDING','PROCESSING','SUCCEEDED','FAILED')),
  created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  FOREIGN KEY (charge_id, order_id, amount, currency) REFERENCES charges(id, order_id, amount, currency),
  CHECK ((state = 'SUCCEEDED' AND completed_at IS NOT NULL) OR (state <> 'SUCCEEDED' AND completed_at IS NULL)),
  UNIQUE (id, charge_id, order_id)
);
CREATE TABLE refund_attempts (
  id uuid PRIMARY KEY, refund_id uuid NOT NULL REFERENCES refunds(id),
  request_key nonempty_text NOT NULL UNIQUE, provider_refund_id nonempty_text,
  started_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz, outcome nonempty_text,
  CHECK ((finished_at IS NULL AND outcome IS NULL) OR (finished_at IS NOT NULL AND outcome IS NOT NULL)),
  CHECK (finished_at IS NULL OR started_at <= finished_at)
);
CREATE UNIQUE INDEX refund_one_unfinished_attempt_uq ON refund_attempts(refund_id) WHERE finished_at IS NULL;
CREATE TABLE event_refund_runs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL,
  source_cancellation_id uuid NOT NULL UNIQUE,
  selection_cursor text, selection_complete boolean NOT NULL DEFAULT false,
  selected_order_count bigint NOT NULL DEFAULT 0 CHECK (selected_order_count >= 0),
  started_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  CHECK (completed_at IS NULL OR selection_complete), UNIQUE (id, event_id)
);
CREATE TABLE event_refund_items (
  run_id uuid NOT NULL, order_id uuid NOT NULL, event_id uuid NOT NULL,
  confirmed_charge_id uuid, refund_id uuid,
  state text NOT NULL CHECK (state IN ('SELECTED','REFUND_PENDING','REFUNDED','NEEDS_ATTENTION')),
  last_error_code text, updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (run_id, order_id),
  FOREIGN KEY (run_id, event_id) REFERENCES event_refund_runs(id, event_id),
  FOREIGN KEY (order_id, event_id) REFERENCES order_payment_snapshots(order_id, event_id),
  FOREIGN KEY (order_id, confirmed_charge_id) REFERENCES payment_confirmations(order_id, charge_id),
  FOREIGN KEY (refund_id, confirmed_charge_id, order_id) REFERENCES refunds(id, charge_id, order_id),
  CHECK (refund_id IS NULL OR confirmed_charge_id IS NOT NULL),
  CHECK (state NOT IN ('REFUND_PENDING','REFUNDED') OR refund_id IS NOT NULL)
);
CREATE TABLE event_payouts (
  event_id uuid PRIMARY KEY, organizer_subject nonempty_text NOT NULL,
  currency currency_code NOT NULL, valid_gross money_amount NOT NULL,
  valid_refunds money_amount NOT NULL, fee_amount money_amount NOT NULL, net_amount money_amount NOT NULL,
  fee_rate numeric(9,8) NOT NULL CHECK (fee_rate BETWEEN 0 AND 1),
  source_fee_version bigint NOT NULL CHECK (source_fee_version > 0), event_ends_at timestamptz NOT NULL,
  payment_pending_count bigint NOT NULL CHECK (payment_pending_count >= 0),
  refund_pending_count bigint NOT NULL CHECK (refund_pending_count >= 0),
  as_of timestamptz NOT NULL, reconciled_by nonempty_text, reconciled_at timestamptz,
  reconciliation_evidence nonempty_text, paid_by nonempty_text, paid_at timestamptz,
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  CHECK (valid_refunds <= valid_gross AND net_amount = valid_gross - valid_refunds - fee_amount),
  CHECK ((reconciled_by IS NULL AND reconciled_at IS NULL AND reconciliation_evidence IS NULL) OR
         (reconciled_by IS NOT NULL AND reconciled_at IS NOT NULL AND reconciliation_evidence IS NOT NULL)),
  CHECK ((paid_by IS NULL AND paid_at IS NULL) OR
         (paid_by IS NOT NULL AND paid_at IS NOT NULL AND reconciled_at IS NOT NULL AND
          paid_at >= reconciled_at AND paid_at >= event_ends_at AND payment_pending_count = 0 AND refund_pending_count = 0))
);
CREATE INDEX payment_attempts_order_idx ON payment_attempts(order_id, started_at);
CREATE INDEX charges_order_idx ON charges(order_id, charged_at);
CREATE INDEX refunds_order_idx ON refunds(order_id);
CREATE INDEX refunds_pending_idx ON refunds(created_at) WHERE state IN ('PENDING','PROCESSING');
CREATE INDEX refund_attempts_parent_idx ON refund_attempts(refund_id, started_at);
CREATE INDEX refund_items_order_idx ON event_refund_items(order_id);
CREATE INDEX refund_items_refund_idx ON event_refund_items(refund_id) WHERE refund_id IS NOT NULL;
CREATE INDEX order_snapshots_event_idx ON order_payment_snapshots(event_id);
COMMENT ON TABLE charges IS 'Verified linked receipts, including late or duplicate charges. PRJ-024 excludes mismatch/orphan handling workflow; validation remains mandatory. Not proof that provider has no missing receipts.';
COMMENT ON TABLE refunds IS 'One full refund per actual charge; reason arbitration and retry lifecycle remain B13/B14.';
COMMENT ON TABLE event_refund_items IS 'One item per order/run; confirmed_charge_id excludes excess charges. No refund is required merely to select an order.';
COMMENT ON TABLE event_payouts IS 'Snapshot constraints do not prove freshness, absence of concurrent payments/refunds or write-once paid behavior; transactional service and B13/B14 required.';
CREATE TABLE payment_sagas (
  order_id uuid PRIMARY KEY REFERENCES order_payment_snapshots(order_id),
  charge_id uuid NOT NULL,
  state text NOT NULL CHECK (state IN ('WAITING_BOOKING','WAITING_TICKETS','COMPLETED','COMPENSATING','COMPENSATED')),
  command_id uuid NOT NULL, next_attempt_at timestamptz NOT NULL DEFAULT now(), last_error_code text,
  row_version bigint NOT NULL DEFAULT 0 CHECK (row_version >= 0),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (charge_id, order_id) REFERENCES charges(id, order_id)
);
CREATE INDEX saga_pending_idx ON payment_sagas(next_attempt_at) WHERE state NOT IN ('COMPLETED','COMPENSATED');
COMMENT ON TABLE payment_sagas IS 'One approved Payment-orchestrated purchase Saga per order, persisted for restart/retry. Charge is the candidate submitted to Booking; confirmation alone does not prove deadline acceptance. Excess linked charges refund independently. Trade-off: coordinator state plus idempotent participants; no additional business Saga.';
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
