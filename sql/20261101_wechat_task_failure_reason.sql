-- 微信消息增加独立的“任务生成失败原因”字段。
-- 可重复执行；已有该列时不做任何变更。
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE wechat_message ADD COLUMN task_failure_reason VARCHAR(1000) NOT NULL DEFAULT '''' COMMENT ''任务生成失败原因；成功生成或无需生成时为空'' AFTER task_message',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE table_schema = DATABASE()
      AND table_name = 'wechat_message'
      AND column_name = 'task_failure_reason'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
