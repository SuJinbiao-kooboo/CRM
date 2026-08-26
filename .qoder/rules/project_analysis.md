# 项目结构与规范分析文档

> 生成时间：2026-08-08
> 基于当前代码库实际代码分析，用于指导后续开发（新增模块、接口、实体时遵循本文档约定）。

## 一、项目概览

- 框架：若依 RuoYi-Vue **3.9.0**（前后端分离），在原生框架基础上扩展了 **CRM（询报价/邮件）**、**Buddy（计划打卡小程序）**、**微信小程序** 三大业务。
- 后端：Java 1.8 + Spring Boot 2.5.15，多 Maven 模块工程，入口模块 `ruoyi-admin`（端口 8081）。
- 前端：Vue 2.6.12 + Element UI 2.15.14 + Vuex 3.6 + Vue Router 3.4.9 + Axios 0.28.1 + ECharts 5.4.0 + Sass。
- 小程序：`miniprogram-1`（微信原生 + TypeScript），对应后端 Buddy 模块 `do_*` 表。

## 二、Maven 模块结构

| 模块 | 职责 |
|------|------|
| ruoyi-admin | Web 启动入口，所有 Controller 位于 `com.ruoyi.web.controller.*` |
| ruoyi-framework | 框架层：Spring Security、本地缓存（Hutool LFU）、MyBatis、Druid、AOP 切面、Token 服务 |
| ruoyi-system | 系统管理 + **自定义业务**（`com.ruoyi.crm.*`、`com.ruoyi.buddy.*`） |
| ruoyi-quartz | 定时任务（SysJob/SysJobLog + Quartz 调度） |
| ruoyi-generator | 代码生成（GenTable/GenTableColumn + Velocity 模板） |
| ruoyi-common | 通用工具、注解、枚举、基础实体（BaseEntity/TreeEntity）、统一返回 |

## 三、实体分析（Domain）

### 3.1 通用基类（ruoyi-common，`com.ruoyi.common.core.domain`）

- **BaseEntity**：所有若依风格实体的基类。字段：`searchValue`、`createBy`、`createTime`、`updateBy`、`updateTime`、`remark`、`params`（Map，用于传参/多条件查询）。
- **TreeEntity**：继承 BaseEntity，树形实体基类。字段：`parentName`、`parentId`、`orderNum`、`ancestors`、`children`。
- 其他：`AjaxResult`（统一返回）、`R`（Vue 版返回对象）、`TreeSelect`、`LoginUser`、`LoginBody`、`RegisterBody`。
- **SysUser / SysDept / SysRole / SysMenu / SysDictData / SysDictType** 这几个核心实体也位于 `com.ruoyi.common.core.domain.entity` 包（不在 ruoyi-system）。

### 3.2 系统实体（ruoyi-system，`com.ruoyi.system.domain`）

SysConfig、SysNotice、SysOperLog、SysLogininfor、SysPost、SysUserOnline、SysCache，以及关联表实体 SysUserRole、SysUserPost、SysRoleMenu、SysRoleDept；VO：`RouterVo`、`MetaVo`。

### 3.3 CRM 实体（`com.ruoyi.crm.domain`）

| 实体 | 表 | 说明 |
|------|-----|------|
| CrmOffer | crm_offer | 询报价单：产品编码/品牌、成本价、报价、MOQ、供应商、标签等，`@Excel` 注解齐全 |
| CrmSupplier | crm_supplier | 供应商 |
| CrmSupplierContact | crm_supplier_contact | 供应商联系人（含邮箱） |
| CrmAttachment | crm_attachment | 附件 |

DTO（`com.ruoyi.crm.domain.dto`）：CrmOfferExportDTO（导出，含 `@Excel` 表头）、CrmOfferImportDTO、CrmSendEmailTaskDTO、CrmSupplierVO、SendEmailReq（邮件发送请求，含 offers + emailGroups）、SubscribeEmailDTO。

### 3.4 Buddy 实体（`com.ruoyi.buddy.common.bean`，MyBatis-Plus 风格）

DoUser、DoUserSetting、DoPlan、DoPlanBuddy、DoCheckin、DoEnergyLog、DoAchievement、DoUserAchievement、DoNotification，对应 `do_schema.sql` 中 9 张表。

## 四、Controller 分析（`com.ruoyi.web.controller`）

### 4.1 包结构

| 包 | 控制器 | 说明 |
|----|--------|------|
| system | SysUser/SysDept/SysRole/SysMenu/SysDict* /SysConfig/SysPost/SysNotice/SysProfile/SysLogin/SysRegister/SysIndex | 系统管理 12 个 |
| monitor | SysOperlog/SysLogininfor/SysUserOnline/Cache/Server | 监控 5 个 |
| crm | OfferController、SupplierController | CRM 业务 |
| buddy | DoPlanController 等 9 个 | 小程序业务 |
| wx | WxMaUser/WxMaMedia/WxPortal | 微信小程序（weixin-java-miniapp） |
| common | CaptchaController、CommonController | 验证码/通用 |
| tool | TestController | 测试 |

