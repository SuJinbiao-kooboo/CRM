-- =====================================================================
-- 2026-10-30 微信 INQ/OFFER 连续消息聚合录入
-- 可重复执行；不会重置已有提示词，只在首次追加规则/示例
-- 执行后刷新字典缓存：POST /system/dict/data/refreshCache 或重启服务
-- =====================================================================

-- 1) 最近30秒内仍有新消息时先不录入（消息稳定窗口）
UPDATE sys_dict_data SET dict_value = '30', update_time = NOW()
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryMinAgeSec';

-- 2) 同一发送人连续消息聚合间隔（秒）
INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 146, 'offerEntryGroupGapSec', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '同一会话同一发送人相邻消息聚合间隔(秒)：间隔内的连续消息合并为一次AI录入'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT dict_code FROM sys_dict_data
                   WHERE dict_type = 'wechat_analysis_config'
                     AND dict_label = 'offerEntryGroupGapSec') t
);

-- 3) TRACE 只对最近6小时的消息告警
UPDATE sys_dict_data SET dict_value = '360', update_time = NOW()
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'traceAlertMaxAgeMin';

-- 4) 提示词：同一发送人连续消息继承前一条类型
UPDATE sys_dict_data
SET dict_value = REPLACE(dict_value, '【输出格式】',
    '【连续消息规则】\n13. 同一发送人在短时间内（默认30秒）连续发送多条消息时，按一个整体判断：后一条只补充前一条的料号、型号、规格、数量、价格、库存、交期、原装/新旧等信息的，继承前一条类型——前一条是 INQ 则补充消息也判 INQ；前一条是 OFFER 则补充消息也判 OFFER 并关联同一 INQ。此类补充不得判 TRACE。\n14. TRACE 只用于对已经存在的具体 INQ/OFFER 做二次了解或推进（可不含疑问句）；同一发送人紧接前一条补充信息的消息，不能因为前文有 INQ/OFFER 就判成 TRACE。\n\n【输出格式】'),
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'llmPromptBase'
  AND dict_value NOT LIKE '%【连续消息规则】%';

-- 5) 示例：连续两条 INQ、引用INQ的报价+交期补充
UPDATE sys_dict_data
SET dict_value = CONCAT(RTRIM(dict_value),
    '\n\n示例19 同一发送人连续两条合并成一个INQ\n#0 P [Ally] Intel 6346 33片有货吗？\n#1 P [Ally] 有货吗\n输出：[{"i":0,"cat":"INQ"},{"i":1,"cat":"INQ"}]\n\n示例20 引用INQ的报价 + 后续交期补充\n#0 H [SELF-OTHER] [May] inq: RTX Pro 6000 Workstation 40Pcs\n#1 P [Johnny] 18400USD\n#2 P [Johnny] 2weeks\n输出：[{"i":1,"cat":"OFFER","inq":0},{"i":2,"cat":"OFFER","inq":0}]'),
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'llmExamples'
  AND dict_value NOT LIKE '%示例19 同一发送人连续两条%';

-- 6) 修正历史误判：539168 是 539167 的连续补充，不是独立 TRACE
UPDATE wechat_message
SET msg_category = 'INQ',
    category_source = 'MANUAL',
    category_reason = '人工修正：与539167为同一发送人连续补充，合并为一个INQ',
    relat_inq = NULL,
    relat_offer = NULL,
    entry_status = 'PENDING',
    entry_message = '等待与539167聚合录入',
    task_status = 'PENDING',
    task_message = ''
WHERE id = 539168 AND msg_category = 'TRACE';