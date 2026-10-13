package com.ruoyi.wexinAuto.analysis.rule;

import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Pattern;

/**
 * Structured quotation/inquiry list rule.
 *
 * <p>Rows like "Xeon Gold 6444Y - 60 pcs 3650" (price after qty pcs) are offer
 * rows; "Xeon Gold 6444Y - 60 pcs" (qty pcs only, line end) are inquiry rows.
 * Not wired into the current OFFER/INQ chains (PriceEvidenceRule covers offer
 * rows), kept here for future use / manual composition.</p>
 */
public class QuotationRule implements Rule {

    private static final String CUR = "(?:US\\$|USD|RMB|CNY|\\$|\u00a5|\u20ac)";

    /** offer row: model - qty pcs price */
    private static final Pattern OFFER_LINE = Pattern.compile(
            "^(.+?)[\\s\\-]*\\d+\\s*pcs\\s+" + CUR + "?\\s*\\d[\\d,.]*\\s*(?:USD|US\\$|\\$|RMB|CNY)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** inquiry row: model - qty pcs (line end, no price) */
    private static final Pattern INQUIRY_LINE = Pattern.compile(
            "^(.+?)[\\s\\-]*\\d+\\s*pcs\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final boolean inquiry;
    private final int minRows;

    public QuotationRule(boolean inquiry) {
        this(inquiry, 2);
    }

    public QuotationRule(boolean inquiry, int minRows) {
        this.inquiry = inquiry;
        this.minRows = Math.max(1, minRows);
    }

    @Override
    public boolean test(MessageContext ctx) {
        WeChatMessage m = ctx == null ? null : ctx.getCurrent();
        if (m == null || m.getContent() == null || m.getContent().isEmpty()) {
            return false;
        }
        int offerRows = 0;
        int inquiryRows = 0;
        for (String line : m.getContent().split("[\r\n]+")) {
            String l = line.trim();
            if (l.isEmpty()) {
                continue;
            }
            if (OFFER_LINE.matcher(l).matches()) {
                offerRows++;
            } else if (INQUIRY_LINE.matcher(l).matches()) {
                inquiryRows++;
            }
        }
        if (inquiry) {
            return inquiryRows >= minRows && offerRows == 0;
        }
        return offerRows >= minRows;
    }
}