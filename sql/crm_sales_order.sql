-- ============================================================
-- CRM 订单需求管理：销售订单 + SN 管理
-- 幂等：CREATE TABLE IF NOT EXISTS / INSERT ... WHERE NOT EXISTS
-- ============================================================

CREATE TABLE IF NOT EXISTS `crm_order_seq` (
  `seq_key` varchar(40) NOT NULL COMMENT '序列键，如 SO20261111',
  `seq_val` int NOT NULL DEFAULT 0 COMMENT '当前序号',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`seq_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单编号序列';

CREATE TABLE IF NOT EXISTS `crm_sales_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(32) NOT NULL COMMENT '编号',
  `order_date` date DEFAULT NULL COMMENT '订单日期',
  `booking_date` date DEFAULT NULL COMMENT '入账日期',
  `booking_month` varchar(7) DEFAULT NULL COMMENT '入账月份(yyyy-MM)',
  `cooperation_status` varchar(32) DEFAULT NULL COMMENT '合作状态：NEW_NEW/NEW_OLD/OLD_NEW/OLD_OLD',
  `pn` varchar(128) DEFAULT NULL COMMENT 'PN',
  `desp` varchar(512) DEFAULT NULL COMMENT 'Desp.',
  `product_types` varchar(512) DEFAULT NULL COMMENT '产品类型，多选用逗号分隔',
  `status` varchar(32) NOT NULL DEFAULT 'UNPAID' COMMENT '状态：UNPAID/PAID_WAITING_RECEIPT/RECEIVED_WAITING_DELIVERY/DELIVERED/CANCELLED',
  `quantity` decimal(18,2) DEFAULT NULL COMMENT '数量',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID(crm_supplier.id)',
  `customer_po_no` varchar(100) DEFAULT NULL COMMENT 'PO号-客',
  `pi_me_no` varchar(100) DEFAULT NULL COMMENT 'PI号-M&E',
  `customer_pi_no` varchar(100) DEFAULT NULL COMMENT 'PI号-客户',
  `sale_price` decimal(18,4) DEFAULT NULL COMMENT '售价',
  `sales_amount` decimal(18,2) DEFAULT NULL COMMENT '销售额',
  `payment_received_date` date DEFAULT NULL COMMENT '付款到账日期',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID(crm_supplier.id)',
  `po_me_no` varchar(100) DEFAULT NULL COMMENT 'PO号-M&E',
  `supplier_pi_no` varchar(100) DEFAULT NULL COMMENT 'PI号-供',
  `cost_price` decimal(18,4) DEFAULT NULL COMMENT '成本单价',
  `purchase_amount` decimal(18,2) DEFAULT NULL COMMENT '总采购额',
  `price_diff` decimal(18,4) DEFAULT NULL COMMENT '价差',
  `gp` decimal(18,2) DEFAULT NULL COMMENT 'GP',
  `profit_rate` decimal(10,4) DEFAULT NULL COMMENT '利润率(%)',
  `payment_date` date DEFAULT NULL COMMENT '打款日期',
  `arrival_date` date DEFAULT NULL COMMENT '到货日期',
  `logistics_fee` decimal(18,2) DEFAULT NULL COMMENT '物流费',
  `carton_spec` varchar(255) DEFAULT NULL COMMENT '箱规',
  `gross_weight` decimal(10,3) DEFAULT NULL COMMENT '毛重KG',
  `sign_receipt_file` text COMMENT '签收单存档',
  `photo_file` text COMMENT '照片存档',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注',
  `logistics_reconciliation` varchar(255) DEFAULT NULL COMMENT '物流对账',
  `customer_contact_id` bigint DEFAULT NULL COMMENT '客户联系人ID',
  `supplier_contact_id` bigint DEFAULT NULL COMMENT '供应商联系人ID',
  `po_customer_file` text COMMENT 'PO客户文件',
  `pi_our_file` text COMMENT 'PI我方文件',
  `po_our_file` text COMMENT 'PO我方文件',
  `pi_supplier_file` text COMMENT 'PI供应商文件',
  `pl_supplier_file` text COMMENT 'PL供应商文件',
  `pl_our_file` text COMMENT 'PL我方文件',
  `ci_supplier_file` text COMMENT 'CI供应商文件',
  `ci_our_file` text COMMENT 'CI我方文件',
  `entrust_file` text COMMENT '委托单文件',
  `sn_file` text COMMENT 'SN文件',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crm_sales_order_no` (`order_no`),
  KEY `idx_crm_sales_order_date` (`order_date`),
  KEY `idx_crm_sales_order_month` (`booking_month`),
  KEY `idx_crm_sales_order_pn` (`pn`),
  KEY `idx_crm_sales_order_status` (`status`),
  KEY `idx_crm_sales_order_customer` (`customer_id`),
  KEY `idx_crm_sales_order_supplier` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM销售订单';

CREATE TABLE IF NOT EXISTS `crm_sales_order_sn` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '销售订单ID(crm_sales_order.id)',
  `sn_no` varchar(128) NOT NULL COMMENT 'SN编号',
  `carton_no` varchar(64) DEFAULT NULL COMMENT '箱号',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crm_sales_order_sn_no` (`sn_no`),
  KEY `idx_crm_sales_order_sn_order` (`order_id`),
  KEY `idx_crm_sales_order_sn_carton` (`carton_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM销售订单SN';

CREATE TABLE IF NOT EXISTS `crm_sales_order_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '销售订单ID(crm_sales_order.id)',
  `field_key` varchar(64) NOT NULL COMMENT '附件栏位（如 piOurFile）',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME类型',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_content` longblob NOT NULL COMMENT '文件内容（二进制入库）',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标记：0=正常，1=已删除',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_crm_sales_order_file_order` (`order_id`, `del_flag`),
  KEY `idx_crm_sales_order_file_field` (`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM销售订单附件（内容入库）';

-- 菜单目录：订单需求管理（挂在 CRM 管理下）
INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT '订单需求管理', 1200, 5, 'orderDemand', NULL, 1, 0, 'M', '0', '0', '', 'shopping', 'admin', NOW(), 'CRM订单需求管理目录'
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = 1200 AND path = 'orderDemand') t
);
SET @sales_order_group_id := (
  SELECT menu_id FROM `sys_menu` WHERE parent_id = 1200 AND path = 'orderDemand' ORDER BY menu_id LIMIT 1
);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT '订单管理', @sales_order_group_id, 1, 'salesOrder', 'crm/salesOrder/index', 1, 0, 'C', '0', '0', 'crm:salesOrder:list', 'form', 'admin', NOW(), 'CRM订单需求管理-订单管理'
WHERE @sales_order_group_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_group_id AND path = 'salesOrder') t
  );
