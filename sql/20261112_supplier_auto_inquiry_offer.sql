-- 2026-11-12 供应商自动询Offer：供应商开关、祝福语字典、每周一/三/四 10:00 定时任务

-- 1) 供应商“自动询Offer”开关，默认关闭。
ALTER TABLE crm_supplier
    ADD COLUMN auto_inquiry_offer TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '自动询Offer:1开启,0关闭' AFTER auto_approve;

-- 2) 任务来源补充“自动询Offer”。
ALTER TABLE wechat_offer_task
    MODIFY COLUMN task_source VARCHAR(16) NOT NULL DEFAULT '消息自动'
        COMMENT '任务来源：消息自动/人工手动/自动询Offer';

-- 3) 祝福语字典：dict_value=日期最后一位数字(0-9)，dict_label=发送祝福语。
INSERT INTO sys_dict_type
    (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '微信自动询Offer祝福语', 'wechat_auto_inquiry_offer_greeting', '0', 'admin', NOW(),
       '自动询Offer每日祝福语：dict_value 为日期最后一位数字 0-9，dict_label 为对应祝福语'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'wechat_auto_inquiry_offer_greeting'
);

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 1, '早，今天肯定超棒的。', '1', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 1'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '1');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 2, '早安，阳光正好，你也是。', '2', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 2'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '2');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 3, '早呀，新的一天冲鸭！', '3', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '3');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 4, '早上好，今天被温柔以待。', '4', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 4'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '4');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 5, '早安，今天所愿成真～', '5', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 5'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '5');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 6, '早！今天我们会闪闪发光。', '6', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 6'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '6');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 7, '早安，一切顺心如意！', '7', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 7'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '7');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 8, '早上好，好运正在派送中。', '8', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 8'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '8');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 9, '早安，微笑开启美好一天。', '9', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 9'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '9');

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, status, create_by, create_time, remark)
SELECT 10, '早，愿我们今天收获满满！', '0', 'wechat_auto_inquiry_offer_greeting', '', '', 'N', '0', 'admin', NOW(),
       '自动询Offer祝福语，日期尾数 0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'wechat_auto_inquiry_offer_greeting' AND dict_value = '0');

-- 4) 每周一、周三、周四 10:00 创建待审批任务；同日由任务唯一键幂等去重。
INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy,
     concurrent, status, allowed_ips, create_by, create_time, remark)
SELECT '供应商自动询Offer', 'WECHAT',
       'supplierAutoInquiryOfferScheduler.createPendingTasks()', '0 0 10 ? * MON,WED,THU', '3', '1', '0',
       COALESCE((SELECT MAX(allowed_ips) FROM sys_job WHERE job_group = 'WECHAT' AND allowed_ips <> ''), ''),
       'admin', NOW(),
       '每周一、周三、周四10:00扫描开启自动询Offer且有销售群的启用供应商，按日期尾数读取字典祝福语，创建待审批微信任务'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'supplierAutoInquiryOfferScheduler.createPendingTasks()'
);
