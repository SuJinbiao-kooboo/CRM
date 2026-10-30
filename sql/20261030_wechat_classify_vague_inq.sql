-- 2026-10-30 微信分类：笼统问价/是否收货不算 INQ
-- 可重复执行；已存在规则或示例时不会重复追加

UPDATE sys_dict_data
SET dict_value = REPLACE(dict_value, '【输出格式】',
    '12. INQ 必须指向具体待询价的料号、型号、规格或品类：本条或明确引用的上文必须能确定“问的是什么货”，并表达询价/找货意图。只是在打听对方历史成交价（如“多少钱拿的”）、问对方是否还收货/还做不做（如“你们还收不”“还收吗”）、寒暄闲聊，且没有具体待询价对象的，一律 OTHER；若这类追问明确围绕前面某条具体 INQ/OFFER 推进条件，则判 TRACE。\n\n【输出格式】'),
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'llmPromptBase'
  AND dict_value NOT LIKE '%12. INQ 必须指向具体待询价%';

UPDATE sys_dict_data
SET dict_value = CONCAT(RTRIM(dict_value), '\n\n示例17 笼统询问历史价格或是否收货（不是INQ）\n#0 P [客户] 多少钱拿的\n#1 P [客户] 你们还收不\n输出：[{"i":0,"cat":"OTHER"},{"i":1,"cat":"OTHER"}]\n\n示例18 先问具体型号再问价（INQ→TRACE）\n#0 P [客户] 2565X 有货吗\n#1 P [客户] 多少钱\n输出：[{"i":0,"cat":"INQ"},{"i":1,"cat":"TRACE","inq":0}]'),
    update_time = NOW()
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label = 'llmExamples'
  AND dict_value NOT LIKE '%示例17 笼统询问历史价格%';

-- 修正用户指出的两条误判消息：笼统问价/是否收货，不是 INQ
UPDATE wechat_message
SET msg_category = 'OTHER',
    category_source = 'MANUAL',
    category_reason = '人工修正：笼统询问，无具体料号/型号',
    relat_inq = NULL,
    relat_offer = NULL
WHERE id IN (538405, 539079)
  AND msg_category = 'INQ';
