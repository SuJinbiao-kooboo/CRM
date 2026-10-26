-- 微信询报价自动化 v1.0 数据库迁移（MySQL 8.0+，可重复执行）
-- 仅新增自动化所需字段、索引、任务表和配置；不删除旧字段，不发送真实消息。

DELIMITER $$
DROP PROCEDURE IF EXISTS add_wechat_automation_column$$
CREATE PROCEDURE add_wechat_automation_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition VARCHAR(512))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_wechat_automation_index$$
CREATE PROCEDURE add_wechat_automation_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_definition VARCHAR(512))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- 1. 微信消息分阶段状态（旧 status 保留兼容，不再作为新链路事实状态）
CALL add_wechat_automation_column('wechat_message', 'classify_status', "VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '分类状态:PENDING/PROCESSING/SUCCESS/FAILED/SKIPPED'");
CALL add_wechat_automation_column('wechat_message', 'classify_at', "DATETIME NULL COMMENT '分类状态更新时间'");
CALL add_wechat_automation_column('wechat_message', 'classify_fail_count', "INT NOT NULL DEFAULT 0 COMMENT '分类连续失败次数'");
CALL add_wechat_automation_column('wechat_message', 'entry_status', "VARCHAR(24) NULL DEFAULT 'NOT_APPLICABLE' COMMENT '录入状态:PENDING/PROCESSING/SUCCESS/SUCCESS_WARN/FAILED/DEAD/SKIPPED/NOT_APPLICABLE'");
CALL add_wechat_automation_column('wechat_message', 'entry_message', "VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '录入结果说明'");
CALL add_wechat_automation_column('wechat_message', 'entry_offer_count', "INT NOT NULL DEFAULT 0 COMMENT '最近一次成功写入crm_offer行数'");
CALL add_wechat_automation_column('wechat_message', 'entry_offer_ids', "VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '最近一次写入crm_offer主键列表'");
CALL add_wechat_automation_column('wechat_message', 'entry_at', "DATETIME NULL COMMENT '录入状态更新时间'");
CALL add_wechat_automation_column('wechat_message', 'task_status', "VARCHAR(24) NULL DEFAULT 'PENDING' COMMENT '任务生成状态:PENDING/PROCESSING/GENERATED/MANUAL_REVIEW/SKIPPED/FAILED'");
CALL add_wechat_automation_column('wechat_message', 'task_at', "DATETIME NULL COMMENT '任务状态更新时间'");
CALL add_wechat_automation_column('wechat_message', 'task_message', "VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '任务生成结果/异常原因'");
CALL add_wechat_automation_column('wechat_message', 'trace_alerted_at', "DATETIME NULL COMMENT 'TRACE飞书预警成功时间'");
CALL add_wechat_automation_column('wechat_message', 'trace_alert_error', "VARCHAR(1000) NULL COMMENT 'TRACE预警失败原因'");
CALL add_wechat_automation_column('wechat_message', 'trace_alert_suppressed_at', "DATETIME NULL COMMENT 'TRACE预警因同会话冷却跳过的时间，不再补发'");

CALL add_wechat_automation_index('wechat_message', 'idx_classify_state', 'KEY idx_classify_state (classify_status, sort_seq)');
CALL add_wechat_automation_index('wechat_message', 'idx_entry_state', 'KEY idx_entry_state (entry_status, entry_at)');
CALL add_wechat_automation_index('wechat_message', 'idx_task_state', 'KEY idx_task_state (task_status, sort_seq)');
CALL add_wechat_automation_index('wechat_message', 'idx_trace_alert', 'KEY idx_trace_alert (msg_category, trace_alerted_at)');
CALL add_wechat_automation_index('wechat_message', 'idx_name_create', 'KEY idx_name_create (name, create_time)');

-- 2. Offer 标准化料号与微信来源回查索引
CALL add_wechat_automation_column('crm_offer', 'pn_normalized', "VARCHAR(128) NULL COMMENT '标准化料号:去首尾空格/折叠空白/大写'");
CALL add_wechat_automation_index('crm_offer', 'idx_pn_normalized', 'KEY idx_pn_normalized (pn_normalized)');
CALL add_wechat_automation_index('crm_offer', 'idx_wechat_source', 'KEY idx_wechat_source (wechat_username, wechat_local_id)');

-- 3. 供应商利润比例（为空使用全局 offerEntryProfitRatio）
CALL add_wechat_automation_column('crm_supplier', 'profit_ratio', "DECIMAL(6,2) NULL COMMENT '报价利润率(%):为空使用全局默认'");

