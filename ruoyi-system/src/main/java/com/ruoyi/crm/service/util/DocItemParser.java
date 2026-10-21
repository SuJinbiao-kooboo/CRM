package com.ruoyi.crm.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * 从 PO / PL 文件中解析物料明细，供"交货委托单"页面回显编辑。
 *
 * PO 模板明细：第 6 行起（0-based 5），列 1=Brand、2=ITEM、3=Spec.、4=QTY、6=U/P
 * PL 模板明细：第 5 行起（0-based 4），列 1=ItemDescription(B:F)、6=Quantity、8=Weight(I:J)、12=TotalQty、13=TotalWeight
 *
 * 仅解析，不改动原文件；解析结果由页面确认后再写回委托单模板。
 */
public class DocItemParser {

    private static final int PO_ITEM_FIRST = 5;
    private static final int PL_ITEM_FIRST = 4;
    private static final int PL_COLS = 14;

    private DocItemParser() {
    }

    public static List<Map<String, Object>> parse(InputStream poFile, InputStream plFile) throws IOException {
        List<Map<String, Object>> poItems = parsePo(poFile);
        List<Map<String, Object>> plItems = parsePl(plFile);
        return merge(poItems, plItems);
    }

    /** 解析 PO 明细 -> {itemName, spec, qty, unitPrice, currency} */
    private static List<Map<String, Object>> parsePo(InputStream in) throws IOException {
        List<Map<String, Object>> items = new ArrayList<>();
        if (in == null) {
            return items;
        }
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = PO_ITEM_FIRST; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    break;
                }
                String brand = text(row, 1);
                String name = text(row, 2);
                String spec = text(row, 3);
                Double qty = num(row, 4);
                Double unitPrice = num(row, 6);
                if (isTotal(name) || isTotal(spec) || isTotal(brand)) {
                    break;
                }
                if (blank(name) && blank(spec) && qty == null) {
                    break;
                }
                Map<String, Object> m = blankItem();
                m.put("itemName", blank(name) ? brand : name);
                m.put("spec", spec);
                m.put("qty", qty);
                m.put("unitPrice", unitPrice);
                m.put("currency", "USD");
                items.add(m);
            }
        }
        return items;
    }

    /** 解析 PL 明细 -> {itemName, qty, grossWeight} */
    private static List<Map<String, Object>> parsePl(InputStream in) throws IOException {
        List<Map<String, Object>> items = new ArrayList<>();
        if (in == null) {
            return items;
        }
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = PL_ITEM_FIRST; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    break;
                }
                String name = text(row, 1);
                if (isTotal(name)) {
                    break;
                }
                Double qty = num(row, 6);
                if (qty == null) {
                    qty = num(row, 12);
                }
                Double weight = num(row, 8);
                if (weight == null) {
                    weight = num(row, 13);
                }
                if (blank(name) && qty == null && weight == null) {
                    break;
                }
                Map<String, Object> m = blankItem();
                m.put("itemName", name);
                m.put("qty", qty);
                m.put("grossWeight", weight);
                items.add(m);
            }
        }
        return items;
    }

    /** 以 PO 为主线，用 PL 的毛重/数量补齐；PL 中未匹配的行作为新行追加 */
    private static List<Map<String, Object>> merge(List<Map<String, Object>> poItems,
                                                   List<Map<String, Object>> plItems) {
        List<Map<String, Object>> result = new ArrayList<>(poItems);
        boolean[] used = new boolean[plItems.size()];
        for (Map<String, Object> po : result) {
            String key = norm(str(po.get("itemName")));
            if (key.isEmpty()) {
                continue;
            }
            for (int i = 0; i < plItems.size(); i++) {
                if (used[i]) {
                    continue;
                }
                Map<String, Object> pl = plItems.get(i);
                String plKey = norm(str(pl.get("itemName")));
                if (plKey.isEmpty()) {
                    continue;
                }
                if (plKey.equals(key) || plKey.contains(key) || key.contains(plKey)) {
                    if (po.get("grossWeight") == null) {
                        po.put("grossWeight", pl.get("grossWeight"));
                    }
                    if (po.get("qty") == null) {
                        po.put("qty", pl.get("qty"));
                    }
                    used[i] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < plItems.size(); i++) {
            if (!used[i]) {
                result.add(plItems.get(i));
            }
        }
        return result;
    }

    private static Map<String, Object> blankItem() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemName", null);
        m.put("spec", null);
        m.put("qty", null);
        m.put("unit", null);
        m.put("currency", null);
        m.put("unitPrice", null);
        m.put("grossWeight", null);
        m.put("reachNo", null);
        m.put("needUnpack", null);
        return m;
    }

    private static String text(Row row, int col) {
        String s = SheetUtils.cellText(row, col);
        return s == null ? null : s.trim();
    }

    private static Double num(Row row, int col) {
        Object v = SheetUtils.cellValue(row, col);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        String s = String.valueOf(v).trim().replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isTotal(String s) {
        return s != null && s.toLowerCase().contains("total");
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String norm(String s) {
        return s == null ? "" : s.replace(" ", "").replace("\n", "").trim().toLowerCase();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}