package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Pattern;

/**
 * Price evidence rule (OFFER).
 *
 * <p>Hits when any non-empty line of the message contains one of:</p>
 * <ul>
 *   <li>a currency token ($495 / 2368USD / 3000$ / 4310 USD 435)</li>
 *   <li>a quotation row "model - qty pcs price" (Xeon Gold 6544Y - 80 pcs 3650)</li>
 *   <li>a model followed by a standalone trailing number (5955WX 470, 6544Y按3580)</li>
 * </ul>
 * Target prices like TP5400usd are stripped before scanning so they never count
 * as price evidence (TPxx is an inquiry target price, not an offer).
 */
public class PriceEvidenceRule implements Rule {

    private static final String QTY = "(?:pcs|\u4e2a|\u7247|\u9897|eps|\u53ea|\u6761|\u4ef6)";
    private static final String CUR = "(?:US\\$|USD|RMB|CNY|\\$|\u00a5|\u20ac)";

    private static final Pattern TP = Pattern.compile("(?i)TP\\s*\\d+\\s*(?:" + CUR + ")?");
    private static final Pattern CURRENCY = Pattern.compile(CUR, Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFER_LINE = Pattern.compile(
            "^.+?[\\s\\-]*\\d+\\s*pcs\\s+" + CUR + "?\\s*\\d[\\d,.]*\\s*(?:USD|US\\$|\\$|RMB|CNY)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** standalone trailing number: must be preceded by a separator (space / 按 / 以 / : / ：) */
    private static final Pattern TRAIL_UNIT = Pattern.compile("[\\s\u6309\u4ee5:\uff1a]\\s*[\\d,.]+\\s*$");
    private static final Pattern PURE_NUM = Pattern.compile("^[\\d,.]+\\s*$");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern QTY_LINE = Pattern.compile(QTY, Pattern.CASE_INSENSITIVE);

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null) {
            return false;
        }
        String text = m.getContent();
        if (text == null || text.isEmpty()) {
            return false;
        }
        String t = TP.matcher(text).replaceAll("TP");
        for (String line : t.split("[\r\n]+")) {
            String l = line.trim();
            if (l.isEmpty()) {
                continue;
            }
            if (CURRENCY.matcher(l).find()) {
                return true;
            }
            if (OFFER_LINE.matcher(l).matches()) {
                return true;
            }
            if (!QTY_LINE.matcher(l).find() && !PURE_NUM.matcher(l).matches()) {
                if (HAS_LETTER.matcher(l).find() && TRAIL_UNIT.matcher(l).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}