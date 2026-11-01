-- 2026-11-01 供应商群配置定时检查
-- 可重复执行；飞书地址沿用系统参数 sys.ai.alert.webhook。

-- 兼容尚未执行 20261030_scheduler_whitelist_log_retention.sql 的库
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_job ADD COLUMN allowed_ips varchar(1000) NOT NULL DEFAULT '''' AFTER status',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_job'
      AND column_name = 'allowed_ips'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 继承现有微信自动化任务的IP白名单，避免新任务在非授权主机运行
SET @wechat_allowed_ips = COALESCE((
    SELECT MAX(allowed_ips) FROM sys_job
    WHERE job_group = 'WECHAT' AND allowed_ips IS NOT NULL AND allowed_ips <> ''
), '');

INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, allowed_ips, create_by, create_time, remark)
SELECT '供应商群配置检查', 'WECHAT', 'supplierGroupCheckScheduler.checkMissingGroups()',
       '0 0 9 * * ?', '2', '1', '0', @wechat_allowed_ips, 'admin', NOW(),
       '每天09:00检查启用供应商；采购群和销售群均为空时推送飞书告警'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'supplierGroupCheckScheduler.checkMissingGroups()'
);
