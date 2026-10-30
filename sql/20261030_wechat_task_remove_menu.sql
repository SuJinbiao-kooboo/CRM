-- 2026-10-30 微信询报价任务：新增删除权限与菜单按钮

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT '任务删除', p.menu_id, 5, '', '', 1, 0, 'F', '0', '0', 'wechat:task:remove', '#', 'admin', NOW(), '', NULL, ''
FROM sys_menu p
WHERE p.perms = 'wechat:task:list'
  AND NOT EXISTS (SELECT 1 FROM sys_menu c WHERE c.perms = 'wechat:task:remove');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.perms = 'wechat:task:remove'
WHERE rm.menu_id = (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE perms = 'wechat:task:list' LIMIT 1) t)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.menu_id);
