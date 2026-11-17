-- 2026-11-16 修正 Intel CPU 料号误带 S-Spec 的问题
-- 影响范围：Converge 群 59200282316@chatroom，2026-11-16 14:43 的询价
-- 规则：U7-366H/SA4R9 -> U7-366H；I7-1365U/SRMM0 -> I7-1365U
-- 执行前先备份，执行后核对末尾 SELECT 结果。

-- 0. 影响行数（执行前确认）
SELECT COUNT(*) AS crm_offer_rows
FROM crm_offer
WHERE id IN (20781, 20782, 20783, 20784, 20785, 20786)
  AND product_code REGEXP '/S[A-Z][A-Z0-9]{3}$';

SELECT COUNT(*) AS task_rows
FROM wechat_offer_task
WHERE id IN (1769, 1770, 1771, 1772, 1773, 1774)
  AND pn_normalized REGEXP '/S[A-Z][A-Z0-9]{3}$';

-- 1. 备份
CREATE TABLE IF NOT EXISTS crm_offer_intel_sspec_bak_20261116 LIKE crm_offer;
INSERT IGNORE INTO crm_offer_intel_sspec_bak_20261116
SELECT * FROM crm_offer
WHERE id IN (20781, 20782, 20783, 20784, 20785, 20786);

CREATE TABLE IF NOT EXISTS wechat_offer_task_intel_sspec_bak_20261116 LIKE wechat_offer_task;
INSERT IGNORE INTO wechat_offer_task_intel_sspec_bak_20261116
SELECT * FROM wechat_offer_task
WHERE id IN (1769, 1770, 1771, 1772, 1773, 1774);

-- 2. 修正 crm_offer 料号
UPDATE crm_offer
SET product_code = SUBSTRING_INDEX(product_code, '/', 1),
    pn_normalized = SUBSTRING_INDEX(pn_normalized, '/', 1),
    update_time = NOW()
WHERE id IN (20781, 20782, 20783, 20784, 20785, 20786)
  AND product_code REGEXP '/S[A-Z][A-Z0-9]{3}$';

-- 3. 修正 wechat_offer_task 料号、发送内容和 offer_json
UPDATE wechat_offer_task
SET send_content = REPLACE(send_content, pn_original, SUBSTRING_INDEX(pn_original, '/', 1)),
    offer_json = REPLACE(offer_json, pn_original, SUBSTRING_INDEX(pn_original, '/', 1)),
    pn_original = SUBSTRING_INDEX(pn_original, '/', 1),
    pn_normalized = SUBSTRING_INDEX(pn_normalized, '/', 1),
    update_time = NOW()
WHERE id IN (1769, 1770, 1771, 1772, 1773, 1774)
  AND pn_normalized REGEXP '/S[A-Z][A-Z0-9]{3}$';

-- 4. 核对
SELECT id, product_code, pn_normalized, product_detail
FROM crm_offer
WHERE id IN (20781, 20782, 20783, 20784, 20785, 20786)
ORDER BY id;

SELECT id, pn_original, pn_normalized, send_content, offer_json
FROM wechat_offer_task
WHERE id IN (1769, 1770, 1771, 1772, 1773, 1774)
ORDER BY id;

-- 可选：20853 / 1802 是 I7-6822EQ 的重复 MM# 行，确认后可另行清理：
-- SELECT id, product_code, product_detail FROM crm_offer WHERE id = 20853;
-- SELECT id, pn_original, pn_normalized, send_content FROM wechat_offer_task WHERE id = 1802;
