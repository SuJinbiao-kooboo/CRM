-- 2026-10-29 将历史 Offer 中的 NVIDIA RTX 5090 / RTX PRO 6000 SE/WS 统一为规范料号
-- 只处理 product_type='GPU' 且能明确判断的记录；5090D、PRO6000D、MAX-Q、AMD Radeon 等变体保持原样
-- 执行前备份受影响行到 crm_offer_nvidia_backup_20261029

CREATE TABLE IF NOT EXISTS crm_offer_nvidia_backup_20261029 LIKE crm_offer;
INSERT IGNORE INTO crm_offer_nvidia_backup_20261029
SELECT * FROM crm_offer
WHERE product_type = 'GPU'
  AND (
    UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%5090%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%PRO%6000%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%6000%PRO%'
  );

-- 5090：先把带连字符的原始SKU保留到 product_detail，避免归一化后丢失原厂编码
UPDATE crm_offer
SET product_detail = LEFT(CONCAT(COALESCE(product_detail,''), IF(product_detail IS NULL OR product_detail = '', '', '; '), '原PN ', product_code), 512)
WHERE product_type = 'GPU'
  AND UPPER(COALESCE(product_code,'')) LIKE '%5090%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%5090D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%5090 D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%AMD%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%RADEON%'
  AND product_code LIKE '%-%'
  AND product_code <> 'RTX509032G'
  AND (product_detail IS NULL OR product_detail NOT LIKE CONCAT('%', product_code, '%'));

UPDATE crm_offer
SET product_code = 'RTX509032G', pn_normalized = 'RTX509032G', update_time = NOW()
WHERE product_type = 'GPU'
  AND (
    UPPER(COALESCE(product_code,'')) LIKE '%5090%'
    OR UPPER(COALESCE(product_detail_code,'')) LIKE '%5090%'
  )
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%5090D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%5090 D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%AMD%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%RADEON%'
  AND product_code <> 'RTX509032G';

-- PRO 6000 Server Edition：先保留带连字符的原始SKU
UPDATE crm_offer
SET product_detail = LEFT(CONCAT(COALESCE(product_detail,''), IF(product_detail IS NULL OR product_detail = '', '', '; '), '原PN ', product_code), 512)
WHERE product_type = 'GPU'
  AND (
    UPPER(COALESCE(product_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail,'')) LIKE '%PRO%6000%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%6000%PRO%'
  )
  AND (
    UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%SERVER%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%SEVER%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%服务器%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%服务版%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) REGEXP '(^|[^A-Z0-9])SE([^A-Z0-9]|$)'
  )
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%WORKSTATION%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%工作站%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT REGEXP '(^|[^A-Z0-9])WS([^A-Z0-9]|$)'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000 D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%6000 PRO D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX-Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX~Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAXQ%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%AMD%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%RADEON%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%整机%'

  AND product_code LIKE '%-%'
  AND product_code <> 'PRO6000SE96G'
  AND (product_detail IS NULL OR product_detail NOT LIKE CONCAT('%', product_code, '%'));

UPDATE crm_offer
SET product_code = 'PRO6000SE96G', pn_normalized = 'PRO6000SE96G', update_time = NOW()
WHERE product_type = 'GPU'
  AND (
    UPPER(COALESCE(product_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail,'')) LIKE '%PRO%6000%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%6000%PRO%'
  )
  AND (
    UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%SERVER%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%SEVER%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%服务器%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%服务版%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) REGEXP '(^|[^A-Z0-9])SE([^A-Z0-9]|$)'
  )
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%WORKSTATION%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%工作站%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT REGEXP '(^|[^A-Z0-9])WS([^A-Z0-9]|$)'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000 D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%6000 PRO D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX-Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX~Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAXQ%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%AMD%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%RADEON%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%整机%'

  AND product_code <> 'PRO6000SE96G';

-- PRO 6000 Workstation Edition：先保留带连字符的原始SKU
UPDATE crm_offer
SET product_detail = LEFT(CONCAT(COALESCE(product_detail,''), IF(product_detail IS NULL OR product_detail = '', '', '; '), '原PN ', product_code), 512)
WHERE product_type = 'GPU'
  AND (
    UPPER(COALESCE(product_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail,'')) LIKE '%PRO%6000%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%6000%PRO%'
  )
  AND (
    UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%WORKSTATION%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%工作站%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) REGEXP '(^|[^A-Z0-9])WS([^A-Z0-9]|$)'
  )
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%SERVER%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%服务器%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%服务版%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT REGEXP '(^|[^A-Z0-9])SE([^A-Z0-9]|$)'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000 D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%6000 PRO D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX-Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX~Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAXQ%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%AMD%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%RADEON%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%整机%'

  AND product_code LIKE '%-%'
  AND product_code <> 'PRO6000WS96G'
  AND (product_detail IS NULL OR product_detail NOT LIKE CONCAT('%', product_code, '%'));

UPDATE crm_offer
SET product_code = 'PRO6000WS96G', pn_normalized = 'PRO6000WS96G', update_time = NOW()
WHERE product_type = 'GPU'
  AND (
    UPPER(COALESCE(product_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail_code,'')) LIKE '%PRO%6000%'
    OR UPPER(COALESCE(product_detail,'')) LIKE '%PRO%6000%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%6000%PRO%'
  )
  AND (
    UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%WORKSTATION%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) LIKE '%工作站%'
    OR UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) REGEXP '(^|[^A-Z0-9])WS([^A-Z0-9]|$)'
  )
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%SERVER%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%服务器%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%服务版%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT REGEXP '(^|[^A-Z0-9])SE([^A-Z0-9]|$)'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%PRO 6000 D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%6000 PRO D%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX-Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX~Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAX Q%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%MAXQ%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%AMD%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%RADEON%'
  AND UPPER(CONCAT(COALESCE(product_code,''),' ',COALESCE(product_detail_code,''),' ',COALESCE(product_detail,''))) NOT LIKE '%整机%'

  AND product_code <> 'PRO6000WS96G';