-- Offer 增加“消费/服务”字段及字典（幂等）
-- usage_segment: 服务器 / 消费级，供 Offer 列表、AI 录入与历史批量回填共用。

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_offer' AND COLUMN_NAME = 'usage_segment'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE crm_offer ADD COLUMN usage_segment VARCHAR(16) DEFAULT NULL COMMENT ''消费/服务：服务器/消费级'' AFTER product_type',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_offer' AND INDEX_NAME = 'idx_offer_usage_segment'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE crm_offer ADD INDEX idx_offer_usage_segment (usage_segment)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_dict_type
  (dict_id, dict_name, dict_type, status, create_by, create_time, update_by, update_time, remark)
SELECT 10007, 'Offer消费/服务', 'crm_offer_usage_segment', '0', 'admin', NOW(), '', NULL, 'Offer消费级/服务器枚举'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_type WHERE dict_type = 'crm_offer_usage_segment'
);

INSERT INTO sys_dict_data
  (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark)
SELECT 100168, 1, '服务器', '服务器', 'crm_offer_usage_segment', '', 'primary', 'N', '0', 'admin', NOW(), '', NULL, '服务器级产品'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_data WHERE dict_type = 'crm_offer_usage_segment' AND dict_value = '服务器'
);

INSERT INTO sys_dict_data
  (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, update_by, update_time, remark)
SELECT 100169, 2, '消费级', '消费级', 'crm_offer_usage_segment', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '消费级产品'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_data WHERE dict_type = 'crm_offer_usage_segment' AND dict_value = '消费级'
);
