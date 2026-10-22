package com.ruoyi.crm.service.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 对 xlsx 内的 VML 表单控件（复选框）打勾/取消。
 *
 * 模板中的复选框是 Excel 旧式表单控件，勾选状态记录在 xl/drawings/vmlDrawing*.vml 的
 * &lt;x:Checked&gt;1&lt;/x:Checked&gt; 中，POI 不暴露该结构，因此在生成后对 zip 内的 VML 做后处理。
 */
public class VmlCheckboxTuner {

    private static final Pattern SHAPE = Pattern.compile("<v:shape\\b.*?</v:shape>", Pattern.DOTALL);
    private static final Pattern ANCHOR = Pattern.compile("<x:Anchor>(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)</x:Anchor>");

    private VmlCheckboxTuner() {
    }

    /** 按复选框文本内容定位并打勾（如"送货""自提""快递"） */
    public static Predicate<String> byLabel(String label) {
        return block -> block.contains(label);
    }

    /** 按复选框锚点所在行（0-based）定位并打勾 */
    public static Predicate<String> byAnchorRow(int row0Based) {
        return block -> {
            Matcher m = Pattern.compile("<x:Anchor>(\\d+),(\\d+),(\\d+),").matcher(block);
            return m.find() && Integer.parseInt(m.group(3)) == row0Based;
        };
    }

    /** 按复选框锚点起始单元格（0-based 行、列）定位并打勾 */
    public static Predicate<String> byAnchorCell(int row0Based, int col0Based) {
        return block -> {
            Matcher m = Pattern.compile("<x:Anchor>(\\d+),(\\d+),(\\d+),").matcher(block);
            return m.find() && Integer.parseInt(m.group(3)) == row0Based
                    && Integer.parseInt(m.group(1)) == col0Based;
        };
    }

    /**
     * 对给定 xlsx 字节流应用勾选规则。
     *
     * @param xlsx    源 xlsx
     * @param tickMap 规则：匹配器 -&gt; 是否勾选
     */
    public static byte[] apply(byte[] xlsx, Map<Predicate<String>, Boolean> tickMap) throws IOException {
        return apply(xlsx, tickMap, 0, -1);
    }

    /**
     * @param anchorShiftFrom 0-based 行号，&gt;= 该行的锚点整体下移
     * @param anchorShift     下移行数（&lt;=0 表示不平移）
     */
    public static byte[] apply(byte[] xlsx, Map<Predicate<String>, Boolean> tickMap,
                               int anchorShiftFrom, int anchorShift) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, byte[]> overrides = new LinkedHashMap<>();

        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                byte[] data = readAll(zin);
                if (entry.getName().matches("xl/drawings/vmlDrawing\\d+\\.vml")) {
                    String vml = new String(data, StandardCharsets.UTF_8);
                    if (anchorShift > 0) {
                        vml = shiftAnchors(vml, anchorShiftFrom, anchorShift);
                    }
                    vml = applyRules(vml, tickMap);
                    data = vml.getBytes(StandardCharsets.UTF_8);
                }
                overrides.put(entry.getName(), data);
            }
        }

        try (ZipOutputStream zout = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : overrides.entrySet()) {
                zout.putNextEntry(new ZipEntry(e.getKey()));
                zout.write(e.getValue());
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** 把锚点起始行 &gt;= fromRow0Based 的复选框整体下移 delta 行（插入明细行后保持位置正确） */
    private static String shiftAnchors(String vml, int fromRow0Based, int delta) {
        Matcher m = ANCHOR.matcher(vml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int fromRow = Integer.parseInt(m.group(3));
            int toRow = Integer.parseInt(m.group(7));
            String replacement = m.group();
            if (fromRow >= fromRow0Based) {
                replacement = "<x:Anchor>" + m.group(1) + "," + m.group(2) + "," + (fromRow + delta) + "," + m.group(4)
                        + "," + m.group(5) + "," + m.group(6) + "," + (toRow + delta) + "," + m.group(8) + "</x:Anchor>";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String applyRules(String vml, Map<Predicate<String>, Boolean> tickMap) {
        for (Map.Entry<Predicate<String>, Boolean> rule : tickMap.entrySet()) {
            vml = tick(vml, rule.getKey(), rule.getValue());
        }
        return vml;
    }

    private static String tick(String vml, Predicate<String> matcher, boolean checked) {
        Matcher m = SHAPE.matcher(vml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String block = m.group();
            if (matcher.test(block)) {
                block = setChecked(block, checked);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(block));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String setChecked(String block, boolean checked) {
        String cleaned = block.replaceAll("<x:Checked>\\d+</x:Checked>", "");
        if (!checked) {
            return cleaned;
        }
        if (cleaned.contains("<x:NoThreeD/>")) {
            return cleaned.replace("<x:NoThreeD/>", "<x:NoThreeD/><x:Checked>1</x:Checked>");
        }
        return cleaned.replace("</x:ClientData>", "<x:Checked>1</x:Checked></x:ClientData>");
    }

    private static byte[] readAll(ZipInputStream zin) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int len;
        while ((len = zin.read(chunk)) != -1) {
            buffer.write(chunk, 0, len);
        }
        return buffer.toByteArray();
    }
}
