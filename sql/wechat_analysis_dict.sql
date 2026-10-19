-- =====================================================================
-- 微信消息：建表 + 分析参数数据字典（一条 SQL 全部搞定）
-- 用途：
--   1) wechat_message：消息全量入库（监听+拉取都写这里），只保留最近 msgRetentionDays 天；
--   2) wechat_analysis_config 字典：黑白名单过滤 + 批量AI分类参数 + 固定OFFER名单。
-- 用法(MySQL)：  mysql -uroot -p 数据库名 < wechat_analysis_dict.sql
-- 导入后需刷新字典缓存：POST /system/dict/data/refreshCache（或重启 ruoyi-admin）
-- =====================================================================

-- 1) 消息表（不存在才建；列名/类型与 Java POJO 对应）
CREATE TABLE IF NOT EXISTS wechat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键', 
  username VARCHAR(64) NOT NULL COMMENT '会话标识(群id/好友wxid)', 
  name VARCHAR(128) DEFAULT '' COMMENT '会话显示名', 
  local_id BIGINT NOT NULL DEFAULT 0 COMMENT '库内消息ID', 
  server_id VARCHAR(64) DEFAULT '' COMMENT '服务器消息ID', 
  type VARCHAR(32) DEFAULT '' COMMENT '消息类型', 
  sender VARCHAR(128) DEFAULT '' COMMENT '发言人', 
  content MEDIUMTEXT COMMENT '正文', 
  quote TEXT COMMENT '被引用内容', 
  quote_kind VARCHAR(16) DEFAULT '' COMMENT '引用类型', 
  media_path VARCHAR(512) DEFAULT '' COMMENT '媒体路径', 
  create_time DATETIME DEFAULT NULL COMMENT '消息产生时间', 
  sort_seq BIGINT NOT NULL DEFAULT 0 COMMENT '排序键', 
  md5 VARCHAR(64) DEFAULT '' COMMENT '媒体md5', 
  msg_category VARCHAR(16) DEFAULT '' COMMENT '分类(INQ/OFFER/TRACE/OTHER)', 
  category_source VARCHAR(16) DEFAULT '' COMMENT '分类来源(FIXED/LLM/CACHE/FILTER/FAIL)', 
  category_reason VARCHAR(512) DEFAULT '' COMMENT '分类理由', 
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0=未分类 1=已分类未处理 2=已分类处理未发送 3=无需处理 4=已分类处理发送', 
  entry_fail_count INT NOT NULL DEFAULT 0 COMMENT 'AI录入失败次数(连续失败达offerEntryMaxFail次则置status=3无需处理)', 
  relat_inq TEXT COMMENT '关联INQ(JSON: id/server_id/sender/content/create_time)', 
  relat_offer TEXT COMMENT '关联OFFER(JSON: id/server_id/sender/content/create_time)', 
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间', 
  UNIQUE KEY uk_username_localid (username, local_id), 
  KEY idx_create_time (create_time), 
  KEY idx_category (msg_category), 
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信消息表(仅保留最近N天,定时清理)';

-- 1.5) 黑名单消息表：pollIgnoreChats(名称包含关键词) 命中的群消息只入库、不做分类/处理；
-- 与主表结构一致，唯一键相同，不参与 msgRetentionDays 自动清理（保留备查）。
CREATE TABLE IF NOT EXISTS wechat_message_black (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键', 
  username VARCHAR(64) NOT NULL COMMENT '会话标识(群id/好友wxid)', 
  name VARCHAR(128) DEFAULT '' COMMENT '会话显示名', 
  local_id BIGINT NOT NULL DEFAULT 0 COMMENT '库内消息ID', 
  server_id VARCHAR(64) DEFAULT '' COMMENT '服务器消息ID', 
  type VARCHAR(32) DEFAULT '' COMMENT '消息类型', 
  sender VARCHAR(128) DEFAULT '' COMMENT '发言人', 
  content MEDIUMTEXT COMMENT '正文', 
  quote TEXT COMMENT '被引用内容', 
  quote_kind VARCHAR(16) DEFAULT '' COMMENT '引用类型', 
  media_path VARCHAR(512) DEFAULT '' COMMENT '媒体路径', 
  create_time DATETIME DEFAULT NULL COMMENT '消息产生时间', 
  sort_seq BIGINT NOT NULL DEFAULT 0 COMMENT '排序键', 
  md5 VARCHAR(64) DEFAULT '' COMMENT '媒体md5', 
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间', 
  UNIQUE KEY uk_username_localid (username, local_id), 
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信黑名单消息表(只入库不处理)';

-- 2) 字典类型
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '微信消息分析参数', 'wechat_analysis_config', '0', 'admin', NOW(), '消息分析：黑白名单 + 批量AI分类参数 + 固定OFFER名单'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='wechat_analysis_config');

