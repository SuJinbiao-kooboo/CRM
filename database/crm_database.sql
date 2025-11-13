-- IT分销公司CRM系统数据库设计
-- 创建数据库
CREATE DATABASE IF NOT EXISTS crm_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crm_system;

-- ================================
-- 系统管理模块
-- ================================

-- 字典主表
CREATE TABLE sys_dict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
    dict_key VARCHAR(100) NOT NULL UNIQUE COMMENT '字典Key',
    description VARCHAR(500) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态：1启用，0禁用',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '字典主表';

-- 字典子表
CREATE TABLE sys_dict_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    dict_id BIGINT NOT NULL COMMENT '字典主表ID',
    item_key VARCHAR(100) NOT NULL COMMENT '字典项Key',
    item_label VARCHAR(200) NOT NULL COMMENT '字典项标签',
    item_value VARCHAR(200) COMMENT '字典项值',
    sort_order INT DEFAULT 0 COMMENT '排序',
    remark VARCHAR(500) COMMENT '备注',
    status TINYINT DEFAULT 1 COMMENT '状态：1启用，0禁用',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (dict_id) REFERENCES sys_dict(id) ON DELETE CASCADE
) COMMENT '字典子表';

-- 部门表
CREATE TABLE sys_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '部门ID',
    parent_id BIGINT DEFAULT 0 COMMENT '父部门ID',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    dept_code VARCHAR(50) COMMENT '部门编码',
    leader VARCHAR(50) COMMENT '负责人',
    phone VARCHAR(20) COMMENT '联系电话',
    email VARCHAR(100) COMMENT '邮箱',
    sort_order INT DEFAULT 0 COMMENT '显示顺序',
    status TINYINT DEFAULT 1 COMMENT '状态：1正常，0停用',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '部门表';

-- 角色表
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    role_key VARCHAR(100) NOT NULL UNIQUE COMMENT '角色权限字符串',
    role_sort INT DEFAULT 0 COMMENT '显示顺序',
    data_scope TINYINT DEFAULT 1 COMMENT '数据范围：1全部数据权限，2自定数据权限，3本部门数据权限，4本部门及以下数据权限，5仅本人数据权限',
    status TINYINT DEFAULT 1 COMMENT '状态：1正常，0停用',
    remark VARCHAR(500) COMMENT '备注',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '角色信息表';

-- 用户表
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    dept_id BIGINT COMMENT '部门ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户账号',
    nickname VARCHAR(50) COMMENT '用户昵称',
    email VARCHAR(100) COMMENT '用户邮箱',
    phone VARCHAR(20) COMMENT '手机号码',
    sex TINYINT COMMENT '用户性别：0男，1女，2未知',
    avatar VARCHAR(500) COMMENT '头像地址',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    status TINYINT DEFAULT 1 COMMENT '帐号状态：1正常，0停用',
    login_ip VARCHAR(128) COMMENT '最后登录IP',
    login_date DATETIME COMMENT '最后登录时间',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (dept_id) REFERENCES sys_dept(id)
) COMMENT '用户信息表';

-- 用户和角色关联表
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) COMMENT '用户和角色关联表';

-- 菜单权限表
CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜单ID',
    menu_name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
    order_num INT DEFAULT 0 COMMENT '显示顺序',
    path VARCHAR(200) COMMENT '路由地址',
    component VARCHAR(255) COMMENT '组件路径',
    query VARCHAR(255) COMMENT '路由参数',
    is_frame TINYINT DEFAULT 1 COMMENT '是否为外链：0是，1否',
    is_cache TINYINT DEFAULT 0 COMMENT '是否缓存：0缓存，1不缓存',
    menu_type CHAR(1) COMMENT '菜单类型：M目录，C菜单，F按钮',
    visible TINYINT DEFAULT 1 COMMENT '菜单状态：1显示，0隐藏',
    status TINYINT DEFAULT 1 COMMENT '菜单状态：1正常，0停用',
    perms VARCHAR(100) COMMENT '权限标识',
    icon VARCHAR(100) COMMENT '菜单图标',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注'
) COMMENT '菜单权限表';

-- 角色和菜单关联表
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id),
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_id) REFERENCES sys_menu(id) ON DELETE CASCADE
) COMMENT '角色和菜单关联表';

