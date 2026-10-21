-- ============================================================
-- CRM 订单模块（PO 等单据）建表与菜单脚本
-- 表：crm_order 订单主表 / crm_order_item 物料明细 / crm_order_seq 编号序列
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT ... WHERE NOT EXISTS
-- ============================================================

CREATE TABLE IF NOT EXISTS `crm_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `order_type` varchar(16) NOT NULL DEFAULT 'PO' COMMENT '单据类型：PO/PI/PL/ENTRUST/SN/CI',
  `order_date` date DEFAULT NULL COMMENT '订单日期(Order Date)',
  `seller_id` bigint DEFAULT NULL COMMENT '供应商ID(crm_supplier.id)',
  `seller_contact_id` bigint DEFAULT NULL COMMENT '供应商联系人ID',
  `seller_snapshot` text COMMENT '供应商信息块文本(生成单据用，可人工编辑)',
  `buyer_id` bigint DEFAULT NULL COMMENT '买方ID',
  `buyer_snapshot` text COMMENT '买方信息块文本',
  `currency` varchar(8) DEFAULT 'USD' COMMENT '币种',
  `total_amount` decimal(18,2) DEFAULT '0.00' COMMENT '合计金额',
  `status` char(1) DEFAULT '0' COMMENT '状态：0草稿 1已生成 2作废',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crm_order_no` (`order_no`),
  KEY `idx_crm_order_seller` (`seller_id`),
  KEY `idx_crm_order_date` (`order_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM订单主表';

CREATE TABLE IF NOT EXISTS `crm_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单ID(crm_order.id)',
  `line_no` int DEFAULT NULL COMMENT '行号 NO.',
  `brand` varchar(64) DEFAULT NULL COMMENT 'Brand',
  `item_name` varchar(255) DEFAULT NULL COMMENT 'ITEM',
  `spec` varchar(500) DEFAULT NULL COMMENT 'Spec.',
  `qty` decimal(18,2) DEFAULT '0.00' COMMENT 'QTY(pcs)',
  `warranty` varchar(64) DEFAULT NULL COMMENT 'Warranty',
  `unit_price` decimal(18,4) DEFAULT '0.0000' COMMENT 'U/P(USD)',
  `amount` decimal(18,2) DEFAULT '0.00' COMMENT 'AMOUNT(USD)',
  `lead_time` varchar(32) DEFAULT NULL COMMENT 'Lead time',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_crm_order_item_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM订单物料明细';

CREATE TABLE IF NOT EXISTS `crm_order_seq` (
  `seq_key` varchar(40) NOT NULL COMMENT '序列键，如 PO20261020',
  `seq_val` int NOT NULL DEFAULT 0 COMMENT '当前序号',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`seq_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单编号序列';

-- 菜单：订单管理（挂在 CRM 目录 1200 下，menu_id=1212）
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 1212,'订单管理',1200,3,'order','crm/order/index',1,0,'C','0','0','crm:order:list','form','admin',now(),'CRM订单管理菜单'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu`) t WHERE t.menu_id = 1212);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 2230,'订单查询',1212,1,'','',1,0,'F','0','0','crm:order:query','#','admin',now(),''
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu`) t WHERE t.menu_id = 2230);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 2231,'订单新增',1212,2,'','',1,0,'F','0','0','crm:order:add','#','admin',now(),''
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu`) t WHERE t.menu_id = 2231);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 2232,'订单修改',1212,3,'','',1,0,'F','0','0','crm:order:edit','#','admin',now(),''
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu`) t WHERE t.menu_id = 2232);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 2233,'订单删除',1212,4,'','',1,0,'F','0','0','crm:order:remove','#','admin',now(),''
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu`) t WHERE t.menu_id = 2233);

INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 2234,'单据生成',1212,5,'','',1,0,'F','0','0','crm:order:generate','#','admin',now(),'生成PO等单据'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu`) t WHERE t.menu_id = 2234);

-- 角色授权：与现有 CRM 菜单保持一致，授权给 普通角色(2) 与 管理员(100)
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, m.menu_id FROM (SELECT 2 AS role_id UNION SELECT 100) r
CROSS JOIN (SELECT 1212 AS menu_id UNION SELECT 2230 UNION SELECT 2231 UNION SELECT 2232 UNION SELECT 2233 UNION SELECT 2234) m
WHERE NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `sys_role_menu`) t WHERE t.role_id = r.role_id AND t.menu_id = m.menu_id);


-- ============================================================
-- 追加：单据生成页面菜单（类型驱动动态表单，menu_id=1213）
-- ============================================================
INSERT INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
SELECT 1213,'单据生成',1200,4,'orderGenerate','crm/order/generate',1,0,'C','0','0','crm:order:generate','documentation','admin',now(),'选择单据类型后填充字段生成Excel/PDF'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM `sys_menu`) t WHERE t.menu_id = 1213);

INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT r.role_id, 1213 FROM (SELECT 2 AS role_id UNION SELECT 100) r
WHERE NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM `sys_role_menu`) t WHERE t.role_id = r.role_id AND t.menu_id = 1213);