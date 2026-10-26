# -*- coding: utf-8 -*-
"""wechatauto 消息网关 —— 给外部程序(如 Java)读取微信本地数据库的桥接层

运行环境: 单一 Python 环境(C:\\Software\\Python), 已装 wechatauto-replica。
不新建 venv。Java 每次需要时跑一下本脚本, 读完 stdout 即可, 无需常驻服务。

用法:

  1) 主动拉取(带时间/可选会话, 结果作为 NDJSON 打到 stdout):
     python wx_gateway.py dump [--since "2026-10-05 12:00:00"] [--chat "群名或好友名"]
                               [--out wx_history.db] [--media 目录] [--limit N]
     入选入参:
       --since   起始时间(到秒, 闭区间: >= 该时刻的消息), 不填=全部历史。
                 格式 "YYYY-MM-DD HH:MM:SS" / "YYYY-MM-DD HH:MM" / "YYYY-MM-DD"
       --chat    群名或好友名(可选)。不填=拉所有会话; 填=只拉该会话。
       --out     可选。填了才追加写 SQLite 记录(重复运行增量去重); 不填只打 stdout。
       --media   可选。填了才下载图片/语音/视频/文件到该目录。
     输出约定: 每条消息一行 NDJSON 到 stdout; 诊断/进度一律到 stderr。

  2) 实时监听新消息(所有会话; NDJSON 一行一条到 stdout, 水位自动持久化):
     python wx_gateway.py listen [--media 目录]

  3) 按消息键下载单条媒体(供聊天记录页面按需查看/下载):
     python wx_gateway.py media --username <会话ID> --local-id <库内消息ID> --media <目录>

  消息 NDJSON 字段:
    username  会话(微信号/群id)  name       会话显示名
    local_id  库内ID             type       文本/图片/语音/视频/文件/系统...
    sender    发言人的显示名      content    正文
    media_path 已下载媒体绝对路径 create_time 格式化字符串 YYYY-MM-DD HH:MM:SS
    sort_seq  排序键(增量断点)    md5        消息md5(媒体消息)
"""
from __future__ import annotations
import argparse, atexit, datetime, json, os, random, re, shutil, sqlite3, sys, tempfile, time, xml.etree.ElementTree as ET

try:
    os.system("chcp 65001 >nul 2>&1")
except Exception:
    pass
