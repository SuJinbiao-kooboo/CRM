-- Offer价格优势阈值（单位USD）：AI录入/AI查询一键复制时判定"今天最好价 ≤ 上两次报价最低价 - 阈值"的阈值
-- 前端默认15，读取该参数覆盖默认值；非管理员可在系统管理→参数设置里调整
-- 幂等：config_key 已存在时不重复插入
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'Offer价格优势阈值(USD)', 'offer.price.advantage.threshold', '15', 'Y', 'admin', NOW(), 'AI录入/AI查询一键复制价格优势判定阈值'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'offer.price.advantage.threshold');