-- 操作日志表
CREATE TABLE sys_oper_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志主键',
    title VARCHAR(50) COMMENT '模块标题',
    business_type INT DEFAULT 0 COMMENT '业务类型：0其它，1新增，2修改，3删除',
    method VARCHAR(100) COMMENT '方法名称',
    request_method VARCHAR(10) COMMENT '请求方式',
    operator_type INT DEFAULT 0 COMMENT '操作类别：0其它，1后台用户，2手机端用户',
    oper_name VARCHAR(50) COMMENT '操作人员',
    dept_name VARCHAR(50) COMMENT '部门名称',
    oper_url VARCHAR(255) COMMENT '请求URL',
    oper_ip VARCHAR(128) COMMENT '主机地址',
    oper_location VARCHAR(255) COMMENT '操作地点',
    oper_param VARCHAR(2000) COMMENT '请求参数',
    json_result VARCHAR(2000) COMMENT '返回参数',
    status INT DEFAULT 0 COMMENT '操作状态：0正常，1异常',
    error_msg VARCHAR(2000) COMMENT '错误消息',
    oper_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) COMMENT '操作日志记录';

-- ================================
-- 供应商管理模块
-- ================================

-- 供应商表
CREATE TABLE crm_supplier (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '供应商ID',
    supplier_code VARCHAR(50) NOT NULL UNIQUE COMMENT '供应商编号',
    supplier_name VARCHAR(200) NOT NULL COMMENT '供应商名称',
    supplier_short_name VARCHAR(100) COMMENT '供应商简称',
    supplier_type VARCHAR(50) COMMENT '供应商类型',
    country VARCHAR(100) COMMENT '所属国家',
    website VARCHAR(200) COMMENT '官网地址',
    main_products TEXT COMMENT '主营产品',
    cooperation_level VARCHAR(20) COMMENT '合作等级：A/B/C',
    credit_rating VARCHAR(20) COMMENT '信用评级',
    payment_terms VARCHAR(200) COMMENT '付款条件',
    business_license VARCHAR(100) COMMENT '营业执照号',
    tax_number VARCHAR(100) COMMENT '税号',
    bank_name VARCHAR(200) COMMENT '开户银行',
    bank_account VARCHAR(100) COMMENT '银行账号',
    address TEXT COMMENT '地址',
    introduction TEXT COMMENT '介绍信息',
    tags VARCHAR(500) COMMENT '标签',
    status TINYINT DEFAULT 1 COMMENT '状态：1正常，0停用',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注'
) COMMENT '供应商表';

-- 供应商联系人表
CREATE TABLE crm_supplier_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '联系人ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    contact_name VARCHAR(100) NOT NULL COMMENT '联系人姓名',
    position VARCHAR(100) COMMENT '职位',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    whatsapp VARCHAR(50) COMMENT 'WhatsApp',
    wechat VARCHAR(50) COMMENT '微信',
    qq VARCHAR(20) COMMENT 'QQ',
    other_contact VARCHAR(200) COMMENT '其他联系方式',
    is_primary TINYINT DEFAULT 0 COMMENT '是否主要联系人：1是，0否',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (supplier_id) REFERENCES crm_supplier(id) ON DELETE CASCADE
) COMMENT '供应商联系人表';

-- ================================
-- 线索管理模块
-- ================================

-- 线索表
CREATE TABLE crm_lead (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '线索ID',
    lead_code VARCHAR(50) NOT NULL UNIQUE COMMENT '线索编号',
    lead_name VARCHAR(200) NOT NULL COMMENT '线索名称',
    country VARCHAR(100) COMMENT '所属国家',
    lead_type VARCHAR(50) COMMENT '线索类型',
    website VARCHAR(200) COMMENT '官网地址',
    lead_source VARCHAR(100) COMMENT '线索来源',
    lead_status VARCHAR(50) COMMENT '线索状态',
    intended_products TEXT COMMENT '意向产品',
    estimated_amount DECIMAL(15,2) COMMENT '预估订单金额',
    introduction TEXT COMMENT '介绍信息',
    input_by BIGINT COMMENT '录入人',
    follow_by BIGINT COMMENT '跟进人',
    next_follow_time DATETIME COMMENT '下次跟进时间',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (input_by) REFERENCES sys_user(id),
    FOREIGN KEY (follow_by) REFERENCES sys_user(id)
) COMMENT '线索表';

