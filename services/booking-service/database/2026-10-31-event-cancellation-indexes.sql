-- Chạy ngoài transaction; không dựa vào Hibernate ddl-auto để tạo partial index.
-- Trước/sau chạy, kiểm pg_indexes và pg_index.indisvalid/indisready, không chỉ tên.
CREATE INDEX CONCURRENTLY IF NOT EXISTS tbl_reservations_active_order_idx
ON booking_schema.tbl_reservations (order_id)
WHERE state IN ('HELD', 'COMMITTED');
