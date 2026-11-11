-- 2026-11-11 微信 AI 超时联动、失败聚合告警
-- 说明：整包脚本应在发布新版后端后执行；仅更新超时参数可先行执行。
-- 可重复执行。

-- 1) 模型单次调用超时由 2 分钟延长到 5 分钟
UPDATE sys_config
SET config_value = '300000', update_by = 'admin', update_time = NOW(),
    remark = '单次模型调用5分钟；长报价和多行物料需要更长响应时间，网络超时不重复重试'
WHERE config_key = 'sys.ai.timeout.ms';

INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'AI接口超时(毫秒)', 'sys.ai.timeout.ms', '300000', 'Y', 'admin', NOW(),
       '单次模型调用5分钟；长报价和多行物料需要更长响应时间，网络超时不重复重试'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.timeout.ms');

-- 2) 录入 PROCESSING 解锁时间必须覆盖模型总超时
UPDATE sys_dict_data
SET dict_value = '360', remark = 'PROCESSING自动解锁秒数；代码会再以 sys.ai.timeout.ms 秒数+60秒作为下限',
    update_by = 'admin', update_time = NOW()
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryProcessingTimeoutSec';

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 160, 'offerEntryProcessingTimeoutSec', '360', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'PROCESSING自动解锁秒数；代码会再以 sys.ai.timeout.ms 秒数+60秒作为下限'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryProcessingTimeoutSec'
);

-- 3) 失败聚合告警参数
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 161, 'failureAlertEnabled', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '分类/录入/任务失败聚合告警开关：true=启用，false=关闭'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'failureAlertEnabled'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 162, 'failureAlertThreshold', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '失败聚合告警阈值(条)：统计窗口内分类/录入/任务失败合计达到该值才发送，默认3'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'failureAlertThreshold'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 163, 'failureAlertWindowMin', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '失败聚合告警统计窗口(分钟)：默认回看最近30分钟，每10分钟最多告警一次'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'failureAlertWindowMin'
);

-- 4) 09:00-21:30 每10分钟检查一次；发布新版后端后执行
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, allowed_ips, create_by, create_time, remark)
SELECT '微信自动化-失败聚合告警', 'WECHAT', 'weChatClassifyScheduler.alertFailures()',
       '0 0/10 9-21 * * ?', '3', '1', '0',
       COALESCE((SELECT MAX(allowed_ips) FROM sys_job WHERE job_group = 'WECHAT' AND allowed_ips <> ''), ''),
       'admin', NOW(),
       '09:00-21:30每10分钟检查最近30分钟分类/录入/任务失败，达到阈值后推送飞书；代码内置时段与10分钟去重'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.alertFailures()'
);

UPDATE sys_job
SET cron_expression = '0 0/10 9-21 * * ?', job_name = '微信自动化-失败聚合告警',
    remark = '09:00-21:30每10分钟检查最近30分钟分类/录入/任务失败，达到阈值后推送飞书；代码内置时段与10分钟去重',
    update_by = 'admin', update_time = NOW()
WHERE invoke_target = 'weChatClassifyScheduler.alertFailures()';