for _s in (sys.stdin, sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

MSG_FIELDS = ["username", "name", "local_id", "type", "sender",
              "content", "media_path", "create_time", "sort_seq", "md5"]


def _proc_root():
    """wechatauto 临时解密目录根：%TEMP%/wechatauto_db/_proc/<pid>/"""
    return os.path.join(tempfile.gettempdir(), "wechatauto_db", "_proc")


def _pid_alive(pid):
    try:
        import psutil
        return psutil.pid_exists(pid)
    except Exception:
        pass
    try:
        os.kill(pid, 0)
        return True
    except (OSError, ValueError):
        return False


def cleanup_stale_proc_dirs():
    """启动时清扫历史遗留：_proc/<pid> 中 pid 已不存在的目录直接删除。

    wechatauto 每次运行会把解密的消息库(约79MB/份)写到 %TEMP%/wechatauto_db/_proc/<pid>/，
    进程异常退出(强杀/断电)会留下垃圾。网关被高频调用(定时轮询 dump)时如不清理会堆出十几 GB。
    """
    root = _proc_root()
    try:
        if not os.path.isdir(root):
            return
        removed = 0
        for name in os.listdir(root):
            if not name.isdigit():
                continue
            pid_dir = os.path.join(root, name)
            if _pid_alive(int(name)):
                continue  # 进程还活着(可能是并发中的 dump/listen)，跳过避免误删
            try:
                shutil.rmtree(pid_dir)
                removed += 1
            except Exception as e:
                log("[清理] 删除遗留目录失败 %s: %s" % (pid_dir, e))
        if removed:
            log("[清理] 已删除 %d 个已退出进程的临时解密目录" % removed)
    except Exception as e:
        log("[清理] 扫描临时目录失败: %s" % e)


def cleanup_own_proc_dir():
    """进程退出时删除自己的临时解密目录，保证正常结束时不留垃圾。"""
    d = os.path.join(_proc_root(), str(os.getpid()))
    try:
        if os.path.isdir(d):
            shutil.rmtree(d)
            log("[清理] 已删除自身临时解密目录: %s" % d)
    except Exception as e:
        log("[清理] 删除自身临时目录失败 %s: %s" % (d, e))


atexit.register(cleanup_own_proc_dir)


def log(*a):
    print(*a, file=sys.stderr, flush=True)

def die(msg, code=2):
    log("[错误] %s" % msg)
    sys.exit(code)

def parse_since(text):
    """把用户输入的时间串解析成 epoch 秒(int)。闭区间语义由调用方用 >= 判断。"""
    if not text:
        return None
    s = (text or "").strip().replace("T", " ")
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d"):
        try:
            return int(datetime.datetime.strptime(s, fmt).timestamp())
        except ValueError:
            continue
    die("时间格式无法识别: %r（请用 2026-10-05 12:00:00）" % text)

def build_db(media_dir, account=None):
    from wechatauto import WeChatDB
    from wechatauto.media import MediaDownloader
    kwargs = {"account": account} if account else {}
    # 每个进程独占一个解密工作目录(按 PID 区分)：避免多个并行进程
    # (如监听的 fetch / listen 同时跑)在同一个临时目录里 os.replace 解密库
    # 时互相抢占同一个文件而触发 PermissionError([WinError 5] 拒绝访问)。
    kwargs["workdir"] = os.path.join(
        tempfile.gettempdir(), "wechatauto_db", "_proc", str(os.getpid()))
    db = WeChatDB(**kwargs)
    media = MediaDownloader(db, save_dir=media_dir) if media_dir else None
    return db, media

def load_ctx(db):
    info = db.get_self_info()
    return {
        "self_name": info.get("nick_name") or info.get("username") or "我",
        "nicks": db.nickname_map(),
    }

def resolve_sender(ctx, m):
    sid = m.get("sender_id")
    if sid == 2:
        return ctx["self_name"]
    su = sender_from_prefix(m.get("content"))
    if su:
        return ctx["nicks"].get(su, su)
    su = m.get("sender_username") or m.get("username") or ""
    return ctx["nicks"].get(su, su) if su else ""

def md5_of(m):
    c = str(m.get("content") or "")
    t = re.search(r"md5=([0-9a-fA-F]{32})", c)
    if t:
        return t.group(1).lower()
    return m.get("md5") or ""

def resolve_chat(db, name):
    """按群名/好友名/备注/昵称定位会话，返回 (username, display_name) 或 None。"""
    name = (name or "").strip()
    if not name:
        return None
    # 1) 群名
    try:
        gid = db.group_name_to_id(name)
        if gid:
            return gid, db.get_nickname(gid) or name
    except Exception as e:
        log("  [按群名查找失败] %s" % e)
    # 2) 昵称/备注精确匹配（含好友与群；contact 表有 @chatroom 行）
    try:
        u = db.username_by_nickname(name)
        if u:
            return u, db.get_nickname(u) or name
    except Exception as e:
        log("  [按昵称查找失败] %s" % e)
    # 3) 模糊搜索联系人
    try:
        for h in db.search_contact(name):
            u, nick = h["username"], h["nick_name"] or h["remark"] or h["username"]
            if name in (nick, h.get("remark") or "", h["username"]):
                return u, nick
    except Exception as e:
        log("  [search_contact 失败] %s" % e)
    # 4) 在已有会话里按显示名兜底
    try:
        for c in db.list_message_chats():
            if name in (c.get("name") or "") or name in (c.get("username") or ""):
                return c["username"], c.get("name") or c["username"]
    except Exception as e:
        log("  [list_message_chats 兜底失败] %s" % e)
    return None

def load_existing(conn):
    """已入库的 (username,local_id) 集合，用于增量去重。"""
    if conn is None:
        return set()
    try:
        rows = conn.execute("SELECT username, local_id FROM messages").fetchall()
        return {(r[0], r[1]) for r in rows}
    except Exception:
        return set()

def _schema(conn):
    conn.execute("""CREATE TABLE IF NOT EXISTS chats(
        username TEXT PRIMARY KEY, name TEXT)""")
    conn.execute("""CREATE TABLE IF NOT EXISTS messages(
        username TEXT, local_id INT,
        type TEXT, sender TEXT, content TEXT, media_path TEXT,
        create_time INT, sort_seq INT, md5 TEXT,
        PRIMARY KEY(username, local_id))""")

def cmd_dump(args):
    since = parse_since(args.since)
    db, media = build_db(args.media, args.account)
    ctx = load_ctx(db)

    conn = None
    if args.out:
        conn = sqlite3.connect(args.out)
        _schema(conn)
    existing = load_existing(conn)

    def emit(m, user, name):
        m["__session"] = user
        key = (user, m["local_id"])
        if key in existing:
            return "skip"
        j = row_to_json(db, ctx, m, media, name)
        existing.add(key)
        sys.stdout.write(json.dumps(j, ensure_ascii=False) + "\n")
        sys.stdout.flush()
        if conn:
            conv = (j["username"], j["local_id"], j["type"], j["sender"],
                    j["content"], j["media_path"], m.get("create_time") or 0,
                    j["sort_seq"], j["md5"])
            conn.execute("INSERT OR IGNORE INTO messages VALUES(?,?,?,?,?,?,?,?,?)", conv)
        return "add"

    if since is not None:
        since_seq = max(0, int(since * 1000.0) - 1)
        only_user = None
        if args.chat:
            hit = resolve_chat(db, args.chat)
            if not hit:
                die("没有找到会话: %r（试试不用全名，或检查是否与群/微信号一致）" % args.chat)
            only_user, disp = hit
            log("会话已锁定: %s (%s)" % (disp, only_user))
        hits = _global_messages_after(db, since_seq, args.limit, only_user=only_user)
        log("增量扫描: sort_seq>%d, 命中 %d 条" % (since_seq, len(hits)))
        added = skip = 0
        for name, user, m in hits:
            if emit(m, user, name) == "add":
                added += 1
            else:
                skip += 1
        if conn:
            conn.commit(); conn.close()
        log("完成: 新增 %d, 跳过重复 %d" % (added, skip))
        return

    # 无 since：保持原来的全量/指定会话逻辑
    if args.chat:
        hit = resolve_chat(db, args.chat)
        if not hit:
            die("没有找到会话: %r（试试不用全名，或检查是否与群/微信号一致）" % args.chat)
        user, disp = hit
        chats = [{"username": user, "name": disp}]
        log("会话已锁定: %s (%s)" % (disp, user))
    else:
        chats = _list_message_chats_with_retry(db)
        log("共发现 %d 个含消息的会话" % len(chats))
    if not chats:
        die("没有可读取的会话")

    added = skip = hit = 0
    for i, chat in enumerate(chats, 1):
        user, name = chat["username"], chat["name"]
        msgs = db.get_messages(user, limit=args.limit)
        xs = list(reversed(msgs))  # get_messages 为降序，反转成正序
        if conn:
            conn.execute("INSERT OR REPLACE INTO chats VALUES(?,?)", (user, name))
        for m in xs:
            if emit(m, user, name) == "add":
                added += 1
            else:
                skip += 1
        if conn:
            conn.commit()
        log("[%d/%d] %s: 命中 %d 条(新增 %d)" % (i, len(chats), name, len(msgs), added))
    if conn:
        conn.commit(); conn.close()
    log("完成: 新增 %d, 跳过重复 %d" % (added, skip))


def _global_messages_after(db, since_seq, limit, only_user=None):
    """全库增量扫描：每个分片库只打开一次连接，对每个会话表
    Msg_<md5(username)> 执行 WHERE sort_seq > since_seq，跨表合并按 sort_seq 升序。
    相比逐会话 get_new_messages（每次都重开全部分片连接），会话多时快几十倍。"""
    import hashlib
    want = max(1, int(limit or 1000000))
    cols = ("local_id, local_type, real_sender_id, create_time, message_content, "
            "source, packed_info_data, compress_content, server_id, sort_seq")
    # Windows decrypt files are replaced via os.replace. Enumerate chats first and release shard handles.
    chats = _list_message_chats_with_retry(db)
    conns = []
    try:
        for rel in db._message_dbs():
            conns.append(_open_message_db_with_retry(db, rel))
        hits = []
        for c in chats:
            user = str(c.get("username") or "")
            if not user:
                continue
            if only_user and user != only_user:
                continue
            target = "Msg_" + hashlib.md5(user.encode("utf-8")).hexdigest()
            for conn in conns:
                try:
                    if conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (target,)).fetchone():
                        rows = conn.execute(
                            "SELECT %s FROM %s WHERE sort_seq > ? ORDER BY sort_seq ASC, local_id ASC LIMIT %d" % (cols, target, want),
                            (since_seq,)).fetchall()
                        for r in rows:
                            d = db._msg_row_to_dict(r)
                            hits.append((c.get("name") or user, user, d))
                except sqlite3.DatabaseError:
                    continue
        hits.sort(key=lambda x: ((x[2].get("sort_seq") or 0), x[1]))
        return hits[:want]
    finally:
        for conn in conns:
            try:
                conn.close()
            except Exception:
                pass


