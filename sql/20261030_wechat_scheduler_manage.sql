-- 2026-10-30 微信自动化定时任务迁移到 RuoYi 定时任务中心
-- 执行后访问：系统监控 -> 定时任务；任务组选择“微信自动化”

-- 任务分组字典
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '微信自动化', 'WECHAT', 'sys_job_group', '', '', 'N', '0', 'admin', NOW(), '微信询报价自动化任务组'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'sys_job_group' AND dict_value = 'WECHAT'
);

-- 本机 IPv4 白名单：多个 IP 用英文逗号分隔；不包含本机 IPv4 时任务只跳过，不执行
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '微信定时任务IP白名单', 'wechat.scheduler.allowedIps', '192.168.128.222', 'N', 'admin', NOW(),
       '仅本机IPv4命中白名单时运行微信定时任务；多个IP用英文逗号分隔'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'wechat.scheduler.allowedIps'
);

-- 1. 拉取微信消息：每5秒检查
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT '微信自动化-消息拉取', 'WECHAT', 'weChatClassifyScheduler.pollMessages()', '0/5 * * * * ?', '3', '1', '0', 'admin', NOW(), '实际拉取间隔由字典 pollIntervalSec 控制；仅白名单主机运行'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.pollMessages()');

-- 2. 未分类消息扫描：每10秒检查
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT '微信自动化-消息分类', 'WECHAT', 'weChatClassifyScheduler.scanUnclassified()', '0/10 * * * * ?', '3', '1', '0', 'admin', NOW(), '触发条件由字典 classifyTriggerCount / classifyMaxIdleSec 控制'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.scanUnclassified()');

-- 3. TRACE 预警补扫：每30秒检查
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT '微信自动化-TRACE预警', 'WECHAT', 'weChatClassifyScheduler.scanTraceAlerts()', '0/30 * * * * ?', '3', '1', '0', 'admin', NOW(), '只处理最近 traceAlertMaxAgeMin 分钟内的TRACE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.scanTraceAlerts()');

-- 4. 历史消息清理：每6小时
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT '微信自动化-历史消息清理', 'WECHAT', 'weChatClassifyScheduler.purgeOldMessages()', '0 0 0/6 * * ?', '3', '1', '0', 'admin', NOW(), '保留天数由字典 msgRetentionDays 控制'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.purgeOldMessages()');

-- 5. INQ/OFFER 自动录入：每10秒检查
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT '微信自动化-INQ/OFFER录入', 'WECHAT', 'weChatClassifyScheduler.scanOfferEntry()', '0/10 * * * * ?', '3', '1', '0', 'admin', NOW(), '实际开关和间隔由字典 offerEntryEnabled / offerEntryScanSec 控制'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.scanOfferEntry()');

-- 6. 询报价任务生成：每10秒检查
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT '微信自动化-任务生成', 'WECHAT', 'weChatClassifyScheduler.scanTaskGenerate()', '0/10 * * * * ?', '3', '1', '0', 'admin', NOW(), '实际开关和间隔由字典 autoTaskGenerateEnabled / taskGenerateScanSec 控制'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.scanTaskGenerate()');

-- 7. 询报价任务发送：每10秒检查
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT '微信自动化-任务发送', 'WECHAT', 'weChatClassifyScheduler.scanTaskSend()', '0/10 * * * * ?', '3', '1', '0', 'admin', NOW(), '安全发送限频：calm；间隔30~60s；单批≤3；每轮≤3组；每600s扫描；maxBatch=3；verify=true；autoSendEnabled=false'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.scanTaskSend()');
