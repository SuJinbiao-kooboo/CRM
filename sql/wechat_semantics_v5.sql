-- =====================================================================
-- 微信询报价分类 v5：纯提示词驱动（2026-10-22）
-- 目标：删除旧的本地固定 OFFER / 语义复核参数，压缩提示词与 token 消耗。
-- 可重复执行；不修改 blackNames / pollIgnoreChats / 消息表数据。
-- =====================================================================

-- 1) 我方成员：分类本身按买卖语义判断，此配置只用于录入时区分销售 Offer / 采购 Inq
UPDATE sys_dict_data
SET dict_value = 'Eke,May,Jin,Jasmine',
    remark = '我方微信名/别名(逗号分隔)：只用于录入方向，不参与INQ/OFFER分类'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'selfNames';

-- 2) 提示词与示例仍可在字典维护；超长旧示例清空，回退代码内置精简示例
UPDATE sys_dict_data
SET dict_value = '',
    remark = '留空使用代码内置精简分类提示词'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'llmPromptBase';
UPDATE sys_dict_data
SET dict_value = '',
    remark = '留空使用代码内置精简示例(最多800字)：输出i/cat/inq/offer，不输出理由'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'llmExamples'
  AND CHAR_LENGTH(dict_value) > 800;
UPDATE sys_dict_data
SET remark = '留空使用代码内置精简示例(最多800字)：输出i/cat/inq/offer，不输出理由'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'llmExamples';

-- 3) 旧本地规则参数已无代码引用，清理避免误以为仍会生效
DELETE FROM sys_dict_data
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label IN (
      'llmConcurrency',
      'offerFixedSenders', 'offerFixedGroups', 'offerFollowWindowMin',
      'strictBusinessSemantics', 'strictCustomerOnly',
      'offerLinkMaxAgeMin', 'offerShortReplyWindowMin',
      'traceLinkMaxAgeMin', 'traceShortWindowMin'
  );

UPDATE sys_dict_data
SET remark = 'OFFER自动录入前必须有AI返回的对向INQ关联，且关联INQ与OFFER发送人不同'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryRequireRelatInq';