def _open_message_db_with_retry(db, rel, attempts=5):
    last = None
    for i in range(max(1, attempts)):
        try:
            return db._open(rel)
        except PermissionError as e:
            last = e
            if i + 1 >= attempts:
                raise
            time.sleep(0.3 * (i + 1))
    raise last


def _list_message_chats_with_retry(db, attempts=5):
    last = None
    for i in range(max(1, attempts)):
        try:
            return db.list_message_chats()
        except PermissionError as e:
            last = e
            if i + 1 >= attempts:
                raise
            time.sleep(0.3 * (i + 1))
    raise last


# ---- 文本消息清洗：去掉库内多余的发送者前缀行 ----
_SENDER_PREFIX_STRICT = re.compile(r"^wxid_[A-Za-z0-9_-]+:$")
# 非 wxid_ 前缀（普通微信号/手机号/@openim）：小写字母或数字开头，避免误匹配 "OFFER:" 之类大写缩写
_SENDER_PREFIX_LOOSE = re.compile(r"^[a-z0-9][a-z0-9_@.\-]*:$")

def sender_from_prefix(content):
    """从消息内容首行提取发送者 user_name；无前缀返回 None。"""
    if not isinstance(content, str):
        return None
    first = content.split("\n", 1)[0].strip()
    if _SENDER_PREFIX_STRICT.match(first) or _SENDER_PREFIX_LOOSE.match(first):
        return first[:-1]
    return None

