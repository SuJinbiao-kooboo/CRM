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

不需要每次都帮我部署，仅当我要求你帮我部署的时候才部署线上  

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
