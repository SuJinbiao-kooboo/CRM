package com.ruoyi.crm.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.ruoyi.crm.domain.CrmOrderItem;

/**
 * 订单单据 Excel 填充工具。
 *
 * 首个模板为 PO（Purchasing Order），模板结构（1-based 行号）：
 *  行1 标题 | 行2 PO No / Order Date | 行3 Buyer / Seller | 行4 Ship to
 *  行5 明细表头 | 行6 明细数据（仅 1 行）| 行7 TOTAL | 行8 条款
 *
 * 明细多于 1 行时向下插入行、复制明细行样式，并同步下移 TOTAL/条款、扩展 SUM 公式。
 */
public class OrderDocumentFiller {

    /** PO 模板坐标（0-based） */
    private static final int ROW_PO_NO = 1;
    private static final int COL_PO_NO = 0;
    private static final int ROW_DATE = 1;
    private static final int COL_DATE = 5;
    private static final int ROW_SELLER = 2;
    /** Ship to 行（0-based，对应模板第 4 行） */
    private static final int ROW_SHIP_TO = 3;
    private static final int COL_BUYER = 0;
    private static final int COL_SELLER = 5;

    /** 明细首行（0-based，对应模板第 6 行） */
    private static final int ITEM_FIRST_ROW = 5;
    /** 明细列数 A..I */
    private static final int ITEM_COLS = 9;
    /** 模板 TOTAL 行（0-based，对应第 7 行） */
    private static final int TOTAL_ROW = 6;
    /** 金额列：H */
    private static final int COL_AMOUNT = 7;

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private OrderDocumentFiller() {
    }

    /**
     * 填充 PO 单据并写出 xlsx。
     *
     * @param template    PO 模板输入流（.xlsx）
     * @param out         输出目标
     * @param orderNo     订单编号
     * @param orderDate   订单日期
     * @param sellerText  Seller 区域文本（多行）
     * @param items       物料明细
     */
    public static void fillPurchaseOrder(InputStream template, OutputStream out, String orderNo, Date orderDate,
                                         String sellerText, List<CrmOrderItem> items) throws IOException {
        fillInvoice(template, out, orderNo, orderDate, null, sellerText, null, items);
    }

    /**
     * 通用发票类单据填充（PO / PI / CI 模板结构一致）：
     * A2 = 单号标签格、F2 = 日期标签格；A3 = 买方块(可空则不写)、F3 = 卖方块(可空则不写)。
     *
     * @param blockA3 买方信息块（null 表示保留模板原内容）
     * @param blockF3 卖方信息块（null 表示保留模板原内容）
     * @param shipTo  收货信息块，写 A4（null 表示保留模板原内容）
     */
    public static void fillInvoice(InputStream template, OutputStream out, String orderNo, Date orderDate,
                                   String blockA3, String blockF3, String shipTo, List<CrmOrderItem> items) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(template)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            int itemCount = items == null ? 0 : items.size();
            int rows = Math.max(itemCount, 1);

            // 1. PO No / Order Date
            appendToLabeledCell(sheet, ROW_PO_NO, COL_PO_NO, safe(orderNo));
            String dateStr = orderDate == null ? "" : new SimpleDateFormat(DATE_PATTERN).format(orderDate);
            appendToLabeledCell(sheet, ROW_DATE, COL_DATE, dateStr);

            // 2. 买方 / 卖方信息块
            if (blockA3 != null) {
                writeCell(sheet, ROW_SELLER, COL_BUYER, blockA3);
            }
            if (blockF3 != null) {
                writeCell(sheet, ROW_SELLER, COL_SELLER, blockF3);
            }
            if (shipTo != null) {
                writeCell(sheet, ROW_SHIP_TO, COL_BUYER, shipTo);
            }

            // 3. 明细多于 1 行时插入行
            if (rows > 1) {
                insertItemRows(sheet, rows - 1);
            }

