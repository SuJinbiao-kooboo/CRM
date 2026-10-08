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
| `create_time` | 秒时间戳 |
| `sort_seq` | 排序键（增量断点） |
| `md5` | 媒体消息 md5 |

## 4) 长期记录（可选）
`dump --out X.db` 会追加写 SQLite（表 `chats(username,name)`、`messages(...)`，
主键 `(username,local_id)` 去重）。Java 用 `org.xerial:sqlite-jdbc` 直接读。

## 合规提醒
- 读取通过解密微信本地库实现（PyWxDump 同类机制），请只用于你自己账号的数据。
- 自动**发送**消息请另走包内 `WeChatGUI/quick_send`（UIA）路线，与读取解耦。
