-- Offer 状态字段说明（复用现有 status 列，无需 ALTER）：
--   status = 1 有效（默认，历史数据全部为 1）
--   status = 0 无效（失效：列表"失效"按钮/弹窗行"失效"操作后置为 0）
-- 失效的 Offer：AI查询/AI录入比价/复制Offer 的"一键复制"与"下载"均自动过滤，不再输出
-- 存量数据兜底：统一置为有效
UPDATE crm_offer SET status = 1 WHERE status IS NULL OR status NOT IN (0, 1);
