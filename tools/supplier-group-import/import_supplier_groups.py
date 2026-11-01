#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""导入供应商群聊审核结果、群成员联系人，并生成导入结果表。"""

from __future__ import annotations

import argparse
import json
import os
import re
import unicodedata
from collections import OrderedDict
from datetime import datetime
from pathlib import Path

import pymysql
from openpyxl import load_workbook
from openpyxl.styles import Alignment, Font, PatternFill


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EXCEL = ROOT / "Reference" / "供应商群聊匹配审核.xlsx"
DEFAULT_MEMBERS = Path(os.environ.get("TEMP", "")) / "wechat_groups_members_full.json"
DEFAULT_DB = {
    "host": "42.194.240.191",
    "port": 3306,
    "user": "root",
    "password": "eke123",
    "database": "crm_me",
}

KNOWN_INTERNAL_WXIDS = {
    "wxid_73q92mckc37522",
    "wxid_81u4px76yqre22",
    "wxid_r8shtygx097229",
    "wxid_ofhm8gl5rtfd12",
    "wxid_8rh3a1dorbjr12",
    "wxid_xhxf9j6t9u5g21",
    "wxid_su64lac2tnh822",
    "wxid_u3y3jd877yvz51",
}

EXTRA_INTERNAL_NAMES = {"蓝胖", "rainbow", "林铭月", "姐"}

SECOND_EXISTING_CODE = {
    "3D M&E订单群": "VC0820841",
    "Greater Bay M&E": "VC0917682",
    "Match M&E订单群": "VC0820266",
    "Xinhao M&E订单群": "VC0820231",
    "顺屹 亦钟人订单群": "VC1013386",
}

SPECIAL_GROUP_NAME = {
    "3D M&E订单群": "云仲存储",
    "Greater Bay M&E": "大湾电子",
    "Match M&E订单群": "迈驰",
    "Xinhao M&E订单群": "鑫浩",
    "顺屹 亦钟人订单群": "顺屿",
    "陈总亦钟人订单群": "同泰怡",
    "亦钟人 张总solidigm对接群RMB": "郑州一方服务器",
    "思腾亦钟人订单群": "思腾合力",
    "安擎 亦钟人配件销售对接群": "安擎",
    "亦钟人神码销售群思得SSD，东芝nas": "神码",
    "亦钟人 深博订单群rmb": "深博",
    "亦钟人众晟云订单群网卡硬盘内存": "众晟云",
    "远信 亦钟人offer群": "远信",
    "同泰怡 亦钟人 显卡需求对接群": "同泰怡",
    "鸿通智算—-亦钟人 订单群": "鸿通智算",
    "怡英嘉美 亦钟人订单群拆新RAM CPU": "怡英嘉美",
    "亦钟人科通订单群 Intel CPU网卡": "科通",
    "亦钟人神码销售群思得SSD,东芝nas": "神码",
}

SKIP_EXACT = {
    "1131代帐-.亦钟人电子科技（平安已授权 ）",
    "空中云汇   香港亦钟人集团",
    "网易售后沟通 O6259300 深圳市亦钟人电子科技有限公司meelectronic.hzqiye.",
    "亦钟人/征途香港公司事务群",
    "亦钟人20楼",
    "亦钟人小新人培训",
    "亦钟人内部群",
    "亦钟人内部offer群",
    "XT亦钟人沟通人",
    "M&E order group",
    "M&E Squad",
    "DD M&E行情交流",
    "Link enterprise M&E VIP order group",
    "亦钟人 昆明心存储对接群",
    "亦钟人紫光数码专营店rmb",
}

PRODUCT_ORDER = ["CPU", "GPU", "内存", "SSD", "HDD", "网卡", "交换机", "服务器", "NAS", "存储"]


def clean_space(value):
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", str(value or ""))).strip()


def norm(value):
    text = unicodedata.normalize("NFKC", str(value or "")).lower()
    return re.sub(r"[\W_]+", "", text, flags=re.UNICODE)


def clip(value, length):
    return str(value or "")[:length]


def split_multi(value):
    return [clean_space(part) for part in re.split(r"[/,，、;；\n]+", str(value or "")) if clean_space(part)]


def read_rows(ws):
    raw_headers = [str(ws.cell(1, col).value or "").strip() for col in range(1, ws.max_column + 1)]
    headers = [clean_space(value) for value in raw_headers]
    rows = []
    for row_index in range(2, ws.max_row + 1):
        values = {}
        for col in range(1, ws.max_column + 1):
            value = ws.cell(row_index, col).value
            values[raw_headers[col - 1]] = value
            values[headers[col - 1]] = value
        if not any(value not in (None, "") for value in values.values()):
            continue
        values["_row"] = row_index
        rows.append(values)
    return rows


