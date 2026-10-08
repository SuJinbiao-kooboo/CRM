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

  消息 NDJSON 字段:
    username  会话(微信号/群id)  name       会话显示名
    local_id  库内ID             type       文本/图片/语音/视频/文件/系统...
    sender    发言人的显示名      content    正文
    media_path 已下载媒体绝对路径 create_time 秒时间戳
    sort_seq  排序键(增量断点)    md5        消息md5(媒体消息)
"""
from __future__ import annotations
import argparse, datetime, json, os, re, sqlite3, sys, time

try:
    os.system("chcp 65001 >nul 2>&1")
except Exception:
    pass
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

MSG_FIELDS = ["username", "name", "local_id", "type", "sender",
              "content", "media_path", "create_time", "sort_seq", "md5"]

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

    # 决定拉哪些会话
    if args.chat:
        hit = resolve_chat(db, args.chat)
        if not hit:
            die("没有找到会话: %r（试试不用全名，或检查是否与群/微信号一致）" % args.chat)
        user, disp = hit
        chats = [{"username": user, "name": disp}]
        log("会话已锁定: %s (%s)" % (disp, user))
    else:
        chats = db.list_message_chats()
        log("共发现 %d 个含消息的会话" % len(chats))
    if not chats:
        die("没有可读取的会话")

    # 可选落 SQLite
    conn = None
    if args.out:
        conn = sqlite3.connect(args.out)
        _schema(conn)
    existing = load_existing(conn)

    cond = " >= %s" % (time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(since))) if since else "全部"
    log("时间窗: 起始%s(含)。合计 %d 会话" % (cond, len(chats)))
    added = skip = hit = 0
    for i, chat in enumerate(chats, 1):
        user, name = chat["username"], chat["name"]
        msgs = db.get_messages(user, limit=args.limit)
        if since is not None:
            msgs = [m for m in msgs if (m.get("create_time") or 0) >= since]
        xs = list(reversed(msgs))  # 保证按时间正序输出
        if conn:
            conn.execute("INSERT OR REPLACE INTO chats VALUES(?,?)", (user, name))
        for m in xs:
            m["__session"] = user
            key = (user, m["local_id"])
            if key in existing:
                skip += 1
                continue
            j = row_to_json(db, ctx, m, media, name)
            existing.add(key)
            sys.stdout.write(json.dumps(j, ensure_ascii=False) + "\n")
            sys.stdout.flush()
            if conn:
                conv = (j["username"], j["local_id"], j["type"], j["sender"],
                        j["content"], j["media_path"], j["create_time"],
                        j["sort_seq"], j["md5"])
                conn.execute("INSERT OR IGNORE INTO messages VALUES(?,?,?,?,?,?,?,?,?)", conv)
            added += 1
        if conn:
            conn.commit()
        log("[%d/%d] %s: 命中 %d 条(新增 %d)" % (i, len(chats), name, len(msgs), added))
    if conn:
        conn.commit(); conn.close()
    log("完成: 新增 %d, 跳过重复 %d" % (added, skip))

def row_to_json(db, ctx, m, media, name):
    media_path = ""
    mtype = m.get("type")
    if media is not None and mtype in ("图片", "语音", "视频", "文件"):
        try:
            media_path = media.download_media(m.get("__session") or m.get("username"),
                                              m["local_id"], save_dir=media.save_dir) or ""
        except Exception as e:
            log("  [媒体下载失败 %s/%s]: %s" % (m.get("__session"), m["local_id"], e))
            media_path = ""
    return {
        "username": m.get("__session") or m.get("username"),
        "name": name,
        "local_id": m["local_id"],
        "type": mtype,
        "sender": resolve_sender(ctx, m),
        "content": m.get("content") or "",
        "media_path": media_path,
        "create_time": m.get("create_time") or 0,
        "sort_seq": m.get("sort_seq") or 0,
        "md5": md5_of(m),
    }

def cmd_listen(args):
    from wechatauto.db import Listener
    db, media = build_db(args.media, args.account)
    ctx = load_ctx(db)
    watermark = os.path.join(db.workdir, "listener_watermark.json")
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
    p.set_defaults(func=cmd_listen)

    args = ap.parse_args()
    try:
        args.func(args)
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    main()
