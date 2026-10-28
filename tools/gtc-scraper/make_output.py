# -*- coding: utf-8 -*-
import json, os
import pandas as pd
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

base = r"C:\Project\RuoYi-Vue\tools\gtc-scraper"
outdir = r"C:\Project\RuoYi-Vue\tools\gtc-exhibitors"
os.makedirs(outdir, exist_ok=True)
rows = json.load(open(os.path.join(base, "final_rows.json"), encoding="utf-8"))
df = pd.DataFrame(rows)
level_order = {"Elite":1,"Diamond":2,"Platinum":3,"Gold":4,"Silver":5,"A La Carte":6,"Exhibitor":7,"Inception Startup":8}
df["_level"] = df["赞助级别"].map(level_order).fillna(9)
rel_order = {"是":1,"待核实":2,"否":3}
df["_rel"] = df["是否IT配件或服务器相关"].map(rel_order).fillna(4)

cols = ["展商名称","赞助级别","所在国家/区域","区域","主营业务分类","主营业务简介","是否IT配件或服务器相关","相关度说明","官网","联系邮箱","联系电话","联系方式备注","展位号","官方简介","展商ID","数据来源"]
df_out = df[cols].copy().fillna("")

# CSV full
csv_path = os.path.join(outdir, "GTC2026_展商总表.csv")
df_out.to_csv(csv_path, index=False, encoding="utf-8-sig")

# IT related
it = df[(df["是否IT配件或服务器相关"]=="是")].sort_values(["_rel","_level","所在国家/区域","展商名称"])[cols].fillna("")
it.to_csv(os.path.join(outdir, "GTC2026_IT相关展商.csv"), index=False, encoding="utf-8-sig")

# core hardware
core_cats = ["服务器/整机/系统集成","GPU/CPU/加速卡/AI芯片","半导体/芯片","存储/内存","网络/互连/光模块","散热/电源/机柜/数据中心基础设施","PCB/连接器/电子制造"]
core = df[(df["主营业务分类"].isin(core_cats))].sort_values(["_level","所在国家/区域","展商名称"])[cols].fillna("")
core.to_csv(os.path.join(outdir, "GTC2026_核心硬件与配件.csv"), index=False, encoding="utf-8-sig")

# XLSX
wb = Workbook()
ws = wb.active
ws.title = "展商总表"
header_fill = PatternFill("solid", fgColor="1F4E78")
header_font = Font(color="FFFFFF", bold=True, size=10)
thin = Side(style="thin", color="D9E2F3")
border = Border(left=thin,right=thin,top=thin,bottom=thin)
green_fill = PatternFill("solid", fgColor="E2EFDA")
red_fill = PatternFill("solid", fgColor="FCE4EC")
yellow_fill = PatternFill("solid", fgColor="FFF2CC")

def write_sheet(ws, data, title, freeze="A2", widths=None, rel_col=None, url_col=None):
    ws.append(list(data.columns))
    for c in range(1, len(data.columns)+1):
        cell = ws.cell(row=1, column=c)
        cell.fill = header_fill; cell.font = header_font
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        cell.border = border
    for _, row in data.iterrows():
        ws.append([row[c] if pd.notna(row[c]) else "" for c in data.columns])
    ws.row_dimensions[1].height = 32
    ws.freeze_panes = freeze
    ws.auto_filter.ref = f"A1:{get_column_letter(len(data.columns))}{ws.max_row}"
    default_widths = {"展商名称":30,"赞助级别":14,"所在国家/区域":14,"区域":10,"主营业务分类":28,"主营业务简介":55,"是否IT配件或服务器相关":14,"相关度说明":34,"官网":35,"联系邮箱":32,"联系电话":24,"联系方式备注":38,"展位号":14,"官方简介":70,"展商ID":24,"数据来源":42}
    for idx, col in enumerate(data.columns, start=1):
        ws.column_dimensions[get_column_letter(idx)].width = (widths or default_widths).get(col, 18)
    for r in range(2, ws.max_row+1):
        for c in range(1, len(data.columns)+1):
            cell = ws.cell(row=r, column=c)
            cell.alignment = Alignment(vertical="top", wrap_text=(data.columns[c-1] in ("主营业务简介","官方简介","相关度说明","数据来源")))
            cell.font = Font(size=9)
            cell.border = border
        if rel_col:
            v = ws.cell(row=r, column=rel_col).value
            fill = green_fill if v=="是" else (yellow_fill if v=="待核实" else red_fill)
            ws.cell(row=r, column=rel_col).fill = fill
        if url_col:
            cell = ws.cell(row=r, column=url_col)
            if cell.value:
                cell.hyperlink = cell.value
                cell.font = Font(size=9, color="0563C1", underline="single")