-- 线索联系人表
CREATE TABLE crm_lead_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '联系人ID',
    lead_id BIGINT NOT NULL COMMENT '线索ID',
    contact_name VARCHAR(100) NOT NULL COMMENT '联系人姓名',
    position VARCHAR(100) COMMENT '职位',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    whatsapp VARCHAR(50) COMMENT 'WhatsApp',
    wechat VARCHAR(50) COMMENT '微信',
    qq VARCHAR(20) COMMENT 'QQ',
    other_contact VARCHAR(200) COMMENT '其他联系方式',
    is_primary TINYINT DEFAULT 0 COMMENT '是否主要联系人：1是，0否',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (lead_id) REFERENCES crm_lead(id) ON DELETE CASCADE
) COMMENT '线索联系人表';

-- 线索跟进记录表
CREATE TABLE crm_lead_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '跟进记录ID',
    lead_id BIGINT NOT NULL COMMENT '线索ID',
    follow_time DATETIME NOT NULL COMMENT '跟进时间',
    follow_by BIGINT NOT NULL COMMENT '跟进人',
    follow_method VARCHAR(50) COMMENT '跟进方式：电话/邮件/面访/在线',
    follow_content TEXT COMMENT '跟进内容',
    follow_result VARCHAR(200) COMMENT '跟进结论',
    next_plan TEXT COMMENT '下次跟进计划',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (lead_id) REFERENCES crm_lead(id) ON DELETE CASCADE,
    FOREIGN KEY (follow_by) REFERENCES sys_user(id)
) COMMENT '线索跟进记录表';

-- 线索标签表
CREATE TABLE crm_lead_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    lead_id BIGINT NOT NULL COMMENT '线索ID',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名称',
    tag_color VARCHAR(20) COMMENT '标签颜色',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (lead_id) REFERENCES crm_lead(id) ON DELETE CASCADE
) COMMENT '线索标签表';

-- ================================
-- 客户管理模块
-- ================================

-- 客户表
CREATE TABLE crm_customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '客户ID',
    customer_code VARCHAR(50) NOT NULL UNIQUE COMMENT '客户编号',
    customer_name VARCHAR(200) NOT NULL COMMENT '客户名称',
    customer_short_name VARCHAR(100) COMMENT '客户简称',
    country VARCHAR(100) COMMENT '所属国家/地区',
    customer_type VARCHAR(50) COMMENT '客户类型',
    website VARCHAR(200) COMMENT '官网地址',
    main_business VARCHAR(500) COMMENT '主营业务',
    company_size VARCHAR(50) COMMENT '公司规模',
    customer_level VARCHAR(10) COMMENT '客户等级：A/B/C',
    customer_status VARCHAR(50) COMMENT '客户状态',
    customer_source VARCHAR(100) COMMENT '客户来源',
    intended_products TEXT COMMENT '意向产品',
    credit_limit DECIMAL(15,2) COMMENT '信用额度',
    introduction TEXT COMMENT '介绍信息',
    input_by BIGINT COMMENT '录入人',
    follow_by BIGINT COMMENT '跟进人',
    next_follow_time DATETIME COMMENT '下次跟进时间',
    lead_id BIGINT COMMENT '来源线索ID',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (input_by) REFERENCES sys_user(id),
    FOREIGN KEY (follow_by) REFERENCES sys_user(id),
    FOREIGN KEY (lead_id) REFERENCES crm_lead(id)
) COMMENT '客户表';

-- 客户联系人表
CREATE TABLE crm_customer_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '联系人ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    contact_name VARCHAR(100) NOT NULL COMMENT '联系人姓名',
    position VARCHAR(100) COMMENT '职位',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    whatsapp VARCHAR(50) COMMENT 'WhatsApp',
    wechat VARCHAR(50) COMMENT '微信',
    qq VARCHAR(20) COMMENT 'QQ',
    other_contact VARCHAR(200) COMMENT '其他联系方式',
    is_primary TINYINT DEFAULT 0 COMMENT '是否主要联系人：1是，0否',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (customer_id) REFERENCES crm_customer(id) ON DELETE CASCADE
) COMMENT '客户联系人表';

