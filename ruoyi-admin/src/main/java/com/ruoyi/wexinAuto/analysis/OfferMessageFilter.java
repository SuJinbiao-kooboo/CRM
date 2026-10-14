package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.analysis.rule.AllRule;
import com.ruoyi.wexinAuto.analysis.rule.AnyRule;
import com.ruoyi.wexinAuto.analysis.rule.NeutralRule;
import com.ruoyi.wexinAuto.analysis.rule.NotRule;
import com.ruoyi.wexinAuto.analysis.rule.PriceEvidenceRule;
import com.ruoyi.wexinAuto.analysis.rule.PriceReplyRule;
import com.ruoyi.wexinAuto.analysis.rule.QuotePriceReplyRule;
import com.ruoyi.wexinAuto.analysis.rule.QuantityReplyRule;
import com.ruoyi.wexinAuto.analysis.rule.ReplySupplementRule;
import com.ruoyi.wexinAuto.analysis.rule.Rule;

import java.util.Collections;
import java.util.List;

/**
 * OFFER offer filter (runs first in the classifier chain).
 *
 * <p>Rules (any hit = OFFER):</p>
 * <ul>
 *   <li>price evidence in content (currency / quotation row / model+trailing number)</li>
 *   <li>quoted reply with price ("这个950")</li>
 *   <li>pure price reply to a previous INQ ("2900")</li>
 *   <li>short supplement right after an OFFER ("1wk")</li>
 * </ul>
 * Neutral words (收到/没/check...) and inventory-quantity answers never count.
 */
public class OfferMessageFilter extends AbstractKeywordFilter {

    @Override
    public String type() {
        return WeChatMessageClassifier.OFFER;
    }

    @Override
    protected String keywordLabel() {
        return "offerKeywords";
    }

    @Override
    protected List<Rule> buildRules() {
        return Collections.singletonList(AllRule.of(
                NotRule.of(new NeutralRule()),
                NotRule.of(new QuantityReplyRule()),
                AnyRule.of(
                        new PriceEvidenceRule(),
                        new QuotePriceReplyRule(),
                        new PriceReplyRule(),
                        new ReplySupplementRule())));
    }
}