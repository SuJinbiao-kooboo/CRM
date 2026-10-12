-- =========================================================
-- 飞书机器人模块（ruoyi-feishu）菜单权限
-- 说明：超级管理员(admin)默认拥有所有权限(*:*:*)，无需此脚本即可调用接口。
--       如需给普通角色开放控制权限，请执行本脚本，并把菜单授权给对应角色。
-- =========================================================
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES ('飞书机器人', 0, 6, 'feishu', '', 1, 1, 'C', '0', '0', 'feishu:bot:manage', 'chat', 'admin', sysdate(), '飞书机器人控制（自测用）');

-- 把菜单授权给常用角色（以角色编号 2 为例，可按需调整）
-- INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, menu_id FROM sys_menu WHERE perms = 'feishu:bot:manage';
