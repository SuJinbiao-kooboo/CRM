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

### 3.5 编译/启动 Java 后台
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
| GET | `/wechat/messages` | `since`(可选,`2026-10-05 12:00:00`)、`chat`(可选)、`media`(默认false) | 按时间+可选会话拉取，返回消息列表 |
| POST | `/wechat/listen/start` | `media`(可选)、`fresh`(可选) | 启动实时监听；`fresh=true`=从现在开始(不回放历史) |
| GET | `/wechat/listen/status` | 无 | `{message,running,processed}` |
| POST | `/wechat/listen/stop` | 无 | 停止监听，返回已处理条数 |
| POST | `/wechat/send` | `{"name":"文件传输助手","text":"hello"}` | 单条发送 |
| POST | `/wechat/batch_send` | `{"names":["张三","李四"],"text":"你好，{name}！"}` | 批量发送 |

### 5.4 curl 示例（PowerShell 里直接测）
```powershell
# ① 按时间+群名拉取（返回 2026-10-05 12:00:00 起、特斯拉并购委员会 的消息）
curl.exe -s "http://localhost:8081/wechat/messages?since=2026-10-05%2012:00:00&chat=%E7%89%B9%E6%96%AF%E6%8B%89%E5%B9%B6%E8%B4%AD%E5%A7%94%E5%91%98%E4%BC%9A"

# ② 只按时间拉全部会话
curl.exe -s "http://localhost:8081/wechat/messages?since=2026-10-05%2012:00:00"

# ③ 启动监听
curl.exe -s -X POST "http://localhost:8081/wechat/listen/start"

# ④ 查询监听状态
curl.exe -s "http://localhost:8081/wechat/listen/status"

# ⑤ 停止监听
curl.exe -s -X POST "http://localhost:8081/wechat/listen/stop"

# ⑥ 单发
curl.exe -s -X POST "http://localhost:8081/wechat/send" -H "Content-Type: application/json" `
  -d '{"name":"文件传输助手","text":"测试消息"}'

# ⑦ 群发（{name} 会被替换成目标名）
curl.exe -s -X POST "http://localhost:8081/wechat/batch_send" -H "Content-Type: application/json" `
  -d '{"names":["张三","李四"], "text":"你好，{name}，周末有空吗？"}'
```

### 5.5 监听消息怎么拿到
- 监听启动后，脚本逐条把新消息打到你实现的
  `com.ruoyi.wexinAuto.msg.WeChatMessageHandler` 上。
- 默认实现 `DefaultWeChatMessageHandler` 只是打日志+计数；要接业务逻辑，
  写一个同接口的 `@Component` Bean 并标 `@Primary` 即可覆盖它（入库/推送都在那做）。

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

## 7. 消息分析：白名单/黑名单过滤 + 业务类型打标（第一、二层过滤器）

> 本功能不是 Python 网关注入的，是 Java 侧 `WeChatGatewayService` 在**返回给接口之前**
> 对每条消息做处理：第一层过滤不通过的消息直接丢弃，第二层给通过的每条消息打 `msg_category` 标签。
> 拉取（`/wechat/messages`）和监听（`/wechat/listen`）两条链路都生效，监听被丢弃时会计入
> `/wechat/listen/status` 的 `filtered` 计数并在日志打 `过滤器丢弃消息`。

### 7.1 第一层：会话名（name 备注名）黑/白名单过滤

- 名单配置在 RuoYi 数据字典 `wechat_analysis_config`，两个参数：
  - `whiteNames`：会话白名单（多个用**英文逗号**拼接，按消息的 `name` 精确匹配）
  - `blackNames`：会话黑名单（同上）
- 生效逻辑（`passAnalysis`）：
  - 白名单**非空**：只放过白名单里的会话，其余全部丢弃；
  - 白名单**为空**：只丢掉黑名单里的会话，其余全部放过。
- 注意：匹配的是**会话显示名/备注名**（`name`），不是 wxid（`username`），精确匹配不做模糊。

### 7.2 第二层：业务类型打标 INQ / OFFER / TRACE / OTHER

- 每条通过第一层的消息都会调用 `WeChatMessageClassifier.classify`，给 `msg_category` 字段赋值：
  - `INQ` 询价、`OFFER` 报价、`TRACE` 询报价跟进、`OTHER` 其他。
- 当前规则为**占位关键词**（按标题/正文+引用内容包含关键词判断），代码位置：
  `ruoyi-admin/src/main/java/com/ruoyi/wexinAuto/analysis/WeChatMessageClassifier.java`
