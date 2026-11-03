-- Offer 管理列表按 Offer 日期倒排。
-- Offer 日期口径与现有业务一致：stock_date 优先，为空时回退 create_time。
-- MySQL 8.0 函数索引与 SQL 中的 coalesce(stock_date, create_time) 完全一致。

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE crm_offer ADD INDEX idx_crm_offer_offer_date ((coalesce(stock_date, create_time)))',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'crm_offer'
      AND index_name = 'idx_crm_offer_offer_date'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
