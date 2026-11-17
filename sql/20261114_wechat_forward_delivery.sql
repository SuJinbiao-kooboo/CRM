-- 2026-11-14 微信任务转发模式（FORWARD）
-- 供应商询价、客户推送、手工群发/自动询Offer 改为：先发到“文件传输助手”，
-- 再按每批最多 9 个目标点击“分别发送”；客户回复仍逐目标直发。
-- 执行后需刷新字典缓存或重启后端，使 wechat_send_config 新参数生效。

ALTER TABLE wechat_offer_task
    ADD COLUMN delivery_mode varchar(16) NOT NULL DEFAULT 'DIRECT'
        COMMENT '发送方式：DIRECT逐目标直发/FORWARD微信转发' AFTER send_type;

UPDATE wechat_offer_task
SET delivery_mode = 'FORWARD'
WHERE task_type IN ('SUPPLIER_INQUIRY', 'CUSTOMER_PUSH', 'MANUAL_SEND')
  AND delivery_mode <> 'FORWARD';

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 8, 'forwardSourceChat', '文件传输助手', 'wechat_send_config', 'N', '0', 'admin', NOW(),
       '转发模式的源会话：先发送到该会话，再右键转发给目标群'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_send_config' AND dict_label = 'forwardSourceChat'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 9, 'forwardChunkSize', '9', 'wechat_send_config', 'N', '0', 'admin', NOW(),
       '每次转发窗最多勾选的目标数，微信上限 9'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_send_config' AND dict_label = 'forwardChunkSize'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 10, 'forwardVerify', 'false', 'wechat_send_config', 'N', '0', 'admin', NOW(),
       '转发后是否回读会话确认；false 表示点击分别发送即视为成功'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_send_config' AND dict_label = 'forwardVerify'
);
