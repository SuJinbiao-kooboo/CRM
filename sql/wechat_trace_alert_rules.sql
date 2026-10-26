-- =====================================================================
-- 微信 TRACE 规则增量脚本（可重复执行，不会重置现有提示词/示例）
-- 1. 我方成员（selfNames）的消息不判 TRACE；
-- 2. 同一聊天对象在 N 分钟内已成功发送 TRACE 预警时不再重复发送飞书。
-- 执行后刷新字典缓存：POST /system/dict/data/refreshCache，或重启服务。
-- =====================================================================

-- 0) 记录冷却跳过的 TRACE，避免冷却结束后补发旧预警
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE wechat_message ADD COLUMN trace_alert_suppressed_at DATETIME NULL COMMENT ''TRACE预警因同会话冷却跳过的时间，不再补发''',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wechat_message'
      AND COLUMN_NAME = 'trace_alert_suppressed_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1) 新增同会话预警冷却参数，默认 5 分钟，0=关闭
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 125, 'traceAlertChatCooldownMin', '5', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '同一聊天对象TRACE飞书预警冷却(分钟)：期间已成功预警则跳过，默认5；0=关闭冷却'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT dict_code FROM sys_dict_data
                   WHERE dict_type = 'wechat_analysis_config'
                     AND dict_label = 'traceAlertChatCooldownMin') t
);

-- 2) 给现有自定义提示词补充硬性规则；留空时由代码内置提示词生效
UPDATE sys_dict_data
SET dict_value = CONCAT(dict_value,
        '\n\n【我方成员补充规则】\n我方成员发送的消息不得判为TRACE，只能判INQ、OFFER或OTHER；即使内容像二次跟进，也判OTHER。买卖双向规则不变。')
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'llmPromptBase'
  AND dict_value <> ''
  AND dict_value NOT LIKE '%我方成员发送的消息不得判为TRACE%';

-- 3) 修正历史误判：我方成员不保留 TRACE，也不参与 TRACE 预警
UPDATE wechat_message
SET msg_category = 'OTHER',
    relat_inq = NULL,
    relat_offer = NULL,
    status = 3,
    trace_alert_error = NULL,
    category_reason = LEFT(
        CASE WHEN category_reason IS NULL OR category_reason = ''
             THEN '我方成员不判TRACE，按OTHER处理'
             ELSE CONCAT(category_reason, '；我方成员不判TRACE，按OTHER处理')
        END, 512)
WHERE msg_category = 'TRACE'
  AND LOWER(TRIM(sender)) IN ('eke', 'may', 'jin', 'jasmine');
