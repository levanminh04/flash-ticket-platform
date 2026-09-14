\set ON_ERROR_STOP on
-- Executed as the bootstrap administrator after all four owner scripts.
\connect event_db
BEGIN;
SET LOCAL ROLE event_owner;
REVOKE ALL ON ALL TABLES IN SCHEMA event_schema FROM PUBLIC;
GRANT USAGE ON SCHEMA event_schema TO event_app;
GRANT DELETE ON event_schema.event_images, event_schema.event_layouts, event_schema.event_sectors, event_schema.event_seats, event_schema.ticket_types, event_schema.promotions TO event_app;
GRANT SELECT, INSERT, UPDATE ON event_schema.categories, event_schema.venues, event_schema.events, event_schema.event_images, event_schema.event_layouts, event_schema.event_sectors, event_schema.event_seats, event_schema.ticket_types, event_schema.promotions, event_schema.event_cancellation_requests TO event_app;
GRANT SELECT, INSERT, UPDATE ON event_schema.outbox_messages, event_schema.inbox_messages TO event_app;
ALTER DEFAULT PRIVILEGES FOR ROLE event_owner IN SCHEMA event_schema REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE event_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE event_owner REVOKE USAGE ON TYPES FROM PUBLIC;
COMMIT;
\connect booking_db
BEGIN;
SET LOCAL ROLE booking_owner;
REVOKE ALL ON ALL TABLES IN SCHEMA booking_schema FROM PUBLIC;
GRANT USAGE ON SCHEMA booking_schema TO booking_app;
GRANT SELECT, INSERT, UPDATE ON booking_schema.idempotency_requests TO booking_app;
GRANT SELECT, INSERT, UPDATE ON booking_schema.event_sales_snapshots, booking_schema.orders, booking_schema.order_items, booking_schema.order_item_seats, booking_schema.reservations, booking_schema.reservation_allocations, booking_schema.ticket_type_inventory, booking_schema.sector_inventory, booking_schema.seat_inventory, booking_schema.purchase_limits, booking_schema.promotion_inventory, booking_schema.promotion_usages TO booking_app;
GRANT SELECT, INSERT, UPDATE ON booking_schema.outbox_messages, booking_schema.inbox_messages TO booking_app;
ALTER DEFAULT PRIVILEGES FOR ROLE booking_owner IN SCHEMA booking_schema REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE booking_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE booking_owner REVOKE USAGE ON TYPES FROM PUBLIC;
COMMIT;
\connect payment_db
BEGIN;
SET LOCAL ROLE payment_owner;
REVOKE ALL ON ALL TABLES IN SCHEMA payment_schema FROM PUBLIC;
GRANT USAGE ON SCHEMA payment_schema TO payment_app;
GRANT SELECT, INSERT, UPDATE ON payment_schema.event_finance_snapshots TO payment_app;
GRANT SELECT, INSERT, UPDATE ON payment_schema.payment_sagas TO payment_app;
GRANT SELECT, INSERT, UPDATE ON payment_schema.order_payment_snapshots, payment_schema.payment_attempts, payment_schema.charges, payment_schema.payment_confirmations, payment_schema.refunds, payment_schema.refund_attempts, payment_schema.event_refund_runs, payment_schema.event_refund_items, payment_schema.event_payouts TO payment_app;
GRANT SELECT, INSERT, UPDATE ON payment_schema.outbox_messages, payment_schema.inbox_messages TO payment_app;
ALTER DEFAULT PRIVILEGES FOR ROLE payment_owner IN SCHEMA payment_schema REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE payment_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE payment_owner REVOKE USAGE ON TYPES FROM PUBLIC;
COMMIT;
\connect ticket_db
BEGIN;
SET LOCAL ROLE ticket_owner;
REVOKE ALL ON ALL TABLES IN SCHEMA ticket_schema FROM PUBLIC;
GRANT USAGE ON SCHEMA ticket_schema TO ticket_app;
GRANT SELECT, INSERT, UPDATE ON ticket_schema.event_access_snapshots, ticket_schema.issuances, ticket_schema.tickets, ticket_schema.ticket_deliveries, ticket_schema.checkin_attempts TO ticket_app;
GRANT SELECT, INSERT, UPDATE ON ticket_schema.outbox_messages, ticket_schema.inbox_messages TO ticket_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ticket_owner IN SCHEMA ticket_schema REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE ticket_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE ticket_owner REVOKE USAGE ON TYPES FROM PUBLIC;
COMMIT;
-- New tables intentionally fail closed: an explicit reviewed table grant is required.