def load_self_names(cur):
    cur.execute(
        "SELECT dict_value FROM sys_dict_data WHERE dict_type = %s AND dict_label = %s LIMIT 1",
        ("wechat_analysis_config", "selfNames"),
    )
    row = cur.fetchone()
    raw = row.get("dict_value") if row else ""
    values = [clean_space(item) for item in re.split(r"[,，]", str(raw or "")) if clean_space(item)]
    return values or ["Eke", "May", "Jin", "Jasmine"]


def display_name(member):
    for key in ("remark", "nick_name"):
        value = clean_space(member.get(key))
        if value:
            return value
    return ""


def is_meaningful_name(value):
    text = clean_space(value)
    if not re.search(r"[A-Za-z\u4e00-\u9fff]", text):
        return False
    return not bool(re.fullmatch(r"[A-Za-z]", text))


def is_internal_member(member, self_names):
    if str(member.get("username") or "").strip() in KNOWN_INTERNAL_WXIDS:
        return True
    values = {norm(member.get("nick_name")), norm(member.get("remark"))}
    exact_names = {norm(item) for item in self_names} | EXTRA_INTERNAL_NAMES
    if values & exact_names:
        return True
    return any(value.startswith("rainbow") or value.startswith("林铭月") for value in values)


def external_members(members, self_names):
    result = []
    for member in members or []:
        name = display_name(member)
        if name and is_meaningful_name(name) and not is_internal_member(member, self_names):
            result.append(member)
    return result


def extract_phone(text):
    value = unicodedata.normalize("NFKC", str(text or ""))
    mobile = re.search(r"(?<!\d)(?:\+?86[\s-]?)?(1[3-9]\d{9})(?!\d)", value)
    if mobile:
        return mobile.group(1)
    for match in re.finditer(r"(?<!\d)(\+?\d[\d\s\-()]{6,18}\d)(?!\d)", value):
        digits = re.sub(r"\D", "", match.group(1))
        if 7 <= len(digits) <= 15:
            return digits
    return ""


def extract_email(text):
    match = re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", str(text or ""))
    return match.group(0) if match else ""


def infer_post(text):
    value = str(text or "").lower()
    if re.search(r"采购|buyer|采销|procurement", value):
        return "采购"
    if re.search(r"销售|sales|sale|业务", value):
        return "销售"
    if re.search(r"boss|老板|ceo|总经理|负责人|总监|主管", value):
        return "负责人"
    if re.search(r"财务|会计|finance", value):
        return "财务"
    if re.search(r"物流|报关|支付", value):
        return "物流/支付"
    if re.search(r"技术|工程师|engineer", value):
        return "技术"
    return ""


def infer_products(text):
    value = str(text or "")
    rules = {
        "CPU": r"(?i)\bCPU\b|英特尔|intel|amd处理器",
        "GPU": r"(?i)\bGPU\b|显卡|nvidia|英伟达",
        "内存": r"(?i)\bRAM\b|内存|条子",
        "SSD": r"(?i)\bSSD\b|\bSS\b|固态|闪存盘",
        "HDD": r"(?i)\bHDD\b|硬盘|机械盘",
        "网卡": r"网卡",
        "交换机": r"交换机",
        "服务器": r"服务器",
        "NAS": r"(?i)\bNAS\b",
        "存储": r"存储",
    }
    found = {label for label, pattern in rules.items() if re.search(pattern, value)}
    return ",".join(item for item in PRODUCT_ORDER if item in found)


def derive_short_name(name):
    value = clean_space(name)
    if not value:
        return ""
    if not re.search(r"[\u4e00-\u9fff]", value):
        return clip(value, 100)
    for prefix in ("北京市", "北京", "深圳市", "深圳", "广州市", "广州", "上海市", "上海", "香港特别行政区", "香港"):
        if value.startswith(prefix) and len(value) > len(prefix):
            value = value[len(prefix):]
            break
    for suffix in (
        "集团有限公司", "股份有限公司", "有限责任公司", "有限公司", "科技有限公司",
        "科技股份有限公司", "信息技术有限公司", "计算机有限公司", "电子科技有限公司",
        "国际贸易有限公司", "集团", "科技", "电子", "实业", "国际", "公司",
    ):
        if value.endswith(suffix) and len(value) > len(suffix):
            value = value[:-len(suffix)]
            break
    return clip(value.strip(" -_/&") or name, 100)