def strip_wxid_prefix(content):
    """去掉 content 首行的发送者前缀行（wxid_xxx: / jingfan5807: 等），返回真实正文；无前缀原样返回。"""
    if not isinstance(content, str):
        return content
    lines = content.split("\n", 1)
    first = lines[0].rstrip("\r")
    if _SENDER_PREFIX_STRICT.match(first.strip()) or _SENDER_PREFIX_LOOSE.match(first.strip()):
        return lines[1] if len(lines) > 1 else ""
    return content

# ---- 引用消息(卡片)解析：把 XML 拆成「原文 + 被引用内容」 ----
_QUOTE_LABEL = {"3": "图片", "47": "表情", "34": "语音", "43": "视频", "49": "卡片"}

def _quote_kind(qt):
    """把微信原始引用类型映射成可读类型：text/image/emoji/voice/video/card。"""
    qt = (qt or "").strip()
    if qt == "1" or not qt:
        return "text"
    return _QUOTE_LABEL.get(qt, "other")

def parse_quote(content):
    """若 content 是带 <refermsg> 的卡片 XML，返回 (原文txt, 引用展示txt, 引用类型)；
    否则返回 (content, "", "")。引用为文本时只取前 256 字；图片/表情/语音/视频/卡片只给说明。"""
    if not isinstance(content, str):
        return content, "", ""
    low = content.strip()
    # 原始内容可能带发送者前缀(如 "wxid_xxx:\n" 或 "xxx:\n")，先定位 XML 本体
    m = re.search(r"<msg[\s\S]*?</msg>", low)
    if not m:
        return content, "", ""
    xml_str = m.group(0)
    try:
        root = ET.fromstring(xml_str)
    except Exception:
        return content, "", ""
    app = root.find("appmsg")
    refer = (app.find("refermsg") if app is not None else None) or root.find(".//refermsg")
    if refer is None:
        return content, "", ""
    # 原文：优先取卡片标题/摘要，否则保留原 content
    own = (app.findtext("title") or "").strip() if app is not None else ""
    if not own and app is not None:
        own = (app.findtext("des") or "").strip()
    qt = (refer.findtext("type") or "").strip()
    qtext = (refer.findtext("content") or "").strip()
    kind = _quote_kind(qt)
    if kind in ("图片", "表情", "语音", "视频", "卡片"):
        quote = "【%s】" % kind
    else:
        quote = qtext[:256] if qtext else ""
    return own, quote, kind

