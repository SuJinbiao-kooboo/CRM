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
 * <p>业务口径：INQ/OFFER 不按发送方区分。我方、客户或供应商都可以询价或报价；
 * INQ 表示提出询价，OFFER 表示对向回复具体价格/库存/交期，TRACE 表示围绕已有
 * INQ/OFFER 的后续业务沟通（可以是疑问句，也可以是价格确认、数量调整、下单安排等）。
 * 无上下文的群发广告、内部闲聊统一降级为 OTHER。</p>
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
            "GOOD", "OK", "YES", "NO", "PLEASE", "CHECK", "THANKS",
            "2400", "2666", "2933", "3200", "4400", "4800", "5200", "5600", "6000", "6400", "7200"
    ));

    /** 明确的询价意图。只有这些词或“型号/数量”组合才允许进入 INQ。 */
    private static final Pattern INQ_STRONG = Pattern.compile(
            "(?i)(\\bINQ\\b|\\bRFQ\\b|询价|求购|找货|谁有|报个价|报来|来报|请报价|什么价|多少钱|" +
                    "价格多少|报价多少|目标价|有货吗|有没有货|有没有|是否有|有现货吗|现货吗|" +
                    "问一下|问下|报多少|给个价|啥价|底价|能给.*价|可以.*报价|预算价|期望价|" +
                    "\\b(?:quote|quotation|price please|best price)\\b)"
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

    /** 卖方群发/收购广播的典型开头；只拦截明确的广告句式，避免把“现货 24pcs 660USD”这类正常回价误伤。 */
    private static final Pattern BROADCAST_START = Pattern.compile(
            "^(?:[\\p{So}\\p{Sk}\\s]*)(?:" +
                    "(?:今日|本周|周[一二三四五六日天])?(?:出(?:货|售|[:：\\s]|\\d)|" +
                    "现货(?:出|出售|直出|供应|速览|清单|报价))|" +
                    "(?:含税|不含税|税前|税后|特价|清仓|公司|仓库)出(?:货|售|[:：\\s]|\\d)|" +
                    "长期(?:收|收购)|(?:高价|大量)收(?:购)?|收购|回收)"
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

    /** TRACE 不一定带问号，但必须有明确的交易延续动作，避免把普通陈述误判为跟进。 */
    private static final Pattern TRACE_CONTINUATION_CUE = Pattern.compile(
            "(?i)(就按|按这个|按刚才|按.*安排|这个价(?:格)?(?:可以|行|合适|能接受)?|价格(?:就)?按|可以接受|" +
                    "能做|可以做|那就定|定了|" +
                    "确定要|要了|安排上|安排发货|备货|留货|锁货|付款|打款|开票|" +
                    "明天(?:确认|回复|给)|稍后(?:确认|回复)|商量一下|申请一下|" +
                    "再让点|再优惠|再降|加单|减单|数量改成|数量改为|改成|改为|" +
                    "确认(?:价格|数量|交期|订单)|交期(?:提前|延后|改|按)|库存(?:还有|没了|有)|" +
                    "\\b(?:proceed|go ahead|place(?: the)? order|book(?: it)?|reserve|confirm(?:ed)?|" +
                    "accept(?:ed)?|deal|better price|discount|reduce|increase(?: quantity)?|" +
                    "decrease(?: quantity)?|change quantity)\\b)"
    );

    /** 单独一个“可以/行/成交”只有在引用或上下文能锁定具体交易时才可视为 TRACE。 */
    private static final Pattern GENERIC_ACCEPTANCE = Pattern.compile(
            "(?i)(?:^|[\\s，,。.!！])(?:可以|行|没问题|成交|ok|okay)(?:[\\s，,。.!！]|$)"
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

    /** 是否是明确的业务疑问句。 */
    public static boolean looksLikeQuestion(String content) {
        String c = clean(content);
        if (c.isEmpty()) {
            return false;
        }
        return c.contains("?") || c.contains("？")
                || QUESTION_CUE.matcher(c).find()
                || SHORT_QUESTION_TERM.matcher(c).matches();
    }

    /** 是否具备询价意图（买卖双方均适用）；纯数量、纯数字、广播报价、系统消息均返回 false。 */
    public static boolean looksLikeInquiry(String content) {
        String c = clean(content);
        if (c.isEmpty() || isSystemOrMedia(c)) {
            return false;
        }
        // 明显以“出货/收购”开头的广告即使末尾带“含税/有货吗”，也不能反判为询价。
        if (looksLikeBroadcast(c)) {
            return false;
        }
        if (INQ_STRONG.matcher(c).find()) {
            return true;
        }
        // 型号/数量+完整价格通常是报价陈述；没有问价或目标价语义时不能反向当成 INQ。
        if (hasPriceEvidence(c) && !looksLikeQuestion(c)) {
            return false;
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
     * 判断一条 OFFER 是否是对指定 INQ 的直接回应。询价和报价可由任意一方发出，
     * 但必须是不同发送人对具体询价的对向回应。
     *
     * @param offer          待判断的 OFFER
     * @param inq            对应的 INQ（可以是任一方发出）
     * @param groupChat      是否群聊
     * @param maxAgeMin      最大关联分钟数
     * @param shortWindowMin 纯数字/短报价的允许窗口
     */
    public static boolean isReplyTo(WeChatMessage offer, WeChatMessage inq,
                                    boolean groupChat, int maxAgeMin, int shortWindowMin) {
        if (offer == null || inq == null || StringUtils.isBlank(offer.getContent())
                || StringUtils.isBlank(inq.getContent()) || sameSender(offer, inq)) {
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
        boolean productLinked = sharesProduct(offerContent, inqContent);
        if (productLinked && hasPriceEvidence(offerContent)) {
            return true;
        }
        // 报价里明确写了另一个料号时，不能仅凭数量相同就挂到本条INQ上。
        boolean offerNamesProduct = !tokens(offerContent).isEmpty();
        // 群聊噪声大：仅数量相同不足以证明回复关系，还必须在短回复窗口内且带完整价格。
        if (!offerNamesProduct && sharesQuantity(offerContent, inqContent) && hasPriceEvidence(offerContent)
                && (!groupChat || withinMinutes(inq, offer, shortWindow))) {
            return true;
        }
        // 没有料号、没有引用时，只接受紧邻时间窗内的纯价格回复；其余按广播排除。
        return !offerNamesProduct && isShortPriceReply(offerContent)
                && withinMinutes(inq, offer, shortWindow);
    }

    /** 判断一条消息是否围绕指定 INQ/OFFER 延续业务沟通；不强制为疑问句。 */
    public static boolean isFollowUpTo(WeChatMessage trace, WeChatMessage ref, boolean groupChat,
                                       int maxAgeMin, int shortWindowMin) {
        if (trace == null || ref == null || StringUtils.isBlank(trace.getContent())
                || StringUtils.isBlank(ref.getContent())) {
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
        boolean question = looksLikeQuestion(traceText);
        boolean businessCue = FOLLOW_UP_CUE.matcher(traceText).find();
        boolean genericAcceptance = GENERIC_ACCEPTANCE.matcher(traceText).find();
        boolean continuation = TRACE_CONTINUATION_CUE.matcher(traceText).find() || genericAcceptance;
        boolean productLinked = sharesProduct(traceText, refText);
        boolean quantityLinked = sharesQuantity(traceText, refText);
        boolean contextLinked = productLinked || quantityLinked;
        // 普通寒暄即使带了问号、引用了报价，也必须出现交易要素或明确的推进动作。
        if (!contextLinked && !businessCue && !continuation) {
            return false;
        }
        boolean quoteLinked = false;
        if (!quote.isEmpty()) {
            quoteLinked = sharesProduct(quote, refText)
                    || (refText.length() >= 4 && (quote.contains(refText) || refText.contains(quote)));
            if (quoteLinked && (contextLinked || businessCue || continuation)) {
                return true;
            }
        }
        // “可以/行”本身不是业务信息；只有引用、料号或数量能锁定原交易时才成立。
        if (genericAcceptance && !contextLinked && !quoteLinked) {
            return false;
        }
        if (contextLinked && (businessCue || continuation || question)) {
            return withinMinutes(ref, trace, question ? maxAge : shortWindow);
        }
        if (!question && !continuation) {
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

    private static boolean sameSender(WeChatMessage first, WeChatMessage second) {
        String a = normalizeName(first == null ? null : first.getSender());
        String b = normalizeName(second == null ? null : second.getSender());
        // 无法确认发送人不同则拒绝关联，避免缺 sender 的历史数据生成伪 OFFER。
        return a.isEmpty() || b.isEmpty() || a.equals(b);
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
            if (token.matches("(?:19|20)\\d{2}") || token.matches("\\d{1,3}")) {
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
