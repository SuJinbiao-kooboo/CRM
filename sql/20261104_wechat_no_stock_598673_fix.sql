-- 修正微信消息 598673（内容“缺货”）被误归为 OFFER 并带入 AI 录入的问题。
-- 保留真实报价 17705（9135），删除无价格且来自缺货响应的 17706、17707。

DELETE FROM crm_offer
WHERE id IN (17706, 17707)
  AND price_cost IS NULL
  AND price_offer IS NULL;

UPDATE crm_offer
SET remark = REPLACE(remark, '|聚合消息:287,288', '|聚合消息:287')
WHERE id = 17705;

UPDATE wechat_message
SET entry_offer_count = 1,
    entry_offer_ids = '17705',
    entry_message = '聚合录入[1条]: 消息#287; AI录入: 1行; 缺货消息#598673已排除',
    task_message = ''
WHERE id = 598672;

UPDATE wechat_message
SET msg_category = 'OTHER',
    category_source = 'FILTER',
    category_reason = '缺货/无库存响应，不生成Offer',
    relat_inq = NULL,
    relat_offer = NULL,
    entry_status = 'NOT_APPLICABLE',
    entry_message = '缺货/无库存响应，不录入Offer',
    entry_offer_count = 0,
    entry_offer_ids = '',
    entry_at = NULL,
    task_status = 'SKIPPED',
    task_message = 'NO_STOCK',
    task_failure_reason = ''
WHERE id = 598673;
