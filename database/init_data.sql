-- 初始化基础数据
USE crm_system;

-- 插入系统管理员用户
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, leader, phone, email, sort_order, status) VALUES
(1, 0, '总公司', 'ROOT', '系统管理员', '13800138000', 'admin@crm.com', 0, 1),
(2, 1, '销售部', 'SALES', '销售经理', '13800138001', 'sales@crm.com', 1, 1),
(3, 1, '采购部', 'PURCHASE', '采购经理', '13800138002', 'purchase@crm.com', 2, 1);

-- 插入角色
INSERT INTO sys_role (id, role_name, role_key, role_sort, data_scope, status, remark) VALUES
(1, '超级管理员', 'admin', 1, 1, 1, '超级管理员'),
(2, '销售经理', 'sales_manager', 2, 2, 1, '销售经理'),
(3, '销售员', 'salesman', 3, 5, 1, '销售员'),
(4, '采购员', 'purchaser', 4, 5, 1, '采购员');

-- 插入用户
INSERT INTO sys_user (id, dept_id, username, nickname, email, phone, sex, password, status, remark) VALUES
(1, 1, 'admin', '系统管理员', 'admin@crm.com', '13800138000', 0, '$2a$10$7JB720yubVSOfvam/l0rFOaHDS5PpuQWwHkDrXbXRVNWpDMXs.ZSe', 1, '管理员'),
(2, 2, 'sales01', '销售员01', 'sales01@crm.com', '13800138001', 0, '$2a$10$7JB720yubVSOfvam/l0rFOaHDS5PpuQWwHkDrXbXRVNWpDMXs.ZSe', 1, '销售员'),
(3, 3, 'purchase01', '采购员01', 'purchase01@crm.com', '13800138002', 0, '$2a$10$7JB720yubVSOfvam/l0rFOaHDS5PpuQWwHkDrXbXRVNWpDMXs.ZSe', 1, '采购员');

-- 用户角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 3),
(3, 4);

-- 插入基础字典数据
INSERT INTO sys_dict (id, dict_name, dict_key, description, status) VALUES
(1, '线索类型', 'lead_type', '线索类型字典', 1),
(2, '线索状态', 'lead_status', '线索状态字典', 1),
(3, '线索来源', 'lead_source', '线索来源字典', 1),
(4, '客户类型', 'customer_type', '客户类型字典', 1),
(5, '客户状态', 'customer_status', '客户状态字典', 1),
(6, '客户等级', 'customer_level', '客户等级字典', 1),
(7, '产品分类', 'product_category', '产品分类字典', 1),
(8, '品牌', 'brand', '品牌字典', 1),
(9, '供应商类型', 'supplier_type', '供应商类型字典', 1),
(10, '订单状态', 'order_status', '订单状态字典', 1),
(11, '订单类型', 'order_type', '订单类型字典', 1),
(12, '跟进方式', 'follow_method', '跟进方式字典', 1),
(13, '跟进结果', 'follow_result', '跟进结果字典', 1),
(14, '询价状态', 'inquiry_status', '询价状态字典', 1),
(15, '付款方式', 'payment_method', '付款方式字典', 1),
(16, '物流方式', 'logistics_method', '物流方式字典', 1),
(17, '紧急程度', 'urgency_level', '紧急程度字典', 1),
(18, '库存状态', 'stock_status', '库存状态字典', 1);

-- 插入字典项数据
-- 线索类型
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(1, 'potential', '潜在客户', 'potential', 1, 1),
(1, 'intention', '意向客户', 'intention', 2, 1),
(1, 'cooperation', '合作意向', 'cooperation', 3, 1);

-- 线索状态
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(2, 'new', '新线索', 'new', 1, 1),
(2, 'following', '跟进中', 'following', 2, 1),
(2, 'converted', '已转客户', 'converted', 3, 1),
(2, 'invalid', '无效线索', 'invalid', 4, 1);

-- 线索来源
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(3, 'website', '官网', 'website', 1, 1),
(3, 'exhibition', '展会', 'exhibition', 2, 1),
(3, 'referral', '推荐', 'referral', 3, 1),
(3, 'phone', '电话营销', 'phone', 4, 1),
(3, 'email', '邮件营销', 'email', 5, 1),
(3, 'social', '社交媒体', 'social', 6, 1);

-- 客户类型
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(4, 'distributor', '分销商', 'distributor', 1, 1),
(4, 'retailer', '零售商', 'retailer', 2, 1),
(4, 'manufacturer', '制造商', 'manufacturer', 3, 1),
(4, 'system_integrator', '系统集成商', 'system_integrator', 4, 1);

