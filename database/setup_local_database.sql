-- 创建本地CRM数据库
-- 请先确保MySQL服务已启动，然后执行此脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS crm_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE crm_system;

-- 提示信息
SELECT '数据库 crm_system 创建成功！' as message;
SELECT '请继续执行 crm_database.sql 和 init_data.sql 来创建表结构和初始数据' as next_step;