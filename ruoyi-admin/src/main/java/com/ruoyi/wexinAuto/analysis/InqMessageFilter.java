package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.analysis.rule.AllRule;
import com.ruoyi.wexinAuto.analysis.rule.AnyRule;
import com.ruoyi.wexinAuto.analysis.rule.KeywordRule;
import com.ruoyi.wexinAuto.analysis.rule.NotRule;
import com.ruoyi.wexinAuto.analysis.rule.QuantityInquiryRule;
import com.ruoyi.wexinAuto.analysis.rule.QuantityReplyRule;
import com.ruoyi.wexinAuto.analysis.rule.Rule;
import com.ruoyi.wexinAuto.analysis.rule.TargetPriceRule;
import com.ruoyi.wexinAuto.analysis.rule.WeakInquiryRule;

import java.util.Collections;
import java.util.List;

/**
 * INQ inquiry filter.
 *
 * <p>Rules (any hit = INQ):</p>
 * <ul>
 *   <li>dictionary keywords (wechat_analysis_config / inqKeywords / remark col)</li>
 *   <li>target price TP1234 (buyer target)</li>
 *   <li>quantity without price ("5317 10pcs", "9654 100个")</li>
 * </ul>
 * The OFFER chain runs first, so any message with real price evidence never
 * reaches this filter. Inventory-quantity answers are excluded explicitly.
 */
public class InqMessageFilter extends AbstractKeywordFilter {

    @Override
    public String type() {
        return WeChatMessageClassifier.INQ;
    }

    @Override
    protected String keywordLabel() {
        return "inqKeywords";
    }

    @Override
    protected List<Rule> buildRules() {
        return Collections.singletonList(AllRule.of(
                NotRule.of(new QuantityReplyRule()),
                AnyRule.of(
                        new KeywordRule(keywords()),
                        new TargetPriceRule(),
                        new QuantityInquiryRule(),
                        new WeakInquiryRule())));
    }
}