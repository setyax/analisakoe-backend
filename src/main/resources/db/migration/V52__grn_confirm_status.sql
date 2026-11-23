-- RECONSTRUCTED MIGRATION - NOT THE ORIGINAL FILE.
-- Original V52__grn_confirm_status.sql was absent from the project backup.
-- Reconstruction is based on deployed DB evidence:
-- pur.purchase_order_hdr_status_check allows DRAFT, CONFIRMED, RECEIVED, VOID, CLOSED;
-- GrnService.confirmGrn() sets the linked PO to RECEIVED and unconfirmGrn() to CONFIRMED.
-- Do not re-run manually against the existing database. This file is for source alignment
-- and a documented Flyway repair after the schema effects have been verified.

ALTER TABLE pur.purchase_order_hdr
    DROP CONSTRAINT IF EXISTS purchase_order_hdr_status_check;

ALTER TABLE pur.purchase_order_hdr
    ADD CONSTRAINT purchase_order_hdr_status_check
    CHECK (status IN ('DRAFT', 'CONFIRMED', 'RECEIVED', 'VOID', 'CLOSED'));