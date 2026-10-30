-- =====================================================================
-- 2026-10-30 微信同发送人连续消息组 INQ/OFFER 整组归并
-- 可重复执行；提示词按旧规则精确替换，示例仅在首次追加
-- 执行后刷新字典缓存：POST /system/dict/data/refreshCache 或重启服务
-- =====================================================================

-- 1) 提示词：不再按第一条子消息机械继承，改为整组判断
UPDATE sys_dict_data
SET dict_value = REPLACE(dict_value,
    '13. 同一发送人在短时间内（默认30秒）连续发送多条消息时，按一个整体判断：后一条只补充前一条的料号、型号、规格、数量、价格、库存、交期、原装/新旧等信息的，继承前一条类型——前一条是 INQ 则补充消息也判 INQ；前一条是 OFFER 则补充消息也判 OFFER 并关联同一 INQ。此类补充不得判 TRACE。',
    '13. 同一发送人在短时间内（默认30秒）连续发送多条消息时，必须把整组当作一条业务消息判断，不能只沿用第一条子消息：先看整组是在找货/问价，还是报价/给出供货条件。组内出现 OFFER 且无明确询价问句时，前面被误判的型号清单、库存等补充也统一为 OFFER；组内出现 INQ 且无 OFFER 时，前序型号清单、数量等补充也统一为 INQ。此类连续补充不得判 TRACE。'),
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'llmPromptBase'
  AND dict_value LIKE '%13. 同一发送人在短时间内（默认30秒）连续发送多条消息时，按一个整体判断%';

-- 2) 示例：INQ 与 OFFER 两种连续消息组
UPDATE sys_dict_data
SET dict_value = CONCAT(RTRIM(dict_value),
    '\n\n示例21 同发送人连续组按整组判断\n#0 P [May] 5317\n#1 P [May] 10pcs 有货吗\n#2 P [供应商] 超微有这些\n#3 P [供应商] 都是国内库存\n输出：[{"i":0,"cat":"INQ"},{"i":1,"cat":"INQ"},{"i":2,"cat":"OFFER"},{"i":3,"cat":"OFFER"}]'),
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'llmExamples'
  AND dict_value NOT LIKE '%示例21 同发送人连续组按整组判断%';

-- 2.1) 明确配置含义：分类归并与录入共用同一间隔
UPDATE sys_dict_data
SET remark = '同一会话同一发送人相邻消息聚合间隔(秒)：同时用于分类整组判断和一次AI录入，默认30，可在字典修改',
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'offerEntryGroupGapSec';

-- 3) 修正当前群 571675/571678/571679 的 OFFER 组误判
-- 仅修正尚未进入录入或任务流程的数据，避免覆盖已处理结果
UPDATE wechat_message
SET msg_category = 'OFFER',
    category_source = 'LLM',
    category_reason = 'AI:OFFER 同发送人连续消息归并',
    relat_inq = NULL,
    relat_offer = NULL,
    classify_status = 'SUCCESS',
    classify_at = NOW(),
    status = 1,
    entry_status = 'PENDING',
    entry_message = '',
    entry_at = NOW(),
    task_status = 'PENDING',
    task_at = NOW(),
    task_message = ''
WHERE id IN (571675, 571678, 571679)
  AND (entry_status IS NULL OR entry_status IN ('PENDING', 'FAILED', 'NOT_APPLICABLE', ''))
  AND (task_status IS NULL OR task_status IN ('PENDING', 'FAILED', 'SKIPPED', ''));

-- 4) 核对结果
SELECT id, msg_category, category_reason, relat_inq, relat_offer, entry_status, task_status
FROM wechat_message
WHERE id IN (571675, 571678, 571679)
ORDER BY id;
