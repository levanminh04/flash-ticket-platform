\set ON_ERROR_STOP on
\connect event_db
BEGIN;
SET LOCAL search_path = event_schema, pg_catalog;
\ir assertions.sql
\ir event-fixture.sql
SELECT pg_temp.expect_failure($q$UPDATE ticket_types SET unit_price = 0$q$, '23514', 'E01 positive ticket price');
SELECT pg_temp.expect_failure($q$UPDATE events SET sale_ends_at = ends_at + interval '1 second'$q$, '23514', 'E02 sale deadline');
SELECT pg_temp.expect_failure($q$UPDATE events SET checkin_starts_at = starts_at - interval '1 second'$q$, '23514', 'E03 checkin uses event window');
SELECT pg_temp.expect_failure($q$UPDATE promotions SET maximum_uses_per_user = 2$q$, '23514', 'E04 one promotion use per account');
SELECT pg_temp.expect_failure($q$INSERT INTO promotions SELECT gen_random_uuid(), event_id, code, starts_at, ends_at, discount_kind, fixed_discount, currency, discount_rate, maximum_uses, maximum_uses_per_user FROM promotions$q$, '23505', 'E05 exact promotion code unique within event');
SELECT pg_temp.expect_failure($q$UPDATE promotions SET discount_kind = 'PERCENT', fixed_discount = NULL, currency = NULL, discount_rate = NULL$q$, '23514', 'E06 percent NULL does not pass CHECK');
SELECT pg_temp.expect_failure($q$UPDATE ticket_types SET unit_price = 'NaN'$q$, '23514', 'E07 money NaN rejected');
SELECT pg_temp.expect_failure($q$UPDATE ticket_types SET currency = 'USD'$q$, '23514', 'E08 only VND');
SELECT pg_temp.expect_failure($q$UPDATE promotions SET code = 'lower'$q$, '23514', 'E09 canonical promotion code');
SELECT pg_temp.expect_failure($q$UPDATE promotions SET discount_kind='PERCENT',fixed_discount=NULL,currency=NULL,discount_rate=0.105$q$, '23514', 'E10 integer percent');
INSERT INTO event_cancellation_requests(event_id,requested_by,reason) VALUES(md5('event-1')::uuid,'fixture-organizer','again');
SELECT pg_temp.expect_failure($q$INSERT INTO event_cancellation_requests(event_id,requested_by,reason) VALUES(md5('event-1')::uuid,'fixture-organizer','twice')$q$, '23505', 'E11 one pending cancellation');
INSERT INTO event_layouts(id,event_id,name,canvas_width,canvas_height,layout_data)
VALUES(md5('layout-1')::uuid,md5('event-1')::uuid,'fixture',100,100,'{"schemaVersion":1,"decorations":[]}');
SELECT pg_temp.expect_failure($q$UPDATE event_layouts SET layout_data='{}'$q$, '23514', 'E12 required layout shape');
SELECT pg_temp.expect_failure($q$INSERT INTO event_layouts(event_id,name,canvas_width,canvas_height,layout_data) VALUES(md5('event-1')::uuid,'second',100,100,'{"schemaVersion":1,"decorations":[]}')$q$, '23505', 'E13 one layout per event');
UPDATE events SET status='PENDING_APPROVAL' WHERE id=md5('event-1')::uuid;
SET LOCAL ROLE event_app;
SELECT pg_temp.expect_failure($q$DELETE FROM event_layouts WHERE id=md5('layout-1')::uuid$q$, '23514', 'E14 app cannot delete non-DRAFT child');
RESET ROLE;
UPDATE events SET status='DRAFT' WHERE id=md5('event-1')::uuid;
SET LOCAL ROLE event_app;
DELETE FROM event_layouts WHERE id=md5('layout-1')::uuid;
RESET ROLE;
ROLLBACK;

