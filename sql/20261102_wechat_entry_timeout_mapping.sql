-- 微信 INQ/OFFER 自动录入：限制单次 AI 超时，并补充融科张程会话映射
UPDATE sys_config
SET config_value = '120000', update_by = 'admin', update_time = NOW(),
    remark = '单次模型调用2分钟；网络超时不重复重试，避免阻塞自动任务'
WHERE config_key = 'sys.ai.timeout.ms';

UPDATE crm_supplier
SET purchase_group = CONCAT_WS(',', NULLIF(purchase_group, ''), '张程 融科 CPU'),
    update_by = 'wechat_auto', update_time = NOW()
WHERE supplier_code = 'VC0820818'
  AND FIND_IN_SET('张程 融科 CPU', REPLACE(COALESCE(purchase_group, ''), '，', ',')) = 0;

-- 映射补齐后重排该会话此前因“群未映射”被跳过的消息
UPDATE wechat_message
SET entry_status = 'PENDING', entry_message = '补充供应商群映射后重新入队',
    entry_fail_count = 0, entry_at = NOW(), status = 1,
    task_status = 'PENDING', task_message = '', task_failure_reason = '', task_at = NOW()
WHERE name = '张程 融科 CPU'
  AND msg_category IN ('INQ', 'OFFER')
  AND entry_status IN ('SKIPPED', 'FAILED', 'DEAD');
