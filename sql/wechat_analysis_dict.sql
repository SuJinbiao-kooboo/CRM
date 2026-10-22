-- =====================================================================
-- 微信消息：建表 + 分析参数数据字典（一条 SQL 全部搞定）
-- 用途：
--   1) wechat_message：消息全量入库（监听+拉取都写这里），只保留最近 msgRetentionDays 天；
--   2) wechat_analysis_config 字典：黑白名单过滤 + 批量AI分类参数 + 固定OFFER名单。
-- 用法(MySQL)：  mysql -uroot -p 数据库名 < wechat_analysis_dict.sql
-- 导入后需刷新字典缓存：POST /system/dict/data/refreshCache（或重启 ruoyi-admin）
-- 2026-10-22 语义收紧：已有库不要整文件重跑（会重置参数），请按顺序执行
--   sql/wechat_semantics_v2.sql、sql/wechat_semantics_v3.sql。
-- =====================================================================

-- 1) 消息表（不存在才建；列名/类型与 Java POJO 对应）
-- 注：老库（表已存在）需手工补一次索引（2026-10-20 新增，线上库已执行）：
--   ALTER TABLE wechat_message ADD KEY idx_sort_seq (sort_seq), ADD KEY idx_status_sort (status, sort_seq), ADD KEY idx_status_cat_sort (status, msg_category, sort_seq), ADD KEY idx_name_sort (name, sort_seq), ADD KEY idx_status_createtime (status, create_time);
CREATE TABLE IF NOT EXISTS wechat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键', 
  username VARCHAR(64) NOT NULL COMMENT '会话标识(群id/好友wxid)', 
  name VARCHAR(128) DEFAULT '' COMMENT '会话显示名', 
  local_id BIGINT NOT NULL DEFAULT 0 COMMENT '库内消息ID', 
  server_id VARCHAR(255) DEFAULT '' COMMENT '服务器消息ID',
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
  KEY idx_status (status), 
  KEY idx_sort_seq (sort_seq), 
  KEY idx_status_sort (status, sort_seq), 
  KEY idx_status_cat_sort (status, msg_category, sort_seq), 
  KEY idx_name_sort (name, sort_seq), 
  KEY idx_status_createtime (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信消息表(仅保留最近N天,定时清理)';

-- 1.5) 黑名单消息表：pollIgnoreChats(名称包含关键词) 命中的群消息只入库、不做分类/处理；
-- 与主表结构一致，唯一键相同，不参与 msgRetentionDays 自动清理（保留备查）。
CREATE TABLE IF NOT EXISTS wechat_message_black (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键', 
  username VARCHAR(64) NOT NULL COMMENT '会话标识(群id/好友wxid)', 
  name VARCHAR(128) DEFAULT '' COMMENT '会话显示名', 
  local_id BIGINT NOT NULL DEFAULT 0 COMMENT '库内消息ID', 
  server_id VARCHAR(255) DEFAULT '' COMMENT '服务器消息ID',
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
(2,  'blackNames', '山樾湾2F业主群,前海路5号玩乐吃喝,封窗进展,兔宝宝山樾湾2F 812苏总', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '分类黑名单（精确匹配 name；永远优先于白名单，不参与AI分类，多个用逗号拼接）'),
(14, 'llmBatchSize', '20', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '每次AI分类调用最多消息条数(1-20)'),
(15, 'llmConcurrency', '2', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '并行批次数量(注意DeepSeek限频,建议1-3)'),
(16, 'llmTimeoutSec', '180', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '单次大模型请求超时时间(秒)'),
(90, 'aiTriggerCount', '5', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类触发条数：同一会话积累未分类消息达到该数量立即调起AI分类'),
(91, 'aiFlushMinutes', '3', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类超时窗口(分钟)：超时仍未凑够触发条数也调起AI分类'),
(92, 'aiContextWindow', '20', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'AI分类上下文条数：判定时带上该会话最近N条已分类消息'),
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
(104, 'pollIgnoreChats', '业主群,玩乐吃喝,封窗进展,兔宝宝山樾湾', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '拉取过滤：名称包含任一关键词的群/好友消息转存 wechat_message_black，不进入分类；多个用英文逗号分隔'),
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
(119, 'offerEntryCreateBy', 'wechat_auto', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '录入人(create_by/update_by)：定时任务无登录用户，统一用该值标记AI自动录入来源(默认wechat_auto)'),
(120, 'selfNames', 'Eke,May', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '我方微信名/别名(逗号分隔)：用于判断消息方向；后续新增业务员在此追加'),
(121, 'strictBusinessSemantics', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '严格业务语义：广播报价/我方外发询价/内部沟通降级OTHER'),
(122, 'strictCustomerOnly', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), '只保留客户发起的INQ/TRACE；我方问供应商的消息归OTHER'),
(123, 'traceAlertEnabled', 'true', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'TRACE飞书预警：仅对客户发起、已关联INQ/OFFER、且为疑问句的二次询问推送'),
(124, 'traceAlertMaxAgeMin', '1440', 'wechat_analysis_config', 'N', '0', 'admin', NOW(), 'TRACE预警最大时效(分钟)：超过该时长的历史消息不推送飞书，默认1440=24小时');

-- 2.6) 【2026-10-22 调整】AI 渠道配置（地址/Key/模型/备用渠道/告警）已统一到系统参数 sys_config（前缀 sys.ai.），
--       见 sql/deepseek_ai_config.sql；下列字典旧参数已作废并清理，避免两套配置不一致：
DELETE FROM sys_dict_data WHERE dict_type='wechat_analysis_config'
  AND dict_label IN ('llmEnabled','llmApiKey','llmApiBase','llmModel','llmBackupEnabled','llmBackupApiBase','llmBackupApiKey','llmBackupModel','llmFailThreshold','llmBackupCheckSec','alertFeishuWebhook','alertCooldownSec');

-- 4) 【2026-10-20 新增】AI分类few-shot示例：源自 Reference/INQ_OFFER_TRACE_分类示例.xlsx 真实案例（24条）
--    dict_value 原为 varchar(100)，放不下提示词/示例长文本，扩容到 8192（兼容 llmPromptBase 长提示词）
ALTER TABLE sys_dict_data MODIFY COLUMN dict_value VARCHAR(8192) NOT NULL DEFAULT '';
UPDATE sys_dict_data SET dict_value = '真实示例（<换人>表示消息来自不同发言人，示例内按顺序为连续对话）：
示例1：<雅克尼> INQ
AMD 9555P : 153pcs
AMD 9355P : 154pcs
AMD 9115 : 276pcs
AMD 8324P : 76pcs → INQ（买方列型号+数量，无价格）
示例2：<雅克尼> Gold 6430 14pcs 这个找拆新的 → INQ（含型号+数量即询价）
示例3：<雅克尼> MBD-H13SSL-N-B 24pcs → INQ（纯型号数量求购）
示例4：<雅克尼> 9654 $2,145 这个是全新的吗，问32片 → INQ（对报价单单个料号的购买问询，含明确数量）
示例5：<Eke> AMD 9555P : 18pcs 3300 USD 3-5d
AMD 9355P : 110pcs 2320 USD 1-3d
AMD 9115 : 276pcs 24pulled 690USD → OFFER（料号对上一条INQ且带价格=对询价的报价）
示例6：<Eke> M&E 2026-10-19 Offer：
Part No. Qty Price
5955WX 485USD
5965WX 755USD
7975WX 2785USD → OFFER（自家库存报价表整段）
示例7：<Eke> MBD-H13SSL-N-B 20pcs 2w到港 695USD → OFFER（价格+交期）
示例8：<Eke> @雅克尼 一片 MBD-X13SAZ-F-B 660 USD 这个你不是还差1片吗，看下你如果要的话我再去和供应商争取下 → OFFER（给价且含料号）
示例9：<Eke> CPU offer / this offer → OTHER（对Offer的补充说明，不是新报价）
示例10：<Eke> check / no stock / 稍等，我check下哈 → OTHER（礼貌应答，无报价无询价）
示例11：<小辉> <?xml...sysmsg type=revokemsg...> → OTHER（撤回/系统/群公告等系统消息）
示例12：<Eke> <msg><img ...></msg>、<msg><location ...></msg>、xxx.pdf → OTHER（图片/位置/文件/卡片）
示例13：<雅克尼> 是你们库存？ → TRACE（针对上一条OFFER追问）
示例14：<雅克尼> 这个18片，560可以下单吗 → TRACE（针对OFFER砍价/下单确认）
示例15：<May> @雅克尼 我们到港运费正常要+20USD，你客户只要19片吗？ → TRACE（交期/运费等跟进）
示例16：<朱妍> 就是660美金吗 / 2周交期也要全款吗 / 质保多久 / 最晚交货日，我写11.3可以吗 → TRACE（价格/交期/质保确认）
示例17：<May> 你那18片有人接单了吗？ / 原本数量不是19吗？ → TRACE（进度跟进）
示例18：<May> 新的，你先报，有反馈下单前确认 → TRACE（对上次询价的跟进回复）
示例19：<雅克尼> 这个有吗 → OTHER（紧接自己上一条INQ的补充说明，不算新INQ）
示例20：<朱妍> 发点主板的offer给我呗 @May → OTHER（内部向同事要资料，非客户询价）
示例21：<双方> 下单吧 / 给我一下你的邮箱 / PO条款讨论 / 银行信息 / 送货地址 → OTHER（成交后执行细节）
示例22：<A> @Eke / 好 / 收到 / 谢谢 / 哦 → OTHER（@提及、单字应答）
示例23：<嘎嘎呼> 早上好，询一下 5317 10pcs → INQ；<Eke> 早 → OTHER；<Eke> 1145USD @嘎嘎呼 → OFFER（前一条INQ的报价）
示例24：<May> EPYC 9255 10pcs INQ → INQ；<嘎嘎呼> checking → OTHER（核实中）；<嘎嘎呼> EPYC 9255 10pcs 拆新，2368USD → OFFER'
WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'llmExamples';

