# 微信自动化网关（读取 + 监听 + 发送）集成指南

> 本目录把微信桌面版自动化能力封装成一个独立的 Python 桥接层（`wx_gateway.py`），
> 并已集成到 RuoYi-Vue 后台（Spring Boot，端口 `8081`），让 Java 通过 REST 接口读写微信。
> 本指南写给"换一台电脑/新环境后能照着从头部署"的你自己，写得尽量具体。

---

## 0. 根本需求（先讲清楚要解决什么）

做一个"自动收发微信"的程序，具体拆成三点：

1. **读取聊天记录**：不需要指定群或好友，读取全部微信消息并做好记录；
   支持按**起始时间**（精确到秒、闭区间，含该时刻）和一个**可选的群名/好友名**筛选；
   同时保存**发言人、正文、图片、语音、文字**，视频可选（暂不做）。
2. **实时监听**：收到新消息时逐条拿到，交给业务逻辑（入库 / 推送）处理。
3. **批量发送**：单发和群发同一个接口；发送带**拟人节奏**（随机间隔、抖动等），
   参数全部由 **RuoYi 数据字典**控制，用于降低封号风险。

所有能力最终通过 `http://localhost:8081/wechat/**` 的 REST 接口暴露给上层系统。

---

## 1. 用到的开源项目（换环境要重新装的）

### 1.1 核心库：`wechatauto-replica`（本方案的地基）
- **GitHub**：<https://github.com/fanyuantaier/wechatauto-replica>
- **pip 包名**：`wechatauto-replica`（本机安装版本 **1.2.4.4**）
- **它解决什么**：面向 **微信 4.x Windows 客户端** 的自动化库（`wxauto` 进程版）。
  - **读取**：从 `Weixin.exe` 进程内存提取 SQLCipher 密钥，解密本地 SQLite 数据库
    （与 `PyWxDump` 同类机制），可读全量聊天、联系人、媒体索引。
  - **发送**：通过 Windows UIAutomation（`uiautomation`）+ OCR 操作微信窗口。
  - 附带：消息读取 / 实时监听 / 图片/语音/视频/文件下载 / 朋友圈读取 / 消息发送。
- 安装它时会自动带上依赖：`cryptography`、`pillow`、`opencv-python`、`psutil`、
  `pyautogui`、`pyperclip`、`pywin32`、`uiautomation`、`winsdk`、`zstandard`、
  `colorama`、`imageio-ffmpeg`。

### 1.2 桥接层（本项目自研）：`wx_gateway.py`
- 对 `wechatauto-replica` 再封装一层，变成**外部进程可调用**的命令行脚本：
  - `dump`：主动拉取（按时间 + 可选会话 + 可选媒体下载）→ 每行一条 NDJSON 输出到 stdout。
  - `listen`：实时监听所有会话新消息 → 每行一条 NDJSON（常驻进程）。
  - `batch_send`：从 stdin 读 JSON 配置做批量发送 → 每行一条 NDJSON 结果。
- 好处：**它是一次性脚本，不是常驻服务**；Java（或任何语言）用 `ProcessBuilder` 现跑一次、
  读完 stdout 即可，只有 `listen` 需要一直挂着。

### 1.3 微信本体
- **桌面版微信 4.1.15.13**（必须用它）。`wechatauto-replica` 只支持 4.1.12+，官方在
  `4.1.15.13` 上验证过；版本不对会导致 **数据库解密失败 / 密钥提取失败**。
- 微信数据目录在本机为 `C:\Data\WeChat\xwechat_files`（多个 wxid 账号，默认自动选
  `wxid_73q92mckc37522_9c42`）。

---

## 2. 整体架构

```
微信桌面版 4.1.15.13（已登录，主窗口打开）
   │  wechatauto-replica：
   │    读取 = 进程内存提密钥 → 解密 SQLCipher 本地库（WeChatDB）
   │    发送 = UIAutomation + OCR 操作窗口（WeChatGUI）
   ▼
wx_gateway.py（本目录的桥接脚本，单个 64 位 Python 环境执行）
   ├─ dump       按时间(+可选会话)拉记录 → NDJSON 到 stdout
   ├─ listen     所有会话新消息 → NDJSON 到 stdout（常驻进程）
   └─ batch_send stdin 读 JSON 配置 → 逐条结果 NDJSON 到 stdout
   ▼  ProcessBuilder 逐行读 stdout
RuoYi-Vue / Spring Boot（端口 8081，包 com.ruoyi.wexinAuto）
   └─ /wechat/** REST 接口（读取 / 监听生命周期 / 发送）
```

---

## 3. 换环境重新部署清单（按顺序做）

### 3.1 安装微信（版本必须一致）
1. 安装**桌面版微信 4.1.15.13**。
2. 登录你的账号，**保持主窗口打开**。
3. 确认微信数据目录可读（默认在微信设置里的"文件管理"目录）。

### 3.2 安装 64 位 Python
- **必须是 64 位**。32 位 Python 无法读取 64 位 `Weixin.exe` 的内存来提密钥，
  会报「数据库无可用密钥」。
- 本机路径：`C:\Software\Python\python.exe`。

### 3.3 安装 wechatauto-replica
```powershell
# 用 64 位 Python 的 pip
python -m pip install -U wechatauto-replica
python -m pip show wechatauto-replica   # 确认版本 1.2.4.4
```
> 只此一个 Python 环境，不建 venv。所有脚本都用它跑。

### 3.4 微信登录 + 首次提取密钥
- 首次运行需从微信进程内存提取密钥（微信**必须已登录**且有 `Weixin.exe` 进程）。
- 若报「未检测到 Weixin.exe」或「无可用密钥」，先跑诊断：
```powershell
python -m wechatauto.diagnose_keys
```
- 多账号机器会自动选第一个账号；要显式指定时在命令后加 `--account wxid_xxx`。