-- 4. 待审批任务表
CREATE TABLE IF NOT EXISTS wechat_offer_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    source_username VARCHAR(64) NOT NULL COMMENT '来源会话标识',
    source_local_id BIGINT NOT NULL COMMENT '来源消息local_id',
    source_server_id VARCHAR(255) NULL COMMENT '来源消息server_id(仅审计)',
    source_message_time DATETIME NOT NULL COMMENT '来源消息发送时间',
    source_category VARCHAR(16) NOT NULL COMMENT '来源分类INQ/OFFER',
    task_type VARCHAR(32) NOT NULL COMMENT 'SUPPLIER_INQUIRY/CUSTOMER_REPLY/CUSTOMER_PUSH/UNMAPPED_CHAT',
    send_type VARCHAR(16) NOT NULL DEFAULT 'WECHAT' COMMENT '发送渠道',
    ori_target VARCHAR(255) NULL COMMENT '来源聊天显示名',
    ori_msg MEDIUMTEXT NULL COMMENT '来源消息快照',
    pn_original VARCHAR(255) NULL COMMENT '原始料号',
    pn_normalized VARCHAR(128) NOT NULL DEFAULT '' COMMENT '标准化料号',
    brand VARCHAR(64) NULL COMMENT '品牌',
    product_type VARCHAR(100) NULL COMMENT '产品类型',
    spec VARCHAR(512) NULL COMMENT '规格',
    qty INT NULL COMMENT '数量',
    ori_price DECIMAL(18,2) NULL COMMENT '来源价格/成本',
    offer_price DECIMAL(18,2) NULL COMMENT '对外报价',
    send_target JSON NULL COMMENT '发送目标JSON数组',
    send_target_success JSON NULL COMMENT '发送成功目标JSON数组',
    send_content MEDIUMTEXT NULL COMMENT '当前任务料号行片段',
    handle_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT 'PENDING_APPROVAL/APPROVED_PENDING_SEND/SENT/NO_ACTION',
    send_attempts INT NOT NULL DEFAULT 0 COMMENT '发送尝试次数',
    last_attempt_at DATETIME NULL COMMENT '最近调用网关时间',
    last_error VARCHAR(1000) NULL COMMENT '最近失败/人工核对原因',
    matched_inq_json JSON NULL COMMENT '匹配外部客户INQ快照',
    offer_json JSON NULL COMMENT '当前OFFER/INQ快照',
    approved_by VARCHAR(64) NULL COMMENT '审批人',
    approved_at DATETIME NULL COMMENT '审批时间',
    approval_reason VARCHAR(500) NULL COMMENT '审批原因',
    sent_at DATETIME NULL COMMENT '全部成功时间',
    remark VARCHAR(1000) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_task_pn (source_username, source_local_id, task_type, pn_normalized, send_type),
    KEY idx_task_status_time (handle_status, update_time),
    KEY idx_task_pn (pn_normalized),
    KEY idx_task_source (source_username, source_local_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信询报价待审批任务';

-- 一次性迁移标记，必须先于配置更新创建
CREATE TABLE IF NOT EXISTS wechat_automation_migration (
    migration_key VARCHAR(64) NOT NULL COMMENT '迁移标识',
    applied_at DATETIME NOT NULL COMMENT '执行时间',
    PRIMARY KEY (migration_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信自动化一次性迁移标记';

-- 5. 自动化参数（标签为参数名，值为参数值）
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 200, 'classifyEnabled', 'false', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '分类定时开关；验收后开启'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='classifyEnabled');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 201, 'autoSendAccount', 'Eke', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '自动发送固定微信账号'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='autoSendAccount');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 202, 'classifyRetryCooldownSec', '300', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '单条分类失败冷却秒数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='classifyRetryCooldownSec');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 203, 'inqMatchDayNo', '2', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'INQ匹配自然日窗口：2=昨天00:00至今'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='inqMatchDayNo');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 204, 'autoTaskGenerateEnabled', 'false', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '任务生成定时开关'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='autoTaskGenerateEnabled');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 205, 'taskGenerateScanSec', '60', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '任务生成扫描秒数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='taskGenerateScanSec');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 206, 'autoSendEnabled', 'false', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '自动发送定时开关'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='autoSendEnabled');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 207, 'taskSendScanSec', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '自动发送扫描秒数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendScanSec');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 208, 'taskSendMaxAttempts', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '单目标发送最大尝试次数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendMaxAttempts');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 209, 'msgRetentionDays', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '微信消息保留天数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='msgRetentionDays');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 210, 'processingTimeoutSec', '600', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '各阶段PROCESSING超时恢复秒数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='processingTimeoutSec');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 211, 'offerEntryMinAgeSec', '60', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '分类后等待至少60秒再自动录入'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMinAgeSec');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 212, 'offerEntryMaxAgeHours', '24', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '自动录入最大消息年龄小时数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='offerEntryMaxAgeHours');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 213, 'taskGenerateBatchSize', '20', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '单轮任务生成消息数'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='taskGenerateBatchSize');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 214, 'taskSendScanBatchSize', '200', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '单轮发送扫描任务上限'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendScanBatchSize');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark)
SELECT 215, 'taskSendBatchSize', '20', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '单次网关发送目标上限'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='wechat_analysis_config' AND dict_label='taskSendBatchSize');

