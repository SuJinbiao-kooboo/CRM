# 微信消息 → Java 集成指南（第二版：主动拉取 + 实时监听）

架构：Java 用 `ProcessBuilder` 拉一个 Python 脚本进程 `wx_gateway.py`（同一套
Python 环境 `C:\Software\Python`，不建 venv）。**它是脚本不是常驻服务**——你的
Java 每次需要数据时跑一次脚本、读完 stdout 即可；只有 `listen` 是需要一直挂着的。

```
微信4.1.15.13(已登录,窗口打开)
    │  wechatauto 解密本地库(WeChatDB) + 下载媒体(MediaDownloader)
    ▼
wx_gateway.py ─dump---> 按时间(+可选会话)拉记录 → NDJSON 一行一条到 stdout
              ─listen--> 所有会话新消息 → NDJSON 到 stdout (常驻)
    ▼  ProcessBuilder(stdout 逐行)
你的 Java 程序
```

## 0) 前置（一次性）
- 微信 4.1.15.13 已登录、主窗口打开（首次运行需从进程内存提取密钥）。
- 已装 `wechatauto-replica` 于 `C:\Software\Python`，且当前能正常读取。
- 你有 JDK（demo 无第三方依赖）。

## 1) 两种读取形式

### 形式一：主动拉取（按时间 + 可选群/好友名）
用法（时间**含到秒、闭区间**，即返回 `>=` 该时刻的消息）：
```powershell
# 拉 2026-10-05 12:00:00 起的所有会话消息
python "C:\Users\亦钟人\Documents\Codex\2026-10-03\hi-2\outputs\wx_gateway.py" dump --since "2026-10-05 12:00:00"

# 只拉某个群/好友（名称可选；不填=全部）
python "...wx_gateway.py" dump --since "2026-10-05 12:00:00" --chat "文件传输助手"

# 同时下载媒体（图片/语音/视频/文件）
python "...wx_gateway.py" dump --since "..." --chat "..." --media "...\media"

# 需要继续累积 SQLite 记录时，追加 --out 即可（增量去重）
python "...wx_gateway.py" dump --since "..." --out "...\wx_history.db" --media "...\media"
```
- `--chat` 支持群名 / 好友名 / 备注 / 昵称，按精确→模糊自动尝试。
- 输出 = 每条消息一行 JSON（见下方字段），诊断信息走 stderr。

### 形式二：实时监听（所有会话新消息）
```powershell
python "C:\Users\亦钟人\Documents\Codex\2026-10-03\hi-2\outputs\wx_gateway.py" listen --media "...\media"
```
一直挂着，每收到一条打一行 JSON。水位自动持久化，重启不重复推送。

## 2) Java 怎么调用（三条命令）
demo 已就绪：`outputs\WxBridge.java`
```powershell
javac -encoding UTF-8 WxBridge.java

# 主动拉取：时间必填，群/好友名可选
java WxBridge fetch  C:\Software\Python\python.exe "2026-10-05 12:00:00"
java WxBridge fetchm C:\Software\Python\python.exe "2026-10-05 12:00:00" "文件传输助手"   # +媒体

# 实时监听
java WxBridge listen C:\Software\Python\python.exe
```
在你的业务代码里，把 `WxBridge.handle(Message m)` 改成你自己入库/推送的逻辑即可。
`fetch*` 每次现起一个进程、读完 stdout 就结束——不需要维护任何服务。
注意 Windows 下 ProcessBuilder 传中文参数可能出现编码问题，建议用
`ProcessBuilder(List)` 逐参数传（demo 已这样写），必要时给子进程设 UTF-8 环境变量。

## 3) 消息 JSON 字段
| 字段 | 含义 |
| ---- | ---- |
| `username` | 会话（微信号/群id） |
| `name` | 会话显示名 |
| `local_id` | 库内消息 ID |
| `type` | 文本/图片/语音/视频/文件/系统… |
| `sender` | 发言人显示名（群内按成员解析） |
| `content` | 正文（媒体消息含占位+md5） |
| `media_path` | 已下载媒体绝对路径（未下载为空） |
| `create_time` | 创建时间，格式 `yyyy-MM-dd HH:mm:ss`（本地时区） |
| `sort_seq` | 排序键（增量断点） |
| `md5` | 媒体消息 md5 |