def row_to_json(db, ctx, m, media, name):
    media_path = ""
    mtype = m.get("type")
    raw_content = m.get("content") or ""
    is_file = mtype == "文件/链接/卡片" and "<fileext>" in raw_content.lower()
    if media is not None and (mtype in ("图片", "语音", "视频", "文件") or is_file):
        try:
            media_path = media.download_media(m.get("__session") or m.get("username"),
                                              m["local_id"], save_dir=media.save_dir) or ""
        except Exception as e:
            log("  [媒体下载失败 %s/%s]: %s" % (m.get("__session"), m["local_id"], e))
            media_path = ""
    # 先去掉库内多余的 wxid_xxx: 前缀行，再拆解引用/卡片消息
    content, quote, quote_kind = parse_quote(strip_wxid_prefix(m.get("content") or ""))
    ct = m.get("create_time") or 0
    # 业务去重键：会话显示名 + 会话ID + 消息产生时间戳（格式 name:username:1791250634）
    session_name = str(name or "")
    session_uid = str(m.get("__session") or m.get("username") or "")
    server_id = ("%s:%s:%s" % (session_name, session_uid, ct)) if ct else ""
    return {
        "username": m.get("__session") or m.get("username"),
        "name": name,
        "local_id": m["local_id"],
        "server_id": server_id,
        "type": mtype,
        "sender": resolve_sender(ctx, m),
        "content": content,
        "quote": quote,
        "quote_kind": quote_kind,
        "media_path": media_path,
        "create_time": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ct)) if ct else "",
        "sort_seq": m.get("sort_seq") or 0,
        "md5": md5_of(m),
    }

def cmd_listen(args):
    from wechatauto.db import Listener
    db, media = build_db(args.media, args.account)
    ctx = load_ctx(db)
    # 水位放到稳定的账号级目录；工作目录按 PID 变化时不能作为断点位置。
    watermark = os.path.join(
        tempfile.gettempdir(), "wechatauto_db", db.account, "listener_watermark.json")
    if getattr(args, "fresh", False):
        # 从现在开始：清掉上次持久化的水位，只推送启动之后的新消息（不回放历史）
        try:
            os.remove(watermark)
            log("已清空监听水位，只推送从现在起的新消息 (fresh)")
        except OSError:
            pass
    listener = Listener(db, interval=1.0, watermark_file=watermark)

    def on_new(m, lsn):
        try:
            user = m.get("username") or m.get("__session") or ""
            name = db.get_nickname(user) or user
            m["__session"] = user
            j = row_to_json(db, ctx, m, media, name)
            sys.stdout.write(json.dumps(j, ensure_ascii=False) + "\n")
            sys.stdout.flush()
        except Exception as e:
            log("listener 处理异常:", e)

    listener.add_all(on_new, discover=True)
    log("开始监听所有会话的新消息(实时)，Ctrl+C 退出...")
    listener.start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        listener.stop()
        log("监听已停止")


