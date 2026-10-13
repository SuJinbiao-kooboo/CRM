package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.analysis.rule.AllRule;
import com.ruoyi.wexinAuto.analysis.rule.AnyRule;
import com.ruoyi.wexinAuto.analysis.rule.HistoryRule;
import com.ruoyi.wexinAuto.analysis.rule.KeywordRule;
import com.ruoyi.wexinAuto.analysis.rule.Rule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * TRACE follow-up filter.
 *
 * <p>Rules (any hit = TRACE):</p>
 * <ul>
 *   <li>dictionary keywords (wechat_analysis_config / traceKeywords / remark col)</li>
 *   <li>strong follow-up words that need no context (数量有多少 / 有多少 / 数量多少)</li>
 *   <li>weak follow-up words (更新/怎么样/考虑/确认/优惠/货期/时候/方便...) only when
 *       one of the last 6 messages is INQ or OFFER (keeps everyday chat out)</li>
 * </ul>
 */
public class TraceMessageFilter extends AbstractKeywordFilter {

    private static final String[] TRACE_STRONG = {
            "\u6570\u91cf\u6709\u591a\u5c11", "\u6709\u591a\u5c11", "\u6570\u91cf\u591a\u5c11"
    };

    private static final String[] TRACE_WEAK = {
            "\u66f4\u65b0", "\u600e\u4e48\u6837", "\u8003\u8651", "\u786e\u8ba4", "\u5b9a\u4e86\u5417",
            "\u4f18\u60e0", "\u8d27\u671f", "\u6392\u5355", "\u8fdb\u5ea6", "\u518d\u62a5",
            "\u91cd\u65b0\u62a5", "\u6709\u8d27", "\u65b9\u4fbf", "\u65f6\u5019"
    };

    @Override
    public String type() {
        return WeChatMessageClassifier.TRACE;
    }

    @Override
    protected String keywordLabel() {
        return "traceKeywords";
    }

    @Override
    protected List<Rule> buildRules() {
        return Collections.singletonList(AnyRule.of(
                new KeywordRule(keywords()),
                new KeywordRule(KeywordRule.FIELD_CONTENT, Arrays.asList(TRACE_STRONG)),
                AllRule.of(
                        new KeywordRule(KeywordRule.FIELD_CONTENT, Arrays.asList(TRACE_WEAK)),
                        HistoryRule.anyOfLast(6, WeChatMessageClassifier.INQ, WeChatMessageClassifier.OFFER))));
    }
}