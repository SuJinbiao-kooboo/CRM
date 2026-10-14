-- =====================================================================
-- 微信消息分析参数（数据字典）
-- 用途：第一层过滤器的会话白名单/黑名单 + 第二层业务类型(INQ/OFFER/TRACE)关键词。
--       服务(WeChatGatewayService)推送/拉取消息时读取
--       (dict_type=wechat_analysis_config)：
--   whiteNames  会话白名单（非空 ⇒ 只放过白名单里的会话；为空 ⇒ 只过滤黑名单）
--   blackNames  会话黑名单（白名单为空时生效）
--   inqKeywords   INQ 询价关键词（匹配消息正文+引用内容，逗号分隔）
--   offerKeywords OFFER 报价关键词（同上）
--   traceKeywords TRACE 跟进关键词（同上）
-- 名称规则：以消息的 name(会话显示名/备注)匹配，多个名称用英文逗号拼接。
-- 关键词规则：写在【备注(remark)】列，多个关键词用英文逗号拼接；
--       由 WeChatMessageClassifier 过滤器链读取，改完刷新缓存即生效。
-- 用法(MySQL)：  mysql -uroot -p 数据库名 < wechat_analysis_dict.sql
-- 导入后需刷新字典缓存：POST /system/dict/data/refreshCache
-- =====================================================================

-- 1) 字典类型
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '微信消息分析参数', 'wechat_analysis_config', '0', 'admin', NOW(),
       '消息分析第一层过滤器：会话白名单/黑名单（按消息 name 匹配，多个用逗号拼接）'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='wechat_analysis_config');

-- 2) 字典数据（dict_label = 参数名，dict_value = 参数值；关键词写在 remark 列，逗号分隔）
--    注意：以下语句会先清空 wechat_analysis_config 的全部条目再插入，
--    “重复执行 = 以文件为准重置”。日常调整请在“系统管理-字典管理”页面改。
DELETE FROM sys_dict_data WHERE dict_type='wechat_analysis_config';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark) VALUES
(1, 'whiteNames', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '会话白名单（非空时只放过这些名称，多个用逗号拼接）'),
(2, 'blackNames', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '会话黑名单（白名单为空时生效，过滤掉这些名称，多个用逗号拼接）'),
(3, 'inqKeywords', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'INQ询价关键词（逗号分隔）: 询价,INQ,inq,有没有,有货,什么价,多少钱,怎么卖,请报价,报价多少'),
(4, 'offerKeywords', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'OFFER报价关键词（逗号分隔）: 报价,OFFER,offer,单价,价格,价钱,折扣,USD,RMB,CNY,HKD,$'),
(5, 'traceKeywords', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'TRACE跟进关键词（逗号分隔）: 跟进,TRACE,trace,考虑得,考虑一下,还在吗,上一次,回复一下,还要吗,订了吗');