-- 客户跟进记录表
CREATE TABLE crm_customer_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '跟进记录ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    follow_time DATETIME NOT NULL COMMENT '跟进时间',
    follow_by BIGINT NOT NULL COMMENT '跟进人',
    follow_method VARCHAR(50) COMMENT '跟进方式：电话/邮件/面访/在线',
    follow_content TEXT COMMENT '跟进内容',
    follow_result VARCHAR(200) COMMENT '跟进结论',
    next_plan TEXT COMMENT '下次跟进计划',
    business_negotiation TEXT COMMENT '商务洽谈记录',
    customer_evaluation TEXT COMMENT '客户评价',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (customer_id) REFERENCES crm_customer(id) ON DELETE CASCADE,
    FOREIGN KEY (follow_by) REFERENCES sys_user(id)
) COMMENT '客户跟进记录表';

-- 客户标签表
CREATE TABLE crm_customer_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名称',
    tag_color VARCHAR(20) COMMENT '标签颜色',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (customer_id) REFERENCES crm_customer(id) ON DELETE CASCADE
) COMMENT '客户标签表';

-- ================================
-- 订单管理模块
-- ================================

-- 订单表
CREATE TABLE crm_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    order_code VARCHAR(50) NOT NULL UNIQUE COMMENT '订单编号',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    supplier_id BIGINT COMMENT '供应商ID',
    order_date DATE NOT NULL COMMENT '订单日期',
    supplier_ship_date DATE COMMENT '供应商发货日期',
    supplier_arrival_date DATE COMMENT '供应商到货日期',
    our_ship_date DATE COMMENT '我方发货日期',
    customer_receive_date DATE COMMENT '客户收货日期',
    delivery_date DATE COMMENT '交付日期',
    order_status VARCHAR(50) COMMENT '订单状态',
    order_type VARCHAR(50) COMMENT '订单类型',
    urgency_level VARCHAR(20) COMMENT '紧急程度',
    salesman BIGINT COMMENT '业务员',
    total_amount DECIMAL(15,2) COMMENT '订单总金额',
    profit DECIMAL(15,2) COMMENT '利润',
    profit_rate DECIMAL(5,2) COMMENT '利润率',
    payment_method VARCHAR(100) COMMENT '付款方式',
    logistics_method VARCHAR(100) COMMENT '物流方式',
    delivery_address TEXT COMMENT '收货地址',
    special_requirements TEXT COMMENT '特殊要求',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (customer_id) REFERENCES crm_customer(id),
    FOREIGN KEY (supplier_id) REFERENCES crm_supplier(id),
    FOREIGN KEY (salesman) REFERENCES sys_user(id)
) COMMENT '订单表';

-- 订单明细表
CREATE TABLE crm_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_code VARCHAR(100) COMMENT '产品料号',
    product_name VARCHAR(200) NOT NULL COMMENT '产品名称',
    specification VARCHAR(500) COMMENT '规格型号',
    brand VARCHAR(100) COMMENT '品牌',
    quantity INT NOT NULL COMMENT '数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    total_price DECIMAL(15,2) NOT NULL COMMENT '总价',
    supplier_id BIGINT COMMENT '供应商ID',
    delivery_time VARCHAR(100) COMMENT '交期',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注信息',
    FOREIGN KEY (order_id) REFERENCES crm_order(id) ON DELETE CASCADE,
    FOREIGN KEY (supplier_id) REFERENCES crm_supplier(id)
) COMMENT '订单明细表';

-- 订单标签表
CREATE TABLE crm_order_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名称',
    tag_color VARCHAR(20) COMMENT '标签颜色',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (order_id) REFERENCES crm_order(id) ON DELETE CASCADE
) COMMENT '订单标签表';

-- ================================
-- 库存管理模块
-- ================================