### 3.5 初始化 MySQL（建表 + 字典）
```powershell
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_analysis_dict.sql
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_offer_entry.sql   # 自动录入的表结构+枚举/参数字典(可重复执行)
```
- 建表 `wechat_message`（消息全量入库，保留最近 `msgRetentionDays` 天）；
- 写入 `wechat_analysis_config` 字典（黑白名单 / AI 分类参数 / 固定 OFFER 名单）；
- 写入 `wechat_message_status` 枚举字典（0=未分类 1=已分类未处理 2=已分类处理未发送 3=无需处理 4=已分类处理发送）；
- `wechat_offer_entry.sql` 负责：`crm_offer` 加来源字段(`wechat_server_id/wechat_username/wechat_local_id`)、
  `wechat_message` 加 `entry_fail_count`、写入录入参数 `offerEntry*`；
- 导入后重启 ruoyi-admin 生效。

### 3.6 编译/启动 Java 后台
- 用 IDE（IntelliJ 内置 Maven 即可，本项目没配系统 Maven 变量）编译并运行
  `ruoyi-admin` 模块，监听 `8081`。
- `/wechat/**` 控制器都标了 `@Anonymous`，由框架的 `PermitAllUrlProperties`
  **自动加入匿名白名单**，因此无需登录态即可调用（无需 JWT）。

---

## 4. Python 脚本单独调用（不经过 Java，可直接验证）

统一入口：`python wx_gateway.py <子命令> <参数>`（脚本在本目录）。

### 4.1 主动拉取（形式一：按时间 + 可选群/好友名）
```powershell
# 拉取 2026-10-05 12:00:00 起（含该秒）的所有会话消息
python "C:\Project\RuoYi-Vue\wechat-gateway\wx_gateway.py" dump `
  --since "2026-10-05 12:00:00"

# 只拉某个群/好友（名称可选；不填=全部）
python "...wx_gateway.py" dump --since "2026-10-05 12:00:00" --chat "文件传输助手"

# 同时把图片/语音/视频/文件下载到 media 目录
python "...wx_gateway.py" dump --since "2026-10-05 12:00:00" --chat "文件传输助手" `
  --media "C:\Project\RuoYi-Vue\wechat-gateway\media"

# 顺手追加写一个 SQLite 记录（增量去重，方便二次查询）
python "...wx_gateway.py" dump --since "2026-10-05 12:00:00" `
  --out "C:\Project\RuoYi-Vue\wechat-gateway\wx_history.db" `
  --media "C:\Project\RuoYi-Vue\wechat-gateway\media"
```
参数说明：
- `--since`：起始时间，含到秒、闭区间（返回 `>=` 该时刻）；格式
  `YYYY-MM-DD HH:MM:SS` / `YYYY-MM-DD HH:MM` / `YYYY-MM-DD`；不填=全部历史。
- `--chat`：群名 / 好友名 / 备注 / 昵称，按精确→模糊自动匹配；不填=所有会话。
- `--media`：可选，填了才下载媒体到该目录。
- `--out`：可选，追加写 SQLite（表 `chats`、`messages`，主键 `(username,local_id)` 去重）。
- `--limit`：每会话最多条数（默认 1000000）。
- `--account`：可选，多账号时指定 `wxid_xxx`。

### 4.2 实时监听（形式二：所有会话新消息）
```powershell
# 常驻进程，每收到一条新消息打一行 NDJSON
python "...wx_gateway.py" listen --media "C:\Project\RuoYi-Vue\wechat-gateway\media"
```
- 水位自动持久化到 `listener_watermark.json`，重启后不重复推送。
- Ctrl+C 结束。

### 4.3 批量发送（形式三：单发/群发，带拟人节奏）
```powershell
$cfg = '{
  "names": ["张三", "李四"],
  "template": "你好，{name}！周末有空吗？",
  "rhythm": "calm",
  "nap": [1.0, 1.6],
  "interval": [6, 15],
  "max_batch": 20,
  "verify": true
}'
$cfg | python "...wx_gateway.py" batch_send
```
send 配置字段（**与 RuoYi 数据字典一一对应**，见第 6 节）：
- `names` / `targets`：发送目标；`names` 给名单列表，`template` 里的 `{name}`
  会自动替换成对应目标名（去指纹用）。
- `rhythm`：拟人节奏档位 `calm` / `fast` / `off`。
- `nap`：`[下限, 上限]`，每条发送前的等待时长抖动倍率（>=1.0）。
- `interval`：`[min, max]`，条与条之间的随机间隔（秒）。
- `max_batch`：单批上限，超限直接拒绝（安全兜底）。
- `verify`：发送后回读数据库确认，防重复。
- 发送走 UIA 操作微信窗口，**微信窗口必须在屏幕上可见（不能最小化）**。

---

## 5. RuoYi（Spring Boot）集成调用

### 5.1 新增代码文件（都在 `ruoyi-admin` 模块，随项目编译）
| 路径 | 作用 |
| ---- | ---- |
| `wexinAuto/config/WeChatGatewayProperties.java` | 读取 `application.yml` 的 `wechat.*` 配置 |
| `wexinAuto/model/WeChatMessage.java` | 消息 POJO |
| `wexinAuto/model/WeChatSendResult.java` | 发送结果 POJO |
| `wexinAuto/msg/WeChatMessageParser.java` | NDJSON → `WeChatMessage` |
| `wexinAuto/msg/WeChatMessageHandler.java` | **逐条处理消息的扩展接口** |
| `wexinAuto/msg/DefaultWeChatMessageHandler.java` | 默认实现（打日志+计数），用 `@Primary` 覆盖 |
| `wexinAuto/service/WeChatGatewayService.java` | 用 `ProcessBuilder` 拉起脚本，管理 fetch/监听/发送 |
| `web/controller/wexinAuto/WeChatMessageController.java` | 全部 REST 接口（`/wechat/**`，已 `@Anonymous`） |
| 桥接脚本 | `wechat-gateway/wx_gateway.py`（本目录） |

