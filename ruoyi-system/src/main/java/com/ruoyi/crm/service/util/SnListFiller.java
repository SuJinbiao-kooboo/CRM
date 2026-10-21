package com.ruoyi.crm.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * SN List 填充：把用户录入的文本按行写入第一个页签的 A 列（N 行单列），其余页签不保留。
 */
public class SnListFiller {

    private SnListFiller() {
    }

    public static void fill(InputStream template, OutputStream out, String snText) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(template)) {
            while (wb.getNumberOfSheets() > 1) {
                wb.removeSheetAt(wb.getNumberOfSheets() - 1);
            }
            XSSFSheet sheet = wb.getSheetAt(0);
            List<String> lines = splitLines(snText);
            for (int i = 0; i < lines.size(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    row = sheet.createRow(i);
                }
                Cell cell = row.getCell(0);
                if (cell == null) {
                    cell = row.createCell(0);
                }
                cell.setCellValue(lines.get(i));
            }
            wb.write(out);
        }
    }

    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.replace("\uFEFF", "").trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }
}
