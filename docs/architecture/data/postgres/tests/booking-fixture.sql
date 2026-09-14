INSERT INTO event_sales_snapshots(event_id, organizer_subject, sales_mode, event_status, sale_starts_at, sale_ends_at, purchase_limit, source_config_version, received_at, configuration_snapshot)
SELECT md5('event-' || n)::uuid, 'fixture-organizer', 'QUANTITY', 'PUBLISHED', '2030-01-01 00:00Z', '2030-01-02 00:00Z', 5, 1, '2030-01-01 00:00Z', '{"schemaVersion":1}'
FROM generate_series(1,2) AS n;
INSERT INTO orders(id, event_id, buyer_subject, customer_email, sales_mode, lifecycle_state, subtotal, total, currency, expires_at, source_config_version, created_at)
SELECT md5('order-' || n)::uuid, md5('event-1')::uuid, 'fixture-buyer', 'fixture@example.test', 'QUANTITY', 'PENDING_PAYMENT', 10, 10, 'VND', '2030-01-01 01:10Z', 1, '2030-01-01 01:00Z'
FROM generate_series(1,2) AS n;
INSERT INTO order_items(id, order_id, event_id, ticket_type_id, sales_mode, ticket_type_name, quantity, unit_price, line_subtotal, currency, selection_snapshot)
VALUES (md5('item-1')::uuid, md5('order-1')::uuid, md5('event-1')::uuid, md5('type-1')::uuid, 'QUANTITY', 'Fixture ticket', 1, 10, 10, 'VND', '{"schemaVersion":1,"items":[]}');
INSERT INTO reservations(id, order_id, event_id, buyer_subject, state, expires_at)
SELECT md5('reservation-' || n)::uuid, md5('order-' || n)::uuid, md5('event-1')::uuid, 'fixture-buyer', 'HELD', '2030-01-01 01:10Z'
FROM generate_series(1,2) AS n;
INSERT INTO ticket_type_inventory(ticket_type_id, event_id, configured_capacity, source_config_version)
VALUES (md5('type-1')::uuid, md5('event-1')::uuid, 5, 1);
INSERT INTO purchase_limits(event_id, buyer_subject, configured_limit, source_config_version)
VALUES (md5('event-1')::uuid, 'fixture-buyer', 5, 1);
INSERT INTO promotion_inventory(promotion_id, event_id, configured_limit, starts_at, ends_at, configuration_snapshot, source_config_version)
VALUES (md5('promo-1')::uuid, md5('event-1')::uuid, 3, '2030-01-01 00:00Z', '2030-01-02 00:00Z', '{"schemaVersion":1,"items":[]}', 1);
INSERT INTO promotion_usages(promotion_id, event_id, reservation_id, buyer_subject, generation, state, discount, currency, expires_at)
VALUES (md5('promo-1')::uuid, md5('event-1')::uuid, md5('reservation-1')::uuid, 'fixture-buyer', 1, 'HELD', 1, 'VND', '2030-01-01 01:10Z');
