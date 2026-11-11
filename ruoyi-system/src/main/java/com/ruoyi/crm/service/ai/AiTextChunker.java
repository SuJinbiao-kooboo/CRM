package com.ruoyi.crm.service.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 长文本分段器：按空行逻辑块和行数/字符预算切分，保证不截断任何一行。
 *
 * <p>AI 录入的长报价清单一次请求很容易因为输出过长而超时。这里只做确定性的文本切分，
 * 不调用模型；每一段都保留完整行，最终由调用方统一合并和归一化。</p>
 */
public final class AiTextChunker {

    public static final int DEFAULT_MAX_CHARS = 2400;
    public static final int DEFAULT_MAX_LINES = 30;

    /** 行式报价：同一行同时有带字母数字的料号和价格/币种标记。 */
    private static final Pattern ROW_PART = Pattern.compile(
            "(?i)\\b(?=[a-z0-9-]*[a-z])(?=[a-z0-9-]*\\d)[a-z0-9-]{3,}\\b");
    private static final Pattern ROW_PRICE = Pattern.compile(
            "(?i)(?:usd|rmb|cny|eur|hkd|\\$|¥|￥|/\\s*(?:pc|pcs|片|个))");

    private AiTextChunker() {
    }

    /**
     * 是否属于“每行自带料号和价格”的行式报价清单。
     *
     * <p>从 Excel 复制表格时，常见情况是第一列全在前、第二列全在后，单独按行切开就会破坏
     * 列与列的对应关系。只有行式清单才允许分段；表格或未知结构必须整段交给模型。</p>
     */
    public static boolean looksLikeRowOrientedQuote(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        int nonBlank = 0;
        int rowLike = 0;
        for (String raw : normalized.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            nonBlank++;
            if (ROW_PART.matcher(line).find() && ROW_PRICE.matcher(line).find()) {
                rowLike++;
            }
        }
        return rowLike >= 5 && rowLike * 10 >= nonBlank * 6;
    }

    public static List<String> split(String content, int maxChars, int maxLines) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        int charLimit = Math.max(200, maxChars);
        int lineLimit = Math.max(1, maxLines);
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        List<List<String>> blocks = logicalBlocks(normalized);

        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentChars = 0;
        for (List<String> block : blocks) {
            int blockChars = blockChars(block);
            int nextLines = current.size() + block.size();
            int nextChars = currentChars + (current.isEmpty() ? 0 : 1) + blockChars;
            if (!current.isEmpty() && (nextLines > lineLimit || nextChars > charLimit)) {
                chunks.add(join(current));
                current = new ArrayList<>();
                currentChars = 0;
            }
            if (block.size() <= lineLimit && blockChars <= charLimit) {
                for (String line : block) {
                    if (!current.isEmpty()) {
                        currentChars++;
                    }
                    current.add(line);
                    currentChars += line.length();
                }
                continue;
            }
            // 单个逻辑块本身超限：只按完整行拆分，绝不从一行中间切断料号。
            if (!current.isEmpty()) {
                chunks.add(join(current));
                current = new ArrayList<>();
                currentChars = 0;
            }
            for (String line : block) {
                int lineChars = currentChars + (current.isEmpty() ? 0 : 1) + line.length();
                if (!current.isEmpty() && (current.size() + 1 > lineLimit || lineChars > charLimit)) {
                    chunks.add(join(current));
                    current = new ArrayList<>();
                    currentChars = 0;
                }
                if (!current.isEmpty()) {
                    currentChars++;
                }
                current.add(line);
                currentChars += line.length();
            }
            chunks.add(join(current));
            current = new ArrayList<>();
            currentChars = 0;
        }
        if (!current.isEmpty()) {
            chunks.add(join(current));
        }
        return chunks;
    }

    /** 空行作为逻辑块边界；忽略纯空白行，但不会丢失任何非空行。 */
    private static List<List<String>> logicalBlocks(String content) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> block = new ArrayList<>();
        for (String raw : content.split("\n", -1)) {
            String line = stripTrailing(raw);
            if (line.trim().isEmpty()) {
                if (!block.isEmpty()) {
                    blocks.add(block);
                    block = new ArrayList<>();
                }
            } else {
                block.add(line);
            }
        }
        if (!block.isEmpty()) {
            blocks.add(block);
        }
        return blocks;
    }

    private static int blockChars(List<String> block) {
        int chars = 0;
        for (int i = 0; i < block.size(); i++) {
            if (i > 0) {
                chars++;
            }
            chars += block.get(i).length();
        }
        return chars;
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

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0) {
            char c = line.charAt(end - 1);
            if (c != ' ' && c != '\t') {
                break;
            }
            end--;
        }
        return line.substring(0, end);
    }
}
