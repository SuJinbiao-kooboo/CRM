# -*- coding: utf-8 -*-
"""wechatauto 消息网关 —— 给外部程序(如 Java)读取微信本地数据库的桥接层

运行环境: 项目内 Python 3.12 (C:\\Software\\GPT\\runtime\\python312\\python.exe)，已装 wechatauto-replica 和 winsdk。
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


def configure_wechatauto(verbose=False):
    """按当前环境设置发送回读超时和 OCR 开关。

    Python 3.13+ 没有 winsdk 预编译轮子；缺 winsdk 时 guia 的 OCR 路径会直接
    抛 ModuleNotFoundError。这里提前关掉 OCR，让相关操作按“OCR 不可用”
    优雅降级到 UIA，而不是把发送任务打成异常。
    """
    try:
        from wechatauto.param import WxParam
    except Exception as e:
        log("[提示] wechatauto 参数模块加载失败: %s" % e)
        return
    try:
        WxParam.DB_VERIFY_TIMEOUT = float(os.environ.get("WECHAT_VERIFY_TIMEOUT", "20"))
    except ValueError:
        WxParam.DB_VERIFY_TIMEOUT = 20.0
    try:
        import winsdk  # noqa: F401
    except Exception:
        WxParam.ENABLE_OCR = False
        if verbose:
            log("[提示] 未检测到 winsdk：OCR 已关闭，发送只走 UIA；"
                "需要 OCR 兜底请改用 64 位 Python 3.12 并安装 winsdk")


def patch_send_verify():
    """修正发送回读：不依赖写死的 sender_id == 2。

    wechatauto 1.2.9 的 guia._verify_sent 用 ``sender_id == 2`` 判断“这条是我
    自己发的”。微信 4.1.15.56 的 real_sender_id 是分片 Name2Id 的 rowid，
    自己的编号不一定是 2，于是消息明明已落库也会被判成“未确认”。这里改成
    按当前账号 username 匹配发送人；解析不到发送人时退回“正文 + 水位”匹配。
    """
    from wechatauto.guia import WeChatGUI

    def _verify_sent(self, text, who, mode='exact', after=None):
        if not text:
            return False
        try:
            db = self._get_db()
            self_username = (db.get_self_info() or {}).get('username') or ''
            marked = (after or {}).get('username')
            names = [marked] if marked else self._verify_usernames(who)
            for uname in names:
                for m in db.get_messages(uname, limit=8):
                    seq = int(m.get('sort_seq') or 0)
                    if after:
                        if seq < after['seq']:
                            continue
                        if (seq, int(m.get('local_id') or 0)) in after['ids']:
                            continue
                    sender = m.get('sender_username') or ''
                    if sender and self_username and sender != self_username:
                        continue
                    content = m.get('content') or ''
                    if (content == text) if mode == 'exact' else (text in content):
                        return True
        except Exception as e:
            log("[回读] 校验异常: %s" % e)
        return False

    WeChatGUI._verify_sent = _verify_sent


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
    info = db.get_self_info() or {}
    return {
        "self_wxid": info.get("username") or getattr(db, "wxid", "") or "",
        "self_name": info.get("nick_name") or info.get("username") or "我",
        "nicks": db.nickname_map(),
    }

# 微信 4.x 的消息按 message_0.db / message_1.db / ... 分片存放，每个分片都有
# 自己的 Name2Id 表；消息行的 real_sender_id 是**当前分片** Name2Id 的 rowid。
# 旧实现统一查 message_resource.db 的 SenderName2Id，分片一换就会张冠李戴。
_SENDER_NAME_CACHE = {}


def _shard_name_index(conn, rel):
    """读取单个消息分片的 Name2Id(rowid -> user_name)，按分片缓存。"""
    if rel in _SENDER_NAME_CACHE:
        return _SENDER_NAME_CACHE[rel]
    idx = {}
    try:
        for rid, user_name in conn.execute("SELECT rowid, user_name FROM Name2Id"):
            if user_name:
                idx[int(rid)] = str(user_name)
    except sqlite3.Error as e:
        log("  [Name2Id 读取失败 %s] %s" % (rel, e))
    _SENDER_NAME_CACHE[rel] = idx
    return idx


def _sender_username_from_shard(conn, rel, sender_id):
    try:
        sid = int(sender_id)
    except (TypeError, ValueError):
        return ""
    idx = _shard_name_index(conn, rel)
    su = idx.get(sid, "")
    if su:
        return su
    # 常驻 listen 期间可能有新成员写入 Name2Id；缓存未命中时补查一次。
    try:
        row = conn.execute(
            "SELECT user_name FROM Name2Id WHERE rowid=? LIMIT 1", (sid,)
        ).fetchone()
        if row and row[0]:
            su = str(row[0])
            idx[sid] = su
    except sqlite3.Error:
        pass
    return su


def _lookup_shard_sender(db, user, local_id, sort_seq, shard_hint=""):
    """按会话+消息定位所在分片，再用该分片 Name2Id 解析发送者。

    仅用于 listen / 无 since 的 dump 这类拿不到分片上下文的入口；
    高频增量 dump 已在扫描时直接复用当前连接，无需再次查找。
    shard_hint 是消息行里的 ``shard`` 字段，命中时优先只查该分片。
    """
    import hashlib
    target = "Msg_" + hashlib.md5((user or "").encode("utf-8")).hexdigest()
    rels = db._message_dbs()
    hint = str(shard_hint or "").strip()
    if hint:
        hinted = [rel for rel in rels
                  if hint in (rel, os.path.basename(rel),
                              rel.replace(os.sep, "__"),
                              os.path.basename(rel).replace(os.sep, "__"))]
        if hinted:
            rels = hinted + [rel for rel in rels if rel not in hinted]
    for rel in rels:
        conn = None
        try:
            conn = _open_message_db_with_retry(db, rel)
            if not conn.execute(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (target,)
            ).fetchone():
                continue
            row = conn.execute(
                "SELECT real_sender_id FROM %s WHERE local_id=? AND sort_seq=? LIMIT 1" % target,
                (local_id, sort_seq),
            ).fetchone()
            if row:
                return _sender_username_from_shard(conn, rel, row[0]), True
        except sqlite3.DatabaseError as e:
            log("  [分片发送者解析失败 %s] %s" % (rel, e))
        finally:
            if conn is not None:
                try:
                    conn.close()
                except Exception:
                    pass
    return "", False


def _chat_sender_map(db, user, msgs):
    """无 since 全量 dump 用：一次读取该会话所在分片的 Name2Id，建立消息键映射。"""
    import hashlib
    target = "Msg_" + hashlib.md5((user or "").encode("utf-8")).hexdigest()
    seqs = [int(m.get("sort_seq") or 0) for m in (msgs or []) if m.get("sort_seq")]
    if not seqs:
        return {}
    lo, hi = min(seqs), max(seqs)
    out = {}
    for rel in db._message_dbs():
        conn = None
        try:
            conn = _open_message_db_with_retry(db, rel)
            if not conn.execute(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (target,)
            ).fetchone():
                continue
            idx = _shard_name_index(conn, rel)
            rows = conn.execute(
                "SELECT local_id, sort_seq, real_sender_id FROM %s "
                "WHERE sort_seq BETWEEN ? AND ?" % target, (lo, hi),
            ).fetchall()
            for r in rows:
                out[(int(r[0]), int(r[1]))] = idx.get(int(r[2]), "")
        except sqlite3.DatabaseError as e:
            log("  [分片发送者索引失败 %s] %s" % (rel, e))
        finally:
            if conn is not None:
                try:
                    conn.close()
                except Exception:
                    pass
    return out


def resolve_sender(ctx, m, session_user="", session_name=""):
    """解析发言人。

    优先级：
      1. 当前消息分片 Name2Id 映射出的 user_name（本机精确映射）；
      2. 分片映射缺失时，才用正文首行前缀兜底；普通微信号前缀必须在联系人
         映射中命中，避免把 ``inq:`` / ``offer:`` 这类正文误当发言人；
      3. 单聊直接按会话对象显示（自己/对方）。
    禁止使用 message_resource.SenderName2Id 或群会话 username 兜底，二者都会张冠李戴。
    """
    self_wxid = str(ctx.get("self_wxid") or "")
    mtype = str(m.get("type") or "")
    content = str(m.get("content") or "")
    if mtype in ("系统消息", "系统") or content.lstrip().startswith("<sysmsg"):
        return "系统消息"
    # 拍一拍是 appmsg type=62 的互动卡片，real_sender_id 可能指向未命名 rowid。
    # 这不是聊天发言人，统一标成系统消息，避免列表出现无法解释的空发送人。
    if mtype == "文件/链接/卡片" and re.search(r"<type>\s*62\s*</type>", content):
        return "系统消息"
    su = str(m.get("sender_username") or "").strip()
    if su.isdigit():
        su = ""
    is_group = bool(session_user and session_user.endswith("@chatroom"))
    if su and su == session_user:
        # real_sender_id 偶尔指向群本身；群 ID 不是发言人。
        su = ""
    is_self = bool(su and self_wxid and su == self_wxid)
    if is_group:
        if su:
            if is_self:
                return ctx["self_name"]
            return ctx["nicks"].get(su, su)
        known_users = set(ctx.get("nicks") or {})
        if self_wxid:
            known_users.add(self_wxid)
        if session_user:
            known_users.add(session_user)
        prefix = sender_from_prefix(content, known_users=known_users)
        if prefix:
            if prefix == self_wxid:
                return ctx["self_name"]
            return ctx["nicks"].get(prefix, prefix)
        # 只有完全拿不到分片映射时才兼容旧库的 sender_id=2；
        # 分片已成功读取但该 rowid 对不上联系人时，绝不能猜成“我”。
        if m.get("sender_index_ok") is False and str(m.get("sender_id")) == "2":
            return ctx["self_name"]
        return ""
    if is_self:
        return ctx["self_name"]
    return session_name or ctx["nicks"].get(session_user, session_user)

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
        sender_map = _chat_sender_map(db, user, msgs)
        for m in msgs:
            key = (int(m.get("local_id") or 0), int(m.get("sort_seq") or 0))
            if key in sender_map:
                m["sender_username"] = sender_map[key]
                m["sender_index_ok"] = True
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
            conns.append((rel, _open_message_db_with_retry(db, rel)))
        hits = []
        for c in chats:
            user = str(c.get("username") or "")
            if not user:
                continue
            if only_user and user != only_user:
                continue
            target = "Msg_" + hashlib.md5(user.encode("utf-8")).hexdigest()
            for rel, conn in conns:
                try:
                    if conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (target,)).fetchone():
                        rows = conn.execute(
                            "SELECT %s FROM %s WHERE sort_seq > ? ORDER BY sort_seq ASC, local_id ASC LIMIT %d" % (cols, target, want),
                            (since_seq,)).fetchall()
                        for r in rows:
                            d = db._msg_row_to_dict(r)
                            d["sender_username"] = _sender_username_from_shard(
                                conn, rel, d.get("sender_id"))
                            d["sender_index_ok"] = bool(_shard_name_index(conn, rel))
                            hits.append((c.get("name") or user, user, d))
                except sqlite3.DatabaseError:
                    continue
        hits.sort(key=lambda x: ((x[2].get("sort_seq") or 0), x[1]))
        return hits[:want]
    finally:
        for _, conn in conns:
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
# 非 wxid_ 前缀（普通微信号/手机号/@openim）：小写字母或数字开头。
# 调用方必须传 known_users，否则 inq:/offer: 这类正文仍可能被误认成前缀。
_SENDER_PREFIX_LOOSE = re.compile(r"^[a-z0-9][a-z0-9_@.\-]*:$")

def sender_from_prefix(content, known_users=None):
    """从消息内容首行提取发送者 user_name；无有效前缀返回 None。

    ``known_users`` 非空时，非 wxid_ 前缀必须能在联系人/会话表中命中才认可，
    防止 ``inq:``、``offer:`` 这类业务正文被误判成发送者前缀。
    """
    if not isinstance(content, str):
        return None
    first = content.split("\n", 1)[0].strip()
    strict = bool(_SENDER_PREFIX_STRICT.match(first))
    if not strict and not _SENDER_PREFIX_LOOSE.match(first):
        return None
    prefix = first[:-1]
    if known_users is not None and not strict and prefix not in known_users:
        return None
    return prefix

def strip_wxid_prefix(content, known_users=None):
    """去掉 content 首行的发送者前缀行（wxid_xxx: / jingfan5807: 等），返回真实正文；无前缀原样返回。"""
    if not isinstance(content, str):
        return content
    lines = content.split("\n", 1)
    if sender_from_prefix(content, known_users=known_users) is not None:
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


def _xml_root(value):
    """解析引用 content 中嵌套的 XML；不是 XML 时返回 None。"""
    text = value.strip() if isinstance(value, str) else ""
    if not text:
        return None
    match = re.search(r"<msg[\s\S]*?</msg>", text)
    if not match:
        return None
    try:
        return ET.fromstring(match.group(0))
    except Exception:
        return None


def parse_quote_detail(content):
    """解析一条引用消息，返回 (原文, 引用展示文本, 引用类型, 媒体信息)。

    媒体信息仅用于按需读取引用图片/文件，不写入消息表，避免扩大现有数据模型。
    """
    if not isinstance(content, str):
        if isinstance(content, bytes):
            content = content.decode("utf-8", "replace")
        else:
            return content, "", "", {}
    low = content.strip()
    # 原始内容可能带发送者前缀(如 "wxid_xxx:\n" 或 "xxx:\n")，先定位 XML 本体
    m = re.search(r"<msg[\s\S]*?</msg>", low)
    if not m:
        return content, "", "", {}
    xml_str = m.group(0)
    try:
        root = ET.fromstring(xml_str)
    except Exception:
        return content, "", "", {}
    app = root.find("appmsg")
    refer = (app.find("refermsg") if app is not None else None) or root.find(".//refermsg")
    if refer is None:
        return content, "", "", {}
    # 原文：优先取卡片标题/摘要，否则保留原 content
    own = (app.findtext("title") or "").strip() if app is not None else ""
    if not own and app is not None:
        own = (app.findtext("des") or "").strip()
    qt = (refer.findtext("type") or "").strip()
    qtext = (refer.findtext("content") or "").strip()
    kind = _quote_kind(qt)
    nested = _xml_root(qtext)
    nested_app = nested.find("appmsg") if nested is not None else None
    nested_title = (nested_app.findtext("title") or "").strip() if nested_app is not None else ""
    file_ext = (nested_app.findtext(".//fileext") or "").strip() if nested_app is not None else ""
    media = {
        "file_name": "",
        "image_md5": "",
        "refer_svrid": (refer.findtext("svrid") or "").strip(),
        "refer_create_time": (refer.findtext("createtime") or "").strip(),
        "has_media": False,
    }

    if file_ext:
        kind = "文件"
        media["file_name"] = nested_title or qtext[:256]
        media["has_media"] = bool(media["file_name"])
        quote = media["file_name"]
    elif kind == "图片" or kind == "表情":
        image = nested.find(".//img") if nested is not None else None
        media["image_md5"] = (image.get("md5") or "").strip().lower() if image is not None else ""
        media["has_media"] = bool(media["image_md5"])
        quote = "【%s】" % kind
    elif kind == "卡片":
        media["file_name"] = nested_title
        quote = nested_title[:256] if nested_title else "【卡片】"
    elif nested_title:
        quote = nested_title[:256]
    elif kind in ("语音", "视频"):
        quote = "【%s】" % kind
    else:
        quote = qtext[:256] if qtext else ""
    return own, quote, kind, media


def parse_quote(content):
    """兼容旧调用：返回 (原文, 引用展示文本, 引用类型)。"""
    own, quote, kind, _ = parse_quote_detail(content)
    return own, quote, kind


def _cached_quote_file(save_dir, stem):
    for ext in ("jpg", "jpeg", "png", "gif", "webp", "bmp", "wxgf", "img"):
        path = os.path.join(save_dir, stem + "." + ext)
        if os.path.isfile(path):
            return path
    return None


def _decoded_row_content(db, row):
    """get_message_row 返回的是原始压缩 BLOB，按微信库同一套规则解码正文。"""
    content = row.get("content") if row else None
    if not isinstance(content, bytes):
        return content or ""
    try:
        from wechatauto.db import WeChatDB
        mtype = WeChatDB._msg_type_name(row.get("local_type") or 49)
        return WeChatDB._friendly_content(content, mtype)
    except Exception as e:
        log("原始消息解码失败: %s" % e)
        return content.decode("utf-8", "replace")


def _quote_media_path(db, media, username, local_id, save_dir):
    """按外层引用消息解析并落盘被引用的图片或文件。"""
    row = db.get_message_row(username, local_id)
    if not row:
        die("原始消息不存在: %s/%s" % (username, local_id))
    _, quote, kind, meta = parse_quote_detail(
        strip_wxid_prefix(_decoded_row_content(db, row)))
    stem = "%s_%s_quote" % (username, local_id)
    if kind in ("图片", "表情"):
        md5 = meta.get("image_md5") or ""
        if not md5:
            die("引用图片缺少 md5，无法读取")
        cached = _cached_quote_file(save_dir, stem)
        if cached:
            return cached
        files = media._image_files(username, md5)
        for tier in ("original", "mid", "thumb"):
            hit = files.get(tier)
            if not hit:
                continue
            try:
                data = media.decrypt_image(hit[0])
                return media._write_decrypted(data, stem, save_dir=save_dir)
            except Exception as e:
                log("引用图片解密失败(%s): %s" % (tier, e))
        # 引用里记录的是图片内容 md5；本地附件目录有时按原消息 packed_info 的另一
        # 个 md5 落盘，因此回查同会话原图片行，再走微信库自己的图片下载器。
        for image_row in db.get_image_rows(username, limit=5000):
            parts = []
            for value in (image_row.get("content"), image_row.get("packed_info")):
                if isinstance(value, str):
                    parts.append(value.encode("utf-8", "replace"))
                elif isinstance(value, (bytes, bytearray, memoryview)):
                    parts.append(bytes(value))
            raw = b"".join(parts)
            svrid = str(image_row.get("server_id") or "")
            if md5.encode() not in raw and (not meta.get("refer_svrid") or svrid != meta["refer_svrid"]):
                continue
            downloaded = media.download_image(
                username, image_row["local_id"], save_dir=save_dir, tier="best")
            if downloaded:
                ext = os.path.splitext(downloaded)[1]
                target = os.path.join(save_dir, stem + ext)
                shutil.copy2(downloaded, target)
                return target
        die("本机暂无引用图片缓存，未下载成功")
    if kind == "文件":
        name = os.path.basename(str(meta.get("file_name") or "").replace("\\", "/"))
        if not name:
            die("引用文件缺少文件名，无法读取")
        target = os.path.join(save_dir, stem + "_" + name)
        if os.path.isfile(target):
            return target
        base = os.path.join(db.account_dir, "msg", "file")
        for root, _, files in os.walk(base):
            if name in files:
                source = os.path.join(root, name)
                os.makedirs(save_dir, exist_ok=True)
                shutil.copy2(source, target)
                return target
        die("本机暂无引用文件: %s" % name)
    die("该引用%s没有可读取的图片或文件" % (quote or kind))


def _file_card_info(content):
    """读取文件卡片 XML 里的原文件名与扩展名。"""
    root = _xml_root(strip_wxid_prefix(content or ""))
    app = root.find("appmsg") if root is not None else None
    if app is None:
        return "", ""
    title = os.path.basename((app.findtext("title") or "").strip().replace("\\", "/"))
    ext = (app.findtext(".//fileext") or "").strip().lstrip(".").lower()
    if title and ext and not title.lower().endswith("." + ext):
        title += "." + ext
    return title, ext


def _normalized_file_key(name):
    """忽略微信重复下载自动追加的 (1)/(2) 后缀，便于回查本机原文件。"""
    base, ext = os.path.splitext(os.path.basename(name or ""))
    base = re.sub(r"\s*[\(（]\d+[\)）]$", "", base)
    return base.strip().lower(), ext.lower().lstrip(".")


def _copy_local_file(db, content, create_time, save_dir, username, local_id):
    """微信有时把附件保存为 name(1).xlsx，按标题扫描 msg/file 并复制到媒体目录。"""
    name, expected_ext = _file_card_info(content)
    if not name:
        return ""
    base = os.path.join(db.account_dir, "msg", "file")
    if not os.path.isdir(base):
        return ""
    target_key = _normalized_file_key(name)
    candidates = []
    for root, _, files in os.walk(base):
        for file_name in files:
            normalized = _normalized_file_key(file_name)
            if normalized != target_key:
                continue
            if expected_ext and normalized[1] != expected_ext:
                continue
            path = os.path.join(root, file_name)
            try:
                score = abs(os.path.getmtime(path) - float(create_time or 0))
            except Exception:
                score = float("inf")
            candidates.append((score, path, file_name))
    if not candidates:
        return ""
    candidates.sort(key=lambda item: item[0])
    source = candidates[0][1]
    safe_name = re.sub(r'[<>:"/\\|?*]', "_", os.path.basename(name))[:180] or "attachment"
    os.makedirs(save_dir, exist_ok=True)
    target = os.path.join(save_dir, "%s_%s_%s" % (username, local_id, safe_name))
    if not os.path.isfile(target):
        shutil.copy2(source, target)
    return target


def row_to_json(db, ctx, m, media, name):
    media_path = ""
    mtype = m.get("type")
    raw_content = m.get("content") or ""
    session_uid = str(m.get("__session") or m.get("username") or "")
    known_users = set(ctx.get("nicks") or {})
    if ctx.get("self_wxid"):
        known_users.add(ctx["self_wxid"])
    if session_uid:
        known_users.add(session_uid)
    is_file = mtype == "文件/链接/卡片" and "<fileext>" in raw_content.lower()
    if media is not None and (mtype in ("图片", "语音", "视频", "文件") or is_file):
        try:
            media_path = media.download_media(m.get("__session") or m.get("username"),
                                              m["local_id"], save_dir=media.save_dir) or ""
            if not media_path and is_file:
                media_path = _copy_local_file(
                    db, strip_wxid_prefix(raw_content, known_users=known_users),
                    m.get("create_time") or 0,
                    media.save_dir, m.get("__session") or m.get("username"), m["local_id"])
        except Exception as e:
            log("  [媒体下载失败 %s/%s]: %s" % (m.get("__session"), m["local_id"], e))
            media_path = ""
            if is_file:
                try:
                    media_path = _copy_local_file(
                        db, strip_wxid_prefix(raw_content, known_users=known_users),
                        m.get("create_time") or 0,
                        media.save_dir, m.get("__session") or m.get("username"), m["local_id"])
                except Exception as fe:
                    log("  [本地文件兜底失败 %s/%s]: %s" % (m.get("__session"), m["local_id"], fe))
    # 先去掉库内多余的 wxid_xxx: 前缀行，再拆解引用/卡片消息
    content, quote, quote_kind = parse_quote(
        strip_wxid_prefix(m.get("content") or "", known_users=known_users))
    ct = m.get("create_time") or 0
    # 业务去重键：会话显示名 + 会话ID + 消息产生时间戳（格式 name:username:1791250634）
    session_name = str(name or "")
    server_id = ("%s:%s:%s" % (session_name, session_uid, ct)) if ct else ""
    return {
        "username": m.get("__session") or m.get("username"),
        "name": name,
        "local_id": m["local_id"],
        "server_id": server_id,
        "type": mtype,
        "sender": resolve_sender(ctx, m, session_uid, session_name),
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
            # Listener 返回的是跨分片合并后的行，必须按消息自己的 shard 重建映射；
            # 不能再因正文带前缀就跳过分片校验，否则 inq: 这类正文会串成发言人。
            if user.endswith("@chatroom"):
                su, ok = _lookup_shard_sender(
                    db, user, m.get("local_id"), m.get("sort_seq"),
                    shard_hint=m.get("shard"))
                if ok:
                    m["sender_username"] = su
                m["sender_index_ok"] = ok
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

    max_batch = int(cfg.get("max_batch") or 3)
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
    nap_min = float(nap[0]) if len(nap) > 0 else 1.1
    nap_max = float(nap[1]) if len(nap) > 1 else 1.8
    if nap_max < nap_min:
        nap_max = nap_min
    interval = cfg.get("interval") or []
    iv_min = float(interval[0]) if len(interval) > 0 else 30.0
    iv_max = float(interval[1]) if len(interval) > 1 else 60.0
    if iv_max < iv_min:
        iv_max = iv_min

    os.environ["WECHATAUTO_RHYTHM"] = rhythm
    try:
        import wechatauto.rhythm as rhythm_mod
        # 字典值是最终权威：显式切档并覆盖 nap，避免环境变量或进程默认档位残留
        rhythm_mod.set_profile(rhythm)
        rhythm_mod.configure(nap=(nap_min, nap_max))
        active = rhythm_mod.profile()
        log("拟人节奏: %s, nap=%.2f~%.2f, gap=%.1f~%.1f, %ds 内最多 %d 次写动作"
            % (active.name, active.nap[0], active.nap[1], active.gap[0],
               active.gap[1], int(active.window), int(active.burst)))
    except Exception as e:
        log("配置拟人节奏失败(继续用默认): %s" % e)

    log("批量发送: %d 条, 间隔[%.1f, %.1f]s, verify=%s, 上限=%d"
        % (len(targets), iv_min, iv_max, verify, max_batch))
    try:
        from wechatauto.guia import WeChatGUI
        patch_send_verify()
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


def cmd_diagnose_sender(args):
    """排障：输出一条消息在各分片中的 real_sender_id 与最终解析结果。"""
    import hashlib
    db, _ = build_db(None, args.account)
    ctx = load_ctx(db)
    target = "Msg_" + hashlib.md5(args.username.encode("utf-8")).hexdigest()
    out = {
        "account": db.account,
        "self": db.get_self_info() or {},
        "username": args.username,
        "local_id": args.local_id,
        "chat_name": db.get_nickname(args.username) or args.username,
        "candidates": [],
    }
    for rel in db._message_dbs():
        conn = None
        try:
            conn = _open_message_db_with_retry(db, rel)
            if not conn.execute(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (target,)
            ).fetchone():
                continue
            rows = conn.execute(
                "SELECT * FROM %s WHERE local_id=? ORDER BY sort_seq DESC" % target,
                (args.local_id,),
            ).fetchall()
            for r in rows:
                m = db._msg_row_to_dict(r)
                sid = m.get("sender_id")
                shard_su = _sender_username_from_shard(conn, rel, sid)
                try:
                    global_su = db._sender_id_index().get(int(sid), "") if sid else ""
                except Exception:
                    global_su = ""
                m["sender_username"] = shard_su
                m["sender_index_ok"] = True
                resolved = resolve_sender(
                    ctx, m, args.username, db.get_nickname(args.username) or args.username)
                out["candidates"].append({
                    "shard": rel,
                    "sort_seq": m.get("sort_seq"),
                    "real_sender_id": sid,
                    "shard_name2id": shard_su,
                    "global_sender_name2id": global_su,
                    "content_prefix": sender_from_prefix(m.get("content")) or "",
                    "resolved_sender": resolved,
                    "content_head": (m.get("content") or "")[:80],
                })
        except sqlite3.DatabaseError as e:
            out["candidates"].append({"shard": rel, "error": str(e)})
        finally:
            if conn is not None:
                try:
                    conn.close()
                except Exception:
                    pass
    print(json.dumps(out, ensure_ascii=False))
    sys.stdout.flush()


def cmd_media(args):
    """下载指定消息的图片或文件，成功后输出 MEDIA_PATH=<绝对路径>。"""
    db, media = build_db(args.media, args.account)
    if media is None:
        die("未配置 --media 目录")
    path = ""
    try:
        path = media.download_media(args.username, args.local_id, save_dir=args.media)
    except Exception as e:
        log("媒体下载异常，尝试本机附件兜底: %s/%s %s" % (args.username, args.local_id, e))
    if not path:
        row = db.get_message_row(args.username, args.local_id)
        if row:
            path = _copy_local_file(
                db, strip_wxid_prefix(_decoded_row_content(db, row)),
                row.get("create_time") or 0, args.media, args.username, args.local_id)
    if not path:
        die("媒体文件未下载或本机不存在: %s/%s" % (args.username, args.local_id))
    print("MEDIA_PATH=" + os.path.abspath(path))
    sys.stdout.flush()


def cmd_quote_info(args):
    """解析外层消息引用的真实文本/类型；供聊天记录悬浮与详情按需补全。"""
    db, _ = build_db(None, args.account)
    row = db.get_message_row(args.username, args.local_id)
    if not row:
        die("原始消息不存在: %s/%s" % (args.username, args.local_id))
    _, quote, kind, media = parse_quote_detail(
        strip_wxid_prefix(_decoded_row_content(db, row)))
    out = {
        "kind": kind,
        "quote": quote,
        "file_name": media.get("file_name") or "",
        "has_media": bool(media.get("has_media")),
    }
    print(json.dumps(out, ensure_ascii=False))
    sys.stdout.flush()


def cmd_quote_media(args):
    """按外层消息键下载被引用的图片或文档文件。"""
    db, media = build_db(args.media, args.account)
    if media is None:
        die("未配置 --media 目录")
    path = _quote_media_path(db, media, args.username, args.local_id, args.media)
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

    p = sub.add_parser("diagnose-sender", help="排障：输出消息分片与发送者解析结果(JSON)")
    p.add_argument("--username", required=True, help="会话ID(wxid/群id)")
    p.add_argument("--local-id", required=True, type=int, help="库内消息ID")
    p.add_argument("--account", default=None)
    p.set_defaults(func=cmd_diagnose_sender)

    p = sub.add_parser("media", help="按消息键下载单条媒体，输出 MEDIA_PATH")
    p.add_argument("--username", required=True, help="会话ID(wxid/群id)")
    p.add_argument("--local-id", required=True, type=int, help="库内消息ID")
    p.add_argument("--media", required=True, help="媒体保存目录")
    p.add_argument("--account", default=None)
    p.set_defaults(func=cmd_media)

    p = sub.add_parser("quote-info", help="解析消息引用的真实类型与文本，输出 JSON")
    p.add_argument("--username", required=True, help="会话ID(wxid/群id)")
    p.add_argument("--local-id", required=True, type=int, help="库内消息ID")
    p.add_argument("--account", default=None)
    p.set_defaults(func=cmd_quote_info)

    p = sub.add_parser("quote-media", help="下载消息引用的图片/文件，输出 MEDIA_PATH")
    p.add_argument("--username", required=True, help="会话ID(wxid/群id)")
    p.add_argument("--local-id", required=True, type=int, help="库内消息ID")
    p.add_argument("--media", required=True, help="媒体保存目录")
    p.add_argument("--account", default=None)
    p.set_defaults(func=cmd_quote_media)
    args = ap.parse_args()
    cleanup_stale_proc_dirs()
    configure_wechatauto(verbose=getattr(args.func, "__name__", "") in (
        "cmd_batch_send", "cmd_media", "cmd_quote_media"))
    try:
        args.func(args)
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    main()






