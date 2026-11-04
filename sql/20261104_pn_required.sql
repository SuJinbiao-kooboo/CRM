-- =====================================================================
-- 料号必填与空数据清理（2026-11-04）
-- 1. 群未映射不再生成空料号 UNMAPPED_CHAT 任务，来源消息转人工复核。
-- 2. 清理历史空料号任务和空料号 Offer。
-- 3. 为 Offer 表、微信询报价任务表增加非空约束。
-- 脚本可重复执行；约束已存在时跳过。
-- =====================================================================

SET @schema_name := DATABASE();

UPDATE wechat_message m
JOIN wechat_offer_task t
  ON m.username = t.source_username
 AND m.local_id = t.source_local_id
SET m.task_status = 'MANUAL_REVIEW',
    m.task_message = '群未映射异常仅记录在微信消息，不再生成空料号任务',
    m.task_failure_reason = CONCAT('UNMAPPED_CHAT: ',
        COALESCE(NULLIF(TRIM(t.remark), ''), '群未映射到供应商档案，未生成任务')),
    m.task_at = NOW()
WHERE t.pn_original IS NULL OR TRIM(t.pn_original) = '';

DELETE FROM wechat_offer_task
WHERE pn_original IS NULL OR TRIM(pn_original) = ''
   OR pn_normalized IS NULL OR TRIM(pn_normalized) = '';

DELETE FROM crm_offer
WHERE product_code IS NULL OR TRIM(product_code) = '';

SET @sql := IF(
    (SELECT IS_NULLABLE
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @schema_name
        AND TABLE_NAME = 'wechat_offer_task'
        AND COLUMN_NAME = 'pn_original') = 'YES',
    'ALTER TABLE wechat_offer_task MODIFY COLUMN pn_original VARCHAR(255) NOT NULL COMMENT ''原始料号''',
    'SELECT ''wechat_offer_task.pn_original 已是 NOT NULL'' AS message');
PREPARE ensure_stmt FROM @sql;
EXECUTE ensure_stmt;
DEALLOCATE PREPARE ensure_stmt;

SET @sql := IF(
    (SELECT COUNT(*)
       FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = @schema_name
        AND TABLE_NAME = 'crm_offer'
        AND CONSTRAINT_NAME = 'chk_crm_offer_product_code') = 0,
    'ALTER TABLE crm_offer ADD CONSTRAINT chk_crm_offer_product_code CHECK (product_code IS NOT NULL AND CHAR_LENGTH(TRIM(product_code)) > 0)',
    'SELECT ''chk_crm_offer_product_code 已存在'' AS message');
PREPARE ensure_stmt FROM @sql;
EXECUTE ensure_stmt;
DEALLOCATE PREPARE ensure_stmt;

SET @sql := IF(
    (SELECT COUNT(*)
       FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA = @schema_name
        AND TABLE_NAME = 'wechat_offer_task'
        AND CONSTRAINT_NAME = 'chk_wechat_offer_task_pn') = 0,
    'ALTER TABLE wechat_offer_task ADD CONSTRAINT chk_wechat_offer_task_pn CHECK (pn_original IS NOT NULL AND CHAR_LENGTH(TRIM(pn_original)) > 0 AND pn_normalized IS NOT NULL AND CHAR_LENGTH(TRIM(pn_normalized)) > 0)',
    'SELECT ''chk_wechat_offer_task_pn 已存在'' AS message');
PREPARE ensure_stmt FROM @sql;
EXECUTE ensure_stmt;
DEALLOCATE PREPARE ensure_stmt;
