package com.ruoyi.wexinAuto.analysis;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 微信报价附件读取：只读 xls/xlsx/csv，限制文件、表格与输出大小，避免拖慢分类和录入。 */
@Slf4j
public final class WeChatExcelTextExtractor {

    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_SHEETS = 20;
    private static final int MAX_ROWS = 500;
    private static final int MAX_COLUMNS = 50;
    private static final int MAX_TEXT_CHARS = 6000;

    private WeChatExcelTextExtractor() {
    }

    public static String extract(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                log.warn("微信附件超过读取上限，已跳过: file={} size={}", file, Files.size(file));
                return "";
            }
            String extension = extension(file.getFileName().toString());
            if ("csv".equals(extension)) {
                return extractCsv(Files.readAllBytes(file));
            }
            if (!"xls".equals(extension) && !"xlsx".equals(extension)) {
                return "";
            }
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(Files.readAllBytes(file)))) {
                return extractWorkbook(workbook);
            }
        } catch (Exception e) {
            log.warn("微信附件解析失败: file={} error={}", file, e.getMessage());
            return "";
        }
    }

    private static String extractWorkbook(Workbook workbook) {
        StringBuilder out = new StringBuilder();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        int sheets = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);
        for (int sheetIndex = 0; sheetIndex < sheets && out.length() < MAX_TEXT_CHARS; sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            appendLine(out, "[" + StringUtils.defaultIfBlank(sheet.getSheetName(), "Sheet" + (sheetIndex + 1)) + "]");
            int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS - 1);
            for (int rowIndex = 0; rowIndex <= lastRow && out.length() < MAX_TEXT_CHARS; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                StringBuilder line = new StringBuilder();
                int lastCell = Math.min(row.getLastCellNum(), MAX_COLUMNS);
                for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
                    String value = clean(cellText(row.getCell(cellIndex), formatter));
                    if (value.isEmpty()) {
                        continue;
                    }
                    if (line.length() > 0) {
                        line.append(" | ");
                    }
                    line.append(value);
                }
                appendLine(out, line.toString());
            }
        }
        return clip(out.toString(), MAX_TEXT_CHARS);
    }

    private static String extractCsv(byte[] bytes) {
        String text = decodeCsv(bytes);
        StringBuilder out = new StringBuilder();
        int rowCount = 0;
        for (String line : text.split("\\r?\\n")) {
            if (rowCount++ >= MAX_ROWS || out.length() >= MAX_TEXT_CHARS) {
                break;
            }
            appendLine(out, clean(line));
        }
        return clip(out.toString(), MAX_TEXT_CHARS);
    }

    private static String decodeCsv(byte[] bytes) {
        int offset = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, offset, bytes.length - offset, Charset.forName("GBK"));
        }
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type != CellType.FORMULA) {
            return formatter.formatCellValue(cell);
        }
        CellType cached = cell.getCachedFormulaResultType();
        if (cached == CellType.NUMERIC) {
            return formatter.formatRawCellContents(cell.getNumericCellValue(),
                    cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString());
        }
        if (cached == CellType.STRING) {
            return cell.getStringCellValue();
        }
        if (cached == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (cached == CellType.ERROR) {
            return FormulaError.forInt(cell.getErrorCellValue()).getString();
        }
        return "";
    }

    private static void appendLine(StringBuilder out, String line) {
        String value = StringUtils.trimToEmpty(line);
        if (value.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(value);
    }

    private static String clean(String value) {
        return StringUtils.defaultString(value)
                .replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "…";
    }

    private static String extension(String fileName) {
        int dot = StringUtils.defaultString(fileName).lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