### 5.2 配置 `application.yml`
```yaml
wechat:
  python: C:/Software/Python/python.exe                 # 64 位 Python
  script: C:/Project/RuoYi-Vue/wechat-gateway/wx_gateway.py
  media:  C:/Project/RuoYi-Vue/wechat-gateway/media
  send:                        # 批量发送的拟人节奏/安全参数（默认即可）
    rhythm: calm               # calm/fast/off
    napMin: 1.0                # 等待抖动倍率下限(>=1.0)
    napMax: 1.6                # 等待抖动倍率上限
    intervalMin: 6             # 条与条随机间隔下限(秒)
    intervalMax: 15            # 条与条随机间隔上限(秒)
    maxBatch: 20               # 单批上限(超限拒绝发送)
    verify: true               # 回读确认，防重复
```
> 当前 `application.yml` 里没写 `wechat:` 块，全部走代码里的默认值；
> 换环境只需改 `python` / `script` / `media` 三个路径。

### 5.3 接口一览（端口 8081，匿名可调）
| 方法 | 路径 | 入参 | 说明 |
| ---- | ---- | ---- | ---- |
| GET | `/wechat/messages` | `since`(可选,`2026-10-05 12:00:00`)、`chat`(可选)、`status`(可选 0/1)、`media`(默认false)、`limit`(可选) | 查询消息（先增量同步再查库）：按时间+可选会话，可按打标状态过滤 |
| POST | `/wechat/poll/run` | `chat`(可选)、`since`(可选,`2026-10-17 00:00:00`) | 手动触发一次拉取（忽略间隔强制拉；`chat`=只拉该会话；`since`=从该时刻起重新拉取，已入库的按唯一键跳过；都不填=按水位拉增量） |
| GET | `/wechat/poll/status` | 无 | 拉取/分类参数+运行状态：`{enabled,intervalSec,ignoreChats,classifyTriggerCount,lastRunAt,unclassifiedCnt,...}` |
| POST | `/wechat/classify/run` | `chat`(可选) | 手动触发一次分类（先拉最新，再处理最早未分类的 `classifyBatchSize` 条；chat=只处理该会话） |
| POST | `/wechat/offer/entry/run` | `chat`(可选) | 手动触发一次 INQ/OFFER 自动录入（AI查询 → AI录入 → 同料号比价；chat=只录入该会话，不填=全部会话并受 `offerEntryScanSec` 限频） |
| GET | `/wechat/offer/entry/status` | 无 | 录入参数、运行状态和各 `entry_status` 数量：`{enabled,...,minAgeSec,maxAgeHours,pendingCnt,entryStatusStats,...}` |
| POST | `/wechat/offer/entry/requeue` | `chat`(可选) | 人工确认后把 `SKIPPED/FAILED/DEAD` 的 INQ/OFFER 重新入队；不绕过语义校验 |
| POST | `/wechat/listen/start` | `media`(可选)、`fresh`(可选) | 启动实时监听；`fresh=true`=从现在开始(不回放历史) |
| GET | `/wechat/listen/status` | 无 | `{message,running,processed,filtered,pendingChats,pendingMsgs}` |
| POST | `/wechat/listen/stop` | 无 | 停止监听，返回已处理条数 |
| POST | `/wechat/send` | `{"name":"文件传输助手","text":"hello"}` | 单条发送 |
| POST | `/wechat/batch_send` | `{"names":["张三","李四"],"text":"你好，{name}！"}` | 批量发送 |

### 5.4 curl 示例（PowerShell 里直接测）
```powershell
# ① 按时间+群名拉取（返回 2026-10-05 12:00:00 起、特斯拉并购委员会 的消息）
curl.exe -s "http://localhost:8081/wechat/messages?since=2026-10-05%2012:00:00&chat=%E7%89%B9%E6%96%AF%E6%8B%89%E5%B9%B6%E8%B4%AD%E5%A7%94%E5%91%98%E4%BC%9A"

# ② 只按时间拉全部会话
curl.exe -s "http://localhost:8081/wechat/messages?since=2026-10-05%2012:00:00"

# ③ 手动拉取一次（指定时间+指定群，已入库的自动跳过）
curl.exe -s -X POST "http://localhost:8081/wechat/poll/run?since=2026-10-17%2000:00:00&chat=%E7%89%B9%E6%96%AF%E6%8B%89%E5%B9%B6%E8%B4%AD%E5%A7%94%E5%91%98%E4%BC%9A"

# ③-1 手动拉取一次（不指定时间=按水位只拉增量）
curl.exe -s -X POST "http://localhost:8081/wechat/poll/run"

# ④ 查看拉取/分类状态（未分类条数、水位、各参数）
curl.exe -s "http://localhost:8081/wechat/poll/status"

# ⑤ 手动触发分类（处理最早未分类20条；chat=只处理指定群）
curl.exe -s -X POST "http://localhost:8081/wechat/classify/run?chat=%E7%89%B9%E6%96%AF%E6%8B%89%E5%B9%B6%E8%B4%AD%E5%A7%94%E5%91%98%E4%BC%9A"

# ⑥ 只查已打标(status=1)的消息
curl.exe -s "http://localhost:8081/wechat/messages?since=2026-10-16%2000:00:00&status=1"

# ⑦ 启动监听
curl.exe -s -X POST "http://localhost:8081/wechat/listen/start"

# ⑧ 查询监听状态
curl.exe -s "http://localhost:8081/wechat/listen/status"

# ⑨ 停止监听
curl.exe -s -X POST "http://localhost:8081/wechat/listen/stop"

# ⑩ 单发
curl.exe -s -X POST "http://localhost:8081/wechat/send" -H "Content-Type: application/json" `
  -d '{"name":"文件传输助手","text":"测试消息"}'

