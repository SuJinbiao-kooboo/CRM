# PO（Purchasing Order）单据生成 —— 设计方案草稿

> 目标：在 CRM 模块中新增"订单"功能，根据订单数据自动把内容填入 PO 模板的空白单元格，
> 输出 Excel，并转换出 PDF。本文以 PO 为例，后续 PI / 装箱单 / 委托单 / SN List / CI 沿用同一套机制。

---

## 一、模板结构解析（`OrderTemplate/PO-Purchasing Order template.xlsx`，单 Sheet「PO」）

| 区域 | 单元格 | 内容 | 是否需填 |
|------|--------|------|----------|
| 标题 | `A1:I1`（合并） | `Purchasing Order` | 固定 |
| 单号 | `A2:E2`（合并） | `PO No:` | **待填（单号值）** |
| 日期 | `F2:I2`（合并） | `Order Date:` | **待填（订单日期）** |
| 买方 | `A3:E3`（合并） | `Buyer:` + 香港亦鐘人集团抬头/地址/联系人/邮箱/电话 | 固定 |
| 卖方 | `F3:I3`（合并） | `Seller:` 标签，下方空白 | **待填（供应商信息块）** |
| 收货 | `A4:E4`（合并） | `Ship to:` + 香港仓库地址/联系人 | 固定 |
| 印章 | 浮动图片 `xl/media/image1.png` | HK M&E 电子印章（透明底），锚点在 D1:F5 | 保留 |
| 明细表头 | `A5:I5` | NO. / Brand / ITEM / Spec. / QTY(pcs) / Warranty / U\\P(USD) / AMOUNT(USD) / Lead time | 固定 |
| 明细数据 | 第 6 行（当前仅 1 行） | 空 | **待填（物料行，可多行）** |
| 合计 | 第 7 行 `A7` = TOTAL，`H7` = `=SUM(H6:H6)` | — | 公式需随行数扩展 |
| 条款 | `A8:I8`（合并） | Terms and Conditions 1~12 条 | 固定 |

关键细节：
- 空白值都落在**合并单元格**里（只写左上角格即可）。
- 明细区模板**只有 1 个数据行**（第 6 行）。物料多于 1 条时必须**插入行**并同步下移 TOTAL 行、扩展 `SUM` 公式。
- 列格式：`G/H` 为金额两位小数（`H`=`0.00`），`I`（Lead time）为日期格式 `mm-dd-yy`，`C/D`（ITEM/Spec.）为文本格式。
- 版面为 A4 纵向、Fit-to-page 缩放。

---

## 二、数据模型（新增表）

### 1. `crm_order` —— 订单主表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| order_no | varchar(32) UK | 订单编号，如 `PO20261019-001` |
| order_type | varchar(16) | 单据类型 PO/PI/PL/ENTRUST/SN/CI |
| order_date | date | 下单日期（填 Order Date） |
| seller_id | bigint | 供应商ID → 关联 `crm_supplier` |
| seller_contact_id | bigint | 供应商联系人ID（可空，默认取主联系人） |
| seller_snapshot | text/json | 生成时的供应商信息快照（名称/地址/联系人/邮箱/电话/银行等），防止供应商资料变更影响历史单据 |
| buyer_id | bigint | 采购方ID（可空，PO 固定为 HK M&E） |
| buyer_snapshot | text/json | 采购方信息快照 |
| currency | varchar(8) | 币种，默认 USD |
| total_amount | decimal(18,2) | 合计金额 |
| status | char(1) | 状态：0 草稿 / 1 已生成 / 2 作废 |
| remark | varchar(500) | 备注 |
| create_by/create_time/update_by/update_time | — | 审计字段（RuoYi 规范） |

> `seller_snapshot` 采用快照的意义：单据一旦开出即冻结当时信息；同时页面"选择后仍可修改"只需改快照内容，不回写供应商主档。

### 2. `crm_order_item` —— 订单物料明细
| 字段 | 类型 | 对应模板列 |
|------|------|-----------|
| id | bigint PK | — |
| order_id | bigint | 关联主表 |
| line_no | int | NO. |
| brand | varchar(64) | Brand |
| item_name | varchar(255) | ITEM |
| spec | varchar(500) | Spec. |
| qty | decimal(18,2) | QTY(pcs) |
| warranty | varchar(64) | Warranty |
| unit_price | decimal(18,4) | U/P(USD) |
| amount | decimal(18,2) | AMOUNT(USD) = qty × unit_price（可覆盖） |
| lead_time | varchar(32) | Lead time |
| remark | varchar(255) | — |

### 3. `crm_order_seq`（可选）—— 单号流水
用于并发下生成不重复序号；也可直接 `SELECT MAX(order_no)` + 唯一索引重试。

---

## 三、订单编号规则

- 格式：`{type}{yyyyMMdd}-{3位流水}`，例：`PO20261019-001`。
- 流水按"类型 + 日期"维度每日重置；由主表唯一索引保证不重复，冲突则重试。
- 编号规则做成可配置（前缀 / 日期格式 / 位数），便于后续 PI、CI 等复用。

---

## 四、Excel 生成流程（PO 为例）

