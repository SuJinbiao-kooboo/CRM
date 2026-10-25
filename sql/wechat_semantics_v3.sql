-- =====================================================================
-- 微信询报价链路增量迁移 v3（2026-10-22）
-- 兼容已有库，可重复执行；不会删除 sys_dict_data，也不覆盖现有业务配置。
-- 执行后刷新字典缓存或重启后端。
-- =====================================================================

-- 1) server_id 实际格式为「会话名:群ID:时间戳」，64 位会触发
--    Data too long for column 'server_id'，统一扩容到 255。
ALTER TABLE wechat_message
    MODIFY COLUMN server_id VARCHAR(255) DEFAULT '' COMMENT '服务器消息ID';
ALTER TABLE wechat_message_black
    MODIFY COLUMN server_id VARCHAR(255) DEFAULT '' COMMENT '服务器消息ID';
ALTER TABLE crm_offer
    MODIFY COLUMN wechat_server_id VARCHAR(255) NOT NULL DEFAULT ''
        COMMENT '来源微信消息server_id(聊天记录关联,可回查wechat_message)';

-- 2) 新增语义关联时间窗和自动录入门禁参数。
--    只补齐缺失项，已有值保持人工配置不变。
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 130, 'offerLinkMaxAgeMin', '180', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'OFFER关联客户INQ的最大时间窗(分钟)，默认180'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerLinkMaxAgeMin');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 131, 'offerShortReplyWindowMin', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '无料号/无引用的短报价允许关联INQ的时间窗(分钟)，默认30'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerShortReplyWindowMin');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 132, 'traceLinkMaxAgeMin', '180', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'TRACE关联INQ/OFFER的最大时间窗(分钟)，默认180'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='traceLinkMaxAgeMin');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 133, 'traceShortWindowMin', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '群聊短追问仅允许在该时间窗内关联(分钟)，默认30'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='traceShortWindowMin');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 134, 'traceAlertScanBatchSize', '50', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'TRACE预警补扫每批条数(1-500)，默认50'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertScanBatchSize');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 135, 'offerEntryRequireRelatInq', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'OFFER自动录入crm_offer前必须关联客户INQ，默认true'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryRequireRelatInq');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 136, 'offerEntryRequireOfferPrice', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'OFFER自动录入crm_offer前必须有可解析正价格，默认true'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryRequireOfferPrice');

-- 3) 兼容缺失的基础配置；仅插入，不覆盖当前库中已维护的名单和供应商映射。
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 104, 'pollIgnoreChats', '业主群,玩乐吃喝,封窗进展,兔宝宝山樾湾', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '拉取过滤：名称包含任一关键词的群/好友消息转存 wechat_message_black，不进入分类'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='pollIgnoreChats');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 2, 'blackNames', '山樾湾2F业主群,前海路5号玩乐吃喝,封窗进展,兔宝宝山樾湾2F 812苏总', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '会话排除名单：名称包含任一关键词即转存 wechat_message_black，永远优先于白名单'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='blackNames');

UPDATE sys_dict_data
SET remark='会话排除名单：名称包含任一关键词即转存 wechat_message_black，永远优先于白名单'
WHERE dict_type='wechat_analysis_config' AND dict_label='blackNames';
UPDATE sys_dict_data
SET remark='拉取过滤：名称包含任一关键词的群/好友消息转存 wechat_message_black，不进入分类'
WHERE dict_type='wechat_analysis_config' AND dict_label='pollIgnoreChats';

-- 4) 历史纠正：非业务群已进入主表的消息统一标记 OTHER/FILTER/status=3。
UPDATE wechat_message
SET msg_category='OTHER',
    category_source='FILTER',
    category_reason='非业务群排除（历史纠正）',
    status=3,
    relat_inq=NULL,
    relat_offer=NULL
WHERE name IN (
    '山樾湾2F业主群',
    '前海路5号玩乐吃喝',
    '封窗进展',
    '兔宝宝山樾湾2F 812苏总'
);

-- 5) 审计参考（不自动执行）：
--    清理 crm_offer 前先确认来源会话、价格和型号，避免误删正常业务数据。
-- SELECT id, material_model, cost_price, offer_price, wechat_server_id, wechat_username,
--        wechat_local_id, create_time
-- FROM crm_offer
-- WHERE wechat_username IN (
--     SELECT username FROM wechat_message_black
--     WHERE name IN ('山樾湾2F业主群','前海路5号玩乐吃喝','封窗进展','兔宝宝山樾湾2F 812苏总')
-- );

-- 历史重跑分类必须清空全部分类字段，不能只把 status 改回 0：
-- UPDATE wechat_message
-- SET status=0, msg_category='', category_source='', category_reason='',
--     relat_inq=NULL, relat_offer=NULL, entry_fail_count=0
-- WHERE id IN (...);
