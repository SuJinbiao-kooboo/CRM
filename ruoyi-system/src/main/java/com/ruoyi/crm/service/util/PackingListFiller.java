package com.ruoyi.crm.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 装箱单（Packing List）填充：
 *  - 模板固定区：Date(A2)、SHIP TO(F3)
 *  - 物料信息：原样抄录供应商提供的 PL 文件的数据行（A..N 列），行数不足时插入行并复制样式
 */
public class PackingListFiller {

    /** 模板数据首行（0-based，对应第 5 行） */
    private static final int DATA_FIRST = 4;
    /** 模板合计行（0-based，对应第 6 行） */
    private static final int TOTALS_ROW = 5;
    /** A..N 共 14 列 */
    private static final int COLS = 14;

    private PackingListFiller() {
    }

    public static void fill(InputStream template, OutputStream out, String dateText, String shipTo,
                            InputStream supplierFile) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(template)) {
            XSSFSheet sheet = wb.getSheetAt(0);

            if (dateText != null && !dateText.isEmpty()) {
                SheetUtils.setValue(SheetUtils.ensureRow(sheet, 1), 0, "Date: " + dateText);
            }
            if (shipTo != null && !shipTo.isEmpty()) {
                SheetUtils.setValue(SheetUtils.ensureRow(sheet, 2), 5, "  SHIP TO:\n" + shipTo);
            }

            List<List<Object>> dataRows = readSupplierRows(supplierFile);
            int rows = Math.max(dataRows.size(), 1);
            int shift = rows - 1;

            if (shift > 0) {
                List<CellRangeAddress> moved = SheetUtils.detachMerges(sheet, TOTALS_ROW);
                sheet.shiftRows(TOTALS_ROW, sheet.getLastRowNum(), shift, true, true);
                SheetUtils.restoreMerges(sheet, moved, shift);
                SheetUtils.copyRowStyle(sheet, DATA_FIRST, DATA_FIRST + 1, DATA_FIRST + shift, COLS);
                // 每个数据行保留 品名(B:F) 与 单箱重量(I:J) 的合并
                for (int r = DATA_FIRST + 1; r <= DATA_FIRST + shift; r++) {
                    sheet.addMergedRegion(new CellRangeAddress(r, r, 1, 5));
                    sheet.addMergedRegion(new CellRangeAddress(r, r, 8, 9));
                }
            }

            for (int i = 0; i < dataRows.size(); i++) {
                Row row = SheetUtils.ensureRow(sheet, DATA_FIRST + i);
                List<Object> values = dataRows.get(i);
                for (int c = 0; c < COLS; c++) {
                    Object v = c < values.size() ? values.get(c) : null;
                    SheetUtils.setValue(row, c, v);
                }
            }

            wb.setForceFormulaRecalculation(true);
            wb.write(out);
        }
    }

    /** 读取供应商 PL 文件的数据行：自第 5 行起，遇到空行或含 "Total" 的合计行停止 */
    private static List<List<Object>> readSupplierRows(InputStream supplierFile) throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        if (supplierFile == null) {
            return rows;
        }
        try (Workbook src = WorkbookFactory.create(supplierFile)) {
            org.apache.poi.ss.usermodel.Sheet sheet = src.getSheetAt(0);
            for (int r = DATA_FIRST; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    break;
                }
                String first = SheetUtils.cellText(row, 0);
                if (first != null && first.toLowerCase().contains("total")) {
                    break;
                }
                List<Object> values = new ArrayList<>();
                boolean blank = true;
                for (int c = 0; c < COLS; c++) {
                    Object v = SheetUtils.cellValue(row, c);
                    values.add(v);
                    if (v != null && !String.valueOf(v).trim().isEmpty()) {
                        blank = false;
                    }
                }
                if (blank) {
                    break;
                }
                rows.add(values);
            }
        }
        return rows;
    }
}