# ⑦ 群发（{name} 会被替换成目标名）
curl.exe -s -X POST "http://localhost:8081/wechat/batch_send" -H "Content-Type: application/json" `
  -d '{"names":["张三","李四"], "text":"你好，{name}，周末有空吗？"}'
```

### 5.5 监听消息怎么拿到
- 监听启动后，新消息先入库（MySQL `wechat_message`），经 AI 批量分类（攒够 `aiTriggerCount` 条或超过 `aiFlushMinutes` 分钟触发），再逐条推给你实现的
  `com.ruoyi.wexinAuto.msg.WeChatMessageHandler` 上。
- 默认实现 `DefaultWeChatMessageHandler` 只是打日志+计数；要接业务逻辑，
  写一个同接口的 `@Component` Bean 并标 `@Primary` 即可覆盖它（入库/推送都在那做）。
- 注意：批量模式下单条消息的 `handle` 回调最多延迟 `aiFlushMinutes` 分钟（凑够 `aiTriggerCount` 条时可立即触发）。

---

### 5.6 监听水位、“从现在开始”与消息去重

- **水位(watermark)机制**：监听用每个会话记录的 `sort_seq` 作为“已推送到哪”的标记，存在 %TEMP%\wechatauto_db\<wxid>\listener_watermark.json。系统只推 `sort_seq` 严格大于水位的新消息。
- **为何会回放历史？** 水位文件会持久化。若上次监听被中断/未处理完，重启后会从上次水位回放之后的消息。想“只要新消息”，启动时加 `fresh=true`（会清空水位）。
- **消息去重**：每条消息有 `server_id`（业务去重键，网关生成 = 会话显示名 + 会话ID + 消息产生时间戳，如 `比特群:20970655512@chatroom:1791250634`）、`local_id`（库内 ID，分片上不全局唯一）、`sort_seq`（单调递增排序键）。听取/拉取都带 `server_id`，建议以 **(username, server_id)** 为主键去重。注意：同一会话同一秒发多条时该键会碰撞，如需更强唯一性，可把主键改为 **(username, local_id)** 或追加 `sort_seq`。
## 6. 反封号参数：RuoYi 数据字典（推荐，优先于 application.yml）

发送时 `WeChatGatewayService.batchSend` **先读数据字典 `wechat_send_config`**，
字典里有的项以字典为准；缺的项才回退到 `application.yml` 的 `wechat.send.*`。

### 已有现成 SQL：`C:\Project\RuoYi-Vue\sql\wechat_send_dict.sql`
```bash
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_send_dict.sql
```
导入后刷新字典缓存（否则服务读不到新值）：
```
POST /system/dict/data/refreshCache   （带管理员 token）
```
或在「系统管理 → 字典管理」页面重新加载。

### 字典内容（dict_type = wechat_send_config）
| dict_label(参数名) | dict_value(默认) | 说明 |
| ---- | ---- | ---- |
| rhythm | calm | 拟人节奏档位 calm/fast/off |
| napMin | 1.0 | 等待抖动倍率下限(>=1.0) |
| napMax | 1.6 | 等待抖动倍率上限 |
| intervalMin | 6 | 每条之间随机间隔下限(秒) |
| intervalMax | 15 | 每条之间随机间隔上限(秒) |
| maxBatch | 20 | 单批上限，超限拒绝发送 |
| verify | true | 发送后回读确认(防重复) |

> 想更安全：`intervalMin=10`、`intervalMax=30`、`maxBatch=10`、`rhythm=calm`，
> 并把发送模板里的 `{name}` 用好去指纹。

---

## 7. 消息分析：全量入库 + 批量 AI 分类（INQ / OFFER / TRACE / OTHER）

> 2026-10 起改为「定时轮询拉取入库 + 按条数/时间窗口触发大模型分类」，取代旧的规则链与
> 常驻监听方案。旧规则链类（`WeChatMessageClassifier`/`*Rule` 等）已删除，不再存在。

### 7.1 数据流（服务启动后全自动）

1. **定时拉取**（`WeChatClassifyScheduler.pollMessages`，每 5 秒检查一次）：
   - 服务启动后自动按 **`pollIntervalSec`** 秒（默认 10）跑一轮 `wx_gateway.py dump` 增量拉取，
     把微信新增消息写入 MySQL 表 `wechat_message`（幂等，唯一键 `username+local_id`，`status=0`）；
   - 增量水位：每轮从「上次拉到的最大消息时间 − `pollBackSec` 冗余」开始扫，防止边界丢消息；
     首次启动（水位为空）往前拉 **`pollInitBackSec`** 秒历史（默认 3 天，与消息保留天数一致）；
   - 单轮上限 **`pollLimit`** 条（默认 5000）；
   - **黑名单 `pollIgnoreChats`**：群/好友名称【包含】任一关键词（逗号分隔，如 `推广群,广告`）的消息
     不进入主表，转存 **`wechat_message_black`** 黑表（唯一键 username+local_id，重复跳过），
     不做分类、不做任何处理，仅留档备查（不参与自动清理）。
   - **分类黑名单 `blackNames`**：按会话名【精确匹配】；即使消息已在主表，也不参与 AI 分类，
     统一标记 `OTHER/FILTER/status=3`。它永远优先于 `whiteNames`。
     两者区别：`pollIgnoreChats` 是入库前的关键词路由，`blackNames` 是主表分类前的精确过滤。