### 4.2 标准写法（必须遵循）

```java
@RestController
@RequestMapping("/crm/offer")          // 路径小写 + 连字符
public class OfferController extends BaseController {
    @Autowired
    private ICrmOfferService offerService;

    @PreAuthorize("@ss.hasPermi('crm:offer:list')")   // 权限标识：模块:资源:操作
    @Log(title = "Offer管理", businessType = BusinessType.INSERT)  // 操作日志
    @GetMapping("/list")
    public TableDataInfo list(CrmOffer offer) {
        startPage();                                  // 分页
        return getDataTable(offerService.selectOfferList(offer));
    }
}
```

- 一律继承 `BaseController`（提供 `startPage()`、`getDataTable()`、`toAjax()`、`success()`）。
- 返回类型：分页列表 `TableDataInfo`，其余 `AjaxResult`。
- 权限：`@PreAuthorize("@ss.hasPermi('xxx')")`；匿名接口用 `@Anonymous`（如 OfferController 的 sendOffer/sendInq）。
- 导入导出：`@Log(businessType = BusinessType.IMPORT/EXPORT)` + `ExcelUtil`。
- 批量操作：`@DeleteMapping("/{ids}")` 接收 `Long[] ids`。
- Buddy 控制器（MP 风格）：接口加 `@Api`/`@ApiOperation` Swagger 注解，直接调 `doPlanService.list(new QueryWrapper<>(query))`，无权限注解，路径为 `/common/do-plan`。

## 五、Service 分析

项目存在**两套并存**的 Service 规范：

### 5.1 若依原生风格（system + crm 模块）

- 接口：`ISysXxxService` / `ICrmXxxService`（`com.ruoyi.crm.service`）。
- 实现：`XxxServiceImpl implements IXxxService`，`@Service` 注解，字段注入 `@Autowired Mapper`。
- 方法命名（CRUD 固定套路）：
  - 列表：`selectXxxList(实体)`，Mapper 支持 `params` 动态条件
  - 详情：`selectXxxById(Long id)`
  - 新增：`insertXxx(实体)`（内部 `SecurityUtils.getUsername()` 填充 createBy/updateBy）
  - 修改：`updateXxx(实体)`
  - 删除：`deleteXxxByIds(Long[] ids)`
- 复杂事务：`@Transactional`（如 CrmOfferServiceImpl.importOffers）。

### 5.2 MyBatis-Plus 风格（buddy 模块）

- 接口：`DoXxxService extends IService<DoXxx>`。
- 实现：`DoXxxServiceImpl extends ServiceImpl<DoXxxMapper, DoXxx> implements DoXxxService`，通常为空壳，直接使用 MP 内置 CRUD（`list()`、`getById()`、`save()`、`updateById()`、`removeByIds()`）。
- 对应 Mapper 继承 `BaseMapper<DoXxx>`，XML 位于 `com/ruoyi/buddy/common/mapper/xml/`。

## 六、Mapper 层

- 若依风格：接口 `XxxMapper.java` + XML `resources/mapper/{模块}/XxxMapper.xml`，namespace 指向接口全限定名。
- XML 规范：`<resultMap id="XxxMap" type="实体全限定名">` 做下划线→驼峰映射，`<sql id="Base_Column_List">` 统一定义列，`<where>` + `<if test="...">` 动态条件，`params.xxx` 传复杂条件（如 `productCodeList.split(',')` 配合 `<foreach>`）。
- MP 风格：`DoPlanMapper extends BaseMapper<DoPlan>`，实体 `@TableName` + `@TableId(type = IdType.AUTO)` 注解映射。
- MyBatis 配置（application.yml）：`typeAliasesPackage: com.ruoyi.**.domain`、`mapperLocations: classpath*:mapper/**/*Mapper.xml`。
- 数据库：MySQL（`crm_me` 库），SQL 脚本 `sql/ry_20250522.sql`、`sql/quartz.sql`、`miniprogram-1/db/mysql/do_schema.sql`。

## 七、中间件与外部依赖

### 7.1 核心中间件

| 中间件 | 版本/配置 | 用途 |
|--------|-----------|------|
| MySQL | mysql-connector-java（Druid 1.2.23 连接池，支持主从 master/slave） | 数据存储 |
| 本地缓存 | Hutool LFU（容量 10000，支持按 key 过期 + 后台线程定时清理），封装类 `RedisCache`（类名保留兼容） | Token 存储、配置/字典缓存、验证码、防重提交、密码错误计数 |
| MyBatis + PageHelper 1.4.7 | helperDialect: mysql | ORM + 分页 |
| MyBatis-Plus | mybatis-plus-boot-starter（仅 buddy 模块） | 小程序业务 ORM |
| Spring Security 5.7.14 | `@EnableMethodSecurity` + JWT（jjwt 0.9.1）+ BCrypt | 认证授权，JwtAuthenticationTokenFilter |
| Quartz | ruoyi-quartz 模块 | 定时任务 |
| Swagger 3.0.0 | springfox-boot-starter + swagger-models 1.6.2 | 接口文档 |
| Kaptcha 2.3.3 | 验证码类型 math | 登录验证码 |
| Druid | 监控页面 + 连接池 | 连接池监视 |