-- 客户状态
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(5, 'active', '活跃', 'active', 1, 1),
(5, 'inactive', '不活跃', 'inactive', 2, 1),
(5, 'cooperation', '合作中', 'cooperation', 3, 1),
(5, 'suspended', '暂停合作', 'suspended', 4, 1);

-- 客户等级
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(6, 'A', 'A级客户', 'A', 1, 1),
(6, 'B', 'B级客户', 'B', 2, 1),
(6, 'C', 'C级客户', 'C', 3, 1);

-- 产品分类
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(7, 'ssd', 'SSD固态硬盘', 'ssd', 1, 1),
(7, 'hdd', 'HDD机械硬盘', 'hdd', 2, 1),
(7, 'memory', '内存', 'memory', 3, 1),
(7, 'cpu', 'CPU处理器', 'cpu', 4, 1),
(7, 'gpu', 'GPU显卡', 'gpu', 5, 1),
(7, 'motherboard', '主板', 'motherboard', 6, 1),
(7, 'power', '电源', 'power', 7, 1);

-- 品牌
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(8, 'samsung', '三星', 'samsung', 1, 1),
(8, 'intel', '英特尔', 'intel', 2, 1),
(8, 'amd', 'AMD', 'amd', 3, 1),
(8, 'nvidia', '英伟达', 'nvidia', 4, 1),
(8, 'kingston', '金士顿', 'kingston', 5, 1),
(8, 'western_digital', '西部数据', 'western_digital', 6, 1),
(8, 'seagate', '希捷', 'seagate', 7, 1);

-- 供应商类型
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(9, 'manufacturer', '制造商', 'manufacturer', 1, 1),
(9, 'distributor', '分销商', 'distributor', 2, 1),
(9, 'agent', '代理商', 'agent', 3, 1);

-- 订单状态
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(10, 'pending', '待确认', 'pending', 1, 1),
(10, 'confirmed', '已确认', 'confirmed', 2, 1),
(10, 'purchasing', '采购中', 'purchasing', 3, 1),
(10, 'purchased', '已采购', 'purchased', 4, 1),
(10, 'shipping', '发货中', 'shipping', 5, 1),
(10, 'shipped', '已发货', 'shipped', 6, 1),
(10, 'received', '已收货', 'received', 7, 1),
(10, 'completed', '已完成', 'completed', 8, 1);

-- 订单类型
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(11, 'normal', '普通订单', 'normal', 1, 1),
(11, 'urgent', '紧急订单', 'urgent', 2, 1),
(11, 'sample', '样品订单', 'sample', 3, 1);

-- 跟进方式
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(12, 'phone', '电话', 'phone', 1, 1),
(12, 'email', '邮件', 'email', 2, 1),
(12, 'visit', '面访', 'visit', 3, 1),
(12, 'online', '在线', 'online', 4, 1);

-- 跟进结果
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(13, 'positive', '积极', 'positive', 1, 1),
(13, 'neutral', '中性', 'neutral', 2, 1),
(13, 'negative', '消极', 'negative', 3, 1),
(13, 'no_response', '无回应', 'no_response', 4, 1);

-- 询价状态
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(14, 'pending', '未处理', 'pending', 1, 1),
(14, 'processing', '处理中', 'processing', 2, 1),
(14, 'quoted', '已报价', 'quoted', 3, 1),
(14, 'accepted', '已接受', 'accepted', 4, 1),
(14, 'rejected', '已拒绝', 'rejected', 5, 1),
(14, 'expired', '已过期', 'expired', 6, 1);

-- 付款方式
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(15, 'tt', '电汇(T/T)', 'tt', 1, 1),
(15, 'lc', '信用证(L/C)', 'lc', 2, 1),
(15, 'dp', '付款交单(D/P)', 'dp', 3, 1),
(15, 'da', '承兑交单(D/A)', 'da', 4, 1),
(15, 'cash', '现金', 'cash', 5, 1);

-- 物流方式
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(16, 'express', '快递', 'express', 1, 1),
(16, 'air', '空运', 'air', 2, 1),
(16, 'sea', '海运', 'sea', 3, 1),
(16, 'land', '陆运', 'land', 4, 1),
(16, 'pickup', '自提', 'pickup', 5, 1);

-- 紧急程度
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(17, 'low', '低', 'low', 1, 1),
(17, 'normal', '普通', 'normal', 2, 1),
(17, 'high', '高', 'high', 3, 1),
(17, 'urgent', '紧急', 'urgent', 4, 1);

-- 库存状态
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
(18, 'in_stock', '在售', 'in_stock', 1, 1),
(18, 'discontinued', '停产', 'discontinued', 2, 1),
(18, 'out_of_stock', '缺货', 'out_of_stock', 3, 1);