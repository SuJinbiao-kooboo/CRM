package com.ruoyi.crm.service.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 香港仓库交货委托单填充。
 *
 * 模板（0-based）：A12 日期、H9 收货公司信息（合并 H9:L12）、
 * 明细 15~16 行（列：A序号 B品名 C型号(合并C:D) E数量 F单位 G币种 H单价 I合计金额 J毛重 K通达到货编号 L是否需要拆箱）、
 * 17 行合计、22 行 附发票/装箱单(B) 与 运费(J/K/L)、21 行 快递公司名称、24 行 进口关税、26 行 备注(A26:I26)。
 * 明细超过 2 行时插入行，并同步平移下方合并区与复选框锚点。
 */
public class EntrustFiller {

    private static final int DATE_ROW = 11;
    private static final int DATE_COL = 0;
    private static final int RECEIVER_ROW = 8;
    private static final int RECEIVER_COL = 7;

    private static final int ITEM_FIRST = 14;
    private static final int ITEM_DEFAULT_ROWS = 2;
    private static final int TOTAL_ROW = 16;

    private static final int EXPRESS_ROW = 20;
    private static final int ATTACH_FREIGHT_ROW = 21;
    private static final int CUSTOMS_ROW = 23;
    private static final int REMARK_ROW = 25;
    private static final int AMOUNT_CURRENCY_ROW = 19;

    private EntrustFiller() {
    }