\connect booking_db
BEGIN;
SET LOCAL search_path = booking_schema, pg_catalog;
\ir assertions.sql
\ir booking-fixture.sql
SELECT pg_temp.expect_failure($q$UPDATE ticket_type_inventory SET held = 6$q$, '23514', 'B01 no inventory over-capacity');
SELECT pg_temp.expect_failure($q$UPDATE purchase_limits SET held = 6$q$, '23514', 'B02 no purchase limit overrun');
SELECT pg_temp.expect_failure($q$UPDATE orders SET discount = subtotal, total = 0$q$, '23514', 'B03 final total positive');
SELECT pg_temp.expect_failure($q$UPDATE reservations SET expires_at = expires_at + interval '1 second'$q$, '23503', 'B04 common order reservation expiry');
SELECT pg_temp.expect_failure($q$UPDATE promotion_usages SET expires_at = expires_at + interval '1 second'$q$, '23503', 'B05 common promotion expiry');
SELECT pg_temp.expect_failure($q$INSERT INTO promotion_usages(promotion_id,event_id,reservation_id,buyer_subject,generation,state,discount,currency,expires_at) VALUES(md5('promo-1')::uuid,md5('event-1')::uuid,md5('reservation-2')::uuid,'fixture-buyer',1,'HELD',1,'VND','2030-01-01 01:10Z')$q$, '23505', 'B06 one active promotion claim per buyer');
SELECT pg_temp.expect_failure($q$UPDATE order_items SET event_id = md5('event-2')::uuid$q$, '23503', 'B07 item cannot cross event');
SELECT pg_temp.expect_failure($q$UPDATE order_items SET sales_mode = 'SEAT_MAP', sector_id = NULL$q$, '23514', 'B08 NULL sector cannot bypass mode');
SELECT pg_temp.expect_failure($q$UPDATE orders SET lifecycle_state='REFUNDED'$q$, '23514', 'B10 finalized order enum');
SELECT pg_temp.expect_failure($q$UPDATE orders SET payment_frozen_at='2030-01-01 01:01Z'$q$, '23514', 'B11 freeze fields must agree');
SELECT pg_temp.expect_failure($q$UPDATE orders SET lifecycle_state='ISSUING'$q$, '23514', 'B12 issuing requires acceptance');
SELECT pg_temp.expect_failure($q$UPDATE orders SET customer_email='not-an-email'$q$, '23514', 'B13 email structural guard');
INSERT INTO idempotency_requests VALUES('fixture-buyer','create-order',md5('key')::uuid,'hash',md5('order-1')::uuid,201,now());
SELECT pg_temp.expect_failure($q$INSERT INTO idempotency_requests VALUES('fixture-buyer','create-order',md5('key')::uuid,'hash',md5('order-2')::uuid,201,now())$q$, '23505', 'B14 idempotency key unique');
INSERT INTO outbox_messages(message_id,aggregate_id,aggregate_version,message_type,payload,occurred_at,correlation_id)
VALUES(md5('message')::uuid,md5('order-1')::uuid,1,'fixture','{}',now(),md5('order-1')::uuid);
SELECT pg_temp.expect_failure($q$INSERT INTO outbox_messages SELECT * FROM outbox_messages$q$, '23505', 'B15 outbox message identity');
INSERT INTO inbox_messages(consumer_name,message_id,payload_hash) VALUES('fixture',md5('message')::uuid,'hash');
SELECT pg_temp.expect_failure($q$INSERT INTO inbox_messages(consumer_name,message_id,payload_hash) VALUES('fixture',md5('message')::uuid,'hash')$q$, '23505', 'B16 inbox consumer identity');
UPDATE promotion_usages SET state = 'RELEASED' WHERE reservation_id = md5('reservation-1')::uuid;
INSERT INTO promotion_usages(promotion_id,event_id,reservation_id,buyer_subject,generation,state,discount,currency,expires_at)
VALUES(md5('promo-1')::uuid,md5('event-1')::uuid,md5('reservation-2')::uuid,'fixture-buyer',1,'HELD',1,'VND','2030-01-01 01:10Z');
DO $body$ DECLARE changed bigint; BEGIN
  UPDATE promotion_usages SET state = 'RELEASED' WHERE reservation_id = md5('reservation-1')::uuid AND generation = 1 AND state = 'HELD';
  GET DIAGNOSTICS changed = ROW_COUNT;
  IF changed <> 0 OR NOT EXISTS (SELECT FROM promotion_usages WHERE reservation_id = md5('reservation-2')::uuid AND state = 'HELD') THEN RAISE EXCEPTION 'B09 stale release affected new claim'; END IF;
  RAISE NOTICE 'PASS: B09 example conditional stale release leaves new claim intact (not a service implementation test)';
END $body$;
ROLLBACK;