            // 4. 写明细
            for (int i = 0; i < rows; i++) {
                XSSFRow row = ensureRow(sheet, ITEM_FIRST_ROW + i);
                CrmOrderItem it = (items != null && i < itemCount) ? items.get(i) : null;
                if (it == null) {
                    continue;
                }
                int excelRow = ITEM_FIRST_ROW + i + 1;
                setCellValue(row, 0, (double) excelRow - ITEM_FIRST_ROW); // NO.
                setCellValue(row, 1, it.getBrand());
                setCellValue(row, 2, it.getItemName());
                setCellValue(row, 3, it.getSpec());
                setCellValue(row, 4, it.getQty() == null ? null : it.getQty().doubleValue());
                setCellValue(row, 5, it.getWarranty());
                setCellValue(row, 6, it.getUnitPrice() == null ? null : it.getUnitPrice().doubleValue());
                // AMOUNT 用公式，保留模板原有可编辑性
                setCellFormula(row, COL_AMOUNT, "E" + excelRow + "*G" + excelRow);
                setCellValue(row, 8, it.getLeadTime());
            }

            // 5. 合计公式（TOTAL 行位置随明细行数下移）
            int totalRowIdx = ITEM_FIRST_ROW + rows;
            XSSFRow totalRow = ensureRow(sheet, totalRowIdx);
            setCellFormula(totalRow, COL_AMOUNT,
                    "SUM(H" + (ITEM_FIRST_ROW + 1) + ":H" + (ITEM_FIRST_ROW + rows) + ")");

            wb.setForceFormulaRecalculation(true);
            wb.write(out);
        }
    }

    /** 在明细首行之后插入 count 行，复制明细行样式，并下移 TOTAL/条款合并区 */
    private static void insertItemRows(XSSFSheet sheet, int count) {
        int lastRow = sheet.getLastRowNum();

        // 先摘除需要跟随下移的合并单元格（TOTAL 行及以下，如条款 A8:I8）
        List<CellRangeAddress> toMove = new ArrayList<>();
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() >= TOTAL_ROW) {
                toMove.add(region);
                sheet.removeMergedRegion(i);
            }
        }

        sheet.shiftRows(TOTAL_ROW, lastRow, count, true, true);

        for (CellRangeAddress region : toMove) {
            sheet.addMergedRegion(new CellRangeAddress(
                    region.getFirstRow() + count, region.getLastRow() + count,
                    region.getFirstColumn(), region.getLastColumn()));
        }

        // 复制明细首行的样式到新增行
        XSSFRow src = sheet.getRow(ITEM_FIRST_ROW);
        for (int r = ITEM_FIRST_ROW + 1; r <= ITEM_FIRST_ROW + count; r++) {
            XSSFRow row = ensureRow(sheet, r);
            if (src != null) {
                row.setHeight(src.getHeight());
                for (int c = 0; c < ITEM_COLS; c++) {
                    Cell sc = src.getCell(c);
                    Cell nc = row.getCell(c);
                    if (nc == null) {
                        nc = row.createCell(c);
                    }
                    if (sc != null) {
                        nc.setCellStyle(sc.getCellStyle());
                    }
                }
            }
        }
    }

    /** 读取原单元格文本作为标签前缀，追加值：如 "PO No：" + " " + "PO20261020-001" */
    private static void appendToLabeledCell(XSSFSheet sheet, int rowIdx, int colIdx, String value) {
        Row row = sheet.getRow(rowIdx);
        Cell cell = row == null ? null : row.getCell(colIdx);
        String label = "";
        if (cell != null && cell.getCellType() == CellType.STRING && cell.getStringCellValue() != null) {
            label = cell.getStringCellValue().trim();
        }
        String text = label.isEmpty() ? value : (label + " " + value).trim();
        setCellValue(row, colIdx, text);
    }

    private static void writeCell(XSSFSheet sheet, int rowIdx, int colIdx, String text) {
        XSSFRow row = ensureRow(sheet, rowIdx);
        setCellValue(row, colIdx, text);
    }

    private static XSSFRow ensureRow(XSSFSheet sheet, int rowIdx) {
        XSSFRow row = sheet.getRow(rowIdx);
        return row != null ? row : sheet.createRow(rowIdx);
    }

    private static void setCellValue(Row row, int col, Object value) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
        }
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private static void setCellFormula(Row row, int col, String formula) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
        }
        cell.setCellFormula(formula);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
