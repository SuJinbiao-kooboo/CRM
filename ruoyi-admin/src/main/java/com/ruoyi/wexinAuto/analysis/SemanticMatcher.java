package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.model.WeChatMessage;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信询报价语义校验工具：在 AI 判定之后做一层可解释的兜底。
 *
 * <p>业务口径：INQ 必须是客户向我方询价，OFFER 必须是我方对具体客户 INQ 的直接回应，
 * TRACE 必须是客户对已有 INQ/OFFER 的二次疑问。群发报价、我方外发询价、内部沟通
 * 以及无关联的陈述句统一降级为 OTHER。</p>
 */
public final class SemanticMatcher {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 型号/料号候选：至少3位，允许字母数字及常见分隔符。 */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{2,}");

    /** 通用商务词、容量单位、接口规格等，不作为能关联两条消息的型号。 */
    private static final Set<String> TOKEN_STOP = new HashSet<>(Arrays.asList(
            "INQ", "OFFER", "RFQ", "USD", "RMB", "CNY", "PCS", "MOQ", "TP", "NEW", "USED",
            "STOCK", "READY", "PRICE", "QTY", "DC", "COO", "LOT", "FAST", "SHIP",
            "CPU", "GPU", "SSD", "HDD", "DDR", "DDR4", "DDR5", "NVME", "SATA", "SAS",
            "INTEL", "AMD", "NVIDIA", "WINDOWS", "SERVER", "SERVER", "DELL", "HP", "LENOVO",
            "GOOD", "OK", "YES", "NO", "PLEASE", "CHECK", "THANKS"
    ));

    /** 明确的询价意图。只有这些词或“型号/数量”组合才允许进入 INQ。 */
    private static final Pattern INQ_STRONG = Pattern.compile(
            "(?i)(\\bINQ\\b|\\bRFQ\\b|询价|求购|找货|谁有|报个价|报来|来报|请报价|什么价|多少钱|" +
                    "价格多少|报价多少|目标价|有货吗|有没有货|有没有|是否有|有现货吗|现货吗|" +
                    "问一下|问下|能给.*价|可以.*报价)"
    );

    /** 明确的疑问表达；不把“价格很好”“客户要下单了”这种陈述当成追问。 */
    private static final Pattern QUESTION_CUE = Pattern.compile(
            "(?i)(多少(?:钱|片|条|个|台|套)?|多久|什么时候|几点|哪个|哪家|哪里|什么品牌|什么型号|" +
                    "怎么(?:卖|算|样)|是否|能否|能不能|可不可以|可以吗|行不行|好不好|有没有|有货吗|有吗|" +
                    "现货还是期货|期货还是现货|交期(?:呢|多少|多久|是什么|能|可以|是)|货期(?:呢|多少|多久)|" +
                    "MOQ|质保|保修|含税|下单吗|能下单|接单了吗|确认一下)"
    );

    /** 整条消息就是一个业务疑问词（聊天中常见省略句）。 */
    private static final Pattern SHORT_QUESTION_TERM = Pattern.compile(
            "(?i)^(交期|货期|价格|库存|数量|MOQ|TP|质保|保修)[？?]?$"
    );

    /** 卖方群发/收购广播的典型开头。 */
    private static final Pattern BROADCAST_START = Pattern.compile(
            "^(出(?:[:：\\s]|货)|现货(?:出|直出)?|全新现货|今日现货|公司现货|特价(?:出)?|清仓|" +
                    "仓库现货|长期(?:收|收购)|高价收|大量收|收购|回收)"
    );

