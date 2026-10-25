-- =====================================================================
-- 微信非业务群排除口径统一（2026-10-25）
-- blackNames / pollIgnoreChats 均按会话名关键词包含匹配，命中转存黑表。
-- 可重复执行；不修改名单值，不删除消息。
-- =====================================================================

UPDATE sys_dict_data
SET remark = '会话排除名单：名称包含任一关键词(不区分大小写)即转存 wechat_message_black，不进入主表；多个用英文逗号分隔'
WHERE dict_type = 'wechat_analysis_config'
  AND dict_label IN ('blackNames', 'pollIgnoreChats');