- 等你给出准确的过滤规则（正则/关键词/组合条件）后，只需要改这一个类，其他代码不用动。

### 7.3 字典安装与生效

```bash
mysql -uroot -p 你的库名 < C:\Project\RuoYi-Vue\sql\wechat_analysis_dict.sql
```
导入后刷新字典缓存，否则服务读不到新值：
```
POST /system/dict/data/refreshCache   （带管理员 token）
```
或在「系统管理 → 字典管理」页面把 `whiteNames` / `blackNames` 填好（逗号拼接备注名）后重新加载。
改完字典需要**重启 `ruoyi-admin`** 才会重新读（当前是启动时/调用时按需读缓存，重启最稳妥）。

---

## 8. 消息 JSON 字段（REST 返回 camelCase，与 Java POJO 一致）

| 字段 | 含义 |
| ---- | ---- |
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

---

## 10. 合规与安全提醒
- 读取通过解密微信本地数据库实现（PyWxDump 同类机制），**只用于你自己账号的数据**。
- 自动发送是 UIA 操作，存在微信风险控制；务必控制频率、用活跃老号、
  **不要营销/加粉/广告**，遇「操作频繁/登录异常」提示立即停并人工登录。
- 发送接口目前在匿名白名单里，生产环境建议加上鉴权或限制来源 IP。

---

## 10. 消息分类规则（INQ / OFFER / TRACE / OTHER）

第二层分析由 Java 侧完成（`com.ruoyi.wexinAuto.analysis`），分类结果赋值给字段 `msg_category`
（JSON 输出为 `msg_category`）。分类器链顺序：**OFFER → INQ → TRACE → OTHER**
（OFFER 优先，因为带 INQ 字样的报价单仍是报价，如 `INQ\n... 80 pcs 3650`）。

### OFFER（报价）命中任一即可
- **价格证据行**：货币符号/缩写（`$`、`USD`、`RMB`、`CNY`、`¥`、`€`）、
  `型号 - 数量 pcs 价格` 报价行、`型号+数字`（数字前必须有空格/按/以/: 分隔，
  如 `5955WX 470`、`6544Y按3580`）。注意 `TP1234` 是目标价会被剔除，不算价格证据。
- **引用回复带价格**：引用内容非空且正文 ≤50 字，如 `这个950`、纯价格、含货币。
- **纯价格回复**（`2900`、`$3200`、`2100 30颗`）：回溯最近 5 条、跳过 OTHER 类，
  最近一条业务消息是**不同发言人**的 INQ → OFFER。
- **短补充**（`1wk`、`7-9days`，≤16 字）：上一条是 OFFER → OFFER。
- 中性词（收到/没/check/早/谢谢/好的/ok…）永远不算 OFFER。

### INQ（询价）命中任一即可（OFFER 不命中后）
- 字典关键词（`wechat_analysis_config` / `inqKeywords` / remark 列，逗号分隔）。
- 目标价 `TP1234`。
- 数量行且无价格证据（`5317 10pcs`、`9654 100个`、`这个60片 有吗？`）。

### TRACE（跟进）
- 字典关键词（`traceKeywords`）；内置词：更新/怎么样/考虑/确认/优惠/货期/排单/进度/再报/重新报/数量有多少/有多少/数量多少/给具体价格 等。

### 库存数量回答（例外，归 OTHER）
- 上一条是“数量有多少”类追问（TRACE），本条是 `型号 数字` 列表
  （如 `WUH721816ALE6L4 500\nMG10ACA20TE 40`）→ OTHER，不误判为 OFFER/INQ。

### 规则如何调整
- **关键词**改数据字典即可：`sys_dict_data` 表，`dict_type='wechat_analysis_config'`，
  改完刷新字典缓存或重启后端（目前重启后端加载）。
- **结构/上下文规则**改 Java：`analysis/rule/` 每个规则一个实现类，
  在 `analysis/OfferMessageFilter` / `InqMessageFilter` / `TraceMessageFilter` 的 `buildRules()` 内组合，
  改完 `service.ps1 build` + `start` 生效。
- **14 个真实场景验证脚本**：`C:\Users\亦钟人\Documents\Codex\2026-10-03\hi-2\outputs\simulate_inq_offer.py`
  （38/38 通过，改规则后可重跑回归）。

### 查询性能说明
- `dump --since ...` 采用全库增量扫描：每个分片库只打开一次连接，对每个会话表
  `Msg_<md5(会话)>` 执行 `WHERE sort_seq > since_seq`，跨表合并升序，
  会话多时（如 800+ 会话）从 180s+ 降到约 7s。