    /** 真正的价格值：必须出现金额与币种/单位/税价的组合，不能仅凭“价格/报价”二字。 */
    private static final Pattern PRICE_VALUE = Pattern.compile(
            "(?i)(?:[$¥￥]\\s*\\d|\\d[\\d,.]*\\s*(?:usd|rmb|cny|美元|美金|人民币|元|块)|" +
                    "(?:usd|rmb|cny)\\s*[:：]?\\s*\\d|" +
                    "\\d[\\d,.]*\\s*(?:含税|税前|税后|单价|实价)|" +
                    "(?:单价|实价)\\s*[:：]?\\s*\\d[\\d,.]*|" +
                    "(?:价格|报价)\\s*[:：]?\\s*\\d[\\d,.]*|" +
                    "\\d[\\d,.]*\\s*(?:/|每)\\s*(?:pcs?|pc|片|台|套|条|个))"
    );

    /** 追问必须围绕交易要素，避免“在吗/可以吗”这类普通寒暄被挂到报价上。 */
    private static final Pattern FOLLOW_UP_CUE = Pattern.compile(
            "(?i)(价格|报价|单价|多少钱|price|交期|货期|什么时候|多久|库存|现货|期货|有货|含税|税前|税后|" +
                    "MOQ|起订|数量|型号|料号|下单|订单|优惠|折扣|保修|质保|确认|发货|排单|接单)"
    );

