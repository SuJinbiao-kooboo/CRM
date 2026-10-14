package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Pattern;

/**
 * Quantity-without-price rule (INQ).
 *
 * <p>Hits when "model qty" appears adjacent in the text with no price evidence
 * ("9275F 2pcs", "5317 10pcs", "4410y 200个"). Two shapes:</p>
 * <ul>
 *   <li>pcs/eps: numeric model allowed (5317 10pcs)</li>
 *   <li>Chinese units (个/片/颗/只/条/件): the model part must contain a letter,
 *       so everyday chat like "我3个" / "改2个" / "9.9元2件" never matches</li>
 * </ul>
 */
public class QuantityInquiryRule implements Rule {

    private static final Pattern QTY_PCS = Pattern.compile(
            "[A-Za-z0-9/\\-.]*\\s*\\d+\\s*(?:pcs|eps)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QTY_CN = Pattern.compile(
            "[A-Za-z0-9]*[A-Za-z][A-Za-z0-9/\\-.]*\\s*\\d+\\s*(?:\u4e2a|\u7247|\u9897|\u53ea|\u6761|\u4ef6)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null || m.getContent() == null || m.getContent().isEmpty()) {
            return false;
        }
        String text = m.getContent();
        if (!QTY_PCS.matcher(text).find() && !QTY_CN.matcher(text).find()) {
            return false;
        }
        return !new PriceEvidenceRule().test(ctx);
    }
}