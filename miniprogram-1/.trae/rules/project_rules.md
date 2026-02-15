# 伴搭 · 微信小程序系统设计说明书

> 基于《伴搭-产品需求文档 v1.0》的**可开发级系统设计文档**
> 目标读者：产品经理 / 前端（小程序） / 后端 / 架构设计

---

## 一、整体架构概览

### 1.1 技术形态

* **客户端**：微信小程序
* **后端服务**：RESTful API（Java / Node / Go 均可）
* **数据库**：MySQL（主） + Redis（缓存 / 状态）
* **对象存储**：COS / OSS（打卡图片、视频、文件）
* **消息系统**：微信订阅消息 + 站内通知

### 1.2 核心模块划分

1. 用户与身份模块
2. 计划管理模块
3. 搭子关系模块
4. 打卡与内容模块
5. 提醒与通知模块
6. 激励与能量系统
7. 统计与报告模块

---

## 二、页面（界面）设计

> **V1.0 MVP 版本建议：共 11 个页面**

### 2.1 页面清单总览

| 编号  | 页面名称     | 页面路径               | 说明        |
| --- | -------- | ------------------ | --------- |
| P1  | 启动 / 引导页 | /pages/onboarding  | 新用户流程     |
| P2  | 登录页      | /pages/login       | 微信授权      |
| P3  | 首页（计划列表） | /pages/home        | 核心入口      |
| P4  | 新建计划     | /pages/plan/create | 模板 / 自定义  |
| P5  | 计划详情     | /pages/plan/detail | 打卡与进度     |
| P6  | 打卡页      | /pages/checkin     | 提交打卡      |
| P7  | 搭子关系页    | /pages/buddy       | 关系与互动     |
| P8  | 通知中心     | /pages/notice      | 系统 / 搭子消息 |
| P9  | 成就与能量    | /pages/energy      | 徽章 / 能量   |
| P10 | 周报 / 统计  | /pages/report      | 周期反馈      |
| P11 | 我的 / 设置  | /pages/profile     | 隐私 / 设置   |

---

### 2.2 页面交互关系（核心路径）

```text
启动页
  ↓
登录 / 授权
  ↓
首页（计划列表）
  ├─ 新建计划 → 邀请搭子 → 返回首页
  ├─ 计划详情 → 打卡页 → 返回计划详情
  ├─ 搭子页 → 私信 / 鼓励 / 邀请搭子
  ├─ 通知中心
  └─ 我的 / 设置
```

---

## 三、核心页面说明（重点）

### 3.1 首页（P3）

**核心功能**：

* 当前进行中的计划列表
* 连续打卡天数/次数
* 今日是否已打卡

**关键交互**：

* 点击计划 → 计划详情
* 一键打卡（快捷入口）

---

### 3.2 计划详情页（P5）

**展示内容**：

* 计划目标、周期、完成率
* 连续打卡链
* 搭子信息
* 打卡记录列表

**操作**：

* 去打卡
* 补卡（消耗能量）
* 邀请 / 更换搭子

---

### 3.3 打卡页（P6）

**支持类型**：

* 文本
* 图片 / 视频
* 语音（V1.2）

**扩展信息**：

* 心情
* 难度
* 位置（可选）

---

## 四、接口设计（API）

> **V1.0 建议接口数：约 28–32 个**

### 4.1 用户模块（5 个）

| 接口                | 方法   | 说明   |
| ----------------- | ---- | ---- |
| /api/user/login   | POST | 微信登录 |
| /api/user/profile | GET  | 用户信息 |
| /api/user/profile | PUT  | 修改信息 |
| /api/user/energy  | GET  | 能量值  |
| /api/user/setting | PUT  | 隐私设置 |

---

### 4.2 计划模块（8 个）

| 接口                   | 方法     | 说明   |
| -------------------- | ------ | ---- |
| /api/plan/list       | GET    | 计划列表 |
| /api/plan/create     | POST   | 新建计划 |
| /api/plan/detail     | GET    | 计划详情 |
| /api/plan/update     | PUT    | 修改计划 |
| /api/plan/delete     | DELETE | 删除   |
| /api/plan/progress   | GET    | 进度统计 |
| /api/plan/template   | GET    | 模板库  |
| /api/plan/visibility | PUT    | 权限设置 |

