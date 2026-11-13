-- 2026-11-12 微信询报价：未关联供应商、手工任务、来源对象、双飞书机器人
-- 执行顺序：先备份；本脚本包含一次性 DDL，建议在一个发布窗口内按顺序执行。

-- 1) 固定“未关联”兜底供应商：仅用于归档、来源审计和告警，不参与自动/手工群发。
INSERT INTO crm_supplier
    (supplier_code, supplier_name, supplier_short_name, supplier_type, status,
     profit_ratio, auto_approve, create_by, create_time, remark)
SELECT 'UNLINKED', '未关联', '未关联', '系统兜底', 1,
       2.00, 0, 'admin', NOW(),
       '系统固定兜底供应商：聊天群名无法匹配任何供应商时自动关联；禁止参与自动选群、手工群发和群配置检查'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM crm_supplier WHERE UPPER(COALESCE(supplier_code, '')) = 'UNLINKED'
);

-- 2) 任务来源：自动生成写“消息自动”，弹窗新建写“人工手动”。
ALTER TABLE wechat_offer_task
    ADD COLUMN task_source VARCHAR(16) NOT NULL DEFAULT '消息自动'
        COMMENT '任务来源：消息自动/人工手动' AFTER source_category,
    ADD KEY idx_task_source_value (task_source);

-- 3) 发送目标列容量说明：
--    send_target / send_target_success 已经是 MySQL JSON 类型，不存在 varchar 长度上限。
--    业务层统一限制为“最多 32 个目标，每个目标的群名/伙伴编码/伙伴名称单字段最多 300 字符”。
--    以下 MODIFY 只更新字段注释，不改变 JSON 数据结构，可重复作为容量记录核对。
ALTER TABLE wechat_offer_task
    MODIFY COLUMN send_target JSON
        COMMENT '发送目标JSON数组：最多32个目标，每个目标各字段最多300字符',
    MODIFY COLUMN send_target_success JSON
        COMMENT '发送成功目标JSON数组：最多32个目标，每个目标各字段最多300字符';

-- 4) 微信来源对象：供应商 + 聊天对象 + 发言人唯一；只给管理员/经理返回真实值。
CREATE TABLE IF NOT EXISTS crm_supplier_wechat_source (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    chat_name VARCHAR(300) NOT NULL COMMENT '微信聊天对象/群名',
    sender VARCHAR(300) NOT NULL DEFAULT '未知' COMMENT '微信发言人',
    first_seen_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次出现时间',
    last_seen_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后出现时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_chat_sender (supplier_id, chat_name, sender),
    KEY idx_supplier_source_last_seen (supplier_id, last_seen_time),
    KEY idx_source_last_seen (last_seen_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='微信自动录入来源对象：供应商-聊天对象-发言人';

-- 5) 飞书机器人字典：label=机器人标识，value=Webhook，css_class=Secret，remark=用途。
INSERT INTO sys_dict_type
    (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '飞书机器人配置', 'feishu_robot_config', '0', 'admin', NOW(),
       '飞书机器人统一配置：dict_label=机器人标识，dict_value=Webhook，css_class=Secret，remark=用途'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'feishu_robot_config'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 1, 'business_alert',
       'https://open.feishu.cn/open-apis/bot/v2/hook/5f32c741-59e3-4ca4-81a7-42b27fe3e9ae',
       'feishu_robot_config', 'PKCMSfibXAnNStFMsNJKsc', '', 'N', '0', 'admin', NOW(),
       '业务告警机器人；用途：Offer待跟进、微信失败聚合、供应商群配置缺失、AI通道故障/恢复、未关联供应商聊天、其余所有业务告警'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'feishu_robot_config' AND dict_label = 'business_alert'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 2, 'legacy_trace_offer',
       'https://open.feishu.cn/open-apis/bot/v2/hook/fa58479b-6c7c-4d08-9c68-1403b8c0dead',
       'feishu_robot_config', '', '', 'N', '0', 'admin', NOW(),
       '旧机器人；用途：仅微信TRACE预警、好价格Offer定时推送，不接收其他业务告警'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'feishu_robot_config' AND dict_label = 'legacy_trace_offer'
);

-- 6) 未关联供应商聊天提醒：每天 09:00-22:00 整点执行；无数据不发送。
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy,
     concurrent, status, allowed_ips, create_by, create_time, remark)
SELECT '微信自动化-未关联聊天告警', 'WECHAT',
       'weChatClassifyScheduler.alertUnlinkedChats()', '0 0 9-22 * * ?', '3', '1', '0',
       COALESCE((SELECT MAX(allowed_ips) FROM sys_job WHERE job_group = 'WECHAT' AND allowed_ips <> ''), ''),
       'admin', NOW(),
       '每天09:00-22:00整点扫描当天历史上发过INQ/OFFER的人员进入未关联供应商聊天对象的记录；去重后推送业务告警机器人，无数据不发'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'weChatClassifyScheduler.alertUnlinkedChats()'
);