2. **定时分类**（`WeChatClassifyScheduler.scanUnclassified`，每 10 秒检查一次）：
   - 未分类（`status=0`）消息数量 ≥ **`classifyTriggerCount`** 条（默认 5），
     或最早未分类消息等待 ≥ **`classifyMaxIdleSec`** 秒（默认 180）→ 触发 AI 分类；
   - 每轮只处理最早未分类的 **`classifyBatchSize`** 条（默认 20），分类后 `status=1`；
   - 分类时带上该会话最近 **`aiContextWindow`**（默认20）条原始消息作为上下文（允许未分类），
     分批时每批都带独立滑动窗口；让模型能按「上一条是询价 → 本条纯数字是报价」判断；
    - 分类结果回写数据库：`msg_category` / `category_source` / `category_reason` /
      `relat_inq` / `relat_offer` / `status=1`；
    - AI 同时返回关联编号：`OFFER` 指向前文 INQ，`TRACE` 指向对应 INQ/OFFER；代码只校验编号存在、
      早于当前消息，不再用正则重新判类。关联内容是 JSON：
      `{id, server_id, sender, category, content(截前800字), create_time}`。
   - **TRACE 飞书预警**：分类关联成功后立即推送（疑问/陈述均可）；失败保持 `status=1`，定时补扫接口
     `POST /wechat/trace/alert/scan` 默认每 30 秒重试。只有飞书明确返回成功才置 `status=2`。
3. 另保留**监听**（`/wechat/listen/start`，可选）：收到的新消息同样先入库，
   攒够 `aiTriggerCount` 条或超过 `aiFlushMinutes` 分钟触发分类，并逐条回调 `WeChatMessageHandler`。
   不启动监听也不影响消息落库（靠 1、2 两条定时任务）。
4. 定时清理：每 6 小时删除超过 **`msgRetentionDays`** 天（默认 3）的历史消息。

> 自动录入安全边界：`offerEntryEnabled=false` 对定时任务硬生效，只有显式传 `chat` 的
> `POST /wechat/offer/entry/run` 才允许人工排障执行；分类后默认等待 `offerEntryMinAgeSec`
> 秒再入队，只回溯 `offerEntryMaxAgeHours` 小时。每条消息独立记录
> `entry_status/entry_message/entry_offer_count/entry_offer_ids/entry_at`，失败可重试，
> `SKIPPED/FAILED/DEAD` 需人工确认后调用 `POST /wechat/offer/entry/requeue` 重排。

### 7.2 分类管线（单条消息）

- 顺序：**缓存命中 → AI（DeepSeek）分类**；AI 失败的消息留空，由回收任务重试。
- 不做固定 OFFER 标记，不做本地正则复核。`INQ/OFFER/TRACE/OTHER` 全部由提示词和最近20条上下文判断。
- 请求格式：给每条消息稳定编号 `#i`，历史标 `H`、待判标 `P`；模型只输出：
  `[{"i":23,"cat":"OFFER","inq":18}]`，不输出理由。
- 代码只做结构校验：`i` 必须是待判消息，`inq/offer` 必须存在且早于当前消息；编号无效时空关联。
  分类理由由代码生成（如 `AI:OFFER INQ=客户甲`），不再让模型重复输出。
- token 控制：上下文正文最多240字、待判正文最多600字、引用最多100字；`max_tokens` 按条数动态计算，
  上限1200；`llmExamples` 超过800字时自动回退内置简短示例。
- `llmPromptBase` / `llmExamples` 仍可在 `wechat_analysis_config` 字典维护，留空使用内置版本。
- 黑白名单：`blackNames` 永远优先；`whiteNames` 非空时只放行白名单。被过滤的会话不参与 AI 分类，
  库内标 `OTHER/FILTER/status=3`，查询接口不返回。
- 买卖双向：**INQ/OFFER 都不按发送方区分**。客户或我方都可询价，客户/供应商或我方都可报价；
  判断依据是“在问价还是在给价”，并结合该会话最近 `aiContextWindow`（默认20）条原始消息。
- `selfNames` 必须精确匹配（例如 `May` 不会误匹配 `May 星禾 SSD GPU`），当前为
  `Eke,May,Jin,Jasmine`；它只用于识别角色与交易方向，不能用来固定 INQ/OFFER。
- OFFER 必须由 AI 指向一条前文 INQ；自动录入前还会校验关联 INQ 的发送人不同、关联字段为 `INQ`。
  `TRACE` 可以是疑问句，也可以是“这个价格可以，按24pcs安排/明天确认交期”等陈述式推进。

### 7.3 字典/建表安装与生效

```powershell
# 新库：先执行基础建表/字典，再按顺序执行增量脚本
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_analysis_dict.sql
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_semantics_v2.sql
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_semantics_v3.sql
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_semantics_v4.sql
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_semantics_v5.sql
```
- **已有库不要整文件重跑 `wechat_analysis_dict.sql`**：该文件是初始化并重置字典的脚本，
  会覆盖人工维护的 `offerEntrySupplierMap`。已执行过 v3 的库依次执行 v4、v5。
- `wechat_semantics_v4.sql` 会：① 增加独立录入状态列和索引；② 更新 `selfNames` 为四人；
  ③ 清空旧单向提示词/示例；④ 增加“至少等待60秒、最多回溯24小时”门禁；
  ⑤ 把升级前历史 INQ/OFFER 标为 `SUCCESS` 或 `SKIPPED`，**不会自动批量补录**。
- `wechat_semantics_v5.sql` 会：关闭旧本地规则参数、补充 `selfNames`，并把超过800字的旧
  `llmExamples` 清空回退内置精简示例；不改 `blackNames`、`pollIgnoreChats` 和消息数据。