def derive_group_key(group):
    for special_group, special_name in SPECIAL_GROUP_NAME.items():
        if norm(special_group) == norm(group):
            return special_name
    value = unicodedata.normalize("NFKC", group or "").replace("\u3000", " ")
    value = re.sub(r"[（(][^）)]*[）)]", " ", value)
    value = re.sub(r"(?i)香港\s*亦[钟鍾]人|亦[钟鍾]人", " ", value)
    value = re.sub(r"(?i)(?<![A-Za-z])M\s*&\s*E(?![A-Za-z])", " ", value)
    patterns = [
        r"VIP订单群", r"VIP订单", r"VIP群", r"VIP", r"采购订单群", r"销售订单群",
        r"采购群", r"销售群", r"订单外贸群", r"订单群", r"商务群", r"合作群", r"对接群",
        r"IT配件对接群", r"IT硬件对接群", r"IT配件", r"IT硬件", r"配件", r"订单", r"群",
        r"order\s*group", r"order", r"group", r"sales", r"sale", r"purchase", r"procurement", r"offer",
        r"GPU\s*RAM\s*SS", r"GPU\s*RAM", r"RAM\s*SSD", r"RAM\s*HDD",
        r"HDD", r"SSD", r"CPU", r"GPU", r"RAM", r"内存", r"硬盘", r"拆机", r"拆",
        r"网卡", r"交换机", r"资源对接", r"行情交流", r"需求", r"rmb",
    ]
    for pattern in patterns:
        value = re.sub(pattern, " ", value, flags=re.IGNORECASE)
    value = re.sub(r"\s+", " ", value)
    return re.sub(r"[\s/+,，.。:;；\-_]+$", "", value).strip(" -_/&|")


def group_purpose(value):
    text = clean_space(value)
    if "共用" in text:
        return "共用群"
    if "采购" in text:
        return "采购群"
    if "销售" in text:
        return "销售群"
    if "待定" in text:
        return "待定"
    return ""


def purpose_fields(purpose):
    if purpose == "共用群":
        return "both"
    if purpose == "采购群":
        return "purchase"
    if purpose == "销售群":
        return "sales"
    return ""


def merge_group_values(old_value, new_values):
    result = []
    for value in split_multi(old_value):
        if re.fullmatch(r"\d+", value) or value.lower() in {"null", "none", "-"}:
            continue
        if value not in result:
            result.append(value)
    for value in new_values:
        value = clean_space(value)
        if value and value not in result:
            result.append(value)
    return clip(",".join(result), 1024)


def make_candidate(group, member):
    nickname = clean_space(member.get("nick_name"))
    remark = clean_space(member.get("remark"))
    alias = clean_space(member.get("alias"))
    username = clean_space(member.get("username"))
    name = display_name(member)
    text = " ".join(filter(None, (name, nickname, remark, alias)))
    phone = extract_phone(text)
    email = extract_email(text)
    wechat = ""
    qq = ""
    if alias and not alias.startswith("wxid_"):
        wechat = clip(alias, 50)
    elif username and not username.startswith("wxid_") and not username.endswith("@openim") and not username.startswith("qq"):
        wechat = clip(username, 50)
    if username.lower().startswith("qq") and username[2:].isdigit():
        qq = clip(username[2:], 20)
    second = []
    if nickname and nickname != name:
        second.append(f"昵称：{nickname}")
    if remark and remark != name:
        second.append(f"备注：{remark}")
    candidate = {
        "username": username,
        "name": clip(name, 100),
        "post": clip(infer_post(text), 100),
        "position": "",
        "phone": clip(phone, 20),
        "email": clip(email, 100),
        "whatsapp": clip(phone if phone and "whatsapp" in text.lower() else "", 50),
        "wechat": wechat,
        "qq": qq,
        "other_contact_first": clip(username, 200),
        "other_contact_second": "",
        "remark_first": f"来源微信群：{group}",
        "remark_second": clip("；".join(second), 200),
        "source_groups": [group],
    }
    key = f"u:{norm(username)}" if username else (
        f"p:{phone}" if phone else f"w:{norm(wechat)}" if wechat else f"n:{norm(name)}"
    )
    return key, candidate


def merge_candidate(plan, key, candidate):
    current = plan["candidates"].get(key)
    if current is None:
        plan["candidates"][key] = candidate
        return
    for group in candidate["source_groups"]:
        if group not in current["source_groups"]:
            current["source_groups"].append(group)
    for field in ("phone", "email", "whatsapp", "wechat", "qq", "post"):
        if not current.get(field) and candidate.get(field):
            current[field] = candidate[field]
    if candidate.get("remark_second") and current.get("remark_second") != candidate["remark_second"]:
        combined = "；".join(filter(None, (current.get("remark_second"), candidate.get("remark_second"))))
        current["remark_second"] = clip(combined, 200)


def append_remark(old, addition):
    old = clean_space(old)
    addition = clean_space(addition)
    if not addition:
        return clip(old, 200)
    if not old:
        return clip(addition, 200)
    if addition in old:
        return clip(old, 200)
    return clip(f"{old}；{addition}", 200)


