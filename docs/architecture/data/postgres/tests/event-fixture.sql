-- Synthetic fixtures only; caller owns transaction and rolls it back.
INSERT INTO events(id, organizer_subject, title, sales_mode, starts_at, ends_at,
  sale_starts_at, sale_ends_at, checkin_starts_at, checkin_ends_at, purchase_limit)
SELECT md5('event-' || n)::uuid, 'fixture-organizer', 'Fixture event', 'QUANTITY',
  '2030-01-02 00:00Z', '2030-01-03 00:00Z', '2030-01-01 00:00Z', '2030-01-02 00:00Z',
  '2030-01-02 00:00Z', '2030-01-03 00:00Z', 5 FROM generate_series(1,2) AS n;
INSERT INTO ticket_types(id, event_id, name, sales_mode, unit_price, currency, configured_quantity)
VALUES (md5('type-1')::uuid, md5('event-1')::uuid, 'Fixture ticket', 'QUANTITY', 10, 'VND', 5);
INSERT INTO promotions(id, event_id, code, starts_at, ends_at, discount_kind, fixed_discount, currency, maximum_uses)
VALUES (md5('promo-1')::uuid, md5('event-1')::uuid, 'FIXTURE', '2030-01-01 00:00Z', '2030-01-02 00:00Z', 'FIXED', 1, 'VND', 3);
-- Rejection reason is optional; multiplicity is storage capacity, not a granted resubmit capability.
INSERT INTO event_cancellation_requests(event_id, requested_by, reason, status, reviewed_by, reviewed_at)
VALUES (md5('event-1')::uuid, 'fixture-organizer', 'Fixture request', 'REJECTED', 'fixture-admin', '2030-01-01 00:00Z');
