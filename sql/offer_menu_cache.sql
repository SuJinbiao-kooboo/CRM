-- Offer列表菜单开启页面缓存：去其他页面再回来保留搜索条件与列表状态（需求：Offer列表搜索条件保留）
-- 原理：is_cache=1 → 动态路由 meta.noCache=false → TagsView keep-alive 缓存；
--       route_name 与页面组件 name(CrmOffer) 对齐，keep-alive include 匹配才生效
-- 幂等：仅当配置不一致时更新
UPDATE sys_menu SET is_cache = 1, route_name = 'CrmOffer'
WHERE menu_id = 1211 AND (is_cache <> 1 OR route_name <> 'CrmOffer' OR route_name IS NULL);