-- 2026-11-02 Offer 自动录入修复与 NVIDIA GPU 料号格式统一
-- 1) 新增同一引用消息的补充消息聚合窗口（默认 1800 秒，可调整）
-- 2) 将历史 NVIDIA GPU 料号统一为“系列 型号 版本 显存”的空格格式
-- 3) 合并并清理 2026-11-02 众之佳群错误拆分的 Offer 记录

INSERT INTO sys_dict_data
    (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status,
     create_by, create_time, update_by, update_time, remark)
SELECT 120, 'offerEntryQuoteLinkSec', '1800', 'wechat_analysis_config', NULL, 'default', 'N', '0',
       'admin', NOW(), 'admin', NOW(),
       '同一发送人回复同一条引用消息时，补充消息的聚合窗口秒数；默认1800秒，避免报价与料号分批录入'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'wechat_analysis_config' AND dict_label = 'offerEntryQuoteLinkSec'
);

CREATE TABLE IF NOT EXISTS crm_offer_gpu_format_backup_20261102 LIKE crm_offer;
INSERT IGNORE INTO crm_offer_gpu_format_backup_20261102
SELECT * FROM crm_offer
WHERE product_type = 'GPU'
  AND UPPER(REPLACE(COALESCE(product_code, ''), ' ', '')) IN
      ('RTX509032G', 'PRO6000SE96G', 'PRO6000WS96G', 'PRO6000');

UPDATE crm_offer
SET product_code = CASE UPPER(REPLACE(COALESCE(product_code, ''), ' ', ''))
        WHEN 'RTX509032G' THEN 'RTX 5090 32G'
        WHEN 'PRO6000SE96G' THEN 'PRO 6000 SE 96G'
        WHEN 'PRO6000WS96G' THEN 'PRO 6000 WS 96G'
        WHEN 'PRO6000' THEN 'PRO 6000'
        ELSE product_code
    END,
    pn_normalized = CASE UPPER(REPLACE(COALESCE(product_code, ''), ' ', ''))
        WHEN 'RTX509032G' THEN 'RTX 5090 32G'
        WHEN 'PRO6000SE96G' THEN 'PRO 6000 SE 96G'
        WHEN 'PRO6000WS96G' THEN 'PRO 6000 WS 96G'
        WHEN 'PRO6000' THEN 'PRO 6000'
        ELSE pn_normalized
    END,
    update_time = NOW()
WHERE product_type = 'GPU'
  AND UPPER(REPLACE(COALESCE(product_code, ''), ' ', '')) IN
      ('RTX509032G', 'PRO6000SE96G', 'PRO6000WS96G', 'PRO6000');

UPDATE wechat_offer_task
SET pn_normalized = CASE UPPER(REPLACE(COALESCE(pn_normalized, ''), ' ', ''))
        WHEN 'RTX509032G' THEN 'RTX 5090 32G'
        WHEN 'PRO6000SE96G' THEN 'PRO 6000 SE 96G'
        WHEN 'PRO6000WS96G' THEN 'PRO 6000 WS 96G'
        WHEN 'PRO6000' THEN 'PRO 6000'
        ELSE pn_normalized
    END,
    update_time = NOW()
WHERE UPPER(REPLACE(COALESCE(pn_normalized, ''), ' ', '')) IN
      ('RTX509032G', 'PRO6000SE96G', 'PRO6000WS96G', 'PRO6000');

UPDATE sys_config
SET config_value = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(config_value, 'RTX509032G', 'RTX 5090 32G'),
                'PRO6000SE96G', 'PRO 6000 SE 96G'),
            'PRO6000WS96G', 'PRO 6000 WS 96G'),
        'PRO6000', 'PRO 6000'),
    update_time = NOW()
WHERE config_key IN ('sys.offer.resolve.tips.base', 'sys.offer.resolve.tips');

CREATE TABLE IF NOT EXISTS crm_offer_cleanup_backup_20261102 LIKE crm_offer;
INSERT IGNORE INTO crm_offer_cleanup_backup_20261102
SELECT * FROM crm_offer WHERE id IN (17423, 17426, 17427);

-- 17426=仅有价格无料号；17427=有料号无价格。合并为一条完整 Offer，保留 17427。
UPDATE crm_offer
SET price_cost = 1010.00,
    price_offer = 1035.00,
    price_unit = 'USD',
    quantity = 240,
    dc = '26',
    product_detail = 'Ultrastar DC HC580 24TB SATA 6Gb/s 7200rpm 3.5-inch, 原包散货, DC26',
    remark = '微信自动录入|群:亦钟人 众之佳VIP订单群|发言人:谢丹 广州众之佳 配件|消息时间:2026-11-02 15:13:10|server_id:亦钟人 众之佳VIP订单群:50037418603@chatroom:1793603590|聚合消息:460,463,464,465,466,467,468|引用:590201',
    update_time = NOW()
WHERE id = 17427
  AND product_code = 'WUH722624ALE6L4';

DELETE FROM crm_offer WHERE id IN (17423, 17426);

UPDATE wechat_message
SET entry_status = 'SUCCESS',
    entry_message = '聚合录入[8条]: 消息#463; AI录入: 1行; 价格1010USD; 已与消息#460合并',
    entry_offer_count = 1,
    entry_offer_ids = '17427',
    entry_fail_count = 0,
    entry_at = NOW(),
    task_status = 'SKIPPED',
    task_message = 'MERGED_ENTRY:463',
    task_failure_reason = ''
WHERE id = 590252;

UPDATE wechat_message
SET entry_status = 'SUCCESS',
    entry_message = '聚合录入[8条]: 消息#463; AI录入: 1行; 比价: 14条',
    entry_offer_count = 1,
    entry_offer_ids = '17427',
    entry_fail_count = 0,
    entry_at = NOW(),
    task_status = 'PENDING',
    task_message = '',
    task_failure_reason = ''
WHERE id = 590263;

UPDATE wechat_message
SET entry_message = '聚合录入[8条]: 消息#463; AI录入: 1行; 比价: 14条'
WHERE id IN (590279, 590290)
  AND entry_offer_ids = '17427';

UPDATE wechat_message
SET entry_status = 'SKIPPED',
    entry_message = '无具体料号，已作为后续报价的引用上下文，不单独录入',
    entry_offer_count = 0,
    entry_offer_ids = '',
    entry_fail_count = 0,
    entry_at = NOW(),
    task_status = 'SKIPPED',
    task_message = 'QUOTE_CONTEXT_ONLY',
    task_failure_reason = ''
WHERE id = 590201;