class PlanBuilder:
    def __init__(self, suppliers, groups, self_names):
        self.suppliers_by_code = {item["supplier_code"]: item for item in suppliers if item.get("supplier_code")}
        self.groups_by_id = {item.get("username"): item for item in groups}
        self.self_names = self_names
        self.plans_by_code = {}
        self.new_by_key = {}
        self.row_results = {}
        self.skipped = []
        self.missing_groups = []
        self.internal_skipped = 0
        self._build_existing_map()

    def _build_existing_map(self):
        candidates = {}
        for supplier in self.suppliers_by_code.values():
            fields = [supplier.get("supplier_name"), supplier.get("supplier_short_name")]
            fields.extend(split_multi(supplier.get("supplier_alias")))
            for field in fields:
                key = norm(field)
                if len(key) >= 2:
                    candidates.setdefault(key, set()).add(supplier["supplier_code"])
        self.existing_key_map = {key: next(iter(codes)) for key, codes in candidates.items() if len(codes) == 1}

    def existing_plan(self, code):
        plan = self.plans_by_code.get(code)
        if plan:
            return plan
        supplier = self.suppliers_by_code.get(code)
        if not supplier:
            return None
        plan = {
            "kind": "existing", "code": code, "supplier": supplier,
            "supplier_id": supplier["id"], "name": supplier["supplier_name"],
            "key": norm(supplier["supplier_name"]), "groups": [], "purpose_entries": [],
            "candidates": OrderedDict(), "rows": [], "existing_contacts": [],
            "new_contact_actions": [], "contact_updates": [], "source_type": "",
        }
        self.plans_by_code[code] = plan
        return plan

    def new_plan(self, name):
        key = norm(name)
        plan = self.new_by_key.get(key)
        if plan:
            return plan
        plan = {
            "kind": "new", "code": None, "supplier": None, "supplier_id": None,
            "name": clip(clean_space(name), 200), "key": key, "groups": [], "purpose_entries": [],
            "candidates": OrderedDict(), "rows": [], "existing_contacts": [],
            "new_contact_actions": [], "contact_updates": [], "source_type": "",
        }
        self.new_by_key[key] = plan
        return plan

    def add_group(self, plan, group, purpose):
        group = clean_space(group)
        if group and group not in plan["groups"]:
            plan["groups"].append(group)
        plan["purpose_entries"].append({"group": group, "purpose": purpose})

    def add_members(self, plan, group, members):
        valid = external_members(members, self.self_names)
        for member in valid:
            key, candidate = make_candidate(group, member)
            merge_candidate(plan, key, candidate)
        self.internal_skipped += max(0, len(members or []) - len(valid))

    def process_first_tab(self, rows):
        for row in rows:
            if clean_space(row.get("审核结果")) != "采用":
                continue
            group = clean_space(row.get("群名称（群昵称）"))
            code = clean_space(row.get("确认供应商编码"))
            if not code or code not in self.suppliers_by_code:
                self.skipped.append({"tab": "审核表", "group": group, "reason": f"供应商编码不存在：{code}"})
                continue
            plan = self.existing_plan(code)
            purpose = group_purpose(row.get("确认群用途") or row.get("建议群用途"))
            self.add_group(plan, group, purpose)
            plan["rows"].append(("审核表", row["_row"]))
            group_row = self.groups_by_id.get(clean_space(row.get("群wxid")))
            if group_row:
                self.add_members(plan, group, group_row.get("members") or [])
            else:
                self.missing_groups.append({"tab": "审核表", "group": group, "wxid": row.get("群wxid")})

    def _existing_code_for_group(self, row, key):
        explicit = clean_space(row.get("确认供应商编码"))
        if explicit and explicit in self.suppliers_by_code:
            return explicit
        group = row.get("群名称（群昵称）")
        if group in SECOND_EXISTING_CODE and SECOND_EXISTING_CODE[group] in self.suppliers_by_code:
            return SECOND_EXISTING_CODE[group]
        code = self.existing_key_map.get(norm(key))
        return code if code in self.suppliers_by_code else None

    def process_second_tab(self, rows):
        for row in rows:
            group = clean_space(row.get("群名称（群昵称）"))
            key = derive_group_key(group)
            group_row = self.groups_by_id.get(clean_space(row.get("群wxid")))
            members = group_row.get("members") if group_row else []
            valid = external_members(members, self.self_names)
            purpose = group_purpose(row.get("确认群用途") or row.get("建议群用途"))
            skip_reason = self.skip_reason(group, key, members, valid)
            if skip_reason:
                self.skipped.append({"tab": "未匹配业务群", "group": group, "reason": skip_reason})
                self.row_results[row["_row"]] = {
                    "status": "跳过", "code": "", "name": "", "reason": skip_reason, "contact_count": 0,
                }
                continue
            code = self._existing_code_for_group(row, key)
            if code:
                plan = self.existing_plan(code)
                plan["source_type"] = "second_existing"
            else:
                plan = self.new_plan(key or group)
                plan["source_type"] = "second_new"
            self.add_group(plan, group, purpose)
            plan["rows"].append(("未匹配业务群", row["_row"]))
            self.add_members(plan, group, members)
            self.row_results[row["_row"]] = {
                "status": "已匹配现有" if code else "已创建",
                "code": code or "", "name": plan["name"], "reason": "", "contact_count": len(valid),
            }

    @staticmethod
    def skip_reason(group, key, members, valid_members):
        if group in SKIP_EXACT:
            return "按业务范围跳过：内部/财税/收款/通用群"
        lower = group.lower()
        if any(token in lower for token in ("代帐", "空中云汇", "网易售后", "公司事务", "小新人培训", "内部群", "内部offer")):
            return "按业务范围跳过：非供应商群"
        if not clean_space(key):
            return "群名无法识别供应商"
        return ""

    def finalize(self, contacts_by_supplier):
        for plan in list(self.plans_by_code.values()) + list(self.new_by_key.values()):
            existing = list(contacts_by_supplier.get(plan["supplier_id"], []))
            plan["existing_contacts"] = existing
            primary_exists = any(int(item.get("is_primary") or 0) == 1 for item in existing)
            indexes = {"username": {}, "wechat": {}, "phone": {}, "name": {}}
            for contact in existing:
                values = {
                    "username": norm(contact.get("other_contact_first")),
                    "wechat": norm(contact.get("wechat")),
                    "phone": re.sub(r"\D", "", str(contact.get("phone") or "")),
                    "name": norm(contact.get("contact_name")),
                }
                for field, value in values.items():
                    if value:
                        indexes[field][value] = contact
            used_names = set(indexes["name"])
            used_phones = set(indexes["phone"])
            used_wechats = set(indexes["wechat"])
            used_usernames = set(indexes["username"])
            for candidate in plan["candidates"].values():
                values = {
                    "username": norm(candidate.get("other_contact_first")),
                    "wechat": norm(candidate.get("wechat")),
                    "phone": re.sub(r"\D", "", str(candidate.get("phone") or "")),
                    "name": norm(candidate.get("name")),
                }
                matched = None
                for field in ("username", "wechat", "phone", "name"):
                    if values[field] and values[field] in indexes[field]:
                        matched = indexes[field][values[field]]
                        break
                if matched:
                    plan["contact_updates"].append({"contact": matched, "candidate": candidate})
                else:
                    if ((values["name"] and values["name"] in used_names)
                            or (values["phone"] and values["phone"] in used_phones)
                            or (values["wechat"] and values["wechat"] in used_wechats)
                            or (values["username"] and values["username"] in used_usernames)):
                        continue
                    plan["new_contact_actions"].append({"candidate": candidate, "is_primary": 0 if primary_exists else 1})
                    primary_exists = True
                    if values["username"]:
                        used_usernames.add(values["username"])
                    if values["wechat"]:
                        used_wechats.add(values["wechat"])
                    if values["phone"]:
                        used_phones.add(values["phone"])
                    if values["name"]:
                        used_names.add(values["name"])
        return self


