-- 662043 长报价超时消息重新入队（仅在新后端已部署、行式分段已生效后执行）
-- 该消息 108 行、4180 字符，已确认没有部分 crm_offer / 任务，可安全重试。

UPDATE wechat_message
SET entry_status = 'PENDING',
    entry_message = '长文本超时修复后重新入队',
    entry_fail_count = 0,
    status = 1,
    entry_at = NOW(),
    task_status = 'PENDING',
    task_message = '',
    task_failure_reason = '',
    task_at = NOW()
WHERE id = 662043
  AND entry_status = 'DEAD';
