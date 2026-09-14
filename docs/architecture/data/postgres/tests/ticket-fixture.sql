INSERT INTO event_access_snapshots(event_id, organizer_subject, event_status, event_starts_at, event_ends_at, source_config_version, received_at)
VALUES (md5('event-1')::uuid, 'fixture-organizer', 'PUBLISHED', '2030-01-02 00:00Z', '2030-01-03 00:00Z', 1, '2030-01-01 00:00Z');
INSERT INTO issuances(id, order_id, event_id, buyer_subject, payment_confirmation_id, customer_email, purchase_snapshot, input_hash, expected_ticket_count, state)
VALUES (md5('issuance-1')::uuid, md5('order-1')::uuid, md5('event-1')::uuid, 'fixture-buyer', 'fixture-confirmation', 'fixture@example.test', '{"schemaVersion":1,"items":[]}', 'fixture-input', 1, 'PENDING');
INSERT INTO tickets(id, issuance_id, event_id, order_item_id, ordinal, holder_subject, ticket_type_id, display_snapshot, state, qr_token_ciphertext, qr_key_version, qr_token_hash)
VALUES (md5('ticket-1')::uuid, md5('issuance-1')::uuid, md5('event-1')::uuid, md5('item-1')::uuid, 1, 'fixture-buyer', md5('type-1')::uuid, '{"schemaVersion":1,"items":[]}', 'VALID', decode('010203', 'hex'), 'FIXTURE_NOT_A_KEY', 'FIXTURE_NOT_A_REAL_TOKEN_HASH');
-- Deliberately arbitrary opaque bytes: this does not test cryptographic correctness.
INSERT INTO checkin_attempts(request_key, ticket_id, actor_subject, requested_event_id, request_hash, result_code, succeeded, correlation_id)
VALUES (gen_random_uuid(), md5('ticket-1')::uuid, 'fixture-organizer', md5('event-1')::uuid, 'fixture-request-1', 'SUCCESS', true, 'fixture-correlation-1');
INSERT INTO checkin_attempts(request_key,actor_subject, requested_event_id, request_hash, result_code, succeeded, correlation_id)
VALUES (gen_random_uuid(), 'fixture-organizer', md5('event-1')::uuid, 'fixture-request-2', 'UNKNOWN_TICKET', false, 'fixture-correlation-2');
