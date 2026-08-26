package com.ruoyi.email.service.util;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ruoyi.common.utils.StringUtils;

import cn.hutool.http.HtmlUtil;

/**
 * HTML正文转纯文本工具
 * <p>
 * 处理规则：去除HTML标签；表格按行还原——每个 &lt;tr&gt; 换行、每个 &lt;td&gt;/&lt;th&gt; 用制表符(\t)分隔
 * （还原为TSV形式，Excel/记事本可直接对齐查看）；表格内标签之间的缩进/换行空白全部清理，
 * 保证每一行就是一条完整的表格数据；块级标签(&lt;p&gt;/&lt;div&gt;/&lt;br&gt;等)转为换行；
 * HTML实体反转义；统一换行符并清理多余空白。
 *
 * @author ruoyi
 */
public class EkeHtmlToTextUtil
{
    /** 表格标签匹配：整体匹配一个table区域（非贪婪匹配，嵌套表格支持有限） */
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?is)<\\s*table[^>]*>.*?<\\s*/\\s*table\\s*>");

    /** 单元格内的换行类标签（先转空格，保证单元格内容在一行，避免破坏列对齐） */
    private static final Pattern CELL_BLOCK_PATTERN = Pattern.compile("(?is)<\\s*/?\\s*(p|div|li|h[1-6]|ul|ol|br)[^>]*>");

    /** 表格行标签：转换行 */
    private static final Pattern TR_PATTERN = Pattern.compile("(?is)<\\s*tr[^>]*>");

    /** 表格单元格标签（td/th）：转制表符 */
    private static final Pattern TD_PATTERN = Pattern.compile("(?is)<\\s*t[hd][^>]*>");

    /** 其余任意标签：删除 */
    private static final Pattern ANY_TAG_PATTERN = Pattern.compile("(?s)<[^>]*>");

    /** UTF-8严格解码器：解码失败（字节不是合法UTF-8）时抛异常，用于判断内容实际编码 */
    private static final CharsetDecoder UTF8_STRICT = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);

    /** Windows-1252 0x80-0x9F 区段对应字符（该区段与ISO-8859-1不同，可正确还原 € 等符号） */
    private static final String WINDOWS_1252_HIGH =
            "\u20AC\u0081\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\u008D\u017D\u008F"
            + "\u0090\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\u009D\u017E\u0178";

    /** 从Content-Type中解析charset，如 text/html; charset=utf-8 */
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset\\s*=\\s*[\"']?([^;\"'\\s]+)");

    private EkeHtmlToTextUtil()
    {
    }

    /**
     * HTML转纯文本（表格还原为制表符分隔）
     *
     * @param html HTML内容（可空）
     * @return 纯文本，空入参返回原值
     */
    public static String toText(String html)
    {
        if (StringUtils.isEmpty(html))
        {
            return html;
        }
        String text = html;

        // 1. 移除脚本、样式与注释内容
        text = text.replaceAll("(?is)<\\s*script\\b[^>]*>.*?<\\s*/\\s*script\\s*>", "");
        text = text.replaceAll("(?is)<\\s*style\\b[^>]*>.*?<\\s*/\\s*style\\s*>", "");
        text = text.replaceAll("(?is)<!--.*?-->", "");

        // 2. 表格还原：行换行、单元格制表符分隔（先于全局标签处理，保证表格结构优先）
        text = convertTables(text);

        // 3. 表格外的块级标签转换行，保持段落可读
        text = text.replaceAll("(?is)<\\s*/\\s*(p|div|li|h[1-6]|ul|ol|blockquote|section|article|header|footer)[^>]*>", "\n");
        text = text.replaceAll("(?is)<\\s*br\\s*/?\\s*>", "\n");
        text = text.replaceAll("(?is)<\\s*(p|div|li|h[1-6]|ul|ol|blockquote|section|article|header|footer)[^>]*>", "");

        // 4. 删除剩余标签
        text = ANY_TAG_PATTERN.matcher(text).replaceAll("");

        // 5. 实体反转义；&nbsp; 等转普通空格，避免不换行空格干扰对齐
        text = HtmlUtil.unescape(text).replace('\u00A0', ' ');

        // 6. 统一换行符（兼容 \r\n / \r），并清理空白：制表符两侧多余空格、行首/行尾空白、连续空行、首尾空白
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replaceAll(" *\t *", "\t");
        text = text.replaceAll("(?m)^[ \t]+", "");
        text = text.replaceAll("(?m)[ \t]+$", "");
        text = text.replaceAll("\n{3,}", "\n\n");

        // 7. 通用多余字符清理：链接标记、内嵌图片引用、加粗残留、分隔线、连续空格
        text = cleanCommon(text);

        // 8. 邮件引用标记清理：行首 > 引用标记删除（保留引用内容），单独一行的 ' 残留删除
        text = text.replaceAll("(?m)^[>]+[ \t]*", "");
        text = text.replaceAll("(?m)^'[ \t]*$", "");
        return StringUtils.trim(text);
    }

    /**
     * 纯文本正文基础清理（不处理HTML标签）
     * <p>
     * 统一换行符（兼容 \r\n / \r）、清理行首/行尾空白与多余空行、去除邮件引用标记
     * （行首 &gt; 或单独一行的 ' ），保留原有内容不变。
     *
     * @param text 纯文本正文（可空）
     * @return 清理后的文本，空入参返回原值
     */
    public static String cleanPlainText(String text)
    {
        if (StringUtils.isEmpty(text))
        {
            return text;
        }
        // 1. 统一换行符（兼容 \r\n / \r）
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        // 2. 清理行首空白（列表项缩进等）
        text = text.replaceAll("(?m)^[ \t]+", "");
        // 3. 邮件引用标记清理：行首 > 引用标记删除（保留引用内容），单独一行的 ' 残留删除
        text = text.replaceAll("(?m)^[>]+[ \t]*", "");
        text = text.replaceAll("(?m)^'[ \t]*$", "");
        // 4. 通用多余字符清理：链接标记、内嵌图片引用、加粗残留、分隔线、连续空格
        text = cleanCommon(text);
        // 5. 清理行尾空白与多余空行
        text = text.replaceAll("(?m)[ \t]+$", "");
        text = text.replaceAll("\n{3,}", "\n\n");
        return StringUtils.trim(text);
    }

    /**
     * 通用多余字符清理（HTML与纯文本共用）
     * <p>
     * 1) 邮件客户端生成的链接标记 &lt;mailto:xxx&gt;（显示文本已含邮箱，链接目标冗余）；
     * 2) 链接标记 &lt;http(s)://xxx&gt;：前面已有显示文本时删除，独立链接保留；
     * 3) 内嵌图片 CID 引用残留 [cid:xxx]；
     * 4) Markdown 加粗残留 **；
     * 5) 整行分隔线（表格内价格占位下划线非整行，不受影响）；
     * 6) 连续空格压缩（报价单排版空格，不影响信息）。
     */
    private static String cleanCommon(String text)
    {
        // 1. 链接标记 <mailto:xxx>
        text = text.replaceAll("<mailto:[^>]*>", "");
        // 2. 链接标记 <http(s)://xxx>：前面有非空白字符（即有显示文本）时删除
        text = text.replaceAll("(?<=[^\\s<])<(?:https?|ftp)://[^>]*>", "");
        // 3. 内嵌图片 CID 引用残留 [cid:xxx]
        text = text.replaceAll("\\[cid:[^\\]]*\\]", "");
        // 4. Markdown 加粗残留 **
        text = text.replaceAll("\\*\\*", "");
        // 5. 整行分隔线（下划线/连字符/等号，如 ______________ 或 ------------）
        text = text.replaceAll("(?m)^[-=_]{10,}$", "");
        // 6. 连续空格压缩为单空格，并清理可能残留的行首空白（如删除 ** 后）
        text = text.replaceAll("[ ]{2,}", " ");
        text = text.replaceAll("(?m)^[ \t]+", "");
        return text;
    }

    /**
     * 按字节解码HTML内容
     * <p>
     * 优先按UTF-8严格解码（多数HTML邮件为UTF-8，可避免Content-Type未声明/声明错误导致的乱码）；
     * 失败则按Content-Type声明的charset解码；均失败时按Windows-1252单字节解码（可正确还原 € 等符号）。
     *
     * @param bytes HTML原始字节（已解码传输编码）
     * @param contentType Content-Type头（用于提取声明的charset，可为空）
     * @return 解码后的HTML文本
     */
    public static String decode(byte[] bytes, String contentType)
    {
        if (bytes == null)
        {
            return null;
        }
        // 1. UTF-8严格解码优先
        try
        {
            return UTF8_STRICT.decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException ignored)
        {
        }
        // 2. 按Content-Type声明的charset解码
        String charset = resolveCharset(contentType);
        if (StringUtils.isNotEmpty(charset))
        {
            try
            {
                return new String(bytes, charset);
            }
            catch (UnsupportedEncodingException ignored)
            {
            }
        }
        // 3. 兜底：Windows-1252单字节解码（0x80-0x9F区段映射常见符号，避免控制符/乱码）
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes)
        {
            int v = b & 0xFF;
            if (v >= 0x80 && v <= 0x9F)
            {
                sb.append(WINDOWS_1252_HIGH.charAt(v - 0x80));
            }
            else
            {
                sb.append((char) v);
            }
        }
        return sb.toString();
    }

    /**
     * 从Content-Type中解析charset，如 text/html; charset=utf-8
     */
    private static String resolveCharset(String contentType)
    {
        if (StringUtils.isEmpty(contentType))
        {
            return null;
        }
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 还原HTML表格：每个table区域独立处理，行用换行分隔、单元格用制表符分隔
     */
    private static String convertTables(String html)
    {
        Matcher matcher = TABLE_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find())
        {
            String table = matcher.group();
            // 1. 单元格内的换行类标签先转空格，保证单元格内容不换行、列对齐
            table = CELL_BLOCK_PATTERN.matcher(table).replaceAll(" ");
            // 2. 标签之间（缩进、换行）的所有空白塌缩为单个空格，消除行内残留空行
            table = table.replaceAll("\\s+", " ");
            // 3. 行转换行、单元格转制表符
            table = TR_PATTERN.matcher(table).replaceAll("\n");
            table = TD_PATTERN.matcher(table).replaceAll("\t");
            // 4. 删除表格内剩余标签（含 table 开闭标签、tr/td 结束标签等）
            table = ANY_TAG_PATTERN.matcher(table).replaceAll("");
            // 5. 清理：制表符两侧空格、行首/行尾制表符（首列/末列为空单元格时不占位）、空行、连续换行
            table = table.replaceAll(" *\t *", "\t");
            table = table.replaceAll("(?m)^\t+", "");
            table = table.replaceAll("(?m)\t+$", "");
            table = table.replaceAll("(?m)^[ \t]*\n", "");
            table = table.replaceAll("\n{2,}", "\n");
            // 6. 表格前后各补一个换行与正文分隔
            table = "\n" + table + "\n";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(table));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
