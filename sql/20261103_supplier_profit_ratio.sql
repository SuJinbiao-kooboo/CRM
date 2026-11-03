-- 供应商默认利润率（单位：%）
-- 全量默认 2%；指定供应商按业务约定覆盖。

ALTER TABLE crm_supplier
    MODIFY COLUMN profit_ratio DECIMAL(6,2) NULL DEFAULT 2.00 COMMENT '利润率(%)';

UPDATE crm_supplier
SET profit_ratio = 2.00
WHERE profit_ratio IS NULL;

-- Ma Labs 默认 8%
UPDATE crm_supplier
SET profit_ratio = 8.00
WHERE supplier_code = 'VC0820022';

-- 宇强、泰耘、CPU Power、福达瑞星 默认 3%
UPDATE crm_supplier
SET profit_ratio = 3.00
WHERE supplier_code IN ('VC0820080', 'VC1014130', 'VC0820707', 'VC0820852');
