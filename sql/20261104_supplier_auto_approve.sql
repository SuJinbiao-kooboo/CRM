-- 供应商自动审批开关：默认关闭。
-- 只有微信询报价任务的所有发送目标都开启时，任务生成后才通过批量审批逻辑自动进入“已审批待发送”。

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE crm_supplier ADD COLUMN auto_approve TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''自动审批:1开启,0关闭'' AFTER profit_ratio',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'crm_supplier'
      AND column_name = 'auto_approve'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

