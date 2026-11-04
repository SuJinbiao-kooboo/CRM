package com.ruoyi.crm.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 明确无库存/不可供货响应的统一识别。
 *
 * <p>“缺货、没货、no stock、unavailable”等只是对询价的否定答复，不属于 Offer。
 * 带有效价格的混合报价仍按 Offer 处理，由调用方过滤其中的无库存物料行。</p>
 */
public final class OfferNoStockUtils {

    private static final Pattern NO_STOCK = Pattern.compile(
            "(?i)(缺货|缺貨|没货|沒貨|无货|無貨|没有货|沒有貨|断货|斷貨|缺料|无料|無料|没料"
                    + "|没有库存|沒有庫存|无库存|無庫存|没库存|沒有現貨|没有现货|無現貨|无现货|没现货"
                    + "|暂无货|暫無貨|暂时无货|暫時無貨|暂时缺货|暫時缺貨|缺货中|缺貨中"
                    + "|没有供应|沒有供應|无供应|無供應|没有报价|沒有報價|无报价|無報價|暂不报价|暫不報價"
                    + "|没有这个型号|沒有這個型號|无此型号|無此型號|找不到|查不到"
                    + "|\\bnot\\s+in\\s+stock\\b|\\bno\\s+stock\\b|\\bout\\s*of\\s*stock\\b"
                    + "|\\bout-of-stock\\b|\\bsold\\s+out\\b|\\bunavailable\\b|\\bnot\\s+available\\b"
                    + "|\\bno\\s+supply\\b|\\bno\\s+offer\\b)");

    /** 单独回复“没/没有/无”等，也应视为对询价的无库存答复。 */
    private static final Pattern STANDALONE_NO = Pattern.compile(
            "^(?:(?:这个|这款|这些|那个|那款|那些|这|那|都|也|也都|目前|现在|暂时)*"
                    + "(?:没有货|沒有貨|没货|沒貨|无货|無貨|缺货|缺貨|没有|沒有|没|沒|无|無)"
                    + "(?:的|了|呢|哦|啊|呀|耶|哈|嘛)?|no|none)$");

    private static final Pattern PRICE = Pattern.compile(
            "(?i)(?:usd|us\\$|\\$|cny|rmb|eur|€|¥|￥)\\s*\\d+(?:\\.\\d+)?"
                    + "|\\d+(?:\\.\\d+)?\\s*(?:usd|us\\$|cny|rmb|eur|\\$|€|¥|￥)"
                    + "|(?:价格|價格|报价|報價|单价|單價)\\s*[:：]?\\s*\\d+(?:\\.\\d+)?");

    private OfferNoStockUtils() {
    }

    /** 文本中是否明确提到无库存/不可供货。 */
    public static boolean mentionsNoStock(String content) {
        String text = normalize(content);
        return StringUtils.isNotEmpty(text) && NO_STOCK.matcher(text).find();
    }

    /**
     * 是否是一条以无库存答复为主的消息。
     *
     * <p>带有效价格、或带问号的询价式表达不在此处强制改类，避免误伤混合报价和客户询价。</p>
     */
    public static boolean isNoStockResponse(String content) {
        String text = normalize(content);
        if (StringUtils.isEmpty(text)) {
            return false;
        }
        String stripped = text.replaceAll("[\\s。.!！,，;；:：~～]+", "");
        if (STANDALONE_NO.matcher(stripped).matches()) {
            return true;
        }
        if (!NO_STOCK.matcher(text).find()) {
            return false;
        }
        if (text.contains("?") || text.contains("？")
                || text.endsWith("吗") || text.endsWith("呢") || text.endsWith("嘛")) {
            return false;
        }
        return !PRICE.matcher(text).find();
    }

    private static String normalize(String content) {
        return StringUtils.trimToEmpty(content).replace('\u3000', ' ').toLowerCase(Locale.ROOT);
    }
}