\connect payment_db
BEGIN;
SET LOCAL search_path = payment_schema, pg_catalog;
\ir assertions.sql
\ir payment-fixture.sql
SELECT pg_temp.expect_failure($q$INSERT INTO order_payment_snapshots(order_id,event_id,buyer_subject,expected_amount,currency,expires_at,source_order_version,purchase_snapshot,snapshot_hash,received_at) VALUES(gen_random_uuid(),md5('event-1')::uuid,'fixture-zero',0,'VND','2030-01-01 01:10Z',1,'{"schemaVersion":1,"items":[]}','fixture-zero',now())$q$, '23514', 'P00 positive expected order amount');
SELECT pg_temp.expect_failure($q$INSERT INTO payment_attempts(id,order_id,provider,merchant_account,expected_amount,currency,request_key,request_hash) VALUES(gen_random_uuid(),md5('order-1')::uuid,'fixture-provider','fixture-merchant',10,'VND',gen_random_uuid(),'fixture')$q$, '23505', 'P01 one unfinished attempt per order');
SELECT pg_temp.expect_failure($q$INSERT INTO payment_confirmations(order_id,charge_id,amount,currency) VALUES(md5('order-2')::uuid,md5('charge-1')::uuid,10,'VND')$q$, '23505', 'P02 charge cannot confirm twice');
SELECT pg_temp.expect_failure($q$INSERT INTO payment_confirmations(order_id,charge_id,amount,currency) VALUES(md5('order-2')::uuid,md5('mismatch')::uuid,9,'VND')$q$, '23503', 'P03 mismatch receipt cannot confirm expected order');
SELECT pg_temp.expect_failure($q$INSERT INTO payment_confirmations(order_id,charge_id,amount,currency) VALUES(md5('order-2')::uuid,md5('mismatch')::uuid,10,'VND')$q$, '23503', 'P04 cannot relabel actual receipt amount');
SELECT pg_temp.expect_failure($q$UPDATE refunds SET amount = 9$q$, '23503', 'P05 no partial refund');
SELECT pg_temp.expect_failure($q$INSERT INTO refunds(charge_id,order_id,amount,currency,reason,state) VALUES(md5('charge-1')::uuid,md5('order-1')::uuid,10,'VND','LATE_PAYMENT','PENDING')$q$, '23505', 'P06 one refund per charge');
SELECT pg_temp.expect_failure($q$UPDATE event_refund_items SET confirmed_charge_id = md5('mismatch')::uuid$q$, '23503', 'P07 excess charge cannot enter cancellation run');
SELECT pg_temp.expect_failure($q$UPDATE event_refund_runs SET completed_at = now()$q$, '23514', 'P08 incomplete selection cannot complete run');
SELECT pg_temp.expect_failure($q$UPDATE payment_attempts SET state='SUCCEEDED'$q$, '23514', 'P09 terminal attempt needs finish time');
INSERT INTO event_finance_snapshots(event_id,cancellation_id,cancelled_at,received_at) VALUES(md5('event-1')::uuid,md5('cancel-marker')::uuid,now(),now());
SELECT pg_temp.expect_failure($q$UPDATE event_finance_snapshots SET cancellation_id=NULL,cancelled_at=NULL$q$, '23514', 'P10 empty finance config cannot masquerade as known');
INSERT INTO payment_sagas(order_id,charge_id,state,command_id) VALUES(md5('order-1')::uuid,md5('charge-1')::uuid,'WAITING_BOOKING',md5('command')::uuid);
SELECT pg_temp.expect_failure($q$INSERT INTO payment_sagas SELECT * FROM payment_sagas$q$, '23505', 'P11 one Saga per order');
ROLLBACK;

\connect ticket_db
BEGIN;
SET LOCAL search_path = ticket_schema, pg_catalog;
\ir assertions.sql
\ir ticket-fixture.sql
SELECT pg_temp.expect_failure($q$INSERT INTO issuances(order_id,event_id,buyer_subject,payment_confirmation_id,customer_email,purchase_snapshot,input_hash,expected_ticket_count,state) VALUES(md5('order-1')::uuid,md5('event-1')::uuid,'fixture-buyer','fixture-confirmation','fixture@example.test','{"schemaVersion":1,"items":[]}','different-input',1,'PENDING')$q$, '23505', 'T01 one issuance per order');
SELECT pg_temp.expect_failure($q$UPDATE tickets SET ordinal = 0$q$, '23514', 'T02 positive ticket ordinal');
SELECT pg_temp.expect_failure($q$UPDATE tickets SET state = 'REFUNDED'$q$, '23514', 'T03 no refunded ticket state');
SELECT pg_temp.expect_failure($q$UPDATE tickets SET state = 'USED'$q$, '23514', 'T04 used ticket needs actor and time');
SELECT pg_temp.expect_failure($q$INSERT INTO checkin_attempts(request_key,ticket_id,actor_subject,requested_event_id,request_hash,result_code,succeeded,correlation_id) VALUES(gen_random_uuid(),md5('ticket-1')::uuid,'fixture-organizer',md5('event-1')::uuid,'fixture-request-3','SUCCESS',true,'fixture-correlation-3')$q$, '23505', 'T05 at most one successful checkin audit');
SELECT pg_temp.expect_failure($q$UPDATE tickets SET qr_token_ciphertext = decode('', 'hex')$q$, '23514', 'T06 ciphertext cannot be empty');
SELECT pg_temp.expect_failure($q$UPDATE issuances SET state='FAILED'$q$, '23514', 'T07 failure needs terminal evidence fields');
SELECT pg_temp.expect_failure($q$INSERT INTO checkin_attempts(request_key,actor_subject,requested_event_id,request_hash,result_code,succeeded,correlation_id) SELECT request_key,actor_subject,requested_event_id,request_hash,'REPLAY',false,correlation_id FROM checkin_attempts LIMIT 1$q$, '23505', 'T08 original request identity unique');
INSERT INTO checkin_attempts(request_key,replay_of,actor_subject,requested_event_id,request_hash,result_code,succeeded,correlation_id)
SELECT request_key,id,actor_subject,requested_event_id,request_hash,'REPLAYED',false,correlation_id FROM checkin_attempts WHERE succeeded;
ROLLBACK;
\echo PASS: PostgreSQL constraint suite finished; all fixture transactions rolled back.
