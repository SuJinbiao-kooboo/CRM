-- =====================================================================
-- 2026-11-05 微信历史 OFFER 待跟进飞书告警时效
-- 可重复执行；执行后刷新字典缓存或重启服务。
-- =====================================================================

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 216, 'offerFollowUpMaxAgeMin', '360', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       'Offer待跟进飞书最大时效(分钟)：只推送该时长内的OFFER，超过时长的历史补录消息不推送，默认360=6小时'
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT dict_code FROM sys_dict_data
                   WHERE dict_type = 'wechat_analysis_config'
                     AND dict_label = 'offerFollowUpMaxAgeMin') t
);
