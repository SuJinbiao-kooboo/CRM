-- 2026-11-14 微信自动化字典清理与备注完善
-- 规则：只保留当前代码实际读取的键；历史分类关键词、固定发送人、旧关联窗口等键已由提示词分类和 P/N 匹配替代。

-- 字典类型说明
UPDATE sys_dict_type
SET remark = '微信自动化消息分析全部开关与阈值；只维护当前代码实际读取的键。字典缓存有效期 3 分钟，到期自动回读数据库。'
WHERE dict_type = 'wechat_analysis_config';

UPDATE sys_dict_type
SET remark = '飞书机器人统一配置：一个机器人一行；dict_value=机器人地址 Webhook，css_class=机器人签名 Secret，remark=用途。字典数据列表同时显示“机器人地址”和“机器人签名”两列；签名为空表示该机器人未开启签名校验，不是配置缺失。'
WHERE dict_type = 'feishu_robot_config';

INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '微信发送配置', 'wechat_send_config', '0', 'admin', NOW(),
       '微信发送节奏、直发上限与转发参数；缓存有效期 3 分钟，修改后最多 3 分钟生效。'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'wechat_send_config');

-- 删除已停用、代码不再读取的历史键
DELETE FROM sys_dict_data
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label IN (
    'inqKeywords', 'offerKeywords', 'traceKeywords',
    'llmConcurrency', 'aiTriggerCount', 'aiFlushMinutes',
    'offerFixedSenders', 'offerFixedGroups', 'offerFollowWindowMin',
    'offerEntrySupplierCode', 'offerEntrySupplierMap',
    'strictBusinessSemantics', 'strictCustomerOnly',
    'offerLinkMaxAgeMin', 'offerShortReplyWindowMin',
    'traceLinkMaxAgeMin', 'traceShortWindowMin',
    'offerEntryRequireRelatInq', 'offerEntryRequireOfferPrice',
    'pollIgnoreChats'
  );

-- pollIgnoreChats 已合并进 blackNames：拉取、分类、录入三处统一使用 blackNames。
UPDATE sys_dict_data
SET remark = '会话黑名单：消息 name 包含任一关键词即跳过；多个用逗号分隔；优先于白名单。已合并原 pollIgnoreChats 的作用，拉取、分类、录入三处统一使用本键。'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'blackNames';

-- 补充代码实际读取但字典缺失的键
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 108, 'pollReconcileSec', '600', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '补扫间隔秒；定时从微信库补拉最近消息，默认 600 秒。用于防止漏拉，不要设为 0。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='pollReconcileSec');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 109, 'pollReconcileBackSec', '259200', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '补扫回看秒数；默认 259200 秒（3 天），用于补拉最近消息，避免首次或重启后漏消息。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='pollReconcileBackSec');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 115, 'pollReconcileLimit', '10000', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '单次补扫最多读取的微信消息条数；默认 10000，过大可能增加查询耗时。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='pollReconcileLimit');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 121, 'internalOfferGroupNames', '内部offer记录群', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '内部 Offer 记录群名称，多个用中英文逗号分隔；这些群里我方成员转发供应商 INQ/OFFER 时不按“我方消息”过滤。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='internalOfferGroupNames');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 147, 'offerEntryRetryBaseSec', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'Offer 录入失败重试的基础间隔秒；配合指数退避使用，默认 30 秒，最小 1 秒。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryRetryBaseSec');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 209, 'taskSendRoundMax', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '单轮发送最多处理的任务/目标组数；转发任务按完整正文合并后计数，默认 3，超出部分下一轮继续。'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendRoundMax');