## 4) 长期记录（可选）
`dump --out X.db` 会追加写 SQLite（表 `chats(username,name)`、`messages(...)`，
主键 `(username,local_id)` 去重）。Java 用 `org.xerial:sqlite-jdbc` 直接读。

## 合规提醒
- 读取通过解密微信本地库实现（PyWxDump 同类机制），请只用于你自己账号的数据。
- 自动**发送**消息请另走包内 `WeChatGUI/quick_send`（UIA）路线，与读取解耦。


---

## 5) 已集成到 RuoYi 后台（Spring Boot）—— Controller 直接调用

新增后端代码（都在 `ruoyi-admin` 模块，包名 `com.ruoyi.wexinAuto` / `com.ruoyi.web.controller.wexinAuto`，
随项目一起编译、随 `com.ruoyi` 组件扫描自动注册）：

| 类型 | 文件 | 作用 |
| ---- | ---- | ---- |
| Controller | `wechat\WeChatMessageController.java` | REST 接口（见下表） |
| Service | `wechat\service\WeChatGatewayService.java` | 用 ProcessBuilder 拉起 `wx_gateway.py`，管理 fetch 与监听生命周期 |
| 模型 | `wechat\model\WeChatMessage.java` | 消息 POJO（含全部字段） |
| 解析 | `wechat\msg\WeChatMessageParser.java` | NDJSON → WeChatMessage |
| 处理 | `wechat\msg\WeChatMessageHandler.java` | **逐条处理接口**，监听下每条消息调用一次 |
| 处理(默认) | `wechat\msg\DefaultWeChatMessageHandler.java` | 默认实现：打日志+计数；改这里或 `@Primary` 换成你的逻辑 |
| 配置 | `wechat\config\WeChatGatewayProperties.java` | python/script/media 路径，可被 yml 覆盖 |

### REST 接口（`wechat` 前缀，随项目鉴权）
| 方法 | 路径 | 入参 | 说明 |
| ---- | ---- | ---- | ---- |
| GET | `/wechat/messages` | `since`(可选,如`2026-10-05 12:00:00`,含该秒起)、`chat`(可选,群/好友名)、`media`(可选,下载媒体) | 主动拉取，返回消息数组 |
| POST | `/wechat/listen/start` | `media`(可选) | 启动实时监听（之后逐条回调 Handler） |
| GET | `/wechat/listen/status` | - | `{running, processed}` |
| POST | `/wechat/listen/stop` | - | 停止监听，返回已处理条数 |

### 逐条处理的新消息怎么用
实现并注册一个 `WeChatMessageHandler` Bean 即可（用 `@Primary` 覆盖默认实现），
监听启动后每收到一条消息自动调用一次 `handle(WeChatMessage)`：
```java
@Component @Primary
public class MyMsgHandler implements WeChatMessageHandler {
    @Override public void handle(WeChatMessage m) {
        // 入库 / 推送 / AI 调用……每条消息一次
    }
}
```

### 配置覆盖（application.yml，可选）
默认值已在代码里，只有改路径才需要：
```yaml
wechat:
  python: C:/Software/Python/python.exe
  script: C:/Project/RuoYi-Vue/wechat-gateway/wx_gateway.py
  media:  C:/Project/RuoYi-Vue/wechat-gateway/media
```

### 注意
- 构建：本项目没有 Maven 环境变量，请用你的 IDE（IntelliJ 内置 Maven）编译运行 `ruoyi-admin`。
- 鉴权：`/wechat/**` 默认随项目登录态；若需匿名访问，在 SecurityConfig/Sa-Token 放行相应路径。
- 前置：微信 4.1.15.13 已登录、主窗口打开；首次运行需从进程内存提取密钥。



---

## 6) 批量发送（拟人节奏 + 安全上限，全部由 Java 配置字典控制）

### 直接跑 Python（可单独验证）
```powershell
# 配置从 stdin 传入 JSON，逐条结果打到 stdout
$cfg = '{"names":["张三","李四"],"template":"你好，{name}！","rhythm":"calm","nap":[1.0,1.6],"interval":[6,15],"max_batch":20,"verify":true}'
$cfg | python "C:\Project\RuoYi-Vue\wechat-gateway\wx_gateway.py" batch_send
```
配置字段：`names`/`targets`、`template`(含`{name}`自动替换)、`rhythm`(calm/fast/off)、
`nap`[下限,上限]、`interval`[条间随机秒]、`max_batch`(超限拒绝)、`verify`(回读防重复)。

