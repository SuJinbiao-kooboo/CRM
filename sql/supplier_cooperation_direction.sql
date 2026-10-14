-- 供应商列表新增字段：合作方向(多选字典)、销售群、采购群
-- 说明：ALTER 采用直接语句（曾尝试 IF()+PREPARE 动态拼接，因 MySQL 对 '''+中文注释解析不可靠而弃用）
-- 幂等性：字典类型/数据已存在时不重复插入；ALTER 在列已存在时会报 duplicate column，可先行查询确认是否已执行过

ALTER TABLE crm_supplier ADD COLUMN cooperation_direction varchar(1024) DEFAULT NULL COMMENT '合作方向' AFTER payment_terms;
ALTER TABLE crm_supplier ADD COLUMN sales_group varchar(1024) DEFAULT NULL COMMENT '销售群(多个用逗号分隔)' AFTER payment_terms;
ALTER TABLE crm_supplier ADD COLUMN purchase_group varchar(1024) DEFAULT NULL COMMENT '采购群(多个用逗号分隔)' AFTER payment_terms;

-- 新增字典类型：供应商列表-合作方向
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '供应商列表-合作方向', 'crm_supplier_cooperation_direction', '0', 'admin', NOW(), '供应商合作方向(多选)'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'crm_supplier_cooperation_direction');

-- 字典数据（label=value=字面量，与主营产品等现有字典约定一致）
INSERT INTO sys_dict_data (dict_label, dict_value, dict_type, dict_sort, status, create_by, create_time, remark)
SELECT v.label, v.label, 'crm_supplier_cooperation_direction', v.sort, '0', 'admin', NOW(), ''
FROM (SELECT '消费级SSD' label, 1 sort UNION ALL SELECT '服务器SSD', 2 UNION ALL SELECT '消费级CPU', 3 UNION ALL SELECT '服务器CPU', 4 UNION ALL SELECT '消费级RAM', 5 UNION ALL SELECT '服务器RAM-Samsung', 6 UNION ALL SELECT '服务器RAM-Micron', 7 UNION ALL SELECT '服务器RAM-SKhynix', 8 UNION ALL SELECT '显卡-Nvida', 9 UNION ALL SELECT '显卡-AMD', 10 UNION ALL SELECT 'HDD-WD', 11 UNION ALL SELECT 'HDD-Seagate', 12 UNION ALL SELECT 'HDD-Toshiba', 13 UNION ALL SELECT 'SSD-Samsung', 14 UNION ALL SELECT 'SSD-Micron', 15 UNION ALL SELECT 'SSD-SKhynix', 16 UNION ALL SELECT 'SSD-Kioxia', 17 UNION ALL SELECT 'SSD-SanDisk', 18 UNION ALL SELECT 'SSD-YMTC', 19) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'crm_supplier_cooperation_direction' AND dict_value = v.label);