    public static byte[] fill(InputStream template, String dateText, String receiverInfo,
                              List<Map<String, Object>> items, Map<String, String> extras) throws IOException {
        byte[] bytes;
        int shift;
        try (XSSFWorkbook wb = new XSSFWorkbook(template)) {
            XSSFSheet sheet = wb.getSheetAt(0);
            int count = items == null ? 0 : items.size();
            shift = Math.max(0, count - ITEM_DEFAULT_ROWS);

            if (shift > 0) {
                List<CellRangeAddress> moved = SheetUtils.detachMerges(sheet, TOTAL_ROW);
                sheet.shiftRows(TOTAL_ROW, sheet.getLastRowNum(), shift, true, true);
                SheetUtils.restoreMerges(sheet, moved, shift);
                SheetUtils.copyRowStyle(sheet, ITEM_FIRST, ITEM_FIRST + ITEM_DEFAULT_ROWS, ITEM_FIRST + count - 1, 12);
                for (int r = ITEM_FIRST + ITEM_DEFAULT_ROWS; r <= ITEM_FIRST + count - 1; r++) {
                    sheet.addMergedRegion(new CellRangeAddress(r, r, 2, 3));
                }
            }

            // 日期 / 收货公司信息
            if (notBlank(dateText)) {
                SheetUtils.setValue(SheetUtils.ensureRow(sheet, DATE_ROW), DATE_COL, "日  期: " + dateText);
            }
            if (notBlank(receiverInfo)) {
                SheetUtils.setValue(SheetUtils.ensureRow(sheet, RECEIVER_ROW), RECEIVER_COL, receiverInfo);
            }

            // 明细
            for (int i = 0; i < count; i++) {
                Map<String, Object> it = items.get(i);
                Row row = SheetUtils.ensureRow(sheet, ITEM_FIRST + i);
                int excelRow = ITEM_FIRST + i + 1;
                SheetUtils.setValue(row, 0, i + 1);
                SheetUtils.setValue(row, 1, str(it.get("itemName")));
                SheetUtils.setValue(row, 2, str(it.get("spec")));
                SheetUtils.setValue(row, 4, num(it.get("qty")));
                SheetUtils.setValue(row, 5, str(it.get("unit")));
                SheetUtils.setValue(row, 6, str(it.get("currency")));
                SheetUtils.setValue(row, 7, num(it.get("unitPrice")));
                SheetUtils.setFormula(row, 8, "E" + excelRow + "*H" + excelRow);
                SheetUtils.setValue(row, 9, num(it.get("grossWeight")));
                SheetUtils.setValue(row, 10, str(it.get("reachNo")));
                SheetUtils.setValue(row, 11, str(it.get("needUnpack")));
            }

            // 合计
            int totalRowIdx = TOTAL_ROW + shift;
            Row totalRow = SheetUtils.ensureRow(sheet, totalRowIdx);
            SheetUtils.setFormula(totalRow, 8, "SUM(I" + (ITEM_FIRST + 1) + ":I" + (ITEM_FIRST + Math.max(count, 1)) + ")");
            // 总箱数：默认等于明细行数，可通过 extras.totalBoxes 覆盖
            Object boxes = extras.get("totalBoxes");
            if (boxes == null || String.valueOf(boxes).trim().isEmpty()) {
                boxes = count;
            }
            SheetUtils.setValue(totalRow, 10, num(boxes));

            // 快递公司名称 / 运费（仅当交货方式为快递时填写）
            if ("快递".equals(extras.get("deliveryMethod"))) {
                if (notBlank(extras.get("expressCompany"))) {
                    SheetUtils.setValue(SheetUtils.ensureRow(sheet, EXPRESS_ROW + shift), 10, extras.get("expressCompany"));
                }
                // 运费：把选中的档位文字写到选项下方，未选择则不写
                writePayBelow(sheet, ATTACH_FREIGHT_ROW + shift, extras.get("freight"));
            }
            // 进口关税（默认留空，未选择时不勾选）
            markPayChoice(sheet, CUSTOMS_ROW + shift, extras.get("customsDuty"));
            if (notBlank(extras.get("attachDoc"))) {
                Row attachRow = SheetUtils.ensureRow(sheet, ATTACH_FREIGHT_ROW + shift);
                SheetUtils.setValue(attachRow, 1, "附发票/装箱单：" + extras.get("attachDoc"));
            }
            if (notBlank(extras.get("remark"))) {
                SheetUtils.setValue(SheetUtils.ensureRow(sheet, REMARK_ROW + shift), 0, extras.get("remark"));
            }

            wb.setForceFormulaRecalculation(true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            bytes = bos.toByteArray();
        }

        // 复选框：交货方式按标签打勾；附发票/装箱单按锚点行打勾；锚点整体平移
        Map<Predicate<String>, Boolean> ticks = new LinkedHashMap<>();
        String method = extras.get("deliveryMethod");
        ticks.put(VmlCheckboxTuner.byLabel("送货"), "送货".equals(method));
        ticks.put(VmlCheckboxTuner.byLabel("自提"), "自提".equals(method));
        ticks.put(VmlCheckboxTuner.byLabel("快递"), "快递".equals(method));
        ticks.put(VmlCheckboxTuner.byAnchorRow(ATTACH_FREIGHT_ROW + shift), true);
        String currency = extras.get("payCurrency");
        ticks.put(VmlCheckboxTuner.byAnchorCell(AMOUNT_CURRENCY_ROW + shift, 3), "USD".equals(currency));
        ticks.put(VmlCheckboxTuner.byAnchorCell(AMOUNT_CURRENCY_ROW + shift, 4), "HKD".equals(currency));
        ticks.put(VmlCheckboxTuner.byAnchorCell(AMOUNT_CURRENCY_ROW + shift, 5), "其他".equals(currency));
        return VmlCheckboxTuner.apply(bytes, ticks, TOTAL_ROW, shift);
    }

    /** 运费到付/预付：把选中的档位文字写到选项下方对应单元格（K 列=预付，L 列=到付），未选择则不写 */
    private static void writePayBelow(XSSFSheet sheet, int optionRowIdx, String choice) {
        if (!notBlank(choice)) {
            return;
        }
        Row row = SheetUtils.ensureRow(sheet, optionRowIdx + 1);
        int col = choice.contains("到付") ? 11 : 10;
        SheetUtils.setValue(row, col, choice);
    }

    /** 运费/进口关税：勾选选中的档位（预付/到付），未选中的保留原文字 */
    private static void markPayChoice(XSSFSheet sheet, int rowIdx, String choice) {
        if (!notBlank(choice)) {
            return;
        }
        Row row = SheetUtils.ensureRow(sheet, rowIdx);
        String k = SheetUtils.cellText(row, 10);
        String l = SheetUtils.cellText(row, 11);
        if (k == null && l == null) {
            return;
        }
        if (k != null && k.contains(choice)) {
            SheetUtils.setValue(row, 10, "√" + choice);
        } else if (l != null && l.contains(choice)) {
            SheetUtils.setValue(row, 11, "√" + choice);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Object num(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return s;
        }
    }
}
