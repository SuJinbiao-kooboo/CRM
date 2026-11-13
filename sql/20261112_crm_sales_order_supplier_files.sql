-- ============================================================
-- 订单文件：补齐供应商侧 4 个文件栏位
-- 说明：客户侧复用原有文件栏位；供应商侧的委托单/签收单/照片/SN 为新增栏位。
-- ============================================================

ALTER TABLE `crm_sales_order`
  ADD COLUMN `entrust_supplier_file` text DEFAULT NULL COMMENT '供应商-委托单文件' AFTER `entrust_file`,
  ADD COLUMN `sign_receipt_supplier_file` text DEFAULT NULL COMMENT '供应商-签收单存档' AFTER `sign_receipt_file`,
  ADD COLUMN `photo_supplier_file` text DEFAULT NULL COMMENT '供应商-照片存档' AFTER `photo_file`,
  ADD COLUMN `sn_supplier_file` text DEFAULT NULL COMMENT '供应商-SN文件' AFTER `sn_file`;
