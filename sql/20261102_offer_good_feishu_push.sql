-- 2026-11-02 好价格 Offer 飞书推送
-- 可重复执行。阈值与页面“复制Offer/AI查询”共用 offer.price.advantage.threshold。

-- 阈值参数兜底（已有配置时不覆盖用户设置）
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'Offer价格优势阈值(USD)', 'offer.price.advantage.threshold', '15', 'Y', 'admin', NOW(),
       '复制Offer/AI查询可修改；好价格Offer定时推送共用'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'offer.price.advantage.threshold');

UPDATE sys_config
SET remark = '复制Offer/AI查询可修改；好价格Offer定时推送共用', update_time = NOW()
WHERE config_key = 'offer.price.advantage.threshold';

-- 好价格推送去重签名：同一自然日且内容一致时不重复推送
INSERT INTO sys_config
    (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '好价格Offer上次推送签名', 'offer.good.push.last.signature', '', 'N', 'admin', NOW(),
       '定时任务自动维护；同一自然日且推送内容一致时跳过'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'offer.good.push.last.signature');

-- 白天 9:00-21:00 每小时整点执行；默认使用“复制Offer”的最近2天范围
-- IP白名单继承现有 WECHAT 任务；本机IP不在白名单时 Quartz 自动跳过
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, allowed_ips, create_by, create_time, remark)
SELECT '好价格Offer-飞书推送', 'WECHAT', 'offerGoodPushScheduler.pushGoodOffers()', '0 0 9-21 * * ?', '3', '1', '0',
       COALESCE((SELECT MAX(allowed_ips) FROM sys_job WHERE job_group = 'WECHAT' AND allowed_ips <> ''), ''),
       'admin', NOW(),
       '9:00-21:00每小时执行；复制Offer最近2天最低价；CPU料号全部推送；同日同内容仅推送一次；飞书沿用sys.ai.alert.webhook'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'offerGoodPushScheduler.pushGoodOffers()'
);
