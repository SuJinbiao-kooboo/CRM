-- 供应商列表"写跟进"功能：crm_supplier 表新增跟进记录字段（2026-09-02）
-- 四个字段由列表操作列"写跟进"弹窗维护：
--   last_follow_up_time  上次跟进时间
--   last_follow_up_result 上次跟进结论（富文本 HTML）
--   next_follow_up_time  下次跟进时间
--   next_follow_up_goal  下次跟进目标（富文本 HTML）
-- 注意：字段均为可空，重复执行前请确认列不存在（本脚本幂等性由人工控制）
ALTER TABLE crm_supplier
    ADD COLUMN last_follow_up_time datetime NULL COMMENT '上次跟进时间' AFTER follow_up_by,
    ADD COLUMN last_follow_up_result mediumtext NULL COMMENT '上次跟进结论（富文本）' AFTER last_follow_up_time,
    ADD COLUMN next_follow_up_time datetime NULL COMMENT '下次跟进时间' AFTER last_follow_up_result,
    ADD COLUMN next_follow_up_goal mediumtext NULL COMMENT '下次跟进目标（富文本）' AFTER next_follow_up_time;