-- 库存表
CREATE TABLE inv_stock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '库存ID',
    product_code VARCHAR(100) NOT NULL COMMENT '料号',
    product_name VARCHAR(200) COMMENT '产品名称',
    category VARCHAR(100) COMMENT '分类',
    brand VARCHAR(100) COMMENT '品牌',
    specification VARCHAR(500) COMMENT '规格',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    supplier_code VARCHAR(50) COMMENT '供应商编号',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    contact_person VARCHAR(100) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系方式',
    stock_quantity INT DEFAULT 0 COMMENT '库存数量',
    available_quantity INT DEFAULT 0 COMMENT '可用数量',
    reserved_quantity INT DEFAULT 0 COMMENT '预留数量',
    safety_stock INT DEFAULT 0 COMMENT '安全库存',
    purchase_price DECIMAL(10,2) COMMENT '采购价格',
    sale_price DECIMAL(10,2) COMMENT '销售价格',
    delivery_time VARCHAR(100) COMMENT '交期',
    shipping_location VARCHAR(200) COMMENT '发货地',
    input_date DATE COMMENT '录入日期',
    status VARCHAR(20) DEFAULT '在售' COMMENT '状态：在售/停产/缺货',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (supplier_id) REFERENCES crm_supplier(id)
) COMMENT '库存表';

-- ================================
-- 询价管理模块
-- ================================

-- 询价表
CREATE TABLE crm_inquiry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '询价ID',
    inquiry_code VARCHAR(50) NOT NULL UNIQUE COMMENT '询价编号',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    inquiry_date DATE NOT NULL COMMENT '询价日期',
    valid_until DATE COMMENT '有效期',
    inquirer_contact_id BIGINT COMMENT '询价人（客户联系人ID）',
    follow_by BIGINT COMMENT '跟进人',
    inquiry_status VARCHAR(50) COMMENT '询价状态',
    urgency_level VARCHAR(20) COMMENT '紧急程度',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (customer_id) REFERENCES crm_customer(id),
    FOREIGN KEY (inquirer_contact_id) REFERENCES crm_customer_contact(id),
    FOREIGN KEY (follow_by) REFERENCES sys_user(id)
) COMMENT '询价表';

-- 询价明细表
CREATE TABLE crm_inquiry_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '询价明细ID',
    inquiry_id BIGINT NOT NULL COMMENT '询价ID',
    product_code VARCHAR(100) COMMENT '产品料号',
    product_name VARCHAR(200) NOT NULL COMMENT '产品名称',
    specification VARCHAR(500) COMMENT '规格',
    brand VARCHAR(100) COMMENT '品牌',
    quantity INT NOT NULL COMMENT '数量',
    target_price DECIMAL(10,2) COMMENT '目标价格',
    delivery_requirement VARCHAR(100) COMMENT '交期要求',
    quality_requirement TEXT COMMENT '质量要求',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (inquiry_id) REFERENCES crm_inquiry(id) ON DELETE CASCADE
) COMMENT '询价明细表';

-- 报价表
CREATE TABLE crm_quotation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报价ID',
    inquiry_id BIGINT NOT NULL COMMENT '询价ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    quotation_date DATE NOT NULL COMMENT '报价日期',
    valid_until DATE COMMENT '报价有效期',
    total_amount DECIMAL(15,2) COMMENT '报价总金额',
    quotation_status VARCHAR(50) COMMENT '报价状态',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (inquiry_id) REFERENCES crm_inquiry(id) ON DELETE CASCADE,
    FOREIGN KEY (supplier_id) REFERENCES crm_supplier(id)
) COMMENT '报价表';

-- 报价明细表
CREATE TABLE crm_quotation_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报价明细ID',
    quotation_id BIGINT NOT NULL COMMENT '报价ID',
    inquiry_item_id BIGINT NOT NULL COMMENT '询价明细ID',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    total_price DECIMAL(15,2) NOT NULL COMMENT '总价',
    delivery_time VARCHAR(100) COMMENT '交期',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark VARCHAR(500) COMMENT '备注',
    FOREIGN KEY (quotation_id) REFERENCES crm_quotation(id) ON DELETE CASCADE,
    FOREIGN KEY (inquiry_item_id) REFERENCES crm_inquiry_item(id)
) COMMENT '报价明细表';

-- 询价标签表
CREATE TABLE crm_inquiry_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
    inquiry_id BIGINT NOT NULL COMMENT '询价ID',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名称',
    tag_color VARCHAR(20) COMMENT '标签颜色',
    create_by BIGINT COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (inquiry_id) REFERENCES crm_inquiry(id) ON DELETE CASCADE
) COMMENT '询价标签表';

