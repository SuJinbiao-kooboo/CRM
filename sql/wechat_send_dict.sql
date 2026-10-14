-- =====================================================================
-- 微信自动发送参数（数据字典）
-- 用途：控制批量发送的拟人节奏与防封参数。服务(WeChatGatewayService)
--       发送时从本字典读取(dict_type=wechat_send_config)，缺失项回退到
--       application.yml 的 wechat.send.* 默认值。
-- 用法(MySQL)：  mysql -uroot -p 数据库名 < wechat_send_dict.sql
-- 导入后需刷新字典缓存：调用接口 POST /system/dict/data/refreshCache
--   (带管理员 token)，或在系统管理>字典管理里重新加载。
-- =====================================================================

-- 1) 字典类型
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '微信批量发送参数', 'wechat_send_config', '0', 'admin', NOW(),
       '控制自动发送的拟人节奏与防封参数；发送时读取本字典，缺省回退 application.yml 的 wechat.send.*'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='wechat_send_config');

-- 2) 字典数据（dict_label = 参数名，dict_value = 参数值，dict_sort = 顺序，remark = 说明）
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark) VALUES
(1, 'rhythm',       'calm',  'wechat_send_config', 'N', '0', 'admin', NOW(), '拟人节奏档位：calm(最稳,推荐) / fast / off'),
(2, 'napMin',       '1.0',   'wechat_send_config', 'N', '0', 'admin', NOW(), '等待时长抖动倍率下限(>=1.0)，越大越像真人'),
(3, 'napMax',       '1.6',   'wechat_send_config', 'N', '0', 'admin', NOW(), '等待时长抖动倍率上限'),
(4, 'intervalMin',  '6',     'wechat_send_config', 'N', '0', 'admin', NOW(), '每条消息随机间隔下限(秒)，越大越安全'),
(5, 'intervalMax',  '15',    'wechat_send_config', 'N', '0', 'admin', NOW(), '每条消息随机间隔上限(秒)'),
(6, 'maxBatch',     '20',    'wechat_send_config', 'N', '0', 'admin', NOW(), '单批数量上限，超过直接拒绝发送'),
(7, 'verify',       'true',  'wechat_send_config', 'N', '0', 'admin', NOW(), '发送后是否回读数据库确认(防止重复发送)')
ON DUPLICATE KEY UPDATE dict_value=VALUES(dict_value), remark=VALUES(remark), status='0';
