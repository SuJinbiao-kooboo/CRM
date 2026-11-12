-- ============================================================
-- 订单详情 / 附件入库 / SN 自动带回 PI号-客户
-- 说明：Exec 每次只执行一条 SQL，请分两次执行以下两条语句。
-- ============================================================

-- 1) 订单新增“PI号-客户”字段（若已存在请跳过此条）
ALTER TABLE `crm_sales_order`
  ADD COLUMN `customer_pi_no` varchar(100) DEFAULT NULL COMMENT 'PI号-客户' AFTER `pi_me_no`;

-- 2) 订单附件表：文件内容直接存入数据库，支持软删除
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