1. **载入模板**：从配置目录（如 `application.yml` 的 `order.template-path`）读取 `PO-Purchasing Order template.xlsx`，在内存中打开 workbook（模板文件只读，不修改）。
2. **填 PO No**：写入 `A2`（合并 `A2:E2` 左上角）。
   - 方案A（默认，改动最小）：`A2 = "PO No: " + orderNo`，保持原合并与左对齐。
   - 方案B（标签/值样式分离）：取消 `A2:E2` 合并，`A2="PO No:"`（加粗）+ `B2:E2=orderNo`。
3. **填 Order Date**：写入 `F2`（合并 `F2:I2`），值 `Order Date: 2026-10-19`。
4. **填 Seller**：写入 `F3`（合并 `F3:I3`），沿用与 Buyer 块一致的多行文本风格：
   ```
   Seller:
   {供应商全称}
   Add:{地址}
   Contact:{主联系人}
   Email:{邮箱}
   TEL:{电话}
   ```
   数据默认取 `crm_supplier` + 主联系人，可人工编辑后再生成。
5. **填物料明细**：模板第 6 行仅 1 行。
   - 明细 N 行时，从第 6 行起**插入 N-1 行**（复制第 6 行样式/边框，用 POI `copyRow` + `shiftRows`）。
   - 逐行写入 NO./Brand/ITEM/Spec./QTY/Warranty/U\\P/AMOUNT/Lead time。
   - `AMOUNT = QTY × U/P`（保留两位小数）；也可以在单元格写公式 `=E6*G6`。
6. **合计**：把 `H7` 的 `=SUM(H6:H6)` 动态扩展为 `=SUM(H6:H{5+N})`；`TOTAL` 行随之下移。
7. **保留印章图片**：不触碰 drawing，图片自动跟随。
8. **输出**：`response` 直接回写文件名 `{orderNo}.xlsx` 下载；同时可落盘到订单附件目录。

> 生成逻辑抽象为通用工具 `OrderDocFiller`：入参为"模板路径 + 单元格填充字典 + 明细起始行 + 明细数据"，PO/PI/装箱单/委托单/SN List 共用同一套代码，差异只在各自的坐标映射表。

---

## 五、PDF 转换

- 生成 Excel 后转 PDF：**生产环境（Linux）使用 LibreOffice headless**：
  `soffice --headless --convert-to pdf --outdir <dir> <xlsx>`（保真度最高，合并单元格/边框/图片/字体均保留）。
- 本机开发环境无 `soffice`，可用已安装的 MS Excel COM 导出 PDF 作为临时验证手段（已验证可用）。
- 备选：纯代码用 PDF 库重排（失真风险高、维护成本大），仅在无法安装 LibreOffice 时考虑。

---

## 六、后端接口与前端页面

- Controller：`ruoyi-admin/.../controller/crm/OrderController.java`
  - `GET /crm/order/list`、`GET /crm/order/{id}`、`POST /crm/order`、`PUT /crm/order`、`DELETE /crm/order/{ids}`
  - `POST /crm/order/generateExcel` → 返回 xlsx
  - `POST /crm/order/generatePdf` → 返回 pdf
  - 权限标识：`crm:order:*`
- Service / Mapper：`ruoyi-system` 下新增 `com.ruoyi.crm.order.*`（Domain/Mapper/Service）。
- 前端：`ruoyi-ui/src/views/crm/order/index.vue`（列表）+ 编辑弹窗（主表 + 明细子表 + 供应商选择器 + "预览/生成 Excel/生成 PDF"按钮），照 `views/crm/supplier` 的模式写。

---

## 七、技术要点与风险

1. **合并单元格**：只写左上角格，写其他成员格会丢失。
2. **插入行保样式**：openpyxl 难保 POI 级样式，落地用 POI（`XSSFRow` 复制 + `shiftRows`），项目已引入 `poi-ooxml`。
3. **插入行时公式引用**：注意 `SUM` 范围、以及图片/drawing 锚点是否需跟随（PO 印章在明细区之上，不受影响）。
4. **中文字体**：xlsx 用模板原字体即可；PDF 走 LibreOffice 不受影响。若走纯代码 PDF，必须内嵌中文字体（实测 CID 字体易丢字，需用 TTF）。
5. **模板管理**：坐标映射建议集中成常量表（或改为"命名单元格/占位符"），模板微调时只改映射。
6. **并发编号**：唯一索引兜底 + 失败重试。

---

## 八、待确认项

1. `PO No` 采用方案 A 还是方案 B（标签与值是否分离样式）？
2. 单号规则：前缀就用 `PO`、流水 3 位、日期 `yyyyMMdd` 是否可以？是否需要手动可改？
3. Seller 块是否用固定多行格式（公司/地址/联系人/邮箱/电话），是否需要"银行信息"？
4. 物料字段是否就是模板 9 列（NO./Brand/ITEM/Spec./QTY/Warranty/U\\P/AMOUNT/Lead time），有无需要增减？
5. 明细行数上限（用于分页/PDF 换页处理）大概多少？
6. PDF 方案是否按"生产环境装 LibreOffice"落地？