UPDATE sys_dict_data SET dict_value='Eke,May,Jin,Jasmine' WHERE dict_type='wechat_analysis_config' AND dict_label='selfNames'
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
UPDATE sys_dict_data SET dict_value='Eke' WHERE dict_type='wechat_analysis_config' AND dict_label='autoSendAccount'
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
UPDATE sys_dict_data SET dict_value='30' WHERE dict_type='wechat_analysis_config' AND dict_label='msgRetentionDays'
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
UPDATE sys_dict_data SET dict_value='120' WHERE dict_type='wechat_analysis_config' AND dict_label='classifyMaxIdleSec'
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
UPDATE sys_dict_data SET dict_value='5' WHERE dict_type='wechat_analysis_config' AND dict_label='classifyTriggerCount'
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
UPDATE sys_dict_data SET dict_value='' WHERE dict_type='wechat_analysis_config' AND dict_label IN ('llmPromptBase','llmExamples')
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
UPDATE sys_dict_data SET dict_value='true' WHERE dict_type='wechat_analysis_config' AND dict_label='traceAlertEnabled'
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
UPDATE sys_dict_data SET dict_value='false' WHERE dict_type='wechat_analysis_config' AND dict_label IN ('offerEntryEnabled','classifyEnabled','autoTaskGenerateEnabled','autoSendEnabled')
  AND NOT EXISTS (SELECT 1 FROM wechat_automation_migration WHERE migration_key='CONFIG_V1');
INSERT IGNORE INTO wechat_automation_migration (migration_key, applied_at) VALUES ('CONFIG_V1', NOW());

-- 6. 存量消息割接：一次性标记，重复执行脚本不得再次跳过新消息

DELIMITER $$
DROP PROCEDURE IF EXISTS apply_wechat_cutover_v1$$
CREATE PROCEDURE apply_wechat_cutover_v1()
BEGIN
    INSERT IGNORE INTO wechat_automation_migration (migration_key, applied_at)
    VALUES ('CUTOVER_V1', NOW());
    IF ROW_COUNT() = 1 THEN
        UPDATE wechat_message
        SET classify_status='SKIPPED',
            classify_at=NOW(),
            entry_status='NOT_APPLICABLE',
            task_status='SKIPPED',
            task_at=NOW(),
            task_message='CUTOVER',
            category_reason=COALESCE(NULLIF(category_reason,''), 'CUTOVER');
    END IF;
END$$
DELIMITER ;
CALL apply_wechat_cutover_v1();
DROP PROCEDURE IF EXISTS apply_wechat_cutover_v1;

-- 7. 历史 Offer 料号回填（幂等）
UPDATE crm_offer
SET pn_normalized=UPPER(REGEXP_REPLACE(TRIM(product_code), '[[:space:]]+', ' '))
WHERE (pn_normalized IS NULL OR pn_normalized='') AND product_code IS NOT NULL AND TRIM(product_code)<>'';

-- 8. 菜单与按钮权限（空权限由管理员角色自动拥有；其他角色需在角色管理中勾选）
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '微信消息',1200,90,'wechatMessage','crm/wechatMessage/index',1,0,'C','0','0','wechat:message:list','wechat','admin',NOW(),'微信聊天记录查询与处理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:message:list' AND menu_type='C');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '微信询报价任务',1200,91,'wechatTask','crm/wechatTask/index',1,0,'C','0','0','wechat:task:list','wechat','admin',NOW(),'微信询报价任务审批与发送'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:task:list' AND menu_type='C');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '消息查询',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:message:list' AND menu_type='C' LIMIT 1) m),1,'','',1,0,'F','0','0','wechat:message:query','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:message:query');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '消息分类',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:message:list' AND menu_type='C' LIMIT 1) m),2,'','',1,0,'F','0','0','wechat:message:classify','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:message:classify');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '消息录入',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:message:list' AND menu_type='C' LIMIT 1) m),3,'','',1,0,'F','0','0','wechat:message:entry','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:message:entry');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '消息拉取',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:message:list' AND menu_type='C' LIMIT 1) m),4,'','',1,0,'F','0','0','wechat:message:poll','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:message:poll');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '任务查询',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:task:list' AND menu_type='C' LIMIT 1) m),1,'','',1,0,'F','0','0','wechat:task:query','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:task:query');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '任务审批',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:task:list' AND menu_type='C' LIMIT 1) m),2,'','',1,0,'F','0','0','wechat:task:approve','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:task:approve');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '任务生成',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:task:list' AND menu_type='C' LIMIT 1) m),3,'','',1,0,'F','0','0','wechat:task:generate','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:task:generate');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
SELECT '任务发送',(SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms='wechat:task:list' AND menu_type='C' LIMIT 1) m),4,'','',1,0,'F','0','0','wechat:task:send','#','admin',NOW(),''
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='wechat:task:send');

DROP PROCEDURE IF EXISTS add_wechat_automation_column;
DROP PROCEDURE IF EXISTS add_wechat_automation_index;