### 7.2 工具/业务库

| 依赖 | 版本 | 用途 |
|------|------|------|
| EasyExcel | 3.3.2 | CRM 询报价 Excel 导入导出、邮件附件 |
| Apache POI | 4.1.2 | Excel 读写（CrmOfferServiceImpl 手工解析） |
| javax.mail | 1.6.2 | 邮件发送（`com.ruoyi.common.utils.email.EmailSender`，模板/账号从字典 `crm_email_template_dict` 读取：email_sign/email_title/email_body/email_account/email_smtp 等 key） |
| Hutool | 5.8.28 | 工具类（BeanUtil/DateUtil/NumberUtil/StrUtil） |
| FastJSON2 | 2.0.58 | JSON 序列化 |
| weixin-java-miniapp | 4.8.0 | 微信小程序登录/媒体（appid 在 application.yml `wx.miniapp.configs` 配置） |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Oshi 6.8.3 / Commons-IO 2.19.0 / bitwalker 1.21 | - | 服务监控、IO、UA 解析 |
| Velocity 2.3 | - | 代码生成模板 |
| Lombok | - | @Data/@Getter/@Setter/@Slf4j |
| Maven 仓库 | 阿里云 public 镜像 | - |

## 八、命名规范汇总

| 项 | 规范 | 示例 |
|----|------|------|
| 数据库表 | 小写蛇形，业务前缀 | `sys_user`、`crm_offer`、`do_plan`、`gen_table` |
| 实体类 | 模块前缀 + 业务名（PascalCase） | `SysUser`、`CrmOffer`、`DoPlan` |
| 控制器 | `XxxController`，RequestMapping 小写 kebab-case | `/crm/offer`、`/common/do-plan` |
| Service 接口 | 若依 `I` + 模块 + 业务 + `Service`；buddy 无 `I` 前缀 | `ICrmOfferService`、`DoPlanService` |
| Service 实现 | `XxxServiceImpl`，`@Service` | `CrmOfferServiceImpl` |
| Mapper | `XxxMapper` + `XxxMapper.xml` | `CrmOfferMapper.xml` |
| Service 方法 | `selectXxxList/ById`、`insertXxx`、`updateXxx`、`deleteXxxByIds` | `selectOfferList` |
| 权限标识 | `模块:资源:操作`（list/query/add/edit/remove/export/import） | `crm:offer:list` |
| Java 字段 | 小驼峰；DB 列下划线，resultMap/注解映射 | `productCode` ↔ `product_code` |
| 日期格式 | `yyyy-MM-dd HH:mm:ss`（BaseEntity @JsonFormat） | - |

## 九、开发注意事项（坑位提醒）

1. **两套 ORM 风格并存**：system/crm 用原生 MyBatis（XML 写 SQL），buddy 用 MyBatis-Plus。新增 buddy 业务走 MP，新增 crm 业务走原生 XML，不要混用。
2. **核心实体位置**：SysUser/SysDept/SysRole/SysMenu/SysDictData/SysDictType 在 `ruoyi-common` 的 `core.domain.entity` 包，新增时不要放错模块。
3. **Controller 一律继承 BaseController**，分页必须 `startPage()` + `getDataTable()`，否则 PageHelper 不生效。
4. **发送邮件相关**：模板 key 来自字典表 `crm_email_template_dict`，新增邮件功能直接复用 `ICrmSendOfferService` / `EmailSender`，不要重复造轮子。
5. **权限控制**：默认所有接口需 `@PreAuthorize`；对外暴露接口显式加 `@Anonymous`（参考 OfferController.sendOffer）。
6. **本地缓存**：统一用 `RedisCache`（内部为 Hutool LFU 本地缓存，类名保留兼容），key 统一见 `CacheConstants`；限流由 `RateLimiterAspect` 本地计数实现，不再依赖 Redis。
7. **导入导出**：实体字段加 `@Excel(name = "中文表头")`，导出用 `ExcelUtil` + DTO 拷贝（`BeanUtil.copyToList`），避免把内部字段暴露给客户。
8. **配置文件**：数据源在 `application-druid.yml`（主库 master），小程序 appid 在 `application.yml` 的 `wx.miniapp.configs`，上传目录 `ruoyi.profile`（当前 `D:/ruoyi/uploadPath`）。
