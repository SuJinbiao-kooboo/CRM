PATTERN NOT FOUND:    └─ /wechat/** REST 接口（读取 /  # 微信自动化网关（读取 + 监听 + 发送）集成指南

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
| POST | `/wechat/offer/entry/run` | `chat`(可选) | 手动触发一次 INQ/OFFER 自动录入（status=1 → AI 解析 → 写入 `crm_offer`，成功后 status=2；chat=只录入该会话，不填=全部会话并受 `offerEntryScanSec` 限频） |
| GET | `/wechat/offer/entry/status` | 无 | 录入参数+运行状态：`{enabled,batchSize,categories,supplierCode,supplierMap,profitRatio,maxFail,pendingCnt,lastRunAt,...}` |
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
2. **定时分类**（`WeChatClassifyScheduler.scanUnclassified`，每 10 秒检查一次）：
   - 未分类（`status=0`）消息数量 ≥ **`classifyTriggerCount`** 条（默认 5），
     或最早未分类消息等待 ≥ **`classifyMaxIdleSec`** 秒（默认 180）→ 触发 AI 分类；
   - 每轮只处理最早未分类的 **`classifyBatchSize`** 条（默认 20），分类后 `status=1`；
   - 分类时带上该会话最近 **`aiContextWindow`** 条已分类消息作为上下文，
     让模型能按「上一条是询价 → 本条纯数字是报价」这类前后文判断；
   - 分类结果回写数据库：`msg_category` / `category_source` / `category_reason` / `status=1`；
   - **业务关联**（同会话按时间回溯最近一条）：`OFFER` → `relat_inq`（它回应的 INQ）；
     `TRACE` → `relat_inq`（对应的 INQ）+ `relat_offer`（对应的 OFFER）；`INQ` 无关联字段。
     关联内容是 JSON：`{id, server_id, sender, content(截前800字), create_time}`。
3. 另保留**监听**（`/wechat/listen/start`，可选）：收到的新消息同样先入库，
   攒够 `aiTriggerCount` 条或超过 `aiFlushMinutes` 分钟触发分类，并逐条回调 `WeChatMessageHandler`。
   不启动监听也不影响消息落库（靠 1、2 两条定时任务）。
4. 定时清理：每 6 小时删除超过 **`msgRetentionDays`** 天（默认 3）的历史消息。

### 7.2 分类管线（单条消息）

- 顺序：**缓存命中 → 固定标记 → AI（DeepSeek）精判**；AI 失败的消息留空，由回收任务重试。
- 固定标记（可选，字典为空则纯 AI）：
  - `offerFixedSenders`：固定发 OFFER 的发送人（逗号分隔），命中 → `OFFER`（source=`FIXED`）；
  - `offerFixedGroups`：固定发 OFFER 的群（逗号分隔），命中 → 群内文本 `OFFER`（source=`GROUP-FIXED`）；
  - `offerFollowWindowMin`：上一条是固定 OFFER、间隔不超过该分钟数、且本条无明显询价词
    （inq/询/有货吗/?）→ 也标 `OFFER`（source=`FIXED-FOLLOW`）；0=关闭。
- AI 精判：未命中的消息按会话分批（`llmBatchSize` 条/批、`llmConcurrency` 并行）调 DeepSeek
  （OpenAI 兼容，temperature=0，输出 JSON）：系统提示词 = `llmPromptBase`（留空用内置默认）
  + few-shot 示例 = `llmExamples`（留空用内置默认），两个都可在字典里改，无需改代码。
- 黑白名单（`whiteNames` / `blackNames`）依然生效：被过滤的会话不参与 AI 分类，
  库内标 `OTHER/FILTER`，查询接口不返回（第一层过滤语义不变）。

### 7.3 字典/建表安装与生效

```powershell
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_analysis_dict.sql
```
- 该 SQL 会：① 建 `wechat_message` 表（不存在才建）；② 重置 `wechat_analysis_config` 字典为完整清单
  （黑白名单 + llm 参数 + 轮询拉取参数 + 分类触发参数 + 固定 OFFER 名单）；
- 导入后刷新字典缓存（`POST /system/dict/data/refreshCache`，带管理员 token）
  或直接**重启 ruoyi-admin**（直接改 MySQL 字典不重启不会热生效，走管理端页面改会自动刷新）。

### 7.4 完整字典清单（`wechat_analysis_config`）

| dict_label | 默认值 | 含义 |
| ---- | ---- | ---- |
| whiteNames / blackNames | 空 | 会话白/黑名单（按消息 name 精确匹配，逗号分隔） |
| llmEnabled | true | 是否启用大模型分类 |
| llmApiKey | 空 | DeepSeek API Key（留空读环境变量 `DEEPSEEK_API_KEY`） |
| llmApiBase | https://api.deepseek.com | OpenAI 兼容接口根地址 |
| llmModel | deepseek-chat | 模型名 |
| llmBatchSize | 20 | 每次调用最多消息条数(1-20) |
| llmConcurrency | 2 | 并行批次(建议1-3) |
| llmTimeoutSec | 180 | 单次请求超时(秒) |
| aiTriggerCount | 5 | 攒够该条数立即分类 |
| aiFlushMinutes | 3 | 超时窗口(分钟) |
| aiContextWindow | 10 | 上下文条数 |
| msgRetentionDays | 3 | 消息保留天数（过期自动删） |
| llmPromptBase | 空(内置) | 系统提示词覆盖 |
| llmExamples | 空(内置) | few-shot 示例覆盖 |
| offerFixedSenders / offerFixedGroups | 空 | 固定 OFFER 名单(逗号分隔) |
| offerFollowWindowMin | 5 | 固定 OFFER 回复跟随窗口(分钟)，0=关闭 |
| pollEnabled | true | 轮询拉取总开关（false=仅手动 poll/run） |
| pollIntervalSec | 10 | 拉取间隔(秒)，上一轮完成后等待该秒数再启动下一轮 |
| pollBackSec | 120 | 拉取冗余回看(秒)，防边界丢消息 |
| pollInitBackSec | 259200 | 首轮拉取历史窗口(秒)，默认3天(与保留天数匹配) |
| pollLimit | 5000 | 每轮拉取消息条数上限 |
| pollIgnoreChats | 空 | 拉取过滤：名称包含任一关键词的群/好友消息不入库(逗号分隔) |
| classifyTriggerCount | 5 | 未分类(status=0)消息达到该条数即触发分类 |
| classifyMaxIdleSec | 180 | 最早未分类消息等待超过该秒数即触发分类 |
| classifyBatchSize | 20 | 每轮最多分类条数(取最早)，分类后 status=1 |
| offerEntryEnabled | true | INQ/OFFER 录入总开关（false=仅手动 `POST /wechat/offer/entry/run`） |
| offerEntryScanSec | 30 | 录入扫描间隔(秒)，定时自动录入的限频；手动指定 chat 不受限 |
| offerEntryBatchSize | 5 | 每轮最多录入条数(取最早 status=1 且分类命中) |
| offerEntryCategories | INQ,OFFER | 参与录入的分类(逗号分隔)；TRACE/OTHER 不录入 |
| offerEntrySupplierCode | 空 | 默认供应商编码；`offerEntrySupplierMap` 未命中时使用；为空则该会话消息跳过(保持 status=1 等待配置) |
| offerEntrySupplierMap | 空 | 会话→供应商映射，如 `比特群=VC0820361,硬盘群=VC0820345`；命中优先；会话名需与 `wechat_message.name` 完全一致 |
| offerEntryProfitRatio | 2 | OFFER 报价利润率(%)：报价价=成本价×(1+利润率/100)，向上取整到 5 的倍数(与 Offer 列表 AI 录入一致) |
| offerEntryMaxFail | 3 | 同一消息连续录入失败上限(次)，达到后置 status=3(无需处理)，避免卡队列 |
| offerEntryCreateBy | wechat_auto | 自动录入的录入人(create_by/update_by) |

---


| 字段 | 含义 |
| ---- | ---- |
### 7.5 INQ/OFFER 自动录入 CRM（status=1 → crm_offer，成功后 status=2）

分类完成后（`status=1`），系统自动把 **INQ / OFFER** 消息按「Offer 列表 AI 录入」的**完全一致逻辑**
（同一个 `DeepSeekAiService.parseMaterialContent` 提示词：品牌/料号/型号/规格/数量/报价/DC/COO/交期/产品类型/详情）
解析并写入 `crm_offer`，供业务直接消费，无需人工复制粘贴。

数据流：
```
wechat_message(status=1, msg_category∈INQ,OFFER)
   → 定时任务(offerEntryScanSec 秒一轮, 每轮最多 offerEntryBatchSize 条, 最早的优先)
   → AI 解析物料列表 → 逐行 INSERT crm_offer
       ├─ 写入来源关联字段：wechat_server_id / wechat_username / wechat_local_id（可反查聊天记录）
       ├─ supplier_code：字典 offerEntrySupplierMap(会话名=供应商编码) 优先，回退 offerEntrySupplierCode
       ├─ inq_offer_type：INQ→Inq（只记成本价）/ OFFER→Offer（报价=成本×(1+利润率/100) 取整到5）
       └─ remark：群/发言人/消息时间/server_id 溯源；sheet_name：WX_时间戳_会话名
   → 成功：status=2(已分类处理未发送)；AI 解析失败：entry_fail_count+1，
     达到 offerEntryMaxFail 次 → status=3(无需处理)，未达上限保持 status=1 下轮重试
```

- 供应商编码必须先在字典配好（`offerEntrySupplierCode` 默认值，或 `offerEntrySupplierMap`
  逐会话映射 `会话名=供应商编码`，多个用英文逗号分隔）；未命中供应商的消息**跳过不录入**（保持 status=1），日志提示。
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
| `category_source` | 分类来源：`LLM`/`FIXED`/`GROUP-FIXED`/`FIXED-FOLLOW`/`CACHE`/`FILTER` |
| `category_reason` | AI 判定的简短理由（便于人工复核） |
| `status` | 处理状态枚举：`0`=未分类 `1`=已分类未处理 `2`=已分类处理未发送(已录入crm_offer) `3`=无需处理(录入连续失败达上限等) `4`=已分类处理发送(后续发送环节)；`/wechat/messages` 可用 `status=` 过滤，字典 `wechat_message_status` |
| `entry_fail_count` | AI 录入失败次数：连续失败达 `offerEntryMaxFail` 次自动置 `status=3`；0=未失败 |
| `relat_inq` | 业务关联 INQ（JSON：id/server_id/sender/content/create_time）；OFFER=回应的INQ，TRACE=对应的INQ；无关联为空 |
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
| 关联字段 relat_inq/relat_offer 什么时候有值 | OFFER/TRACE 被 AI 分类回写时自动计算（同会话内找最近一条 INQ/OFFER）；历史已分类消息不会回填，新分类的才有。 |
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

## 10. 消息分类语义（AI 判定，提示词可覆盖）

现在分类由 DeepSeek 按会话批量判定（见第 7 节），内置提示词的分类定义如下，
想改定义/加例子，把 `llmPromptBase`（系统提示词）和 `llmExamples`（few-shot 示例）
填进 `wechat_analysis_config` 字典即可，无需动代码。

- **INQ 询价/求购**：买方要货——型号+数量、问价、有没有货、目标价 TP、RFQ/pls chk；
  纯「型号+数量」无价格且是询问语气。
- **OFFER 报价/出货**：卖方给价——型号+单价/表格式报价单、$USD/RMB 价格、出货清单、
  对询价的纯数字报价回复（如「2900」「这个950」）、报价补充（如「1wk」）。
- **TRACE 跟进**：对询报价的追问——更新、考虑得怎样、确认、优惠、货期、排单、数量多少、还在吗、订了吗。
- **OTHER 其他**：闲聊/单字回复（好/没/收到/谢谢）、收购广告（长期收/高价收/有货滴滴/实单收）、
  系统消息、图片/语音/卡片 XML、无业务含义的内容。

内置提示词关键规则：
1. 先看上下文：上一条是 INQ 时，纯数字或短报价多为 OFFER；上一条是 OFFER 时，还价/问交期多为 TRACE 或 INQ；
2. 含价格证据且说话人在给价（非问价）→ OFFER；`TP5400usd` 这类「目标价」是 INQ 不是 OFFER；
3. 收购广告、闲聊、单字、XML/图片/语音 → OTHER；拿不准按 OTHER，不编造。

> 想 100% 稳定判定的场景（固定发 OFFER 的群/人、以及对报价的回复），
> 优先用固定名单（`offerFixedSenders` / `offerFixedGroups` / `offerFollowWindowMin`），
> 名单内不走 AI，零成本零误判；其余走 AI。

