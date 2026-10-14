package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.regex.Pattern;

/**
 * 硬规则分类器：确定性极强的场景直接定类（零成本、绝不误判的优先）。
 *
 * <p>命中规则即返回 INQ/OFFER/TRACE/OTHER；返回 {@code null} 表示“灰色地带”，
 * 交给 {@link WeChatLlmClassifier} 精判，模型不可用时回退 {@link WeChatMessageClassifier}。</p>
 *
 * <p>设计原则：只在这里放“确定性高”的规则——收购广告、单字回复、XML/系统消息、
 * 目标价 TP、明确的询价词（无价格）、明确的出货+数量/价格（非问句）。
 * 出货清单尾部常见的“换群/没好友/+++”等加好友后缀不会盖过出货主体（交给 LLM 判断）。</p>
 */
public final class HardRuleClassifier {

    private HardRuleClassifier() {
    }

    /** 收购/求购广告特征词（命中即 OTHER，它们不是报价也不是询价跟进） */
    private static final String[] BUY_AD_WORDS = {
            "长期收", "高价收", "高价找", "实单收", "大量收购", "长期回收", "高价回收",
            "持续收货", "有货滴滴", "有货的", "有货来", "有货请", "有货直接", "有货麻烦",
            "没好友", "换群", "全国可飞", "收5090", "收4090", "求购收", "上门回收",
            "回收5090", "高价求购", "收DDR5", "实单求购"
    };

    /** 明确询价词（无价格证据时命中 INQ） */
    private static final String[] INQ_WORDS = {
            "谁有", "有货吗", "有offer吗", "有Offer吗", "有货没有", "询一下", "询价", "报个价",
            "报价多少", "什么价", "多少钱", "怎么卖", "给个价", "价格多少", "有没有", "能报", "还有吗",
            "pls chk", "pls check", "please check", "询个价", "求购", "找货", "收吗",
            "问下", "问一下", "查一下", "有什么", "给具体价格", "给个具体价格"
    };

    /** 目标价（TP=买方心理价，是 INQ 的强信号，优先于一切价格证据） */
    private static final Pattern TARGET_PRICE = Pattern.compile("TP\\s*\\d|目标价|目标TP|TP[:：]|开TP|实单.*TP");

    /** 出货清单动词（配合数量或价格 → OFFER） */
    private static final String[] SELL_WORDS = {
            "现货出", "现货", "库存清单", "库存", "在仓", "好价出", "特价出", "出全新", "出货",
            "源头出", "源头供应", "工厂出", "大量到货", "到货", "出香港", "出深圳", "出现货",
            "一手现货", "含税出", "未税出", "换现出", "甩", "便宜处理", "价格非常美丽", "好价",
            "供应现货", "批发", "历史低价", "清仓", "出货清单", "套现出", "底价出", "出点", "到货"
    };

    /** 内容开头(前12字)出现这些词 → 视为“出货主体”，收购尾缀不盖过它 */
    private static final String[] SELL_LEAD_WORDS = {
            "现货出", "出现货", "出出出", "好价出", "特价出", "低价出", "含税出", "换现出",
            "底价出", "套现出", "现货", "库存清", "在仓", "出:", "出："
    };

    private static final Pattern QUANTITY = Pattern.compile("\\d+\\s*(pcs|PCS|片|张|条|个|台|根|颗|p|P)\\b");
    private static final Pattern PRICE = Pattern.compile("[$＄￥¥]|USD|usd|RMB|rmb|CNY|HKD|美元|港币|人民币|\\d+[wW万]");
    private static final Pattern QUESTION = Pattern.compile("[?？]|吗|么|多少|怎么样|咋样");

    /**
     * 硬判定；无法确定返回 null。
     */
    public static String classify(WeChatMessage m) {
        if (m == null) {
            return null;
        }
        String type = m.getType() == null ? "" : m.getType().trim();
        String content = m.getContent() == null ? "" : m.getContent().trim();

        // 1. 明确的媒体/系统消息（图片/语音/视频/文件/系统）→ OTHER；
        //    “文件/链接/卡片”可能是带文本报价的卡片，不在此列，继续按文本判断
        if (type.contains("图片") || type.contains("语音") || type.contains("视频")
                || "文件".equals(type) || type.contains("系统")) {
            return WeChatMessageClassifier.OTHER;
        }
        // 2. XML / 系统模板原文 → OTHER
        if (content.startsWith("<msg") || content.startsWith("<sysmsg") || content.startsWith("<?xml")) {
            return WeChatMessageClassifier.OTHER;
        }
        // 3. 单字/极短回复且无数字（好/没/早/收到/ok 等）→ OTHER
        if (content.length() <= 2 && !content.matches(".*\\d.*")) {
            return WeChatMessageClassifier.OTHER;
        }
        // 4. 收购/求购广告 → OTHER；但“出货主体+尾部加好友后缀”不归为收购（交给 LLM）
        if (containsAny(content, BUY_AD_WORDS) && !sellLeading(content)) {
            return WeChatMessageClassifier.OTHER;
        }
        // 5. 目标价 TP → INQ（强信号，优先于价格证据）
        if (TARGET_PRICE.matcher(content).find()) {
            return WeChatMessageClassifier.INQ;
        }
        boolean hasPrice = PRICE.matcher(content).find();
        // 6. 明确询价（无价格证据）→ INQ
        if (!hasPrice && containsAny(content, INQ_WORDS)) {
            return WeChatMessageClassifier.INQ;
        }
        // 7. 明确出货（含价格或数量，且非问句）→ OFFER
        if (!QUESTION.matcher(content).find() && containsAny(content, SELL_WORDS)
                && (hasPrice || QUANTITY.matcher(content).find())) {
            return WeChatMessageClassifier.OFFER;
        }
        return null;
    }

    /** 文本开头是否“出货主体”（前 12 字出现出货动词，或以“出”字开头） */
    private static boolean sellLeading(String content) {
        String t = content.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.startsWith("出")) {
            return true;
        }
        String head = t.substring(0, Math.min(t.length(), 12));
        for (String w : SELL_LEAD_WORDS) {
            if (head.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}