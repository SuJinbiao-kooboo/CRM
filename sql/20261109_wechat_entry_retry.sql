-- 2026-11-09 微信 INQ/OFFER 自动录入：指数退避重试 + PROCESSING 自动解锁
-- 说明：
-- 1. offerEntryMaxFail 沿用原字典键，语义调整为“首次失败后的最大重试次数”，默认3次；
-- 2. offerEntryRetryBaseSec 默认60秒，退避节奏为60/120/240秒；
-- 3. offerEntryProcessingTimeoutSec 默认240秒，超时PROCESSING消息自动解锁并按失败进入重试；
-- 4. 代码已内置默认值；执行本脚本后系统字典页可直接维护这些参数。

UPDATE sys_dict_data
SET remark = 'AI录入首次失败后的最大自动重试次数(默认3次，总尝试=1+该值；退避60/120/240秒)',
    update_by = 'admin',
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryMaxFail';

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 119, 'offerEntryRetryBaseSec', '60', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'AI录入首次重试等待秒数(默认60；后续按2倍指数递增，60/120/240秒)'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryRetryBaseSec'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 120, 'offerEntryProcessingTimeoutSec', '240', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'AI录入PROCESSING自动解锁秒数(默认240；超时按失败处理并进入指数退避，避免阻塞队列)'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryProcessingTimeoutSec'
);
