这个系统是使用ruoyi-vue框架开发的,这个是一个很有名的开源框架，你知道吧，我想用它来实现我的业务功能，包括：
客户供应商管理列表模块:
    1. 客户供应商管理有主表 crm_supplier=供应商主表 和 供应商联系人子表 crm_supplier_contact=供应商联系人子表，和供应商的建档资料附件 crm_supplier_attachment=供应商建档资料附件
    2. 这个客户供应商管理列表我希望是供应商和供应商联系人子表join展示的，但是新增和编辑的时候是一起编辑的，即新增和编辑供应商的弹窗可以同时查看、新增和编辑供应商联系人子表的记录。
    3. 同时供应商的附件也是可以同时伴随着供应商的新增和编辑进行管理的
    4. 需要能够选择行进行导出
    5. 需要能够筛选供应商名称，类型，能够支持高级查询

Offer管理模块：
    1. Offer 管理模块需要提供新增、编辑、删除、查询功能。
    2. 新增和编辑 Offer 时，必填的只有供应商和产品料号，其他信息都是可选的，库存日期默认当天
    3. 查询功能需要支持根据 Offer 产品料号，供应商信息等条件进行筛选。
    4. 需要能够选择行进行导出
    5. 能够进行导入，导入的话因为导入的文件是没有模板的，所以需要设计成：
        5.1 弹出框必填项：供应商和Inq/Offer类型字段，供应商从客户供应商列表下拉选择，支持按照名称模糊搜索
        5.2 其他项都是可选的，库存日期默认当天
        5.3 另外的产品编码（必填），产品详情，单价-成本（导入时候转换数字，转换失败留空不要0），单价-报价字段默认=单价-成本字段*（1.02），数量（导入时候转换数字，转换失败留空不要0），交货时间（文本），备注（文本） 都是支持选择该字段属于导入的Excel的哪一个列（A，B，C,D,E...列）
        5.4 支持选中行，全选行批量编辑除开产品编码意外的所有字段，若是填0和不填则不更新，若是填了其他值则更新对应字段
        5.5 支持选中行或者全选行导出

create table crm_supplier
(
    id                  bigint auto_increment comment '供应商ID'
        primary key,
    supplier_code       varchar(50)                        null comment '供应商编号',
    supplier_name       varchar(200)                       not null comment '供应商名称',
    supplier_short_name varchar(100)                       null comment '供应商简称',
    supplier_type       varchar(50)                        null comment '供应商类型',
    country             varchar(100)                       null comment '所属国家',
    address             varchar(1024)                               null comment '地址',
    website             varchar(200)                       null comment '官网地址',
    main_products       varchar(256)                       null comment '主营产品,多個使用逗号拼接',
    cooperation_level   varchar(20)                        null comment '合作等级：S/A/B/C/D/E',
    risk_level       varchar(20)                        null comment '风险等级',
    payment_terms       varchar(200)                       null comment '付款条件',
    business_license    varchar(100)                       null comment '营业执照号',
    tax_number          varchar(100)                       null comment '税号',
    bank_info           varchar(200)                       null comment '银行信息',
    bank_account        varchar(100)                       null comment '银行账号',
	
    introduction        varchar(1024)                               null comment '介绍信息',
    remark        varchar(1024)                               null comment '备注1',
    remark_second        varchar(1024)                               null comment '备注2',
    status              tinyint  default 1                 null comment '状态：1正常，0停用',
    create_by           bigint                             null comment '创建人',
    create_time         datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_by           bigint                             null comment '更新人',
    update_time         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    remark              varchar(500)                       null comment '备注',
    tags_first                varchar(128)                       null comment '标签1',
    tags_second                varchar(128)                       null comment '标签2',
    tags_third                varchar(128)                       null comment '标签3',
    tags_si                varchar(128)                       null comment '标签4',
    constraint supplier_code
        unique (supplier_code)
)
    comment '客戶供应商表';


create table crm_supplier_contact
(
    id            bigint auto_increment comment '联系人ID'  primary key,
    supplier_code   bigint                             not null comment '供应商code',
    contact_name  varchar(100)                       not null comment '联系人姓名',
    post  varchar(100)                       not null comment '岗位',
    position      varchar(100)                       null comment '职位',
    phone         varchar(20)                        null comment '手机号',
    email         varchar(100)                       null comment '邮箱',
    whatsapp      varchar(50)                        null comment 'WhatsApp',
    wechat        varchar(50)                        null comment '微信',
    qq            varchar(20)                        null comment 'QQ',
    other_contact_first varchar(200)                       null comment '其他联系方式1',
    other_contact_second varchar(200)                       null comment '其他联系方式2',
    remark_first varchar(200)                       null comment '备注1',
    remark_second varchar(200)                       null comment '备注2',
    is_primary    tinyint  default 0                 null comment '是否主要联系人：1是，0否',
    create_by     bigint                             null comment '创建人',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_by     bigint                             null comment '更新人',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint crm_supplier_contact_ibfk_1
        foreign key (supplier_id) references crm_supplier (id)
            on delete cascade
) comment '供应商联系人表';




create table crm_attachment
(
    id            bigint auto_increment comment '联系人ID'  primary key,
    supplier_code   bigint                             not null comment '来源类型：客户供应商=supplier/订单=order/询报价=offer',
    from_code   bigint                             not null comment '来源code',
    file_name  varchar(256)                       not null comment '附件名称',
    file_url  varchar(1024)                       not null comment '附件地址',
    remark  varchar(2048)                       not null comment '备注',
    create_by     bigint                             null comment '创建人',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_by     bigint                             null comment '更新人',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint crm_supplier_contact_ibfk_1
        foreign key (supplier_id) references crm_supplier (id)
            on delete cascade
) comment 'CRM_系统上传文件，用于存放系统其他模块上传的附件';





create table crm_offer
(
    id                  bigint auto_increment comment '主键ID'
        primary key,
    product_code        varchar(64)                        not null comment '产品编码',
    product_brand        varchar(64)                        not null comment '产品品牌',
    stock_date          datetime                               null comment '库存日期',
    product_detail      varchar(512)                       null comment '产品详情',
    price_cost               decimal(18, 2)                     null comment '单价-成本',
    price_offer               decimal(18, 2)                     null comment '单价-报价',
    price_unit               varchar                     default 'USD' comment '价格单位 默认USD',
    quantity            int                                null comment '数量',
    delivery_time       varchar(64)                        null comment '交货时间',
    remark              varchar(512)                       null comment '备注',
    sheet_name          varchar(512)                       null comment '来源表名/Sheet名',
    supplier_code            varchar(128)                       null comment '客户供应商编码',
    supplier_name            varchar(128)                       null comment '客户供应商名称',
    tags_first                varchar(255)                       null comment '标签1',
    tags_second                varchar(255)                       null comment '标签2',
    tags_third                varchar(255)                       null comment '标签3',
    tags_si                varchar(255)                       null comment '标签4',
    status              tinyint  default 1                 null comment '状态 0-无效 1-有效',
    create_time         datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    create_by           varchar(64)                        null comment '创建人',
    update_by           varchar(64)                        null comment '更新人',
    product_type        varchar(100)                       null comment '产品类型,eg: CPU,GPU,RAD,MEMORY,HDD,SSD,MOTHER_CARD',
    product_detail_code varchar(100)                       null comment '产品明细编号',
    inq_offer_type      varchar(100)                       null comment 'Inq/Offer类型:Inq,Offer'
)  comment 'Offer表';

