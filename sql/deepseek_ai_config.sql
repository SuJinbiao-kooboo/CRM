-- =====================================================================
-- AI 接口统一配置（唯一配置来源）
-- 说明：
--   项目内所有大模型调用（Offer AI 智能录入、微信消息 AI 分类等）统一读取 sys_config 中的 sys.ai.* 参数，
--   由 AiChannelService 统一发起 OpenAI 兼容的 /chat/completions 请求，并实现防呆：
--   主渠道（第三方聚合平台）连续失败达到阈值后自动切换备用渠道（DeepSeek 官方），
--   备用模式下周期性探测主渠道，恢复后自动切回；切换/恢复/主备均故障都会推送告警（未配 webhook 只记日志）。
--   历史分散配置（数据字典 wechat_analysis_config 的 llmApiKey/llmApiBase/llmModel/llmBackup*、application.yml 的 deepseek.*）已废弃。
-- 幂等：sys_config 对 config_key 未建唯一索引，统一用 UPDATE 全量修正 + INSERT WHERE NOT EXISTS。
-- 敏感项：sys.ai.api.key / sys.ai.backup.api.key 为线上维护项，脚本内的值仅作初始化，请勿据此判断线上实际值。
-- =====================================================================

-- 0) 总开关
UPDATE sys_config SET config_value = '1', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.enabled';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI能力总开关', 'sys.ai.enabled', '1', 'Y', 'admin', NOW(), 'admin', NOW(), '1=true=启用AI能力；0=false=关闭所有AI调用'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.enabled');

-- 1) 主渠道：第三方聚合平台（当前使用）
UPDATE sys_config SET config_value = 'https://token-factory.hon-maas.com/v1', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.base.url';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI主渠道接口地址', 'sys.ai.base.url', 'https://token-factory.hon-maas.com/v1', 'Y', 'admin', NOW(), 'admin', NOW(), 'OpenAI兼容接口根地址(不要以/结尾)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.base.url');

UPDATE sys_config SET config_value = 'deepseek-v4.1-flash-1017', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.model';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI主渠道模型名称', 'sys.ai.model', 'deepseek-v4.1-flash-1017', 'Y', 'admin', NOW(), 'admin', NOW(), '主渠道模型(第三方聚合平台)，全局AI调用共用'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.model');

UPDATE sys_config SET config_value = 'sk-S4v5MQ02A9RhX7TEyegFkHPdRqVSJRwqbWCy97X0nUIQxAhy', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.api.key';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI主渠道Key', 'sys.ai.api.key', 'sk-S4v5MQ02A9RhX7TEyegFkHPdRqVSJRwqbWCy97X0nUIQxAhy', 'Y', 'admin', NOW(), 'admin', NOW(), '主渠道密钥(敏感，仅线上维护)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.api.key');

-- 2) 备用渠道：DeepSeek 官方（主渠道故障时自动切换）
UPDATE sys_config SET config_value = 'true', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.backup.enabled';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI备用渠道开关', 'sys.ai.backup.enabled', 'true', 'Y', 'admin', NOW(), 'admin', NOW(), 'true=主渠道连续失败达阈值后自动切换备用渠道；false=不切换'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.backup.enabled');

UPDATE sys_config SET config_value = 'https://api.deepseek.com', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.backup.base.url';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI备用渠道接口地址', 'sys.ai.backup.base.url', 'https://api.deepseek.com', 'Y', 'admin', NOW(), 'admin', NOW(), '备用渠道根地址(DeepSeek官方，不要以/结尾)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.backup.base.url');

UPDATE sys_config SET config_value = 'deepseek-chat', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.backup.model';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI备用渠道模型名称', 'sys.ai.backup.model', 'deepseek-chat', 'Y', 'admin', NOW(), 'admin', NOW(), '备用渠道模型(DeepSeek官方模型名，与主渠道第三方模型名不通用)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.backup.model');

-- 备用渠道Key：敏感信息，脚本刻意留空，只通过线上系统参数维护；未配置时主渠道故障只告警不切换
UPDATE sys_config SET config_value = '', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.backup.api.key';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI备用渠道Key', 'sys.ai.backup.api.key', '', 'Y', 'admin', NOW(), 'admin', NOW(), 'DeepSeek官方Key(敏感，仅线上维护，不落脚本)；为空则主渠道故障时不切换'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.backup.api.key');

-- 3) 切换策略
UPDATE sys_config SET config_value = '3', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.fail.threshold';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI主渠道切换阈值', 'sys.ai.fail.threshold', '3', 'Y', 'admin', NOW(), 'admin', NOW(), '主渠道连续失败达到该次数后切换备用渠道'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.fail.threshold');

UPDATE sys_config SET config_value = '300', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.backup.check.sec';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI主渠道恢复探测间隔(秒)', 'sys.ai.backup.check.sec', '300', 'Y', 'admin', NOW(), 'admin', NOW(), '备用模式下每隔该秒数探测主渠道，恢复后自动切回'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.backup.check.sec');

-- 4) 超时与告警
UPDATE sys_config SET config_value = '480000', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.timeout.ms';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI接口超时(毫秒)', 'sys.ai.timeout.ms', '480000', 'Y', 'admin', NOW(), 'admin', NOW(), '默认8分钟；需与前端axios超时、nginx proxy_read_timeout 匹配'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.timeout.ms');

UPDATE sys_config SET config_value = '', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.alert.webhook';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI故障告警飞书Webhook', 'sys.ai.alert.webhook', '', 'Y', 'admin', NOW(), 'admin', NOW(), '渠道切换/故障/恢复时推送告警；留空=仅记日志'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.alert.webhook');

UPDATE sys_config SET config_value = '600', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.alert.cooldown.sec';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'AI告警冷却时间(秒)', 'sys.ai.alert.cooldown.sec', '600', 'Y', 'admin', NOW(), 'admin', NOW(), '同一类告警在冷却期内不重复推送，防刷屏'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.alert.cooldown.sec');