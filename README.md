# IT分销公司CRM系统

## 项目概述

这是一个专为IT分销公司设计的客户关系管理系统，采用前后端分离架构，支持线索管理、客户管理、订单管理、库存管理、询价管理、供应商管理等核心业务功能。

## 技术架构

### 后端技术栈
- **Java 8**: 核心开发语言
- **SpringBoot 2.x**: 应用框架
- **MyBatis-Plus**: ORM框架
- **MySQL 8.0**: 数据库
- **Lombok**: 代码简化工具

### 前端技术栈
- **Vue 2.x**: 前端框架
- **ElementUI**: UI组件库
- **Vuex**: 状态管理
- **Vue Router**: 路由管理
- **Axios**: HTTP客户端

## 项目结构

```
CRM/
├── Back-Server/                    # 后端项目
│   ├── src/main/java/com/crm/
│   │   ├── common/                 # 通用模块
│   │   │   ├── core/              # 核心类
│   │   │   ├── constant/          # 常量定义
│   │   │   └── utils/             # 工具类
│   │   ├── framework/             # 框架配置
│   │   │   └── config/            # 配置类
│   │   ├── system/                # 系统管理模块
│   │   │   ├── domain/            # 实体类
│   │   │   ├── mapper/            # 数据访问层
│   │   │   ├── service/           # 业务逻辑层
│   │   │   └── controller/        # 控制器层
│   │   └── CrmSystemApplication.java  # 启动类
│   ├── src/main/resources/
│   │   └── application.yml        # 配置文件
│   └── pom.xml                    # Maven配置
├── Front-Server/                   # 前端项目
│   ├── src/
│   │   ├── api/                   # API接口
│   │   ├── components/            # 通用组件
│   │   ├── layout/                # 布局组件
│   │   ├── plugins/               # 插件
│   │   ├── router/                # 路由配置
│   │   ├── store/                 # 状态管理
│   │   ├── styles/                # 样式文件
│   │   ├── utils/                 # 工具类
│   │   ├── views/                 # 页面组件
│   │   ├── App.vue                # 根组件
│   │   ├── main.js                # 入口文件
│   │   ├── permission.js          # 权限控制
│   │   └── settings.js            # 系统设置
│   └── package.json               # 依赖配置
├── database/                       # 数据库文件
│   ├── crm_database.sql           # 数据库结构
│   └── init_data.sql              # 初始化数据
├── requirement/                    # 需求文档
│   ├── 需求描述                    # 原始需求
│   └── 完善需求描述.md             # 完善后需求
└── README.md                      # 项目说明
```

## 功能模块

### 1. 系统管理
- **字典管理**: 系统字典类型和字典数据管理
- **用户管理**: 用户信息、角色分配、部门归属
- **权限管理**: 角色权限配置、菜单权限、数据权限

### 2. 线索管理
- **线索信息**: 线索基础信息、联系人信息管理
- **线索跟进**: 跟进记录、跟进提醒、跟进统计
- **线索转化**: 线索转客户功能

### 3. 客户管理
- **客户信息**: 客户基础信息、联系人管理
- **客户跟进**: 客户跟进记录、商务洽谈记录
- **客户分析**: 客户价值分析、订单关联查看

### 4. 订单管理
- **订单信息**: 订单头信息、订单明细管理
- **订单流程**: 订单状态流转、审批流程
- **财务管理**: 应收应付管理、利润分析

### 5. 库存管理
- **库存信息**: 产品库存信息、供应商信息
- **库存操作**: 库存导入导出、库存同步
- **库存预警**: 低库存预警、价格监控

### 6. 询价管理
- **询价信息**: 客户询价信息、产品明细
- **报价管理**: 供应商询价、报价比较、客户报价
- **询价分析**: 询价统计、产品热度分析

### 7. 供应商管理
- **供应商信息**: 供应商基础信息、联系人管理
- **合作管理**: 合作协议、采购记录、供应商评价

### 8. 报表统计
- **业务报表**: 销售报表、采购报表、库存报表
- **管理报表**: 业务员绩效、客户分析、产品分析

## 数据库设计

### 主要数据表
- **sys_dict**: 系统字典表
- **sys_dict_item**: 字典项表
- **sys_user**: 用户表
- **sys_role**: 角色表
- **sys_dept**: 部门表
- **crm_lead**: 线索表
- **crm_customer**: 客户表
- **crm_order**: 订单表
- **inv_stock**: 库存表
- **crm_inquiry**: 询价表
- **crm_supplier**: 供应商表

## 安装部署

### 环境要求
- **JDK 8+**
- **Node.js 12+**
- **MySQL 8.0+**
- **Maven 3.6+**

### 后端部署

1. **数据库初始化**
```sql
-- 创建数据库
CREATE DATABASE crm_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入数据库结构
source database/crm_database.sql;

-- 导入初始化数据
source database/init_data.sql;
```

2. **配置文件修改**
```yaml
# 修改 Back-Server/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/crm_system?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
    username: your_username
    password: your_password
```

3. **启动后端服务**
```bash
cd Back-Server
mvn clean install
mvn spring-boot:run
```

### 前端部署

1. **安装依赖**
```bash
cd Front-Server
npm install
```

2. **开发环境启动**
```bash
npm run dev
```

3. **生产环境构建**
```bash
npm run build
```

## 系统特点

### 1. 业务特色
- **无库存模式**: 专为IT分销公司设计，支持无库存业务模式
- **询价匹配**: 支持客户需求与供应商报价的智能匹配
- **全流程管理**: 从线索到成交的完整业务流程管理

### 2. 技术特色
- **前后端分离**: 清晰的架构设计，便于维护和扩展
- **模块化设计**: 功能模块独立，便于团队协作开发
- **响应式设计**: 支持多种设备访问
- **权限控制**: 完善的用户权限管理体系

### 3. 用户体验
- **操作简便**: 直观的用户界面，简化操作流程
- **数据可视**: 丰富的报表统计功能
- **移动友好**: 响应式设计，支持移动端访问

## 开发规范

### 后端开发规范
- 使用RESTful API设计风格
- 统一的响应格式和异常处理
- 完善的参数校验和日志记录
- 遵循阿里巴巴Java开发手册

### 前端开发规范
- 组件化开发，提高代码复用性
- 统一的代码风格和命名规范
- 完善的错误处理和用户提示
- 遵循Vue.js最佳实践

## 系统截图

### 登录页面
- 用户登录界面，支持记住密码功能

### 系统首页
- 数据概览仪表板，展示关键业务指标

### 线索管理
- 线索列表、线索详情、线索跟进功能

### 客户管理
- 客户信息管理、客户跟进记录

### 订单管理
- 订单列表、订单详情、订单状态管理

## 后续规划

### 第一期优化
- 完善移动端适配
- 增加更多报表功能
- 优化系统性能

### 第二期扩展
- 集成第三方系统
- 增加工作流引擎
- 支持多语言

### 第三期升级
- 微服务架构改造
- 大数据分析功能
- AI智能推荐

## 技术支持

如有技术问题，请联系开发团队或查看相关文档。

## 版本信息

- **当前版本**: v1.0.0
- **发布日期**: 2024年1月
- **更新日志**: 详见CHANGELOG.md

---

**注意**: 本系统专为IT分销公司业务场景设计，如需定制化开发，请联系开发团队。