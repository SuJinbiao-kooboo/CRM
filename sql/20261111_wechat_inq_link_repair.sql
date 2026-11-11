-- 2026-11-11 微信短报价 INQ 错配修复（幂等）
-- 753118「拆 2040」应关联 753062「inq: 8358 14pcs TP: 1850USD」，不是前一天的 654058。

UPDATE wechat_message m
JOIN wechat_message src ON src.id = 753062
SET m.relat_inq = JSON_OBJECT(
        'id', src.id,
        'server_id', src.server_id,
        'sender', src.sender,
        'category', src.msg_category,
        'content', LEFT(src.content, 800),
        'create_time', DATE_FORMAT(src.create_time, '%Y-%m-%d %H:%i:%s')),
    m.category_reason = 'AI:OFFER INQ=Eke；原关联654058为错误旧询价，已校正为最近我方询价753062'
WHERE m.id = 753118
  AND JSON_EXTRACT(m.relat_inq, '$.id') = 654058;

UPDATE crm_offer
SET product_code = '8358拆',
    product_brand = 'Intel',
    product_detail = 'Intel Xeon 8358 拆',
    spec = '',
    product_detail_code = 'XEON 8358',
    quantity = 14,
    price_cost = 2040.00,
    pn_normalized = '8358拆',
    remark = '8358拆',
    update_by = 'repair_20261111',
    update_time = NOW()
WHERE id = 19364
  AND product_code = '7713拆'
  AND wechat_local_id = 17;

UPDATE wechat_offer_task
SET handle_status = 'NO_ACTION',
    remark = CONCAT(IFNULL(remark, ''),
        '；已作废：原关联INQ 654058(EPYC 7713/2000pcs)错配，正确INQ为753062(8358/14pcs)'),
    matched_inq_json = NULL,
    update_by = 'repair_20261111',
    update_time = NOW()
WHERE id = 1449
  AND handle_status = 'PENDING_APPROVAL';

-- AI录入专用超时：默认10分钟；不影响微信分类等短请求。
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'AI录入超时(毫秒)', 'sys.ai.entry.timeout.ms', '600000', 'N', 'admin', NOW(),
       'Offer AI录入专用超时，默认10分钟；长报价/整表粘贴可单独调整'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.ai.entry.timeout.ms');

UPDATE sys_config
SET config_value = '600000', update_by = 'repair_20261111', update_time = NOW()
WHERE config_key IN ('sys.ai.entry.timeout.ms', 'sys.ai.timeout.ms')
  AND CAST(config_value AS UNSIGNED) < 600000;