-- ================================
-- 初始化数据
-- ================================

-- 插入默认部门
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, leader, status) VALUES
(1, 0, '总公司', 'HEAD_OFFICE', 'admin', 1),
(2, 1, '销售部', 'SALES_DEPT', 'sales_manager', 1),
(3, 1, '采购部', 'PURCHASE_DEPT', 'purchase_manager', 1),
(4, 1, '技术部', 'TECH_DEPT', 'tech_manager', 1);

-- 插入默认角色
INSERT INTO sys_role (id, role_name, role_key, role_sort, data_scope, status, remark) VALUES
(1, '超级管理员', 'admin', 1, 1, 1, '超级管理员'),
(2, '销售经理', 'sales_manager', 2, 2, 1, '销售经理'),
(3, '销售员', 'salesman', 3, 5, 1, '销售员'),
(4, '采购员', 'purchaser', 4, 3, 1, '采购员');

-- 插入默认用户（密码为123456的MD5值）
INSERT INTO sys_user (id, dept_id, username, nickname, email, phone, password, status) VALUES
(1, 1, 'admin', '系统管理员', 'admin@example.com', '13800138000', 'e10adc3949ba59abbe56e057f20f883e', 1),
(2, 2, 'sales_manager', '销售经理', 'sales@example.com', '13800138001', 'e10adc3949ba59abbe56e057f20f883e', 1),
(3, 2, 'salesman1', '销售员1', 'salesman1@example.com', '13800138002', 'e10adc3949ba59abbe56e057f20f883e', 1),
(4, 3, 'purchaser1', '采购员1', 'purchaser1@example.com', '13800138003', 'e10adc3949ba59abbe56e057f20f883e', 1);

-- 插入用户角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4);

-- 插入字典数据
INSERT INTO sys_dict (id, dict_name, dict_key, description, status) VALUES
(1, '线索类型', 'lead_type', '线索类型字典', 1),
(2, '线索状态', 'lead_status', '线索状态字典', 1),
(3, '客户类型', 'customer_type', '客户类型字典', 1),
(4, '客户状态', 'customer_status', '客户状态字典', 1),
(5, '产品分类', 'product_category', '产品分类字典', 1),
(6, '品牌', 'brand', '品牌字典', 1),
(7, '供应商类型', 'supplier_type', '供应商类型字典', 1),
(8, '订单状态', 'order_status', '订单状态字典', 1),
(9, '跟进结果', 'follow_result', '跟进结果字典', 1),
(10, '询价状态', 'inquiry_status', '询价状态字典', 1),
(11, '付款方式', 'payment_method', '付款方式字典', 1),
(12, '物流方式', 'logistics_method', '物流方式字典', 1);

