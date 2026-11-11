package com.ruoyi.crm.service.ai;

import java.util.ArrayList;
import java.util.List;

/** AiTextChunker 的独立自检，不依赖 JUnit，可直接运行 main。 */
public class AiTextChunkerTest {

    public static void main(String[] args) {
        testLongQuoteKeepsEveryLine();
        testShortContentSingleChunk();
        testOversizedSingleLineNotCut();
        testSectionBlocksStayTogether();
        testRowOrientedQuoteDetection();
        testColumnTableIsNotRowOriented();
        System.out.println("AiTextChunkerTest PASSED");
    }

    private static void testLongQuoteKeepsEveryLine() {
        List<String> original = new ArrayList<>();
        for (int i = 1; i <= 108; i++) {
            original.add("PART-" + i + " " + i + "pcs $" + (1000 + i) + "/pc");
            if (i % 9 == 0) {
                original.add("");
            }
        }
        String content = join(original);
        List<String> chunks = AiTextChunker.split(content, 2400, 30);
        eq(4, chunks.size(), "108行应按30行切成4段");
        List<String> actual = new ArrayList<>();
        for (String chunk : chunks) {
            int nonBlank = 0;
            for (String line : chunk.split("\\n", -1)) {
                if (!line.trim().isEmpty()) {
                    actual.add(line);
                    nonBlank++;
                }
            }
            if (nonBlank > 30) {
                throw new AssertionError("分段超过30行：" + nonBlank);
            }
        }
        List<String> expected = new ArrayList<>();
        for (String line : original) {
            if (!line.trim().isEmpty()) {
                expected.add(line);
            }
        }
        eq(expected, actual, "分段后必须完整保留所有非空行且顺序不变");
    }

    private static void testShortContentSingleChunk() {
        List<String> chunks = AiTextChunker.split("6760P 46pcs 7900拆\n6517P 26pcs 2550", 2400, 30);
        eq(1, chunks.size(), "短文本不分段");
        eq("6760P 46pcs 7900拆\n6517P 26pcs 2550", chunks.get(0), "短文本原样保留");
    }

    private static void testOversizedSingleLineNotCut() {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            line.append('A');
        }
        List<String> chunks = AiTextChunker.split(line.toString(), 200, 30);
        eq(1, chunks.size(), "单行超长也保持一行");
        eq(line.toString(), chunks.get(0), "不得截断料号行");
    }

    private static void testSectionBlocksStayTogether() {
        StringBuilder content = new StringBuilder();
        for (int block = 0; block < 3; block++) {
            for (int i = 0; i < 10; i++) {
                content.append("B").append(block).append("-").append(i).append('\n');
            }
            content.append('\n');
        }
        List<String> chunks = AiTextChunker.split(content.toString(), 2400, 30);
        eq(1, chunks.size(), "三个10行逻辑块应合并为一段");
        for (int block = 0; block < 3; block++) {
            assertContains(chunks.get(0), "B" + block + "-0", "逻辑块起始行应保留");
            assertContains(chunks.get(0), "B" + block + "-9", "逻辑块结束行应保留");
        }
    }

    private static void testRowOrientedQuoteDetection() {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            content.append("HMCT04MHBRC").append(i).append(" DDR5 128G ").append(i)
                    .append("pcs $").append(4000 + i).append("/pc\n");
        }
        eq(true, AiTextChunker.looksLikeRowOrientedQuote(content.toString()), "每行料号+价格应识别为行式清单");
    }

    private static void testColumnTableIsNotRowOriented() {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            content.append("HMCT04MHBRC").append(i).append(" DDR5 128G\n");
        }
        for (int i = 1; i <= 10; i++) {
            content.append(4000 + i).append("\n");
        }
        eq(false, AiTextChunker.looksLikeRowOrientedQuote(content.toString()),
                "第一列在前、第二列在后的表格不能判为行式清单");
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static void assertContains(String value, String search, String message) {
        if (value == null || !value.contains(search)) {
            throw new AssertionError(message + "：value=" + value);
        }
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "：expected=" + expected + "，actual=" + actual);
        }
    }
}