write_sheet(ws, df_out, "展商总表", rel_col=7, url_col=9)

ws2 = wb.create_sheet("IT相关展商")
write_sheet(ws2, it, "IT相关展商", rel_col=7, url_col=9)
ws3 = wb.create_sheet("核心硬件与配件")
write_sheet(ws3, core, "核心硬件与配件", rel_col=7, url_col=9)

# stats
ws4 = wb.create_sheet("统计概览")
stats_rows = []
stats_rows.append(["指标","数值","说明"])
stats_rows.append(["展商总数", len(df), "NVIDIA GTC 2026 官网展商目录抓取"])
stats_rows.append(["IT配件/服务器相关（是）", int((df["是否IT配件或服务器相关"]=="是").sum()), "含核心硬件、云/数据中心、量子计算、服务器基础软件"])
stats_rows.append(["核心硬件/配件/服务器", len(core), "服务器整机、芯片、存储、网络、散热电源、PCB连接器"])
stats_rows.append(["不相关（否）", int((df["是否IT配件或服务器相关"]=="否").sum()), "软件应用、咨询集成、行业AI应用"])
stats_rows.append(["待核实", int((df["是否IT配件或服务器相关"]=="待核实").sum()), "公开信息不足"])
stats_rows.append(["有公开邮箱", int((df["联系邮箱"]!="").sum()), "仅统计企业官网公开的通用/部门邮箱"])
stats_rows.append(["有公开电话", int((df["联系电话"]!="").sum()), "仅统计企业官网公开电话"])
stats_rows.append(["国家/区域待核实", int((df["所在国家/区域"]=="待核实").sum()), "需人工补充"])
stats_rows.append([])
stats_rows.append(["按赞助级别","数量",""])
for k,v in df["赞助级别"].value_counts().items():
    stats_rows.append([k,int(v),""])
stats_rows.append([])
stats_rows.append(["按主营业务分类","数量",""])
for k,v in df["主营业务分类"].value_counts().items():
    stats_rows.append([k,int(v),""])
stats_rows.append([])
stats_rows.append(["按国家/区域（Top 15）","数量",""])
for k,v in df["所在国家/区域"].value_counts().head(15).items():
    stats_rows.append([k,int(v),""])
for row in stats_rows:
    ws4.append(row)
for c in range(1,4):
    cell=ws4.cell(row=1,column=c);cell.fill=header_fill;cell.font=header_font;cell.alignment=Alignment(horizontal="center")
ws4.column_dimensions["A"].width=32; ws4.column_dimensions["B"].width=14; ws4.column_dimensions["C"].width=62
for r in range(2, ws4.max_row+1):
    ws4.cell(row=r,column=3).alignment=Alignment(wrap_text=True, vertical="top")

xlsx_path = os.path.join(outdir, "GTC2026_展商数据.xlsx")
wb.save(xlsx_path)

print("saved", csv_path, len(df_out))
print("saved", xlsx_path)
print("it", len(it), "core", len(core))
print(df["所在国家/区域"].value_counts().head(12).to_string())