def cmd_batch_send(args):
    """从 stdin 读取 JSON 配置，串行批量发送，逐条结果 NDJSON 到 stdout。

    配置字段:
      names      [会话名...]  必填(或 targets)
      targets    [{name,text}...]  可选, 与 names 二选一(可逐条自定义文案)
      template   文本模板, 含 {name} 时按目标名替换
      rhythm     calm/fast/off(拟人节奏档位, 默认 calm)
      nap        [lo, hi]  等待时长抖动倍率(>=1.0)
      interval   [min, max]  目标之间随机间隔(秒)
      max_batch  单批上限, 超限直接拒绝(安全)
      verify     是否回读数据库确认发送(默认 true, 防重复)
    """
    try:
        cfg = json.load(sys.stdin)
    except Exception as e:
        die("读取 stdin JSON 失败: %s" % e)
    targets = cfg.get("targets") or []
    template = (cfg.get("template") or "").strip()
    names = cfg.get("names") or []
    if not targets and names:
        targets = [{"name": n} for n in names]
    if not targets:
        die("未提供 names/targets")

    max_batch = int(cfg.get("max_batch") or 20)
    if len(targets) > max_batch:
        print(json.dumps({"ok": False, "index": 0, "name": "", "text": "",
                          "status": "错误",
                          "message": "超过单批上限 %d 个，已拒绝发送" % max_batch,
                          "duration_ms": 0}, ensure_ascii=False))
        sys.stdout.flush()
        return
    verify = bool(cfg.get("verify", True))
    rhythm = (cfg.get("rhythm") or "calm").lower()
    nap = cfg.get("nap") or []
    nap_min = float(nap[0]) if len(nap) > 0 else 1.0
    nap_max = float(nap[1]) if len(nap) > 1 else 1.6
    if nap_max < nap_min:
        nap_max = nap_min
    interval = cfg.get("interval") or []
    iv_min = float(interval[0]) if len(interval) > 0 else 6.0
    iv_max = float(interval[1]) if len(interval) > 1 else 15.0
    if iv_max < iv_min:
        iv_max = iv_min

    os.environ.setdefault("WECHATAUTO_RHYTHM", rhythm)
    try:
        import wechatauto.rhythm as rhythm_mod
        rhythm_mod.configure(nap=(nap_min, nap_max))
        log("拟人节奏: %s, nap=%.2f~%.2f" % (rhythm, nap_min, nap_max))
    except Exception as e:
        log("配置拟人节奏失败(继续用默认): %s" % e)

    log("批量发送: %d 条, 间隔[%.1f, %.1f]s, verify=%s, 上限=%d"
        % (len(targets), iv_min, iv_max, verify, max_batch))
    try:
        from wechatauto.guia import WeChatGUI
        gui = WeChatGUI()
    except Exception as e:
        die("初始化 WeChatGUI 失败（微信是否已登录且窗口可见？）: %s" % e)
    log("微信窗口已定位 hwnd=%s", gui.main_hwnd if hasattr(gui, "main_hwnd") else "?")

    ok_total = 0
    for i, t in enumerate(targets):
        name = (t.get("name") or "").strip()
        text = (t.get("text") or "").strip()
        if not text:
            text = template.replace("{name}", name).strip() if template else ""
        if not name or not text:
            out = {"ok": False, "index": i, "name": name, "text": text,
                   "status": "错误", "message": "name 或 text 为空，已跳过", "duration_ms": 0}
            sys.stdout.write(json.dumps(out, ensure_ascii=False) + "\n")
            sys.stdout.flush()
            continue
        # 每条前加一点扰动，再执行
        time.sleep(random.uniform(nap_min, nap_min + 0.8))
        t0 = time.time()
        try:
            resp = gui.send_msg(text, who=name, verify=verify)
            d = resp.to_dict()
            ok = (d.get("status") == "成功")
            out = {"ok": ok, "index": i, "name": name, "text": text,
                   "status": d.get("status"), "message": d.get("message"),
                   "duration_ms": int((time.time() - t0) * 1000)}
            log("  [%d/%d] %s => %s (%dms)" % (i + 1, len(targets), name,
                d.get("status"), out["duration_ms"]))
        except Exception as e:
            log("  [%d/%d] %s => 异常 %s" % (i + 1, len(targets), name, e))
            out = {"ok": False, "index": i, "name": name, "text": text,
                   "status": "异常", "message": str(e),
                   "duration_ms": int((time.time() - t0) * 1000)}
        sys.stdout.write(json.dumps(out, ensure_ascii=False) + "\n")
        sys.stdout.flush()
        if out.get("ok"):
            ok_total += 1
        if i < len(targets) - 1:
            time.sleep(random.uniform(iv_min, iv_max))
    log("批量发送结束: 成功 %d/%d" % (ok_total, len(targets)))