- 导入后刷新字典缓存（`POST /system/dict/data/refreshCache`，带管理员 token）
  或直接**重启 ruoyi-admin**（直接改 MySQL 字典不重启不会热生效，走管理端页面改会自动刷新）。

### 7.4 完整字典清单（`wechat_analysis_config`）

### 7.4.1 性能优化（2026-10-20）

- `wechat_message` 已补 5 个索引（线上库已执行，建表脚本已同步）：
  `idx_sort_seq(sort_seq)`、`idx_status_sort(status, sort_seq)`、
  `idx_status_cat_sort(status, msg_category, sort_seq)`、`idx_name_sort(name, sort_seq)`、
  `idx_status_createtime(status, create_time)`；老库补索引：
  `ALTER TABLE wechat_message ADD KEY idx_sort_seq (sort_seq), ADD KEY idx_status_sort (status, sort_seq), ADD KEY idx_status_cat_sort (status, msg_category, sort_seq), ADD KEY idx_name_sort (name, sort_seq), ADD KEY idx_status_createtime (status, create_time);`
- 注意：`GET /wechat/messages` 每次调用都会先做一次微信增量同步（跑 `wx_gateway.py`）
  并对新拉取消息做 AI 分类，属于"重接口"；不带 `since/chat` 等于全量导出（8722 条全字段
  经公网传输），耗时数十秒是物理瓶颈，建议日常按 `since`（时间窗口）或 `chat`（指定会话）查询。

| dict_label | 默认值 | 含义 |
| ---- | ---- | ---- |
| whiteNames / blackNames | 空 / 四个非业务群 | 会话白/黑名单（按消息 name 精确匹配，逗号分隔）；blackNames 永远优先 |
| selfNames | Eke,May,Jin,Jasmine | 我方成员（精确匹配）；只识别角色，不固定 INQ/OFFER 方向 |
| traceAlertEnabled | true | 已关联 INQ/OFFER 的二次业务跟进推送飞书（疑问/陈述均可） |
| traceAlertMaxAgeMin | 1440 | TRACE 预警最大时效(分钟) |
| traceAlertScanBatchSize | 50 | TRACE 预警补扫每批条数 |
| （AI 渠道配置）| 见 sys_config | 大模型地址/Key/模型/备用渠道/故障切换/告警已统一到系统参数 sys.ai.*（sql/deepseek_ai_config.sql），字典中旧参数 llmEnabled/llmApiKey/llmApiBase/llmModel/llmBackup*/llmFailThreshold/llmBackupCheckSec/alertFeishuWebhook/alertCooldownSec 已废弃并清理 |
| llmBatchSize | 20 | 每次调用最多消息条数(1-20) |
| llmTimeoutSec | 180 | 单次请求超时(秒) |
| aiTriggerCount | 5 | 攒够该条数立即分类 |
| aiFlushMinutes | 3 | 超时窗口(分钟) |
| aiContextWindow | 20 | 上下文原始消息条数（代码下限20） |
| msgRetentionDays | 3 | 消息保留天数（过期自动删） |
| llmPromptBase | 空(内置) | 系统提示词覆盖 |
| llmExamples | 空(内置) | 最多800字；超过后自动回退内置精简示例；改后调用 `POST /wechat/classify/cache/clear` |
| pollEnabled | true | 轮询拉取总开关（false=仅手动 poll/run） |
| pollIntervalSec | 10 | 拉取间隔(秒)，上一轮完成后等待该秒数再启动下一轮 |
| pollBackSec | 120 | 拉取冗余回看(秒)，防边界丢消息 |
| pollInitBackSec | 259200 | 首轮拉取历史窗口(秒)，默认3天(与保留天数匹配) |
| pollLimit | 5000 | 每轮拉取消息条数上限 |
| pollIgnoreChats | 业主群,玩乐吃喝... | 入库前关键词过滤：名称包含任一关键词的消息转存 `wechat_message_black` |
| classifyTriggerCount | 5 | 未分类(status=0)消息达到该条数即触发分类 |
| classifyMaxIdleSec | 180 | 最早未分类消息等待超过该秒数即触发分类 |
| classifyBatchSize | 20 | 每轮最多分类条数(取最早)，分类后 status=1 |
| offerEntryEnabled | true | INQ/OFFER 录入总开关（false=仅手动 `POST /wechat/offer/entry/run`） |
| offerEntryScanSec | 30 | 录入扫描间隔(秒)，定时自动录入的限频；手动指定 chat 不受限 |
| offerEntryBatchSize | 5 | 每轮最多录入条数(取最早 status=1 且分类命中) |
| offerEntryCategories | INQ,OFFER | 参与录入的分类(逗号分隔)；TRACE/OTHER 不录入 |
| offerEntrySupplierCode | 空 | 默认供应商编码；`offerEntrySupplierMap` 未命中时使用；为空则该消息标 `SKIPPED`，配置后人工重排 |
| offerEntrySupplierMap | 空 | 会话→供应商映射，如 `比特群=VC0820361,硬盘群=VC0820345`；命中优先；会话名需与 `wechat_message.name` 完全一致 |
| offerEntryProfitRatio | 2 | 仅“我方外发 OFFER”按销售报价加利润；外部供应商报价按采购 Inq 记录，不加价 |
| offerEntryMaxFail | 3 | 连续失败上限，达到后置 `entry_status=DEAD`；人工确认后可重新入队 |
| offerEntryCreateBy | wechat_auto | 自动录入的录入人(create_by/update_by) |
| offerEntryRequireRelatInq | true | OFFER 自动录入前必须关联对向 INQ（买卖任一方均可为 INQ） |
| offerEntryRequireOfferPrice | true | OFFER 自动录入前必须有可解析正价格 |
| offerEntryMinAgeSec | 60 | 分类后至少等待秒数，让后续上下文完整后再录入 |
| offerEntryMaxAgeHours | 24 | 自动录入最大回溯小时数，超期历史需人工重新入队 |

