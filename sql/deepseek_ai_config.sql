-- DeepSeek AI 连接参数：统一从系统参数配置(sys_config)读取，DeepSeekAiService.java 按需读取
-- 注意：sys_config 对 config_key 未建唯一索引，须用 UPDATE 全量修正 + INSERT WHERE NOT EXISTS，方可保证幂等
UPDATE sys_config SET config_value = 'deepseek-v4-flash-0731', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.model';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'DeepSeek模型名称', 'sys.ai.model', 'deepseek-v4-flash-0731', 'Y', 'admin', NOW(), 'admin', NOW(), 'AI录入/AI查询调用的模型'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.model');

UPDATE sys_config SET config_value = 'https://token-factory.hon-maas.com/v1', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.base.url';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'DeepSeek接口地址', 'sys.ai.base.url', 'https://token-factory.hon-maas.com/v1', 'Y', 'admin', NOW(), 'admin', NOW(), 'OpenAI兼容接口地址'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.base.url');

UPDATE sys_config SET config_value = 'sk-S4v5MQ02A9RhX7TEyegFkHPdRqVSJRwqbWCy97X0nUIQxAhy', update_by = 'admin', update_time = NOW() WHERE config_key = 'sys.ai.api.key';
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
SELECT 'DeepSeek API Key', 'sys.ai.api.key', 'sk-S4v5MQ02A9RhX7TEyegFkHPdRqVSJRwqbWCy97X0nUIQxAhy', 'Y', 'admin', NOW(), 'admin', NOW(), 'AI接口密钥(仅存库，不入代码)'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.api.key');