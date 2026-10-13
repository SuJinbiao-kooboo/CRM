package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Weak-inquiry-word rule (INQ).
 *
 * <p>Weak words (询 / 查一下 / 问下 / 问一下) only count as an inquiry when the
 * message also carries a model+qty, a model+number, or a price word.
 * This keeps everyday chat ("想问下，大家是先安热水器…") away from INQ while
 * real trade messages ("问下 5317 10pcs 报价") still match.</p>
 */
public class WeakInquiryRule implements Rule {

    private static final List<String> WEAK_WORDS = Arrays.asList(
            "\u8be2", "\u67e5\u4e00\u4e0b", "\u95ee\u4e0b", "\u95ee\u4e00\u4e0b");

    private static final Pattern QTY = Pattern.compile(
            "[A-Za-z0-9]*[A-Za-z][A-Za-z0-9/\\-.]*\\s*\\d+\\s*(?:pcs|eps|\u4e2a|\u7247|\u9897|\u53ea|\u6761|\u4ef6)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_WORD = Pattern.compile(
            "\\$|usd|rmb|cny|\u4ef7\u683c|\u62a5\u4ef7",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL_NUM = Pattern.compile(
            "[A-Za-z][A-Za-z0-9/\\-.]*\\s*\\d",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null || m.getContent() == null) {
            return false;
        }
        String low = m.getContent().toLowerCase(Locale.ROOT);
        boolean hasWeak = false;
        for (String w : WEAK_WORDS) {
            if (low.contains(w)) {
                hasWeak = true;
                break;
            }
        }
        if (!hasWeak) {
            return false;
        }
        return QTY.matcher(low).find() || PRICE_WORD.matcher(low).find() || MODEL_NUM.matcher(low).find();
    }
}