---

### 7.6 INQ/OFFER 自动录入 CRM（复用“AI查询”和“AI录入”按钮逻辑）

自动录入不复制一套解析代码，而是调用 CRM 页面两个按钮对应的服务：
`aiQueryHistory`（AI查询：识别料号并查近1个月历史）→
`aiEntryOffersReturningAsSystem`（AI录入：同一解析/价格/入库逻辑，系统任务显式操作人）→
`compareRecentOffers`（录入后同料号比价）。

数据流：
```
wechat_message(status=1, msg_category∈INQ,OFFER, 最近24小时, 分类后≥60秒)
  → 原子领取 entry_status=PENDING/FAILED → PROCESSING（防并发重复）
  → 结构校验（OFFER 必须有 AI 返回的 INQ 关联，且关联消息分类为 INQ、发送人不同）
  → supplier_code：offerEntrySupplierMap 优先，回退 offerEntrySupplierCode
  → AI查询：识别料号 + 查近1个月历史
  → AI录入：调用同一 AI录入按钮核心逻辑写 crm_offer（带 wechat_server_id/username/local_id 来源）
  → 同料号比价
  → entry_status：SUCCESS / SUCCESS_WARN / SKIPPED / FAILED / DEAD
```

- 供应商编码必须先在字典配好（`offerEntrySupplierCode` 默认值，或 `offerEntrySupplierMap`
  逐会话映射 `会话名=供应商编码`，多个用英文逗号分隔）；未命中供应商的消息标 `SKIPPED`，不写库。
- 自动录入门禁不再看发送方：INQ 可由任一方发出；OFFER 必须有 AI 指向的对向 INQ，且发送人不同。
- 方向映射：我方外发 OFFER → `Offer` 并加利润；外部供应商向我方 OFFER → `Inq`（采购报价，不加价）；
  我方给供应商 INQ、客户向我方 INQ → `Inq`。
- `SKIPPED` 表示需人工确认（无料号、无供应商、语义不足等）；`FAILED` 自动重试，`DEAD` 达上限；
  人工确认后调用 `POST /wechat/offer/entry/requeue?chat=比特群` 重排。
- 手动测试：`POST /wechat/offer/entry/run?chat=比特群`（指定会话立即执行，不受限频）；状态：`GET /wechat/offer/entry/status`。
- 与「Offer 列表 AI 录入」的区别：无人为指定供应商/利润率时的默认值来自字典；录入人统一 `wechat_auto`；其余字段映射与计算完全一致。
- 改了字典参数后需 `POST /system/dict/data/refreshCache`（带登录态）或重启后端生效。
## 8. 消息 JSON 字段（REST 返回 camelCase，与 Java POJO 一致）

| `username` | 会话（微信号/群id） |
| `name` | 会话显示名（备注名，第一层过滤器按它匹配） |
| `localId` | 库内消息 ID（分片内唯一，跨分片可能重复） |
| `serverId` | 业务去重键，由网关生成：会话显示名+会话ID+消息产生时间戳（如 `比特群:20970655512@chatroom:1791250634`） |
| `type` | 文本/图片/语音/视频/文件/系统… |
| `msg_category` | 消息分类（第二层分析后赋值）：`INQ`/`OFFER`/`TRACE`/`OTHER`；监听消息一定有值，拉取消息也一定有值 |
| `sender` | 发言人显示名（群内按成员解析） |
| `content` | 真实正文（已自动去掉库内多余的 `wxid_xxx:` 前缀行）；媒体消息为占位+md5；**引用/卡片消息**为该消息原文的标题/摘要 |
| `quote` | 被引用内容（仅引用/卡片消息非空）：文本截前 256 字；图片/表情等只给 `【图片】`/`【表情】` 等说明 |
| `quoteKind` | 被引用类型：`text`/`image`/`emoji`/`voice`/`video`/`card`（非引用为空） |
| `mediaPath` | 已下载媒体绝对路径（未下载为空） |
| `createTime` | 创建时间，格式 `yyyy-MM-dd HH:mm:ss`（本地时区） |
| `sortSeq` | 排序键（增量断点） |
| `md5` | 媒体消息 md5（从正文中提取或原样返回） |
| `id` | 库表自增主键（入库/查询后才有值） |
| `category_source` | 分类来源：`LLM`/`CACHE`/`FILTER` |
| `category_reason` | 代码根据 AI 分类和关联生成的简短说明（如 `AI:OFFER INQ=客户甲`），不占模型输出 token |
| `status` | 消息处理状态：`0`=未分类 `1`=已分类未处理 `2`=已处理/已录入 `3`=无需处理或已转人工 `4`=已处理发送；自动录入细节看独立 `entry_status` |
| `entry_fail_count` | AI 录入连续失败次数；达到 `offerEntryMaxFail` 后置 `entry_status=DEAD` |
| `entry_status` | 独立录入状态：`PENDING/PROCESSING/SUCCESS/SUCCESS_WARN/FAILED/DEAD/SKIPPED/NOT_APPLICABLE` |
| `entry_message` | 最近一次录入结果摘要；包含 AI查询组数/历史条数、AI录入行数、比价条数或跳过/失败原因 |
| `entry_offer_count` | 本条微信消息本次/最近一次写入 `crm_offer` 的行数 |
| `entry_offer_ids` | 写入的 `crm_offer.id` 列表（逗号分隔），用于精确审计 |
| `entry_at` | 最近一次录入状态更新时间 |
| `relat_inq` | AI 关联 INQ（JSON：id/server_id/sender/category/content/create_time）；OFFER=回应的INQ，TRACE=对应的INQ；无关联为空 |
| `relat_offer` | 业务关联 OFFER（同上格式）；仅 TRACE 有值（对应的OFFER）；其余为空 |