SET @sales_order_menu_id := (
  SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_group_id AND path = 'salesOrder' ORDER BY menu_id LIMIT 1
);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 'SN管理', @sales_order_group_id, 2, 'salesOrderSn', 'crm/salesOrder/sn', 1, 0, 'C', '0', '0', 'crm:salesOrderSn:list', 'tree-table', 'admin', NOW(), 'CRM订单需求管理-SN管理'
WHERE @sales_order_group_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_group_id AND path = 'salesOrderSn') t
  );
SET @sales_order_sn_menu_id := (
  SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_group_id AND path = 'salesOrderSn' ORDER BY menu_id LIMIT 1
);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT '订单查询', @sales_order_menu_id, 1, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrder:query', '#', 'admin', NOW(), ''
WHERE @sales_order_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_menu_id AND perms = 'crm:salesOrder:query') t);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT '订单新增', @sales_order_menu_id, 2, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrder:add', '#', 'admin', NOW(), ''
WHERE @sales_order_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_menu_id AND perms = 'crm:salesOrder:add') t);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT '订单修改', @sales_order_menu_id, 3, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrder:edit', '#', 'admin', NOW(), ''
WHERE @sales_order_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_menu_id AND perms = 'crm:salesOrder:edit') t);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT '订单删除', @sales_order_menu_id, 4, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrder:remove', '#', 'admin', NOW(), ''
WHERE @sales_order_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_menu_id AND perms = 'crm:salesOrder:remove') t);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 'SN查询', @sales_order_sn_menu_id, 1, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrderSn:query', '#', 'admin', NOW(), ''
WHERE @sales_order_sn_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_sn_menu_id AND perms = 'crm:salesOrderSn:query') t);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 'SN批量录入', @sales_order_sn_menu_id, 2, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrderSn:add', '#', 'admin', NOW(), ''
WHERE @sales_order_sn_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_sn_menu_id AND perms = 'crm:salesOrderSn:add') t);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 'SN修改', @sales_order_sn_menu_id, 3, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrderSn:edit', '#', 'admin', NOW(), ''
WHERE @sales_order_sn_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_sn_menu_id AND perms = 'crm:salesOrderSn:edit') t);

INSERT INTO `sys_menu`
  (`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 'SN删除', @sales_order_sn_menu_id, 4, '', '', 1, 0, 'F', '0', '0', 'crm:salesOrderSn:remove', '#', 'admin', NOW(), ''
WHERE @sales_order_sn_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu` WHERE parent_id = @sales_order_sn_menu_id AND perms = 'crm:salesOrderSn:remove') t);

-- 授权给普通角色(2)与管理员角色(100)，保留幂等
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id
FROM (SELECT 2 AS role_id UNION ALL SELECT 100) r
JOIN `sys_menu` m ON m.menu_id = @sales_order_group_id
  OR m.perms LIKE 'crm:salesOrder:%'
  OR m.perms LIKE 'crm:salesOrderSn:%'
WHERE @sales_order_group_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, menu_id FROM `sys_role_menu`) rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
