-- Chạy trên payment_db bằng tài khoản có quyền SET ROLE payment_owner.
-- Bổ sung metadata ghi nhận hoàn ngoài hệ thống theo B12; giữ nguyên dữ liệu cũ.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL ROLE payment_owner;

ALTER TABLE payment_schema.tbl_refunds
  ADD COLUMN settled_by payment_schema.nonempty_text,
  ADD COLUMN external_reference payment_schema.nonempty_text,
  ADD CONSTRAINT refunds_manual_settlement_check CHECK (
    (settled_by IS NULL AND external_reference IS NULL)
    OR (settled_by IS NOT NULL AND external_reference IS NOT NULL AND state = 'SUCCEEDED')
  );

COMMIT;