---

## 9. 常见问题排查

| 现象 | 原因 / 解决 |
| ---- | ---- |
| `RuntimeError: 数据库无可用密钥` 或「未检测到 Weixin.exe」 | 微信没登录或窗口没开；先登录再跑；或脚本权限低于微信（微信以管理员运行时脚本也要管理员）。 |
| 「无可用密钥」但已登录 | ① Python 是 32 位 → 换 64 位；② 多账号选错 → 加 `--account wxid_xxx`；③ 微信版本不是 4.1.15.13。用 `python -m wechatauto.diagnose_keys` 排查。 |
| 密钥缓存坏了 | 删除缓存后重试：`C:\Users\<你>\AppData\Local\Temp\wechatauto_db\<wxid>\keys.json`。 |
| 发送失败 /「微信窗口不可见」 | 发送走 UIA，微信主窗口必须**在屏幕上可见且不能最小化**。 |
| 传给子进程的中文乱码 | `ProcessBuilder(List)` 逐参数传（代码已这样写），并给子进程设 UTF-8。 |
| sender 张冠李戴（A 发的显示成 B） | 微信 4.x 消息库 `real_sender_id` 经 SenderName2Id 的 rowid 映射会错位；网关已改为按正文首行 `wxid_xxx:` 前缀解析真实发送者，映射仅兜底。 |
| content 开头仍有 `xxx:` 残留 | 网关会剥离发送者前缀行（`wxid_*`/普通微信号/`@openim` 格式），若仍有残留一般是正文本身如此（如 `OFFER:` 开头）。 |
| 改了代码不起作用 | 重新编译启动 `ruoyi-admin`；脚本无缓存，每次现跑。 |
| 黑名单群消息去哪了 | 全部存到 `wechat_message_black` 表（只留档，不分类不处理），可单独查该表。 |
| 黑名单群为什么主表还有旧消息 | `pollIgnoreChats` 只影响新拉取消息；执行 `sql/wechat_semantics_v3.sql` 会把历史主表记录纠正为 OTHER/FILTER/status=3。 |
| `Data too long for column 'server_id'` | 执行 `sql/wechat_semantics_v3.sql`，三张表的 server_id 都会扩到 255。 |
| `PermissionError ... message_*.db.tmp -> message_*.db` | 已修复网关连接顺序并增加 5 次短重试；升级后确认运行的是项目内 `wechat-gateway/wx_gateway.py`。 |
| TRACE 没有飞书 / status 一直是 1 | 检查 `sys.ai.alert.webhook`、飞书机器人是否返回 `code=0`；失败会保持 status=1 并由 `POST /wechat/trace/alert/scan` 重试，成功后置 2。 |
| 关联字段 relat_inq/relat_offer 什么时候有值 | AI 分类时直接返回对应编号，代码校验后写入；没有有效编号就不写，历史已分类消息不会自动回填。 |
| 录入一直跳过 / 日志「未配置供应商，跳过录入」 | 在字典配置 `offerEntrySupplierCode`（默认供应商编码）或 `offerEntrySupplierMap`（逐会话 `会话名=供应商编码`），改字典后需刷新缓存或重启后端。 |
| 已录入的消息在 wechat_message 查不到了 | 消息按 `msgRetentionDays`（默认3天）自动清理属正常；`crm_offer` 的 `wechat_server_id/wechat_username/wechat_local_id` 仍保留来源可反查，想留更久调大字典 `msgRetentionDays`。 |
| 自动录入会不会重复录入 | 不会：录入前按 `wechat_server_id+username+local_id` 查重，已入库的消息直接回写 `status=2` 跳过（重复触发/历史脏数据也能自愈）。 |
| C 盘被 `%TEMP%\wechatauto_db\_proc\` 占满 | 这是 wechatauto 每次运行时解密微信消息库的临时目录（约79MB/进程）。`wx_gateway.py` 已内置两层清理：启动时清扫已退出进程的残留目录；进程退出时删除自身目录（强杀/断电残留由下一次启动的清扫兜底）。可手动安全删除该目录下所有子目录（仅保留当前正在运行的 python 进程对应 PID 的目录）。 |

---

## 10. 合规与安全提醒
- 读取通过解密微信本地数据库实现（PyWxDump 同类机制），**只用于你自己账号的数据**。
- 自动发送是 UIA 操作，存在微信风险控制；务必控制频率、用活跃老号、
  **不要营销/加粉/广告**，遇「操作频繁/登录异常」提示立即停并人工登录。
- 发送接口目前在匿名白名单里，生产环境建议加上鉴权或限制来源 IP。

---

## 11. 提示词驱动的询报价分类

分类细节全部由 `llmPromptBase` + `llmExamples` + 最近20条上下文交给模型判断；
`SemanticMatcher`、`FixedOfferMarker` 等本地规则类已删除，避免两套标准互相覆盖。

- **INQ 询价**：任一方询问/寻找具体货物的价格、库存、交期、数量或型号；我方问供应商也算。
- **OFFER 报价**：AI 指向某条前文 INQ 的对向报价/供货回应；自动录入前另校验发送人不同。
- **TRACE 二次跟进**：围绕已有 INQ/OFFER 的后续业务推进，疑问句或“按24pcs安排/确认交期”等陈述均可。
- **OTHER**：广告、闲聊、无可关联业务的孤立陈述等。

模型输出示例：
```json
[
  {"i":23,"cat":"OFFER","inq":18},
  {"i":24,"cat":"TRACE","offer":23}
]
```

> 修改提示词后先调用 `POST /wechat/classify/cache/clear`。重跑历史消息时清空
> `msg_category/category_source/category_reason/relat_inq/relat_offer/entry_*` 并把 `status` 改回 0。