### Java 配置字典（application.yml）——想调安全就在这里调
```yaml
wechat:
  python: C:/Software/Python/python.exe
  script: C:/Project/RuoYi-Vue/wechat-gateway/wx_gateway.py
  media:  C:/Project/RuoYi-Vue/wechat-gateway/media
  send:                 # 批量发送的拟人节奏/安全参数
    rhythm: calm        # calm/fast/off
    napMin: 1.0         # 等待抖动倍率下限(>=1.0)
    napMax: 1.6         # 等待抖动倍率上限
    intervalMin: 6      # 条与条随机间隔下限(秒)
    intervalMax: 15     # 条与条随机间隔上限(秒)
    maxBatch: 20        # 单批上限(超限拒绝，不发)
    verify: true        # 回读数据库确认，防重复发送
```
想更安全就把 `intervalMin/Max` 拉长、`maxBatch` 调小。

### Controller 接口（已加 @Anonymous，匿名可调）
| 方法 | 路径 | 入参 | 说明 |
| ---- | ---- | ---- | ---- |
| POST | `/wechat/batch_send` | `{"names":["张三","李四"],"text":"你好，{name}！"}` | 批量发送，返回每条结果 |
| POST | `/wechat/send` | `{"name":"文件传输助手","text":"hello"}` | 单条(底层同走批量) |

curl 测试：
```powershell
# 批量（发给多人，一条给一个，{name} 会被替换）
curl.exe -s -X POST http://localhost:8081/wechat/batch_send -H "Content-Type: application/json" `
  -d '{"names":["张三","李四"], "text":"你好，{name}，周末有空吗？"}'

# 单发（names 传一个即可，或用 /wechat/send）
curl.exe -s -X POST http://localhost:8081/wechat/send -H "Content-Type: application/json" `
  -d '{"name":"文件传输助手","text":"测试消息"}'
```

### 防封建议（务必遵守）
- 同一文本群发是最大指纹：用 `template` 里的 `{name}`（或随机措辞）让每条不同。
- 别去连发、别挑高峰：把 `intervalMin/Max` 拉大，控制每日总量。
- 用活跃老号，勿营销/加粉/广告。
- 遇「操作频繁/登录异常」提示立即停，人工登录。


---

## 7) 防封参数：RuoYi 数据字典（推荐，优先级高于 application.yml）

发送时 `WeChatGatewayService.batchSend` 会**先读数据字典**，字典缺失项才回退到
`application.yml` 的 `wechat.send.*`。所以在字典里调参最直观。

### 已经有现成的 SQL：`C:\Project\RuoYi-Vue\sql\wechat_send_dict.sql`
```bash
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_send_dict.sql
```
导入后刷新字典缓存（否则服务读不到新值）：
```
POST /system/dict/data/refreshCache   （带管理员 token）
```
或在「系统管理 → 字典管理」页面重新加载。

### 字典内容（dict_type = wechat_send_config）
| dict_label(参数名) | dict_value(默认) | 说明 remark |
| ---- | ---- | ---- |
| rhythm | calm | 拟人节奏：calm/fast/off |
| napMin | 1.0 | 等待抖动倍率下限(>=1.0) |
| napMax | 1.6 | 等待抖动倍率上限 |
| intervalMin | 6 | 每条随机间隔下限(秒) |
| intervalMax | 15 | 每条随机间隔上限(秒) |
| maxBatch | 20 | 单批上限，超限拒绝发送 |
| verify | true | 发送后回读确认(防重复) |

### 想更安全，把字典值改成
- `intervalMin` 10 / `intervalMax` 30（拉长条间间隔）
- `maxBatch` 10（收紧单批）
- `rhythm` calm（最稳）
- 配合 content 模板里的 `{name}` 去指纹

> 对应关系：字典里 `{label}={value}`，即 `napMin=1.0` 表示 `nap:[1.0, napMax]`；
> 若某列在字典里没配，就用 `application.yml` 的 `wechat.send.*` 默认值兜底。


> 新增字段：quote(被引用内容，文本截前256字；图片/表情只给【图片】【表情】说明)、quote_kind(text/image/emoji/voice/video/card)，仅引用/卡片消息非空。
