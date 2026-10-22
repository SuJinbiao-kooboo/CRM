-- =====================================================================
-- 微信消息 → CRM Offer 自动录入：表结构变更 + 枚举/参数字典（可重复执行）
-- 用途：
--   1) crm_offer 增加「来源微信消息」关联字段(wechat_server_id/wechat_username/wechat_local_id)；
--   2) wechat_message 增加 entry_fail_count(录入失败次数)，status 枚举升级为 0-4；
--   3) 新建枚举字典 wechat_message_status(0=未分类 1=已分类未处理 2=已分类处理未发送 3=无需处理 4=已分类处理发送)；
--   4) wechat_analysis_config 增加 INQ/OFFER 自动录入参数(offerEntry*)，全部可在字典内调整。
-- 用法(MySQL)：  mysql -uroot -p 数据库名 < wechat_offer_entry.sql
-- 导入后需刷新字典缓存(POST /system/dict/data/refreshCache)或重启 ruoyi-admin 生效。
-- =====================================================================

-- 1) crm_offer：来源微信消息关联字段（information_schema 守卫，可重复执行）
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_offer' AND COLUMN_NAME = 'wechat_server_id');
SET @s1 := IF(@c1 = 0, 'ALTER TABLE crm_offer ADD COLUMN wechat_server_id VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''来源微信消息server_id(聊天记录关联,可回查wechat_message)''', 'SELECT 1');
PREPARE st FROM @s1; EXECUTE st; DEALLOCATE PREPARE st;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_offer' AND COLUMN_NAME = 'wechat_username');
SET @s2 := IF(@c2 = 0, 'ALTER TABLE crm_offer ADD COLUMN wechat_username VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''来源微信会话标识username''', 'SELECT 1');
PREPARE st FROM @s2; EXECUTE st; DEALLOCATE PREPARE st;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_offer' AND COLUMN_NAME = 'wechat_local_id');
SET @s3 := IF(@c3 = 0, 'ALTER TABLE crm_offer ADD COLUMN wechat_local_id BIGINT NOT NULL DEFAULT 0 COMMENT ''来源微信消息local_id(与username组合唯一)''', 'SELECT 1');
PREPARE st FROM @s3; EXECUTE st; DEALLOCATE PREPARE st;

SET @i1 := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_offer' AND INDEX_NAME = 'idx_wechat_server_id');
SET @s4 := IF(@i1 = 0, 'ALTER TABLE crm_offer ADD INDEX idx_wechat_server_id (wechat_server_id)', 'SELECT 1');
PREPARE st FROM @s4; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) wechat_message：录入失败计数 + status 枚举含义升级
SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wechat_message' AND COLUMN_NAME = 'entry_fail_count');
SET @s5 := IF(@c4 = 0, 'ALTER TABLE wechat_message ADD COLUMN entry_fail_count INT NOT NULL DEFAULT 0 COMMENT ''AI录入失败次数(连续失败达offerEntryMaxFail次则置status=3无需处理)''', 'SELECT 1');
PREPARE st FROM @s5; EXECUTE st; DEALLOCATE PREPARE st;

ALTER TABLE wechat_message MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0=未分类 1=已分类未处理 2=已分类处理未发送 3=无需处理 4=已分类处理发送';

-- 3) 枚举字典：wechat_message_status
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '微信消息处理状态', 'wechat_message_status', '0', 'admin', NOW(), 'wechat_message.status 枚举: 0=未分类 1=已分类未处理 2=已分类处理未发送 3=无需处理 4=已分类处理发送'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'wechat_message_status');
DELETE FROM sys_dict_data WHERE dict_type = 'wechat_message_status';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) VALUES
(0, '未分类', '0', 'wechat_message_status', '', 'info',    'N', '0', 'admin', NOW(), '消息入库后默认状态，等待AI分类(分类后置为1)'),
(1, '已分类未处理', '1', 'wechat_message_status', '', 'warning', 'N', '0', 'admin', NOW(), 'AI分类已回写(INQ/OFFER/TRACE/OTHER)，等待录入/后续环节'),
(2, '已分类处理未发送', '2', 'wechat_message_status', '', 'primary', 'N', '0', 'admin', NOW(), 'INQ/OFFER已AI录入crm_offer并写入wechat_server_id关联，等待发送环节'),
(3, '无需处理', '3', 'wechat_message_status', '', 'danger',  'N', '0', 'admin', NOW(), '无需处理(如AI录入连续失败达上限、内容无法识别等)'),
(4, '已分类处理发送', '4', 'wechat_message_status', '', 'success', 'N', '0', 'admin', NOW(), '已分类、已处理并已完成发送(后续发送环节置位)');

-- 4) wechat_analysis_config：INQ/OFFER 自动录入参数(110-119)
DELETE FROM sys_dict_data WHERE dict_type = 'wechat_analysis_config' AND dict_sort BETWEEN 110 AND 119;
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark) VALUES
(110, 'offerEntryEnabled', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'INQ/OFFER录入总开关：true=定时自动把已分类未处理(status=1)的INQ/OFFER消息AI录入crm_offer；false=关闭(仅手动 POST /wechat/offer/entry/run)'),
(111, 'offerEntryScanSec', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '录入扫描间隔(秒)：定时任务按该间隔扫描一次待录入消息，防止并发重入(默认30)'),
(112, 'offerEntryBatchSize', '5', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '每轮最多录入条数(条)：每次取最早status=1且分类在offerEntryCategories内的N条(默认5)'),
(113, 'offerEntryCategories', 'INQ,OFFER', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '参与录入的消息分类(逗号分隔)：默认INQ,OFFER；TRACE/OTHER不录入'),
(114, 'offerEntrySupplierCode', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '默认供应商编码：录入crm_offer的supplier_code；offerEntrySupplierMap未命中时使用；为空则该会话消息跳过不录入(日志提示)'),
(116, 'offerEntrySupplierMap', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '群/好友名→供应商映射：英文逗号分隔多组，格式 会话名=供应商编码，如：特斯拉并购委员会=SELL001；命中优先于默认值；会话名需与wechat_message.name完全一致'),
(117, 'offerEntryProfitRatio', '2', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'OFFER报价利润率(%)：报价价=AI识别成本价×(1+利润率/100)，向上取整到5的倍数，与Offer列表AI录入口径一致(默认2)'),
(118, 'offerEntryMaxFail', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI录入失败上限(次)：同一消息连续录入失败达到该次数置status=3无需处理，避免失败消息一直卡住队列(默认3)'),
(119, 'offerEntryCreateBy', 'wechat_auto', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '录入人(create_by/update_by)：定时任务无登录用户，统一用该值标记AI自动录入来源(默认wechat_auto)');
