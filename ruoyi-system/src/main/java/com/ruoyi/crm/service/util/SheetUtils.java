package com.ruoyi.crm.service.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * POI 表格填充公共工具。
 */
public class SheetUtils {

    private SheetUtils() {
    }

    public static XSSFRow ensureRow(XSSFSheet sheet, int rowIdx) {
        XSSFRow row = sheet.getRow(rowIdx);
        return row != null ? row : sheet.createRow(rowIdx);
    }

    public static void setValue(Row row, int col, Object value) {
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

    public static void setFormula(Row row, int col, String formula) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
        }
        cell.setCellFormula(formula);
    }

    /** 摘除并记录 fromRow0Based 起的合并区（shiftRows 后手工恢复） */
    public static List<CellRangeAddress> detachMerges(XSSFSheet sheet, int fromRow0Based) {
        List<CellRangeAddress> moved = new ArrayList<>();
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() >= fromRow0Based) {
                moved.add(region);
                sheet.removeMergedRegion(i);
            }
        }
        return moved;
    }

    public static void restoreMerges(XSSFSheet sheet, List<CellRangeAddress> regions, int delta) {
        for (CellRangeAddress region : regions) {
            sheet.addMergedRegion(new CellRangeAddress(
                    region.getFirstRow() + delta, region.getLastRow() + delta,
                    region.getFirstColumn(), region.getLastColumn()));
        }
    }

    /** 复制 srcRowIdx 行的样式到 [fromRowIdx, toRowIdx] 区间的 cols 列 */
    public static void copyRowStyle(XSSFSheet sheet, int srcRowIdx, int fromRowIdx, int toRowIdx, int cols) {
        XSSFRow src = sheet.getRow(srcRowIdx);
        if (src == null) {
            return;
        }
        for (int r = fromRowIdx; r <= toRowIdx; r++) {
            XSSFRow row = ensureRow(sheet, r);
            row.setHeight(src.getHeight());
            for (int c = 0; c < cols; c++) {
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

    public static String cellText(Row row, int col) {
        Object value = cellValue(row, col);
        return value == null ? null : String.valueOf(value);
    }

    public static Object cellValue(Row row, int col) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
}
