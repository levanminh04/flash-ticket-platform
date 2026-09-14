INSERT INTO order_payment_snapshots(order_id, event_id, buyer_subject, expected_amount, currency, expires_at, source_order_version, purchase_snapshot, snapshot_hash, received_at)
SELECT md5('order-' || n)::uuid, md5('event-1')::uuid, 'fixture-buyer', 10, 'VND', '2030-01-01 01:10Z', 1, '{"schemaVersion":1,"items":[]}', 'fixture-hash-' || n, '2030-01-01 01:00Z'
FROM generate_series(1,2) AS n;
INSERT INTO payment_attempts(id, order_id, provider, merchant_account, expected_amount, currency, request_key, request_hash)
SELECT md5('attempt-' || n)::uuid, md5('order-' || n)::uuid, 'fixture-provider', 'fixture-merchant', 10, 'VND', md5('request-' || n)::uuid, 'fixture-request'
FROM generate_series(1,2) AS n;
INSERT INTO charges(id, attempt_id, order_id, provider, merchant_account, provider_charge_id, amount, currency, charged_at)
SELECT md5('charge-' || n)::uuid, md5('attempt-' || n)::uuid, md5('order-' || n)::uuid, 'fixture-provider', 'fixture-merchant', 'receipt-' || n, 10, 'VND', '2030-01-01 01:01Z'
FROM generate_series(1,2) AS n;
-- Deliberately invalid receipt inserted by the SQL test harness to test the confirmation FK.
-- Not the application path or an implemented Q-07 intake workflow (PRJ-024).
INSERT INTO charges(id, attempt_id, order_id, provider, merchant_account, provider_charge_id, amount, currency, charged_at)
VALUES (md5('mismatch')::uuid, md5('attempt-2')::uuid, md5('order-2')::uuid, 'fixture-provider', 'fixture-merchant', 'mismatch', 9, 'VND', '2030-01-01 01:01Z');
INSERT INTO payment_confirmations(order_id, charge_id, amount, currency)
VALUES (md5('order-1')::uuid, md5('charge-1')::uuid, 10, 'VND');
INSERT INTO refunds(id, charge_id, order_id, amount, currency, reason, state)
VALUES (md5('refund-1')::uuid, md5('charge-1')::uuid, md5('order-1')::uuid, 10, 'VND', 'EVENT_CANCELLED', 'PENDING');
INSERT INTO event_refund_runs(id, event_id, source_cancellation_id)
VALUES (md5('run-1')::uuid, md5('event-1')::uuid, md5('cancel-1')::uuid);
INSERT INTO event_refund_items(run_id, order_id, event_id, state)
VALUES (md5('run-1')::uuid, md5('order-2')::uuid, md5('event-1')::uuid, 'SELECTED');