-- 插入字典子项数据
INSERT INTO sys_dict_item (dict_id, item_key, item_label, item_value, sort_order, status) VALUES
-- 线索类型
(1, 'direct', '直接客户', 'direct', 1, 1),
(1, 'agent', '代理商', 'agent', 2, 1),
(1, 'distributor', '分销商', 'distributor', 3, 1),
-- 线索状态
(2, 'new', '新线索', 'new', 1, 1),
(2, 'contacted', '已联系', 'contacted', 2, 1),
(2, 'qualified', '已确认', 'qualified', 3, 1),
(2, 'converted', '已转客户', 'converted', 4, 1),
(2, 'invalid', '无效线索', 'invalid', 5, 1),
-- 客户类型
(3, 'enterprise', '企业客户', 'enterprise', 1, 1),
(3, 'government', '政府客户', 'government', 2, 1),
(3, 'individual', '个人客户', 'individual', 3, 1),
-- 客户状态
(4, 'potential', '潜在客户', 'potential', 1, 1),
(4, 'active', '活跃客户', 'active', 2, 1),
(4, 'inactive', '非活跃客户', 'inactive', 3, 1),
(4, 'lost', '流失客户', 'lost', 4, 1),
-- 产品分类
(5, 'ssd', 'SSD固态硬盘', 'ssd', 1, 1),
(5, 'hdd', 'HDD机械硬盘', 'hdd', 2, 1),
(5, 'memory', '内存', 'memory', 3, 1),
(5, 'cpu', 'CPU处理器', 'cpu', 4, 1),
(5, 'gpu', 'GPU显卡', 'gpu', 5, 1),
-- 品牌
(6, 'samsung', '三星', 'samsung', 1, 1),
(6, 'intel', '英特尔', 'intel', 2, 1),
(6, 'amd', 'AMD', 'amd', 3, 1),
(6, 'nvidia', '英伟达', 'nvidia', 4, 1),
(6, 'kingston', '金士顿', 'kingston', 5, 1),
-- 供应商类型
(7, 'manufacturer', '制造商', 'manufacturer', 1, 1),
(7, 'distributor', '分销商', 'distributor', 2, 1),
(7, 'agent', '代理商', 'agent', 3, 1),
-- 订单状态
(8, 'pending', '待确认', 'pending', 1, 1),
(8, 'confirmed', '已确认', 'confirmed', 2, 1),
(8, 'purchasing', '采购中', 'purchasing', 3, 1),
(8, 'purchased', '已采购', 'purchased', 4, 1),
(8, 'shipping', '发货中', 'shipping', 5, 1),
(8, 'shipped', '已发货', 'shipped', 6, 1),
(8, 'received', '已收货', 'received', 7, 1),
(8, 'completed', '已完成', 'completed', 8, 1),
-- 跟进结果
(9, 'interested', '有兴趣', 'interested', 1, 1),
(9, 'not_interested', '无兴趣', 'not_interested', 2, 1),
(9, 'need_follow', '需要跟进', 'need_follow', 3, 1),
(9, 'quoted', '已报价', 'quoted', 4, 1),
-- 询价状态
(10, 'pending', '未处理', 'pending', 1, 1),
(10, 'processing', '处理中', 'processing', 2, 1),
(10, 'quoted', '已报价', 'quoted', 3, 1),
(10, 'accepted', '已接受', 'accepted', 4, 1),
(10, 'rejected', '已拒绝', 'rejected', 5, 1),
(10, 'expired', '已过期', 'expired', 6, 1),
-- 付款方式
(11, 'cash', '现金', 'cash', 1, 1),
(11, 'transfer', '银行转账', 'transfer', 2, 1),
(11, 'credit', '信用证', 'credit', 3, 1),
(11, 'installment', '分期付款', 'installment', 4, 1),
-- 物流方式
(12, 'express', '快递', 'express', 1, 1),
(12, 'logistics', '物流', 'logistics', 2, 1),
(12, 'self_pickup', '自提', 'self_pickup', 3, 1),
(12, 'air_freight', '空运', 'air_freight', 4, 1),
(12, 'sea_freight', '海运', 'sea_freight', 5, 1);

-- 创建索引以提高查询性能
CREATE INDEX idx_lead_code ON crm_lead(lead_code);
CREATE INDEX idx_lead_status ON crm_lead(lead_status);
CREATE INDEX idx_lead_follow_by ON crm_lead(follow_by);
CREATE INDEX idx_customer_code ON crm_customer(customer_code);
CREATE INDEX idx_customer_status ON crm_customer(customer_status);
CREATE INDEX idx_customer_follow_by ON crm_customer(follow_by);
CREATE INDEX idx_order_code ON crm_order(order_code);
CREATE INDEX idx_order_status ON crm_order(order_status);
CREATE INDEX idx_order_customer_id ON crm_order(customer_id);
CREATE INDEX idx_stock_product_code ON inv_stock(product_code);
CREATE INDEX idx_stock_supplier_id ON inv_stock(supplier_id);
CREATE INDEX idx_inquiry_code ON crm_inquiry(inquiry_code);
CREATE INDEX idx_inquiry_status ON crm_inquiry(inquiry_status);
CREATE INDEX idx_supplier_code ON crm_supplier(supplier_code);

-- 设置自增起始值
ALTER TABLE crm_lead AUTO_INCREMENT = 1001;
ALTER TABLE crm_customer AUTO_INCREMENT = 1001;
ALTER TABLE crm_order AUTO_INCREMENT = 1001;
ALTER TABLE crm_inquiry AUTO_INCREMENT = 1001;
ALTER TABLE crm_supplier AUTO_INCREMENT = 1001;
