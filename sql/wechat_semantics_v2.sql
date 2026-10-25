-- =====================================================================
-- 微信分类语义收紧 v2（2026-10-22）
-- 目标：
--   1) 补全非业务群排除配置（pollIgnoreChats / blackNames）；
--   2) 明确 INQ=客户问我方、OFFER=对INQ的回应、TRACE=客户二次追问；
--   3) 开启 TRACE 飞书预警；
--   4) 修正历史非业务群误分类。
-- 执行后需刷新字典缓存或重启后端。
-- =====================================================================

-- 1) 非业务群：新消息直接转存 wechat_message_black，不再进入分类主表
UPDATE sys_dict_data
SET dict_value = '业主群,玩乐吃喝,封窗进展,兔宝宝山樾湾',
    remark = '拉取过滤：名称包含任一关键词的群/好友消息转存 wechat_message_black，不进入分类；多个用英文逗号分隔'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'pollIgnoreChats';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 120, 'pollIgnoreChats', '业主群,玩乐吃喝,封窗进展,兔宝宝山樾湾', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '拉取过滤：名称包含任一关键词的群/好友消息转存 wechat_message_black，不进入分类；多个用英文逗号分隔'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='pollIgnoreChats');

-- 2) 会话排除黑名单（名称包含关键词，命中转存黑表）
UPDATE sys_dict_data
SET dict_value = '山樾湾2F业主群,前海路5号玩乐吃喝,封窗进展,兔宝宝山樾湾2F 812苏总',
    remark = '会话排除名单：名称包含任一关键词即转存 wechat_message_black，不进入主表'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'blackNames';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 121, 'blackNames', '山樾湾2F业主群,前海路5号玩乐吃喝,封窗进展,兔宝宝山樾湾2F 812苏总', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '会话排除名单：名称包含任一关键词即转存 wechat_message_black，不进入主表'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='blackNames');

-- 3) 我方微信名/别名：用于区分“客户问我方”和“我方问供应商”
UPDATE sys_dict_data
SET dict_value = 'Eke,May', remark = '我方微信名/别名(逗号分隔)：用于判断消息方向；后续新增业务员在此追加'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'selfNames';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 122, 'selfNames', 'Eke,May', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '我方微信名/别名(逗号分隔)：用于判断消息方向；后续新增业务员在此追加'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='selfNames');

-- 4) 严格业务语义开关
UPDATE sys_dict_data SET dict_value='true', remark='严格业务语义：广播报价/我方外发询价/内部沟通降级OTHER'
WHERE dict_type='wechat_analysis_config' AND dict_label='strictBusinessSemantics';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 123, 'strictBusinessSemantics', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '严格业务语义：广播报价/我方外发询价/内部沟通降级OTHER'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='strictBusinessSemantics');

UPDATE sys_dict_data SET dict_value='true', remark='只保留客户发起的INQ/TRACE；我方问供应商的消息归OTHER'
WHERE dict_type='wechat_analysis_config' AND dict_label='strictCustomerOnly';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 124, 'strictCustomerOnly', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '只保留客户发起的INQ/TRACE；我方问供应商的消息归OTHER'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='strictCustomerOnly');

-- 5) TRACE 飞书预警开关
UPDATE sys_dict_data SET dict_value='true', remark='TRACE飞书预警：仅对客户发起、已关联INQ/OFFER、且为疑问句的二次询问推送'
WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertEnabled';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 125, 'traceAlertEnabled', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'TRACE飞书预警：仅对客户发起、已关联INQ/OFFER、且为疑问句的二次询问推送'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertEnabled');

UPDATE sys_dict_data SET dict_value='1440', remark='TRACE预警最大时效(分钟)：超过该时长的历史消息不推送飞书，默认1440=24小时'
WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertMaxAgeMin';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 126, 'traceAlertMaxAgeMin', '1440', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'TRACE预警最大时效(分钟)：超过该时长的历史消息不推送飞书，默认1440=24小时'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertMaxAgeMin');

-- 6) 收紧 few-shot：广播报价、我方外发询价、陈述句不再当 OFFER/TRACE
UPDATE sys_dict_data
SET dict_value = '严格业务示例（<我方>=自己人，<客户>=外部客户/买方，<群友>=群内其他卖方）：
示例1：<客户> MBD-H13SSL-N-B 24pcs 有货吗 → INQ（客户问我方型号+数量+库存）
示例2：<我方> 有，24pcs 660USD，2周交期 → OFFER（回应上一条INQ）
示例3：<客户> 660美金含税吗？交期能提前吗 → TRACE（客户对OFFER二次询问）
示例4：<群友> 现货出 5090风扇200片 → OTHER（群发报价，没有对应INQ）
示例5：<我方> 找 64G 5600 100条，有货来报 → OTHER（我方外发询价，不是客户INQ）
示例6：<客户> 这个18片，560可以下单吗 → TRACE（对报价的二次确认）
示例7：<客户> 收到/好的/谢谢 → OTHER（无业务含义）
示例8：<客户> RFQ: 5955WX 100pcs → INQ（明确询价）
示例9：<我方> 5955WX 485USD 现货 → OFFER（对上一条INQ的报价）
示例10：<群友> 长期高价收64G内存 → OTHER（收购广告）
示例11：<客户> 客户都要下单了，我们还没货 → OTHER（陈述/内部沟通，不是二次询问）
示例12：<我方> 交期多久？ / 这个MOQ多少？ → OTHER（我方追问供应商，不是客户TRACE）'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'llmExamples';

-- 7) 历史非业务群纠正：不再计入 INQ/OFFER/TRACE，避免污染统计和任务生成
UPDATE wechat_message
SET msg_category='OTHER', category_source='FILTER', category_reason='非业务群排除（历史纠正）',
    status=3, relat_inq='', relat_offer=''
WHERE name IN ('山樾湾2F业主群','前海路5号玩乐吃喝','封窗进展','兔宝宝山樾湾2F 812苏总');

-- 8) 可选：按新口径重跑最近3天分类（默认不执行，确认后再放开）
-- UPDATE wechat_message
-- SET status=0, msg_category='', category_source='', category_reason='', relat_inq='', relat_offer=''
-- WHERE create_time >= DATE_SUB(NOW(), INTERVAL 3 DAY)
--   AND status=1 AND msg_category IN ('INQ','OFFER','TRACE')
--   AND name NOT IN ('山樾湾2F业主群','前海路5号玩乐吃喝','封窗进展','兔宝宝山樾湾2F 812苏总');
