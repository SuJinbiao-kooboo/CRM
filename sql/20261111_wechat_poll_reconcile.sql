-- 2026-11-11 微信消息拉取：增加周期补偿回看，修复延迟写入本地库导致的永久漏拉
-- 1. pollReconcileSec：补偿扫描间隔秒数，默认 600；0=关闭周期补偿
-- 2. pollReconcileBackSec：每轮补偿从当前时间回看秒数，默认 259200（3天）
-- 3. pollReconcileLimit：补偿扫描每页条数，默认 10000；另有代码级自动翻页上限
-- 代码已内置相同默认值，本脚本用于在系统字典页面可视化维护。

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 217, 'pollReconcileSec', '600', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '微信消息周期补偿扫描间隔秒数(默认600；0=关闭；用于补回延迟写入本地库的旧消息)'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'pollReconcileSec'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 218, 'pollReconcileBackSec', '259200', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '微信消息周期补偿回看窗口秒数(默认259200即3天；幂等入库，不会重复处理)'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'pollReconcileBackSec'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 219, 'pollReconcileLimit', '10000', 'wechat_analysis_config', 'N', '0', 'admin', NOW(),
       '微信消息周期补偿扫描每页条数(默认10000；达到上限会继续向后分批，直到追平)'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'pollReconcileLimit'
);