def open_db(config):
    return pymysql.connect(
        host=config["host"], port=config["port"], user=config["user"],
        password=config["password"], database=config["database"], charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor, autocommit=False,
    )


def load_db(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT * FROM crm_supplier ORDER BY supplier_code")
        suppliers = cur.fetchall()
        cur.execute("SELECT * FROM crm_supplier_contact ORDER BY supplier_id, id")
        contacts = cur.fetchall()
        self_names = load_self_names(cur)
    contacts_by_supplier = {}
    for contact in contacts:
        contacts_by_supplier.setdefault(contact["supplier_id"], []).append(contact)
    return suppliers, contacts_by_supplier, self_names


def next_supplier_codes(suppliers, count):
    max_value = 0
    for supplier in suppliers:
        match = re.fullmatch(r"VC(\d+)", str(supplier.get("supplier_code") or ""))
        if match:
            max_value = max(max_value, int(match.group(1)))
    return [f"VC{max_value + index:07d}" for index in range(1, count + 1)]


def plan_supplier_values(plan, existing):
    purchase_new = []
    sales_new = []
    for item in plan["purpose_entries"]:
        mode = purpose_fields(item["purpose"])
        if mode in {"both", "purchase"} and item["group"] not in purchase_new:
            purchase_new.append(item["group"])
        if mode in {"both", "sales"} and item["group"] not in sales_new:
            sales_new.append(item["group"])
    source_text = "、".join(plan["groups"])
    products = infer_products(" ".join(plan["groups"] + [c.get("name", "") for c in plan["candidates"].values()]))
    if existing:
        values = {}
        if purchase_new:
            values["purchase_group"] = merge_group_values(existing.get("purchase_group"), purchase_new)
        if sales_new:
            values["sales_group"] = merge_group_values(existing.get("sales_group"), sales_new)
        if not clean_space(existing.get("supplier_short_name")):
            values["supplier_short_name"] = derive_short_name(existing.get("supplier_name"))
        if not clean_space(existing.get("main_products")) and products:
            values["main_products"] = clip(products, 256)
        if plan["source_type"] == "second_existing" and source_text and not clean_space(existing.get("remark_second")):
            values["remark_second"] = clip(f"微信群补全：{source_text}", 1024)
        values["update_by"] = "wechat_auto"
        return values
    return {
        "supplier_code": plan["code"],
        "supplier_name": plan["name"],
        "supplier_short_name": derive_short_name(plan["name"]),
        "supplier_alias": clip(" / ".join(plan["groups"]), 200),
        "supplier_type": "",
        "main_products": clip(products, 256),
        "purchase_group": clip(",".join(purchase_new), 1024) if purchase_new else None,
        "sales_group": clip(",".join(sales_new), 1024) if sales_new else None,
        "remark": clip(f"来源微信群：{source_text}", 2048),
        "remark_second": clip("由微信群名称及成员备注自动创建", 1024),
        "status": 1,
        "create_by": "wechat_auto",
        "update_by": "wechat_auto",
    }


def update_supplier(cur, supplier_id, values):
    allowed = {
        "supplier_short_name", "supplier_alias", "main_products", "purchase_group",
        "sales_group", "remark", "remark_second", "update_by",
    }
    values = {key: value for key, value in values.items() if key in allowed}
    if not values:
        return
    columns = ", ".join(f"{key} = %s" for key in values)
    params = list(values.values()) + [supplier_id]
    cur.execute(f"UPDATE crm_supplier SET {columns}, update_time = NOW() WHERE id = %s", params)


def insert_supplier(cur, row):
    columns = list(row.keys())
    cur.execute(
        f"INSERT INTO crm_supplier ({', '.join(columns)}) VALUES ({', '.join(['%s'] * len(columns))})",
        [row[column] for column in columns],
    )
    return cur.lastrowid


def apply_contact(cur, supplier_id, candidate, is_primary):
    source = "；".join(f"来源微信群：{group}" for group in candidate["source_groups"])
    cur.execute(
        """
        INSERT INTO crm_supplier_contact
        (supplier_id, contact_name, post, position, phone, email, whatsapp, wechat, qq,
         other_contact_first, other_contact_second, remark_first, remark_second,
         is_primary, create_by, update_by)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (
            supplier_id, candidate["name"], candidate["post"], candidate["position"],
            candidate["phone"] or None, candidate["email"] or None, candidate["whatsapp"] or None,
            candidate["wechat"] or None, candidate["qq"] or None,
            candidate["other_contact_first"] or None, candidate["other_contact_second"] or None,
            clip(source, 200), candidate["remark_second"] or None, is_primary,
            "wechat_auto", "wechat_auto",
        ),
    )
    return cur.lastrowid


def update_contact(cur, contact, candidate):
    values = {}
    for db_field, candidate_field in (
        ("contact_name", "name"), ("post", "post"), ("phone", "phone"), ("email", "email"),
        ("whatsapp", "whatsapp"), ("wechat", "wechat"), ("qq", "qq"),
        ("other_contact_first", "other_contact_first"),
    ):
        old = clean_space(contact.get(db_field))
        new = clean_space(candidate.get(candidate_field))
        if not old and new:
            values[db_field] = clip(new, 200)
    source = "；".join(f"来源微信群：{group}" for group in candidate["source_groups"])
    old_remark = clean_space(contact.get("remark_first"))
    if source and source not in old_remark:
        values["remark_first"] = append_remark(old_remark, source)
    if candidate.get("remark_second") and not clean_space(contact.get("remark_second")):
        values["remark_second"] = clip(candidate["remark_second"], 200)
    if not values:
        return False
    columns = ", ".join(f"{key} = %s" for key in values)
    cur.execute(
        f"UPDATE crm_supplier_contact SET {columns}, update_time = NOW(), update_by = 'wechat_auto' WHERE id = %s",
        list(values.values()) + [contact["id"]],
    )
    return True


def write_backup(conn, path):
    backup = {}
    with conn.cursor() as cur:
        for table in ("crm_supplier", "crm_supplier_contact"):
            cur.execute(f"SELECT * FROM {table}")
            backup[table] = cur.fetchall()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(backup, ensure_ascii=False, indent=2, default=str), encoding="utf-8")


def write_plan_file(path, builder, stats):
    output = {"stats": stats, "suppliers": [], "skipped": builder.skipped, "missing_groups": builder.missing_groups}
    plans = list(builder.plans_by_code.values()) + list(builder.new_by_key.values())
    for plan in plans:
        output["suppliers"].append({
            "code": plan.get("code"), "name": plan["name"], "kind": plan["kind"],
            "groups": plan["groups"], "purpose_entries": plan["purpose_entries"],
            "candidate_count": len(plan["candidates"]),
            "new_contact_count": len(plan["new_contact_actions"]),
            "updated_contact_count": len(plan["contact_updates"]),
        })
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")


def write_result_workbook(source_path, output_path, builder, suppliers_after, contacts_after, stats):
    wb = load_workbook(source_path)
    ws2 = wb["未匹配业务群"]
    headers2 = {clean_space(ws2.cell(1, col).value): col for col in range(1, ws2.max_column + 1)}
    for row_index, result in builder.row_results.items():
        if "确认供应商编码" in headers2:
            ws2.cell(row_index, headers2["确认供应商编码"]).value = result["code"] or None
        if "审核结果" in headers2:
            ws2.cell(row_index, headers2["审核结果"]).value = result["status"]
        if "审核备注" in headers2:
            note = result["reason"] if result["reason"] else (
                f"{result['status']}：{result['name']}；群成员 {result['contact_count']} 人"
            )
            ws2.cell(row_index, headers2["审核备注"]).value = note

    def add_sheet(title, headers, rows):
        ws = wb.create_sheet(title)
        ws.append(headers)
        for row in rows:
            ws.append(row)
        for cell in ws[1]:
            cell.font = Font(bold=True)
            cell.fill = PatternFill("solid", fgColor="D9EAF7")
        for column in ws.columns:
            width = min(50, max(12, max(len(str(cell.value or "")) for cell in column) + 2))
            ws.column_dimensions[column[0].column_letter].width = width
        ws.freeze_panes = "A2"
        for row in ws.iter_rows(min_row=2):
            for cell in row:
                cell.alignment = Alignment(vertical="top", wrap_text=True)
        return ws

    summary_rows = [
        ("执行时间", datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
        ("第一页签采用群", stats["first_groups"]),
        ("第一页签供应商", stats["first_suppliers"]),
        ("第二页签处理群", stats["second_groups"]),
        ("第二页签匹配现有供应商", stats["second_existing"]),
        ("第二页签新建供应商", stats["second_new"]),
        ("微信群跳过", stats["skipped"]),
        ("新增联系人", stats["contacts_inserted"]),
        ("补全现有联系人", stats["contacts_updated"]),
        ("排除我方成员记录", stats["internal_skipped"]),
        ("本地未找到群", stats["missing_groups"]),
        ("供应商总数（导入后）", len(suppliers_after)),
        ("联系人总数（导入后）", len(contacts_after)),
    ]
    add_sheet("导入汇总", ["项目", "结果"], summary_rows)

    change_rows = []
    plans = list(builder.plans_by_code.values()) + list(builder.new_by_key.values())
    for plan in plans:
        old = plan.get("old_supplier") or plan["supplier"] or {}
        purchase_groups = [item["group"] for item in plan["purpose_entries"] if purpose_fields(item["purpose"]) in {"both", "purchase"}]
        sales_groups = [item["group"] for item in plan["purpose_entries"] if purpose_fields(item["purpose"]) in {"both", "sales"}]
        change_rows.append((
            plan.get("code") or "(新建)", plan["name"], "现有" if plan["kind"] == "existing" else "新建",
            old.get("purchase_group") or "", old.get("sales_group") or "",
            "、".join(purchase_groups), "、".join(sales_groups),
            len(plan["new_contact_actions"]), len(plan["contact_updates"]),
        ))
    add_sheet(
        "供应商变更",
        ["供应商编码", "供应商名称", "类型", "原采购群", "原销售群", "导入采购群", "导入销售群", "新增联系人", "补全联系人"],
        change_rows,
    )

    contact_rows = []
    for plan in plans:
        for action in plan["new_contact_actions"]:
            item = action["candidate"]
            contact_rows.append((plan.get("code") or "(新建)", plan["name"], item["name"], item["post"], item["phone"], item["wechat"], " / ".join(item["source_groups"]), "新增"))
        for update in plan["contact_updates"]:
            item = update["candidate"]
            contact_rows.append((plan.get("code") or "(新建)", plan["name"], item["name"], item["post"], item["phone"], item["wechat"], " / ".join(item["source_groups"]), "已存在/补全"))
    add_sheet("联系人补全", ["供应商编码", "供应商名称", "联系人", "岗位", "手机", "微信", "来源群", "处理"], contact_rows)
    add_sheet("未处理群", ["页签", "群名称", "原因"], [(item["tab"], item["group"], item["reason"]) for item in builder.skipped])
    wb.save(output_path)


def print_summary(stats, builder, output_path=None):
    print(json.dumps(stats, ensure_ascii=False, indent=2))
    if builder.skipped:
        print("\n跳过群：")
        for item in builder.skipped:
            print(f"- {item['group']}: {item['reason']}")
    if builder.missing_groups:
        print("\n本地未找到群：")
        for item in builder.missing_groups:
            print(f"- {item['group']} ({item['wxid']})")
    if output_path:
        print(f"\n结果文件：{output_path}")


def main():
    parser = argparse.ArgumentParser(description="导入供应商群聊匹配结果和联系人")
    parser.add_argument("--excel", default=str(DEFAULT_EXCEL))
    parser.add_argument("--members", default=str(DEFAULT_MEMBERS))
    parser.add_argument("--apply", action="store_true", help="执行数据库写入；默认只做干跑")
    parser.add_argument("--out", default="")
    parser.add_argument("--plan-out", default="")
    args = parser.parse_args()

    excel_path = Path(args.excel)
    members_path = Path(args.members)
    if not excel_path.exists():
        raise SystemExit(f"审核表不存在：{excel_path}")
    if not members_path.exists():
        raise SystemExit(f"群成员文件不存在：{members_path}")
    groups = json.loads(members_path.read_text(encoding="utf-8"))

    read_wb = load_workbook(excel_path, data_only=True)
    first_rows = read_rows(read_wb["审核表"])
    second_rows = read_rows(read_wb["未匹配业务群"])

    conn = open_db(DEFAULT_DB)
    try:
        suppliers_before, contacts_by_supplier, self_names = load_db(conn)
        builder = PlanBuilder(suppliers_before, groups, self_names)
        builder.process_first_tab(first_rows)
        builder.process_second_tab(second_rows)
        builder.finalize(contacts_by_supplier)

        new_plans = sorted(builder.new_by_key.values(), key=lambda item: item["name"].lower())
        for plan, code in zip(new_plans, next_supplier_codes(suppliers_before, len(new_plans))):
            plan["code"] = code
            for tab, row_index in plan["rows"]:
                if tab == "未匹配业务群" and row_index in builder.row_results:
                    builder.row_results[row_index]["code"] = code
        unresolved = [
            (row_index, result["name"])
            for row_index, result in builder.row_results.items()
            if result["status"] != "跳过" and not clean_space(result["code"])
        ]
        if unresolved:
            raise RuntimeError(f"存在未生成供应商编码的第二页签记录：{unresolved}")

        first_adopted = [row for row in first_rows if clean_space(row.get("审核结果")) == "采用"]
        second_handled = [
            row for row in second_rows
            if row["_row"] in builder.row_results and builder.row_results[row["_row"]]["status"] != "跳过"
        ]
        all_plans = list(builder.plans_by_code.values()) + new_plans
        stats = {
            "first_groups": len(first_adopted),
            "first_suppliers": len({clean_space(row.get("确认供应商编码")) for row in first_adopted if clean_space(row.get("确认供应商编码"))}),
            "second_groups": len(second_handled),
            "second_existing": len({p["code"] for p in builder.plans_by_code.values() if p.get("source_type") == "second_existing"}),
            "second_new": len(new_plans),
            "skipped": len(builder.skipped),
            "contacts_inserted": sum(len(p["new_contact_actions"]) for p in all_plans),
            "contacts_updated": sum(len(p["contact_updates"]) for p in all_plans),
            "internal_skipped": builder.internal_skipped,
            "missing_groups": len(builder.missing_groups),
        }

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        plan_path = Path(args.plan_out) if args.plan_out else ROOT / "Reference" / f"供应商群聊导入计划_{timestamp}.json"
        output_path = Path(args.out) if args.out else ROOT / "Reference" / f"供应商群聊导入结果_{timestamp}.xlsx"
        write_plan_file(plan_path, builder, stats)
        print_summary(stats, builder)
        print(f"计划文件：{plan_path}")
        if not args.apply:
            print("当前为干跑模式，未写数据库。")
            return

        backup_path = ROOT / "Reference" / f"供应商群聊导入备份_{timestamp}.json"
        write_backup(conn, backup_path)
        with conn.cursor() as cur:
            for plan in all_plans:
                plan["old_supplier"] = dict(plan["supplier"] or {})
                if plan["kind"] == "new":
                    row = plan_supplier_values(plan, None)
                    plan["supplier_id"] = insert_supplier(cur, row)
                    plan["supplier"] = row
                else:
                    values = plan_supplier_values(plan, plan["supplier"])
                    update_supplier(cur, plan["supplier_id"], values)
                    plan["supplier"].update(values)
            for plan in all_plans:
                for action in plan["new_contact_actions"]:
                    apply_contact(cur, plan["supplier_id"], action["candidate"], action["is_primary"])
                for update in plan["contact_updates"]:
                    update_contact(cur, update["contact"], update["candidate"])
        conn.commit()
        suppliers_after, contacts_after_by_supplier, _ = load_db(conn)
        contacts_after = [item for rows in contacts_after_by_supplier.values() for item in rows]
        write_result_workbook(excel_path, output_path, builder, suppliers_after, contacts_after, stats)
        print(f"备份文件：{backup_path}")
        print(f"结果文件：{output_path}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
