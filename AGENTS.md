# AGENTS.md

在本文件添加本项目（RuoYi-Vue）的环境信息，Codex 每次在该仓库工作时都会自动读取这里的规则与说明。

1. 每次做事都要检查一下是否已经完成了当前需求的所有代码编写 编译 代码测试和提交，如果没有完成不准停
2. 使用简体中文回复我，输出的内容主体语言用简体中文
# 环境信息与数据库探查工具说明

## 一、数据库连接信息（主库）

- 地址：`42.194.240.191:3306`
- 库名：`crm_me`
- 账号：`root`
- 密码：`eke123`
- JDBC URL：`jdbc:mysql://42.194.240.191:3306/crm_me?useUnicode=true&useSSL=false&characterEncoding=utf8&serverTimezone=GMT%2B8`
- 配置来源：`ruoyi-admin/src/main/resources/application-druid.yml`（master 数据源）

## 二、数据库表结构探查工具

## 三、部署服务器信息
IP: 114.132.93.127     
账号: root  
密码: eke123//
端口: 22

不需要每次都帮我部署，仅当我要求你帮我部署的时候才部署线上 当我明确要求部署的时候才部署

### 工具位置

- 工具类：`tools/db-inspector/DbInspector.java`（独立 Java 程序，不参与业务代码）
- 一键运行脚本：`tools/db-inspector/run.bat`（Windows 下自动编译 + 运行）

### 用途

开发过程中需要**查看数据库表结构设计**（表清单、字段、索引、外键）时使用，无需安装 MySQL 客户端，
无需记忆 SQL 语句，比直接查 `information_schema` 更快捷，输出为可读性强的格式化文本。

### maven home 地址
C:\Software\Maven-3.9.16

### 使用方式

```bash
# 1. 查看全部表结构（推荐，结果自动写入 tools/db-inspector/db_schema.txt，UTF-8 编码）
cd tools/db-inspector && run.bat

# 2. 只查看某张表（例如供应商表）
run.bat -table crm_supplier

# 3. 指定数据库/连接信息/输出文件（均可选，缺省使用项目默认配置）
run.bat -db crm_me -user root -password xxxx -out c:/tmp/db.txt
```

### 支持参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `-url` | JDBC 连接地址 | 与 application-druid.yml 主库一致 |
| `-user` | 数据库账号 | root |
| `-password` | 数据库密码 | eke123 |
| `-db` | 数据库名（自动替换 url 中的库名） | crm_me |
| `-table` | 仅探查指定表，缺省探查全部表 | 全部 |
| `-out` | 结果输出文件路径（UTF-8，避免控制台乱码） | db_schema.txt |

### 注意事项

1. 脚本会自动在 Maven 本地仓库（`%USERPROFILE%\.m2\repository`）查找 mysql-connector 驱动，
   找不到时需先执行 `mvn clean package -DskipTests` 下载依赖。
2. 编译产物输出到 `tools/db-inspector/classes/` 子目录，不污染工具目录。
3. 该工具为只读探查，不会修改数据库任何数据。
4. 生产环境连接信息变更时，同步更新 `application-druid.yml` 与本文件第一节。


## 工作方式（最高优先级，必须无条件遵守）

### 回合铁律
1. 我每一条非最终总结的消息，必须以工具调用结尾；允许在工具调用前加一句不超过15字的说明，但同一消息内必须立即发起工具调用。
2. 每次工具调用返回结果后，必须立即在同一回合内发起下一个工具调用，连续执行，中间不输出任何文字。
3. 禁止以任何预告/询问/进展文字结束回合（如"接下来我…"、"需要我…吗"、"继续"等）。
4. 编译、构建、测试、SQL执行、部署等长任务启动后，必须持续轮询直到拿到退出码，才进入下一步。
5. 工具报错时当场分析、修复、重跑，禁止把错误暴露给用户或因此停止。

### 交付完成清单（DoD）——输出最终总结前必须全部完成
- [ ] 需求涉及的所有代码已编写/修改
- [ ] 语法校验、编译、构建全部通过
- [ ] 测试（若存在）通过
- [ ] git add + git commit 已执行，无新增文件遗漏
- [ ] 数据库变更（如涉及）已完成，或已提供可执行脚本
- 只有上面全部打勾，才允许输出最终总结并结束回合。

### 允许结束回合的两种例外
(a) 当前需求的 DoD 全部完成，输出最终总结；
(b) 必须由用户决策的事项（如部署上线、密钥凭证），此时只问这一个决策点，并说明任务进行到哪一步。

### 部署边界
- 仅当用户明确说"部署/上线"时才部署；未获指示前，交付止步于代码+构建产物+git提交。
