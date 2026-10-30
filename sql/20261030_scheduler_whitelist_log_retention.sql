-- 2026-10-30 定时任务独立IP白名单 + 调度日志保留3天

-- 1. sys_job 增加每条任务的IP白名单字段（幂等）
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

-- 2. 将原全局白名单迁移到现有微信自动化任务
UPDATE sys_job j
JOIN sys_config c ON c.config_key = 'wechat.scheduler.allowedIps'
SET j.allowed_ips = c.config_value
WHERE j.job_group = 'WECHAT'
  AND j.allowed_ips = '';

-- 3. 原全局参数不再使用，白名单统一由每条任务维护
DELETE FROM sys_config WHERE config_key = 'wechat.scheduler.allowedIps';

-- 4. 调度日志按时间清理需要索引（幂等）
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE sys_job_log ADD INDEX idx_sys_job_log_create_time (create_time)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_job_log'
      AND index_name = 'idx_sys_job_log_create_time'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 每天 00:10 删除3天前的调度日志
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, allowed_ips, create_by, create_time, remark)
SELECT '调度日志清理（保留3天）', 'SYSTEM', 'jobLogCleanTask.cleanExpiredLogs()', '0 10 0 * * ?', '2', '1', '0', '', 'admin', NOW(),
       '每天00:10自动删除3天前的sys_job_log记录'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'jobLogCleanTask.cleanExpiredLogs()'
);
