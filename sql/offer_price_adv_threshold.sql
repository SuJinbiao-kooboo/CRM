-- Offer价格优势阈值（单位USD）：AI录入/AI查询/复制Offer和好价格定时推送共用
-- 判定"区间最好价 ≤ 上两次报价最低价 - 阈值"；页面可直接修改并自动保存，也可在系统管理→参数设置里调整
-- 幂等：config_key 已存在时不重复插入
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'Offer价格优势阈值(USD)', 'offer.price.advantage.threshold', '15', 'Y', 'admin', NOW(), 'AI录入/AI查询一键复制价格优势判定阈值'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'offer.price.advantage.threshold');