    private static final Pattern QUANTITY = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?\\s*(?:pcs|pc|片|条|个|台|套|张|颗|只))"
    );

    /** 常见硬件品类；允许“64G内存多少钱”这类中文询价通过“品类+疑问句”识别。 */
    private static final Pattern PRODUCT_NOUN = Pattern.compile(
            "(?i)(内存条?|固态硬盘?|机械硬盘?|硬盘|处理器|显卡|主板|服务器|网卡|阵列卡|电源|风扇|" +
                    "CPU|GPU|SSD|HDD|DDR[345]?|NVME)"
    );

    private static final Pattern PURE_PRICE_REPLY = Pattern.compile(
            "^[\\d\\s.,/+\\-]+(?:\\s*(?:usd|rmb|cny|元|美元|美金|人民币))?$",
            Pattern.CASE_INSENSITIVE
    );

    private SemanticMatcher() {
    }

    /**
     * 是否为我方发出的消息。必须精确匹配 selfNames，避免“May 星禾 SSD GPU”被误认成我方“May”。
     */
    public static boolean isSelf(String sender, Set<String> selfNames) {
        if (StringUtils.isBlank(sender) || selfNames == null || selfNames.isEmpty()) {
            return false;
        }
        String s = normalizeName(sender);
        for (String name : selfNames) {
            if (StringUtils.isNotBlank(name) && s.equals(normalizeName(name))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeName(String value) {
        String s = value == null ? "" : value.trim();
        while (s.startsWith("@") || s.startsWith("＠")) {
            s = s.substring(1).trim();
        }
        return s.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** 群聊判断：微信群 username 以 @chatroom 结尾；少数群名也可能不含“群”字。 */
    public static boolean isGroupChat(WeChatMessage message) {
        if (message == null) {
            return false;
        }
        String username = StringUtils.defaultString(message.getUsername()).toLowerCase(Locale.ROOT);
        if (username.endsWith("@chatroom") || username.contains("@chatroom:")) {
            return true;
        }
        String name = StringUtils.defaultString(message.getName());
        return name.contains("群");
    }

    /** 是否是可进入 TRACE 的明确疑问句。 */
    public static boolean looksLikeQuestion(String content) {
        String c = clean(content);
        if (c.isEmpty()) {
            return false;
        }
        return c.contains("?") || c.contains("？")
                || QUESTION_CUE.matcher(c).find()
                || SHORT_QUESTION_TERM.matcher(c).matches();
    }

    /**
     * 是否具备客户询价意图。纯数量、纯数字、广播报价、系统消息均返回 false。
     */
    public static boolean looksLikeInquiry(String content) {
        String c = clean(content);
        if (c.isEmpty() || isSystemOrMedia(c)) {
            return false;
        }
        if (looksLikeBroadcast(c) && !looksLikeQuestion(c)) {
            return false;
        }
        if (INQ_STRONG.matcher(c).find()) {
            return true;
        }
        boolean hasProduct = !tokens(c).isEmpty() || PRODUCT_NOUN.matcher(c).find();
        boolean hasQuantity = QUANTITY.matcher(c).find();
        return hasProduct && (hasQuantity || looksLikeQuestion(c));
    }

    /** 是否是典型的群发报价/收购广告开头。 */
    public static boolean looksLikeBroadcast(String content) {
        return BROADCAST_START.matcher(clean(content)).find();
    }

    /**
     * 判断我方报价是否是对指定客户 INQ 的直接回应。
     *
     * @param offer          待判断的 OFFER
     * @param inq            客户 INQ（调用方应保证 sender 非我方）
     * @param self           offer 是否由我方发出
     * @param groupChat     是否群聊
     * @param maxAgeMin      最大关联分钟数
     * @param shortWindowMin 纯数字/短报价的允许窗口
     */
    public static boolean isReplyTo(WeChatMessage offer, WeChatMessage inq, boolean self,
                                    boolean groupChat, int maxAgeMin, int shortWindowMin) {
        if (offer == null || inq == null || !self || StringUtils.isBlank(offer.getContent())) {
            return false;
        }
        int maxAge = Math.max(1, maxAgeMin);
        int shortWindow = Math.max(1, Math.min(shortWindowMin, maxAge));
        if (!withinMinutes(inq, offer, maxAge)) {
            return false;
        }
        String offerContent = clean(offer.getContent());
        String inqContent = clean(inq.getContent());
        String quote = clean(offer.getQuote());
        if (looksLikeBroadcast(offerContent)) {
            return false;
        }
        boolean priced = hasPriceEvidence(offerContent) || isShortPriceReply(offerContent);
        if (!priced) {
            return false;
        }
        if (!quote.isEmpty() && (
                sharesProduct(quote, inqContent)
                        || (inqContent.length() >= 4 && (quote.contains(inqContent) || inqContent.contains(quote))))) {
            return true;
        }
        if (sharesProduct(offerContent, inqContent) && hasPriceEvidence(offerContent)) {
            return true;
        }
        if (sharesQuantity(offerContent, inqContent) && hasPriceEvidence(offerContent)) {
            return true;
        }
        if (isShortPriceReply(offerContent) && withinMinutes(inq, offer, shortWindow)) {
            return true;
        }
        // 没有料号、没有引用时，只接受紧邻时间窗内的纯价格回复；其余按广播排除。
        return isShortPriceReply(offerContent) && withinMinutes(inq, offer, shortWindow);
    }

    /** 兼容旧调用：默认 180 分钟关联窗口、30 分钟短回复窗口。 */
    public static boolean isReplyTo(WeChatMessage offer, WeChatMessage inq, boolean self) {
        return isReplyTo(offer, inq, self, isGroupChat(offer), 180, 30);
    }

    /**
     * 判断客户追问是否针对指定 INQ/OFFER。
     * 群聊短问句只允许短窗口，避免把很久以前的普通问题牵强关联到报价上。
     */
    public static boolean isFollowUpTo(WeChatMessage trace, WeChatMessage ref, boolean groupChat,
                                       int maxAgeMin, int shortWindowMin) {
        if (trace == null || ref == null || !looksLikeQuestion(trace.getContent())) {
            return false;
        }
        int maxAge = Math.max(1, maxAgeMin);
        int shortWindow = Math.max(1, Math.min(shortWindowMin, maxAge));
        if (!withinMinutes(ref, trace, maxAge)) {
            return false;
        }
        String traceText = clean(trace.getContent());
        String refText = clean(ref.getContent());
        String quote = clean(trace.getQuote());
        if (!quote.isEmpty()) {
            boolean quoteLinked = sharesProduct(quote, refText)
                    || (refText.length() >= 4 && (quote.contains(refText) || refText.contains(quote)));
            if (quoteLinked && (FOLLOW_UP_CUE.matcher(traceText).find()
                    || sharesProduct(traceText, refText))) {
                return true;
            }
        }
        if (sharesProduct(traceText, refText)) {
            return true;
        }
        if (!FOLLOW_UP_CUE.matcher(traceText).find()) {
            return false;
        }
        if (groupChat) {
            return traceText.length() <= 24 && withinMinutes(ref, trace, shortWindow);
        }
        return traceText.length() <= 40 && withinMinutes(ref, trace, maxAge);
    }

    /** 兼容旧调用。 */
    public static boolean isFollowUpTo(WeChatMessage trace, WeChatMessage ref) {
        return isFollowUpTo(trace, ref, isGroupChat(trace), 180, 30);
    }

    /** 两条消息是否共享至少一个可信型号/料号 token。 */
    public static boolean sharesProduct(String a, String b) {
        Set<String> ta = tokens(a);
        if (ta.isEmpty()) {
            return false;
        }
        Set<String> tb = tokens(b);
        for (String token : ta) {
            if (tb.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> tokens(String content) {
        String c = clean(content);
        if (c.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(c);
        while (matcher.find()) {
            String token = matcher.group().toUpperCase(Locale.ROOT);
            if (token.length() < 3 || TOKEN_STOP.contains(token)) {
                continue;
            }
            // 64G/16T/100PCS/USD 等容量、数量、币种不是型号。
            if (token.matches("\\d+(?:GB|G|TB|T|MB|M|PCS|PC|USD|RMB|CNY|W|KW|MHZ|GHZ|GBPS)")) {
                continue;
            }
            out.add(token);
        }
        return out;
    }

    /** 数量一致也是报价回应证据，例如客户问 24pcs、我方直接回“有，24pcs 660USD”。 */
    private static boolean sharesQuantity(String a, String b) {
        Set<String> qa = quantities(a);
        if (qa.isEmpty()) {
            return false;
        }
        Set<String> qb = quantities(b);
        for (String q : qa) {
            if (qb.contains(q)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> quantities(String content) {
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = QUANTITY.matcher(clean(content));
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("[^0-9.]", "");
            if (!digits.isEmpty()) {
                out.add(digits);
            }
        }
        return out;
    }

    public static boolean withinMinutes(WeChatMessage from, WeChatMessage to, int minutes) {
        LocalDateTime a = parseTime(from == null ? null : from.getCreateTime());
        LocalDateTime b = parseTime(to == null ? null : to.getCreateTime());
        if (a == null || b == null || b.isBefore(a)) {
            return false;
        }
        return Duration.between(a, b).toMinutes() <= Math.max(0, minutes);
    }

    /** 消息时间是否在最近 N 分钟内；无法解析时返回 false，历史异常数据不触发预警。 */
    public static boolean isRecent(String createTime, int minutes) {
        if (minutes <= 0) {
            return true;
        }
        LocalDateTime t = parseTime(createTime);
        if (t == null) {
            return false;
        }
        return !t.isBefore(LocalDateTime.now().minusMinutes(minutes));
    }

    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\r', ' ').replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    public static boolean isSystemOrMedia(String content) {
        String lower = clean(content).toLowerCase(Locale.ROOT);
        return lower.startsWith("<?xml") || lower.startsWith("<msg") || lower.startsWith("<sysmsg")
                || lower.contains("<appmsg") || lower.contains("<img ") || lower.contains("<voipmsg")
                || lower.matches("^\\[[^]]{1,20}]$");
    }

    private static boolean hasPriceEvidence(String content) {
        return PRICE_VALUE.matcher(content).find();
    }

    private static boolean isShortPriceReply(String content) {
        String c = clean(content);
        if (c.isEmpty() || c.length() > 40) {
            return false;
        }
        if (hasPriceEvidence(c)) {
            return true;
        }
        return PURE_PRICE_REPLY.matcher(c).matches();
    }

    private static LocalDateTime parseTime(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String s = value.trim().replace('T', ' ');
        if (s.length() > 19) {
            s = s.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(s, FMT);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static String clean(String content) {
        if (content == null) {
            return "";
        }
        return content.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
