package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.model.WeChatMessage;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信 INQ/OFFER 关联的确定性兜底规则。
 *
 * <p>模型可能把“拆 2040”这类纯价格回复关联到更早的历史询价。这里只在当前消息明显是
 * 短价格回复、且前面存在更近的我方询价时校正，不覆盖有明确料号的主动报价。</p>
 */
public final class WeChatInqLinkRules {

    private static final Pattern INQUIRY_SIGNAL = Pattern.compile(
            "(?i)inq|rfq|询价|有货吗|有货\\?|有吗|多少钱|什么价|请报|要报价|报价给我|\\?|？");
    /** 带字母和数字的料号/型号 token，如 6760P、M321RAJA0MB2、32G。 */
    private static final Pattern MIXED_TOKEN = Pattern.compile(
            "(?i)\\b(?=[a-z0-9-]*[a-z])(?=[a-z0-9-]*\\d)[a-z0-9-]{2,}\\b");
    private static final Pattern DIGIT_TOKEN = Pattern.compile("\\d+");
    private static final Pattern RELATION_ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    private WeChatInqLinkRules() {
    }

    /**
     * 是否属于“只有价格/数量/拆机标记”的短回复。
     *
     * <p>明确带料号或型号的报价不参与自动改关联；三个及以上数字 token 的清单也不参与，
     * 避免把“8358 14pcs 1850”这类带料号的主动报价误改到无关询价。</p>
     */
    public static boolean isPriceOnlyReply(String content) {
        String text = StringUtils.trimToEmpty(content);
        if (text.isEmpty() || text.length() > 80 || INQUIRY_SIGNAL.matcher(text).find()) {
            return false;
        }
        String withoutQuantityUnits = text.replaceAll(
                "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:pcs?|pc|sets?|units?|packs?|k)\\b", " ");
        if (MIXED_TOKEN.matcher(withoutQuantityUnits).find()) {
            return false;
        }
        String rest = withoutQuantityUnits.toLowerCase()
                .replaceAll("@[^\\s,，;；]+", " ")
                .replaceAll("(?i)\\b(?:usd|rmb|cny|eur|hkd)\\b", " ")
                .replace("us$", " ").replace("美元", " ").replace("美金", " ")
                .replace("人民币", " ").replace("元", " ")
                .replaceAll("(?i)\\b(?:pcs?|sets?|units?|packs?)\\b", " ")
                .replaceAll("(?i)拆(?:机|新|箱|件|封)?|二手|\\bcp\\b|\\bclean\\s+pull\\b"
                        + "|\\bpull(?:ed)?\\b|\\brenew(?:ed)?\\b|\\brefurb(?:ished)?\\b|\\bused\\b", " ")
                .replaceAll("周|天|月|日|交期|货期|现货|含税|未税|包邮", " ")
                .replaceAll("[0-9\\s.,，;；/、|\\\\\\-—–:：$¥￥€]", "");
        if (!rest.trim().isEmpty()) {
            return false;
        }
        int digits = 0;
        Matcher matcher = DIGIT_TOKEN.matcher(text);
        while (matcher.find()) {
            digits++;
        }
        return digits > 0 && digits <= 2;
    }

    /**
     * 找到当前消息之前最近一条我方询价消息。
     *
     * @return input 中的下标；没有候选时返回 -1
     */
    public static int latestSelfInquiryIndex(List<WeChatMessage> input, int currentIndex,
                                             Set<String> selfNames, long maxGapMs) {
        if (input == null || currentIndex <= 0 || currentIndex >= input.size()) {
            return -1;
        }
        WeChatMessage current = input.get(currentIndex);
        long currentSeq = current == null ? 0L : current.getSortSeq();
        for (int i = currentIndex - 1; i >= 0; i--) {
            WeChatMessage candidate = input.get(i);
            if (candidate == null || !WeChatTextUtils.isSelf(candidate.getSender(), selfNames)) {
                continue;
            }
            long candidateSeq = candidate.getSortSeq();
            if (maxGapMs > 0 && currentSeq > 0 && candidateSeq > 0
                    && currentSeq - candidateSeq > maxGapMs) {
                break;
            }
            if ("OFFER".equalsIgnoreCase(StringUtils.trimToEmpty(candidate.getMsgCategory()))) {
                continue;
            }
            if (looksLikeInquiry(candidate.getContent())) {
                return i;
            }
        }
        return -1;
    }

    /** 读取关联 JSON 中的消息 id，并定位到 input 下标；找不到返回 -1。 */
    public static int relationIndex(List<WeChatMessage> input, String relationJson) {
        if (input == null || input.isEmpty() || StringUtils.isBlank(relationJson)) {
            return -1;
        }
        Matcher matcher = RELATION_ID.matcher(relationJson);
        if (!matcher.find()) {
            return -1;
        }
        long id;
        try {
            id = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
        for (int i = 0; i < input.size(); i++) {
            WeChatMessage message = input.get(i);
            if (message != null && message.getId() != null && message.getId() == id) {
                return i;
            }
        }
        return -1;
    }

    private static boolean looksLikeInquiry(String content) {
        String text = StringUtils.trimToEmpty(content);
        return !text.isEmpty() && INQUIRY_SIGNAL.matcher(text).find();
    }
}