def cmd_whoami(args):
    """输出当前登录微信账号，供自动发送前校验固定账号。"""
    db, _ = build_db(None, args.account)
    info = db.get_self_info() or {}
    out = {
        "username": info.get("username") or info.get("user_name") or "",
        "nick_name": info.get("nick_name") or info.get("nickname") or "",
    }
    print(json.dumps(out, ensure_ascii=False))
    sys.stdout.flush()


def cmd_media(args):
    """下载指定消息的图片或文件，成功后输出 MEDIA_PATH=<绝对路径>。"""
    db, media = build_db(args.media, args.account)
    if media is None:
        die("未配置 --media 目录")
    path = media.download_media(args.username, args.local_id, save_dir=args.media)
    if not path:
        die("媒体文件未下载或本机不存在: %s/%s" % (args.username, args.local_id))
    print("MEDIA_PATH=" + os.path.abspath(path))
    sys.stdout.flush()


def main():
    ap = argparse.ArgumentParser(description="wechatauto 消息网关（主动拉取/实时监听）")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("dump", help="主动拉取：按时间(可选会话)拉聊天记录，NDJSON 到 stdout")
    p.add_argument("--since", default=None,
                   help="起始时间，含该秒起，如 2026-10-05 12:00:00；不填=全部")
    p.add_argument("--chat", default=None, help="群名/好友名，可选；不填=所有会话")
    p.add_argument("--out", default=None, help="可选：追加写 SQLite 记录(增量去重)")
    p.add_argument("--media", default=None, help="可选：下载图片/语音/视频/文件到该目录")
    p.add_argument("--limit", type=int, default=1000000, help="每会话最多条数")
    p.add_argument("--account", default=None)
    p.set_defaults(func=cmd_dump)

    p = sub.add_parser("listen", help="实时监听所有会话新消息(NDJSON 到 stdout)")
    p.add_argument("--media", default=None)
    p.add_argument("--account", default=None)
    p.add_argument("--fresh", action="store_true",
                   help="从现在开始：清掉持久化水位，只推送启动之后的新消息")
    p.set_defaults(func=cmd_listen)

    p = sub.add_parser("batch_send",
                       help="批量发送(从 stdin 读 JSON 配置)，逐条结果 NDJSON 到 stdout")
    p.set_defaults(func=cmd_batch_send)

    p = sub.add_parser("whoami", help="输出当前登录微信账号(JSON)")
    p.add_argument("--account", default=None)
    p.set_defaults(func=cmd_whoami)

    p = sub.add_parser("media", help="按消息键下载单条媒体，输出 MEDIA_PATH")
    p.add_argument("--username", required=True, help="会话ID(wxid/群id)")
    p.add_argument("--local-id", required=True, type=int, help="库内消息ID")
    p.add_argument("--media", required=True, help="媒体保存目录")
    p.add_argument("--account", default=None)
    p.set_defaults(func=cmd_media)
    args = ap.parse_args()
    cleanup_stale_proc_dirs()
    try:
        args.func(args)
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    main()