-- 2.5) 状态枚举字典：wechat_message_status（wechat_message.status 的取值含义）
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '微信消息处理状态', 'wechat_message_status', '0', 'admin', NOW(), 'wechat_message.status 枚举: 0=未分类 1=已分类未处理 2=已分类处理未发送 3=无需处理 4=已分类处理发送'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='wechat_message_status');
DELETE FROM sys_dict_data WHERE dict_type='wechat_message_status';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) VALUES
(0, '未分类', '0', 'wechat_message_status', '', 'info',    'N', '0', 'admin', NOW(), '消息入库后默认状态，等待AI分类(分类后置为1)'),
(1, '已分类未处理', '1', 'wechat_message_status', '', 'warning', 'N', '0', 'admin', NOW(), 'AI分类已回写(INQ/OFFER/TRACE/OTHER)，等待录入/后续环节'),
(2, '已分类处理未发送', '2', 'wechat_message_status', '', 'primary', 'N', '0', 'admin', NOW(), 'INQ/OFFER已AI录入crm_offer并写入wechat_server_id关联，等待发送环节'),
(3, '无需处理', '3', 'wechat_message_status', '', 'danger',  'N', '0', 'admin', NOW(), '无需处理(如AI录入连续失败达上限、内容无法识别等)'),
(4, '已分类处理发送', '4', 'wechat_message_status', '', 'success', 'N', '0', 'admin', NOW(), '已分类、已处理并已完成发送(后续发送环节置位)');