-- 保留键的用途说明（按实际代码读取口径）
UPDATE sys_dict_data SET remark='会话白名单：消息 name 精确命中任一名称才处理；多个用逗号分隔；为空表示不限制。' WHERE dict_type='wechat_analysis_config' AND dict_label='whiteNames';
UPDATE sys_dict_data SET remark='会话黑名单：消息 name 包含任一关键词即跳过；多个用逗号分隔；优先于白名单。拉取、分类、录入三处统一生效。' WHERE dict_type='wechat_analysis_config' AND dict_label='blackNames';
UPDATE sys_dict_data SET remark='AI 分类单批消息数；每批最多 20 条，默认 20，过大增加模型延迟和失败重试成本。' WHERE dict_type='wechat_analysis_config' AND dict_label='llmBatchSize';
UPDATE sys_dict_data SET remark='单次 AI 请求超时秒数；默认 180 秒，必须小于 processingTimeoutSec，网络慢时可适当调大。' WHERE dict_type='wechat_analysis_config' AND dict_label='llmTimeoutSec';
UPDATE sys_dict_data SET remark='AI 上下文窗口消息条数；同一会话最多带最近 N 条已分类消息给模型，默认 20，范围 20-100。' WHERE dict_type='wechat_analysis_config' AND dict_label='aiContextWindow';
UPDATE sys_dict_data SET remark='微信消息保留天数；定时清理超过该天数的消息和日志，默认 30 天；同时影响任务可回看范围。' WHERE dict_type='wechat_analysis_config' AND dict_label='msgRetentionDays';
UPDATE sys_dict_data SET remark='AI 分类基础提示词；定义 INQ/OFFER/TRACE/OTHER 判定规则。纯文本，修改后新分类立即使用，建议保留现有结构。' WHERE dict_type='wechat_analysis_config' AND dict_label='llmPromptBase';
UPDATE sys_dict_data SET remark='AI 分类示例集；补充边界样例，单条过长会自动回退内置示例，建议只放高价值样例。' WHERE dict_type='wechat_analysis_config' AND dict_label='llmExamples';
UPDATE sys_dict_data SET remark='消息自动拉取总开关；true=按 pollIntervalSec 拉取微信消息，false=停止自动拉取，手动按钮仍可执行。' WHERE dict_type='wechat_analysis_config' AND dict_label='pollEnabled';
UPDATE sys_dict_data SET remark='自动拉取间隔秒；默认 10 秒，最小 1 秒；过大可能延迟消息入库。' WHERE dict_type='wechat_analysis_config' AND dict_label='pollIntervalSec';
UPDATE sys_dict_data SET remark='增量拉取回看秒数；普通轮询向前回看的时间，默认 120 秒，用于覆盖微信落库延迟。' WHERE dict_type='wechat_analysis_config' AND dict_label='pollBackSec';
UPDATE sys_dict_data SET remark='首次启动初始化回看秒数；默认 259200 秒（3 天），仅在拉取水位为 0 时使用。' WHERE dict_type='wechat_analysis_config' AND dict_label='pollInitBackSec';
UPDATE sys_dict_data SET remark='单次增量拉取最多读取的消息条数；默认 5000；过小可能漏消息，过大会增加查询时间。' WHERE dict_type='wechat_analysis_config' AND dict_label='pollLimit';
UPDATE sys_dict_data SET remark='分类触发条数；同一会话未分类消息达到 N 条即触发 AI 分类，默认 5。' WHERE dict_type='wechat_analysis_config' AND dict_label='classifyTriggerCount';
UPDATE sys_dict_data SET remark='分类最大等待秒数；未达到触发条数但最早消息等待超过 N 秒也会分类，默认 120 秒。' WHERE dict_type='wechat_analysis_config' AND dict_label='classifyMaxIdleSec';
UPDATE sys_dict_data SET remark='单次分类最多处理的消息条数；默认 20，最大 100，超出下一轮继续。' WHERE dict_type='wechat_analysis_config' AND dict_label='classifyBatchSize';
UPDATE sys_dict_data SET remark='INQ/OFFER 自动录入总开关；true=扫描并录入，false=只分类不录入，手动录入按钮不受影响。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryEnabled';
UPDATE sys_dict_data SET remark='自动录入扫描间隔秒；默认 30 秒，最小 5 秒。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryScanSec';
UPDATE sys_dict_data SET remark='单次自动录入最多处理的消息条数；默认 5，最大 100。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryBatchSize';
UPDATE sys_dict_data SET remark='参与自动录入的消息分类；逗号分隔，默认 INQ,OFFER；留空则不录入任何分类。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryCategories';
UPDATE sys_dict_data SET remark='录入 crm_offer 的利润率百分比；默认 2，仅影响报价/成本换算，不改变原始消息。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryProfitRatio';
UPDATE sys_dict_data SET remark='自动录入最大失败次数；默认 3，达到后停止重试并留给人工处理。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMaxFail';
UPDATE sys_dict_data SET remark='自动录入的操作人标识；写入 crm_offer.create_by，默认 wechat_auto，建议保留。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryCreateBy';
UPDATE sys_dict_data SET remark='同一会话内报价关联窗口秒数；默认 1800 秒（30 分钟），用于把连续消息合并为一次 INQ/OFFER。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryQuoteLinkSec';
UPDATE sys_dict_data SET remark='我方成员名单；逗号分隔，内置 Eke,May,Jin,Jasmine 始终生效，这里只补充别名。' WHERE dict_type='wechat_analysis_config' AND dict_label='selfNames';
UPDATE sys_dict_data SET remark='TRACE 预警总开关；true=扫描并推送 TRACE 预警，false=停止预警，不影响分类和录入。' WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertEnabled';
UPDATE sys_dict_data SET remark='同一 TRACE 会话告警冷却分钟数；默认 5 分钟，避免同一会话连续刷屏。' WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertChatCooldownMin';
UPDATE sys_dict_data SET remark='TRACE 告警最大消息年龄分钟数；默认 360 分钟（6 小时），超过该年龄的消息不再告警。' WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertMaxAgeMin';
UPDATE sys_dict_data SET remark='TRACE 每轮最多扫描的消息条数；默认 50，最大 500。' WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertScanBatchSize';
UPDATE sys_dict_data SET remark='同一会话报价合并间隔秒数；默认 30 秒，小于该间隔的连续消息按一组处理。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryGroupGapSec';
UPDATE sys_dict_data SET remark='录入处理锁超时秒数；PROCESSING 状态超过该秒数自动解锁，必须大于 llmTimeoutSec，默认 360。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryProcessingTimeoutSec';
UPDATE sys_dict_data SET remark='失败聚合告警开关；true=连续失败达到阈值时推送飞书，false=只记录日志。' WHERE dict_type='wechat_analysis_config' AND dict_label='failureAlertEnabled';
UPDATE sys_dict_data SET remark='失败聚合告警阈值；窗口内失败次数达到 N 次触发，默认 3。' WHERE dict_type='wechat_analysis_config' AND dict_label='failureAlertThreshold';
UPDATE sys_dict_data SET remark='失败聚合告警统计窗口分钟数；默认 30 分钟，窗口内达到阈值才推送。' WHERE dict_type='wechat_analysis_config' AND dict_label='failureAlertWindowMin';
UPDATE sys_dict_data SET remark='AI 分类总开关；true=允许分类，false=停止分类和后续自动录入。' WHERE dict_type='wechat_analysis_config' AND dict_label='classifyEnabled';
UPDATE sys_dict_data SET remark='自动发送固定微信账号；当前必须为 Eke，登录账号不一致时发送扫描会停止。' WHERE dict_type='wechat_analysis_config' AND dict_label='autoSendAccount';
UPDATE sys_dict_data SET remark='分类失败重试冷却秒数；默认 300 秒，失败消息在该时间内不重复扫描。' WHERE dict_type='wechat_analysis_config' AND dict_label='classifyRetryCooldownSec';
UPDATE sys_dict_data SET remark='INQ 匹配回看天数；默认 2，表示从昨天 00:00 到当前；同时用于客户 INQ 与供应商 OFFER 关联。' WHERE dict_type='wechat_analysis_config' AND dict_label='inqMatchDayNo';
UPDATE sys_dict_data SET remark='询报价任务自动生成开关；true=定时扫描已录入消息生成任务，false=停止自动生成，手动生成仍可用。' WHERE dict_type='wechat_analysis_config' AND dict_label='autoTaskGenerateEnabled';
UPDATE sys_dict_data SET remark='任务自动生成扫描间隔秒；默认 60 秒，最小 5 秒。' WHERE dict_type='wechat_analysis_config' AND dict_label='taskGenerateScanSec';
UPDATE sys_dict_data SET remark='任务自动发送开关；true=定时发送已审批待发送任务，false=停止自动发送，手动发送仍可用。' WHERE dict_type='wechat_analysis_config' AND dict_label='autoSendEnabled';
UPDATE sys_dict_data SET remark='任务发送扫描间隔秒；默认 30 秒，避免过频占用微信桌面。' WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendScanSec';
UPDATE sys_dict_data SET remark='单任务最大发送尝试次数；默认 3；结果未知不会自动重试，必须人工核实。' WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendMaxAttempts';
UPDATE sys_dict_data SET remark='消息/任务处理锁超时秒数；PROCESSING 超过该秒数自动恢复，默认 600，必须大于 AI 超时。' WHERE dict_type='wechat_analysis_config' AND dict_label='processingTimeoutSec';
UPDATE sys_dict_data SET remark='自动录入最小等待秒数；消息入库后等待 N 秒再录入，默认 30 秒，给同一会话后续消息合并留时间。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMinAgeSec';
UPDATE sys_dict_data SET remark='自动录入最大消息年龄小时数；超过该年龄的消息不自动录入，默认 24 小时。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMaxAgeHours';
UPDATE sys_dict_data SET remark='任务生成单批最多领取消息条数；默认 20，最大 100。' WHERE dict_type='wechat_analysis_config' AND dict_label='taskGenerateBatchSize';
UPDATE sys_dict_data SET remark='发送扫描单次最多读取任务条数；默认 50，当前 200，最大 500。' WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendScanBatchSize';
UPDATE sys_dict_data SET remark='逐目标直发模式每批最多目标数；默认 3，当前 20；转发模式不用此值，使用 wechat_send_config.forwardChunkSize。' WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendBatchSize';
UPDATE sys_dict_data SET remark='Offer 待跟进告警时效分钟；默认 360 分钟，超过该时效补录的 Offer 不再推飞书，但仍入库。' WHERE dict_type='wechat_analysis_config' AND dict_label='offerFollowUpMaxAgeMin';

-- 飞书机器人：地址在 dict_value，签名在 css_class，备注写用途
UPDATE sys_dict_data
SET remark = '业务告警机器人（地址+签名校验）。用途：Offer待跟进、微信失败聚合、供应商群配置缺失、AI通道故障/恢复、未关联供应商聊天、自动询Offer告警及其他业务告警。'
WHERE dict_type = 'feishu_robot_config' AND dict_label = 'business_alert';

UPDATE sys_dict_data
SET remark = '旧机器人：地址在 dict_value，签名为空表示该机器人未开启签名校验（不是缺少字段）。用途：只发送微信TRACE预警和好价格Offer定时推送；如开启签名校验，把飞书 Secret 填入 css_class。'
WHERE dict_type = 'feishu_robot_config' AND dict_label = 'legacy_trace_offer';

-- 修正 traceAlert 两项重复的显示排序（原均为 125）。
UPDATE sys_dict_data SET dict_sort = 124
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'traceAlertEnabled';
