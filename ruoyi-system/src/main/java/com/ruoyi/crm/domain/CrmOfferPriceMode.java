package com.ruoyi.crm.domain;

/** AI 录入的价格处理模式。 */
public enum CrmOfferPriceMode {
    /** 成本价按供应商利润率生成对外报价。 */
    COST_PLUS_PROFIT,
    /** 原文价格就是已发出报价，原样归档，不二次加价。 */
    AS_IS,
    /** INQ，不生成对外报价。 */
    NONE
}