-- 3) 字典数据（dict_label=参数名，dict_value=参数值；先清空再插入，重复执行=以文件为准重置）
DELETE FROM sys_dict_data WHERE dict_type='wechat_analysis_config';
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, is_default, status, create_by, create_time, remark) VALUES
(1,  'whiteNames', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '会话白名单（非空时只放过这些名称，多个用逗号拼接）'),
(2,  'blackNames', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '会话黑名单（白名单为空时生效，过滤掉这些名称，多个用逗号拼接）'),
(10, 'llmEnabled', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '是否启用大模型分类(true=DeepSeek精判,false=不分类)'),
(11, 'llmApiKey', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'DeepSeek API Key(敏感)；留空则读环境变量 DEEPSEEK_API_KEY'),
(12, 'llmApiBase', 'https://api.deepseek.com', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'OpenAI兼容接口根地址(不要以/结尾)'),
(13, 'llmModel', 'deepseek-chat', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '大模型名称(deepseek-chat=V3)'),
(14, 'llmBatchSize', '20', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '每次AI分类调用最多消息条数(1-20)'),
(15, 'llmConcurrency', '2', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '并行批次数量(注意DeepSeek限频,建议1-3)'),
(16, 'llmTimeoutSec', '180', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '单次大模型请求超时时间(秒)'),
(90, 'aiTriggerCount', '5', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类触发条数：同一会话积累未分类消息达到该数量立即调起AI分类'),
(91, 'aiFlushMinutes', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类超时窗口(分钟)：超时仍未凑够触发条数也调起AI分类'),
(92, 'aiContextWindow', '10', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类上下文条数：判定时带上该会话最近N条已分类消息'),
(93, 'msgRetentionDays', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '消息保留天数：定时任务删除超过该天数的聊天记录'),
(94, 'llmPromptBase', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类系统提示词(留空用内置默认)：分类定义+判断规则'),
(95, 'llmExamples', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类few-shot示例(留空用内置默认)：真实消息+正确分类'),
(96, 'offerFixedSenders', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '固定发OFFER的发送人(逗号分隔)，命中直接标OFFER，可留空'),
(97, 'offerFixedGroups', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '固定发OFFER的群(逗号分隔)，群内文本直接标OFFER，可留空'),
(98, 'offerFollowWindowMin', '5', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '固定OFFER的回复跟随窗口(分钟)：上一条固定OFFER且本条无明显询价词则标OFFER，0=关闭'),
(99, 'pollEnabled', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '轮询拉取总开关：true=服务启动后自动定时拉取微信消息入库；false=关闭(仅手动 POST /wechat/poll/run)'),
(100, 'pollIntervalSec', '10', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '轮询拉取间隔(秒)：上一轮拉取完成后等待该秒数再启动下一轮(默认10秒)'),
(101, 'pollBackSec', '120', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '拉取冗余回看(秒)：每轮从「上次拉到的最大消息时间-该值」开始扫描，防止边界丢消息(默认120)'),
(102, 'pollInitBackSec', '259200', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '首轮拉取历史窗口(秒)：水位未初始化时往前拉多少秒历史(默认259200=3天，与消息保留天数匹配)'),
(103, 'pollLimit', '5000', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '每轮拉取消息条数上限(条)：dump 增量扫描单次最多返回条数(默认5000)'),
(104, 'pollIgnoreChats', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '拉取过滤名单：群/好友名称【包含】以下任一关键词的消息不入库(不读)，多个用英文逗号分隔，如：推广群,广告'),
(105, 'classifyTriggerCount', '5', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '分类触发条数(条)：未分类(status=0)消息数量达到该值即触发 AI 分类(默认5)'),
(106, 'classifyMaxIdleSec', '180', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '分类触发超时(秒)：最早未分类消息等待超过该秒数即触发 AI 分类(默认180=3分钟)'),
(107, 'classifyBatchSize', '20', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '每轮最多分类条数(条)：定时/手动分类每次只取最早未分类的N条，分类后 status=1(默认20)'),
(110, 'offerEntryEnabled', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'INQ/OFFER录入总开关：true=定时自动把已分类未处理(status=1)的INQ/OFFER消息AI录入crm_offer；false=关闭(仅手动 POST /wechat/offer/entry/run)'),
(111, 'offerEntryScanSec', '30', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '录入扫描间隔(秒)：定时任务按该间隔扫描一次待录入消息，防止并发重入(默认30)'),
(112, 'offerEntryBatchSize', '5', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '每轮最多录入条数(条)：每次取最早status=1且分类在offerEntryCategories内的N条(默认5)'),
(113, 'offerEntryCategories', 'INQ,OFFER', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '参与录入的消息分类(逗号分隔)：默认INQ,OFFER；TRACE/OTHER不录入'),
(114, 'offerEntrySupplierCode', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '默认供应商编码：录入crm_offer的supplier_code；offerEntrySupplierMap未命中时使用；为空则该会话消息跳过不录入(日志提示)'),
(116, 'offerEntrySupplierMap', '', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '群/好友名→供应商映射：英文逗号分隔多组，格式 会话名=供应商编码，如：特斯拉并购委员会=SELL001；命中优先于默认值；会话名需与wechat_message.name完全一致'),
(117, 'offerEntryProfitRatio', '2', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'OFFER报价利润率(%)：报价价=AI识别成本价x(1+利润率/100)，向上取整到5的倍数，与Offer列表AI录入口径一致(默认2)'),
(118, 'offerEntryMaxFail', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI录入失败上限(次)：同一消息连续录入失败达到该次数置status=3无需处理，避免失败消息一直卡住队列(默认3)'),
(119, 'offerEntryCreateBy', 'wechat_auto', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '录入人(create_by/update_by)：定时任务无登录用户，统一用该值标记AI自动录入来源(默认wechat_auto)');