---

### 4.3 搭子模块（6 个）

| 接口                    | 方法   | 说明      |
| --------------------- | ---- | ------- |
| /api/buddy/invite     | POST | 邀请搭子    |
| /api/buddy/accept     | POST | 接受邀请    |
| /api/buddy/remove     | POST | 解绑      |
| /api/buddy/list       | GET  | 搭子列表    |
| /api/buddy/permission | PUT  | 权限调整    |
| /api/buddy/interact   | POST | 鼓励 / 点赞 |

---

### 4.4 打卡模块（6 个）

| 接口                      | 方法     | 说明   |
| ----------------------- | ------ | ---- |
| /api/checkin/create     | POST   | 提交打卡 |
| /api/checkin/list       | GET    | 打卡记录 |
| /api/checkin/supplement | POST   | 补卡   |
| /api/checkin/upload     | POST   | 文件上传 |
| /api/checkin/status     | GET    | 今日状态 |
| /api/checkin/delete     | DELETE | 删除   |

---

### 4.5 通知与统计（5 个）

* /api/notice/list
* /api/notice/read
* /api/report/weekly
* /api/report/monthly
* /api/achievement/list

---

## 五、数据库表设计

> **V1.0 核心表：10 张**

### 5.1 表关系总览

```text
user
 ├─ plan
 │   ├─ checkin
 │   └─ plan_buddy
 ├─ buddy_relation
 ├─ energy_log
 ├─ achievement
 └─ notification
```

---

### 5.2 表结构示例（关键表）

#### user（用户表）

| 字段         | 类型       | 说明        |
| ---------- | -------- | --------- |
| id         | bigint   | 主键        |
| openid     | varchar  | 微信 openid |
| nickname   | varchar  | 昵称        |
| avatar     | varchar  | 头像        |
| created_at | datetime | 创建时间      |

---

#### plan（计划表）

| 字段         | 类型       | 说明 |
| ---------- | -------- | -- |
| id         | bigint   |    |
| user_id    | bigint   |    |
| title      | varchar  |    |
| type       | varchar  |    |
| frequency  | varchar  |    |
| start_date | date     |    |
| end_date   | date     |    |
| visibility | varchar  |    |
| created_at | datetime |    |

---

#### checkin（打卡表）

| 字段            | 类型      | 说明 |
| ------------- | ------- | -- |
| id            | bigint  |    |
| plan_id       | bigint  |    |
| user_id       | bigint  |    |
| content       | text    |    |
| media_url     | varchar |    |
| mood          | varchar |    |
| difficulty    | int     |    |
| is_supplement | tinyint |    |
| checkin_date  | date    |    |

---

#### buddy_relation（搭子关系）

| 字段         | 类型       | 说明 |
| ---------- | -------- | -- |
| id         | bigint   |    |
| user_id    | bigint   |    |
| buddy_id   | bigint   |    |
| permission | varchar  |    |
| status     | varchar  |    |
| created_at | datetime |    |

---

### 5.3 其他表（说明）

* plan_buddy（计划-搭子绑定）
* energy_log（能量流水）
* achievement（成就）
* user_achievement（用户成就）
* notification（通知）

---

## 六、状态与规则设计（关键）

### 6.1 打卡状态机

```text
未打卡 → 已打卡
    └→ 补卡（标记）
```

### 6.2 三级提醒规则

* T+1：提醒用户
* T+2：询问是否通知搭子
* 用户确认 → 通知搭子

---

## 七、V1.0 MVP 边界说明

**不做**：

* 社区内容流
* 群聊
* 金钱惩罚
* 复杂排行榜

**只做**：

* 单搭子
* 单计划核心闭环
* 温和监督

---

## 八、交付清单（你现在已经拥有）

* ✅ 产品 PRD
* ✅ 页面与交互设计
* ✅ 接口清单
* ✅ 数据库设计
* ✅ MVP 边界定义

> 这份文档可以直接作为 **研发启动文档**。
