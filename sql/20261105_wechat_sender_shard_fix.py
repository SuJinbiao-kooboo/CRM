#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""微信消息跨服务器/分片修复（2026-11-05）。

背景：
  - 微信 4.x 的消息库按 message_0.db / message_1.db / ... 分片；
    每条消息的 real_sender_id 只对**所在分片**的 Name2Id 有效。
  - 旧表唯一键是 (username, local_id)，但 local_id 只在单个分片内唯一；
    换服务器后同一会话的分片编号会变化，导致同一消息被当成新消息重复入库，
    或不同消息因 local_id 相同而互相覆盖。

本脚本做两件事：
  1. 把消息身份改为 (username, sort_seq, content_hash)，先清理重复行再建唯一键；
  2. 删除旧的 (username, local_id) 唯一键，保留普通索引供历史查询。

用法：
  python sql/20261105_wechat_sender_shard_fix.py --dry-run
  python sql/20261105_wechat_sender_shard_fix.py --apply
"""
from __future__ import print_function

import argparse
import sys

import pymysql


DB = dict(host="42.194.240.191", port=3306, user="root", password="eke123",
          database="crm_me", charset="utf8mb4")


def column_exists(cur, table, column):
    cur.execute(
        "SELECT 1 FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND COLUMN_NAME=%s",
        (table, column),
    )
    return cur.fetchone() is not None


def index_exists(cur, table, index):
    cur.execute(
        "SELECT 1 FROM information_schema.STATISTICS "
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND INDEX_NAME=%s LIMIT 1",
        (table, index),
    )
    return cur.fetchone() is not None


def table_exists(cur, table):
    cur.execute(
        "SELECT 1 FROM information_schema.TABLES "
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s LIMIT 1",
        (table,),
    )
    return cur.fetchone() is not None


def score_sql():
    return """
        (CASE classify_status
            WHEN 'SUCCESS' THEN 8 WHEN 'PROCESSING' THEN 6
            WHEN 'FAILED' THEN 4 WHEN 'PENDING' THEN 2 ELSE 0 END
         + CASE entry_status
            WHEN 'SUCCESS' THEN 8 WHEN 'SUCCESS_WARN' THEN 7
            WHEN 'PROCESSING' THEN 6 WHEN 'FAILED' THEN 4
            WHEN 'PENDING' THEN 2 ELSE 0 END
         + CASE task_status
            WHEN 'GENERATED' THEN 8 WHEN 'PROCESSING' THEN 6
            WHEN 'FAILED' THEN 4 WHEN 'PENDING' THEN 2 ELSE 0 END
         + IF(sender IS NOT NULL AND sender <> '', 3, 0))
    """


def find_duplicates(cur):
    cur.execute(
        "SELECT id, username, sort_seq, MD5(IFNULL(content,'')) AS content_hash, local_id, server_id, "
        + score_sql()
        + " AS score FROM wechat_message "
        "ORDER BY username, sort_seq, content_hash, score DESC, id DESC"
    )
    groups = {}
    for row in cur.fetchall():
        key = (row[1], row[2], row[3])
        groups.setdefault(key, []).append(row)
    return {k: v for k, v in groups.items() if len(v) > 1}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正执行；缺省只读检查")
    ap.add_argument("--dry-run", action="store_true", help="只读检查（默认）")
    args = ap.parse_args()
    apply = args.apply and not args.dry_run

    conn = pymysql.connect(**DB)
    cur = conn.cursor()

    if not column_exists(cur, "wechat_message", "content_hash"):
        print("[DDL] 增加 content_hash 生成列")
        if apply:
            cur.execute(
                "ALTER TABLE wechat_message ADD COLUMN content_hash CHAR(32) "
                "GENERATED ALWAYS AS (MD5(IFNULL(content,''))) STORED "
                "COMMENT '正文MD5，用于跨服务器/分片消息身份'"
            )

    dups = find_duplicates(cur)
    drop_rows = []
    print("重复身份组: %d" % len(dups))
    for key, rows in sorted(dups.items(), key=lambda x: -len(x[1])):
        keep = rows[0]
        print("  保留 id=%s local_id=%s server_id=%s 丢弃 %s" % (
            keep[0], keep[4], keep[5], [r[0] for r in rows[1:]]))
        drop_rows.extend(rows[1:])
    print("待删除重复行: %d" % len(drop_rows))

    if not apply:
        print("dry-run：未写入。确认无误后加 --apply 执行。")
        conn.close()
        return

    if drop_rows:
        ids = [r[0] for r in drop_rows]
        print("[备份] 写入 wechat_message_dup_bak_20261105（已有则追加，不删除旧备份）")
        if not table_exists(cur, "wechat_message_dup_bak_20261105"):
            # CTAS 会去掉 content_hash 的 GENERATED 属性，才能把原行原样备份进去。
            cur.execute(
                "CREATE TABLE wechat_message_dup_bak_20261105 "
                "AS SELECT * FROM wechat_message WHERE 1=0"
            )
        for i in range(0, len(ids), 500):
            part = ids[i:i + 500]
            placeholders = ",".join(["%s"] * len(part))
            cur.execute(
                "INSERT IGNORE INTO wechat_message_dup_bak_20261105 "
                "SELECT * FROM wechat_message WHERE id IN (%s)" % placeholders,
                part,
            )

        print("[Offer] 把重复行上的 Offer 关联迁到保留行")
        for row in drop_rows:
            drop_id, username, _, _, drop_local_id, _, _ = row
            # 找到同组保留行
            keep = None
            for rows in dups.values():
                if any(r[0] == drop_id for r in rows):
                    keep = rows[0]
                    break
            if keep is None or keep[0] == drop_id:
                continue
            keep_local_id, keep_server_id = keep[4], keep[5]
            cur.execute(
                "UPDATE crm_offer SET wechat_local_id=%s, wechat_server_id=%s "
                "WHERE wechat_username=%s AND wechat_local_id=%s",
                (keep_local_id, keep_server_id, username, drop_local_id),
            )

        print("[删除] 重复消息行")
        for i in range(0, len(ids), 500):
            part = ids[i:i + 500]
            placeholders = ",".join(["%s"] * len(part))
            cur.execute(
                "DELETE FROM wechat_message WHERE id IN (%s)" % placeholders,
                part,
            )
    conn.commit()

    if index_exists(cur, "wechat_message", "uk_username_localid"):
        print("[DDL] 删除旧唯一键 uk_username_localid")
        cur.execute("ALTER TABLE wechat_message DROP INDEX uk_username_localid")
    if not index_exists(cur, "wechat_message", "idx_username_localid"):
        print("[DDL] 增加普通索引 idx_username_localid")
        cur.execute(
            "ALTER TABLE wechat_message ADD INDEX idx_username_localid (username, local_id)"
        )
    if not index_exists(cur, "wechat_message", "uk_message_identity"):
        print("[DDL] 增加消息身份唯一键 uk_message_identity")
        cur.execute(
            "ALTER TABLE wechat_message ADD UNIQUE KEY uk_message_identity "
            "(username, sort_seq, content_hash)"
        )
    conn.commit()
    cur.execute("SELECT COUNT(*) FROM wechat_message")
    print("完成，当前消息行数: %s" % cur.fetchone()[0])
    conn.close()


if __name__ == "__main__":
    main()
