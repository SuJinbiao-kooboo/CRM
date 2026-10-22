-- =====================================================================
-- 微信双向询报价与安全自动录入 v4（2026-10-22）
-- 适用：已有库增量升级。可重复执行；不会清空历史消息、不会删除 crm_offer。
-- 执行后需刷新字典缓存或重启 ruoyi-admin。
-- =====================================================================

-- 1) 独立录入状态字段（不覆盖消息处理状态 status）
SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE wechat_message ADD COLUMN entry_status VARCHAR(24) NOT NULL DEFAULT ''NOT_APPLICABLE'' COMMENT ''自动录入状态：PENDING/PROCESSING/SUCCESS/SUCCESS_WARN/FAILED/DEAD/SKIPPED/NOT_APPLICABLE'' AFTER entry_fail_count, ADD COLUMN entry_message VARCHAR(1000) NOT NULL DEFAULT '''' COMMENT ''自动录入结果或跳过/失败原因'' AFTER entry_status, ADD COLUMN entry_offer_count INT NOT NULL DEFAULT 0 COMMENT ''本次成功写入crm_offer行数'' AFTER entry_message, ADD COLUMN entry_offer_ids VARCHAR(1000) NOT NULL DEFAULT '''' COMMENT ''本次写入的crm_offer主键列表'' AFTER entry_offer_count, ADD COLUMN entry_at DATETIME NULL COMMENT ''最近一次录入状态更新时间'' AFTER entry_offer_ids',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wechat_message' AND COLUMN_NAME = 'entry_status'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx = (
  SELECT IF(COUNT(*) = 0,
    'CREATE INDEX idx_entry_status_since ON wechat_message(entry_status, create_time)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wechat_message' AND INDEX_NAME = 'idx_entry_status_since'
);
PREPARE stmt FROM @idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 我方成员：买卖双向场景，只用于识别角色，不再决定 INQ/OFFER 方向
UPDATE sys_dict_data
SET dict_value = 'Eke,May,Jin,Jasmine',
    remark = '我方微信名/别名(逗号分隔)：仅用于判断角色，INQ/OFFER均可由任一方发出'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'selfNames';
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 122, 'selfNames', 'Eke,May,Jin,Jasmine', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '我方微信名/别名(逗号分隔)：仅用于判断角色，INQ/OFFER均可由任一方发出'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='selfNames');

-- 3) 清空旧单向提示词/示例，回退到代码内置的双向规则；strictCustomerOnly 保留为兼容项但停用
UPDATE sys_dict_data SET dict_value = '', remark = '留空使用代码内置双向语义提示词'
WHERE dict_type='wechat_analysis_config' AND dict_label='llmPromptBase';
UPDATE sys_dict_data SET dict_value = '', remark = '留空使用代码内置双向业务示例'
WHERE dict_type='wechat_analysis_config' AND dict_label='llmExamples';
UPDATE sys_dict_data SET dict_value='false', remark='已废弃：买卖双向，不再限制只保留客户发起'
WHERE dict_type='wechat_analysis_config' AND dict_label='strictCustomerOnly';
UPDATE sys_dict_data SET dict_value='true', remark='严格业务语义：方向看问价/给价，不看发送方；无关联广播降级OTHER'
WHERE dict_type='wechat_analysis_config' AND dict_label='strictBusinessSemantics';
UPDATE sys_dict_data SET remark='OFFER自动录入前必须有对向INQ关联（买卖任一方均可为INQ）'
WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryRequireRelatInq';
UPDATE sys_dict_data SET remark='TRACE飞书预警：已关联INQ/OFFER的二次业务跟进，疑问或陈述均可'
WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertEnabled';

-- 4) 自动录入安全参数：至少等待60秒补足上下文；最多回溯24小时
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 137, 'offerEntryMinAgeSec', '60', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '分类后等待秒数：让同一会话后续消息进入上下文后再自动录入，默认60'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMinAgeSec');
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 138, 'offerEntryMaxAgeHours', '24', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '自动录入最大回溯小时数：超期历史消息需人工重新入队，默认24'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMaxAgeHours');
UPDATE sys_dict_data SET remark='AI录入失败上限(次)：达到后置DEAD，需人工确认后调用重新入队接口'
WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMaxFail';

-- 5) 升级前历史 INQ/OFFER 一律不自动补录：
--    已能从 crm_offer 反查到成功记录的标 SUCCESS；其余标 SKIPPED 并置 status=3，需人工确认后重排。
UPDATE wechat_message wm
LEFT JOIN (
    SELECT wechat_username, wechat_local_id,
           COUNT(*) AS offer_cnt,
           GROUP_CONCAT(id ORDER BY id SEPARATOR ',') AS offer_ids
    FROM crm_offer
    WHERE wechat_username IS NOT NULL AND wechat_username <> ''
    GROUP BY wechat_username, wechat_local_id
) o ON o.wechat_username = wm.username AND o.wechat_local_id = wm.local_id
SET wm.status = IF(COALESCE(o.offer_cnt, 0) > 0, 2, 3),
    wm.entry_status = IF(COALESCE(o.offer_cnt, 0) > 0, 'SUCCESS', 'SKIPPED'),
    wm.entry_message = IF(COALESCE(o.offer_cnt, 0) > 0,
        '升级前已存在crm_offer来源记录，按成功归档',
        '升级前历史消息未自动补录，需人工确认后调用 /wechat/offer/entry/requeue'),
    wm.entry_offer_count = COALESCE(o.offer_cnt, 0),
    wm.entry_offer_ids = COALESCE(o.offer_ids, ''),
    wm.entry_at = NOW()
WHERE wm.msg_category IN ('INQ','OFFER')
  AND (wm.entry_status IS NULL OR wm.entry_status = 'NOT_APPLICABLE');

-- 6) 审计（不自动执行）：查看升级后状态分布
-- SELECT entry_status, COUNT(*) FROM wechat_message
-- WHERE msg_category IN ('INQ','OFFER') GROUP BY entry_status;
