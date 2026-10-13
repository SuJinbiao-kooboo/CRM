package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.analysis.rule.KeywordRule;
import com.ruoyi.wexinAuto.analysis.rule.Rule;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 类型过滤器基类（规则链骨架）。
 *
 * <p>默认规则 = “字典关键词”单条规则：dict_type={@code wechat_analysis_config} 下，
 * 以 {@link #keywordLabel()} 作为 dict_label 的那一条，关键词写在【备注(remark)】列，
 * 多个关键词用英文逗号分隔；改字典后刷新缓存即可生效，无需重启。</p>
 *
 * <p><b>关键词只是其中一种规则。</b> 复杂规则（群名、发言人、上一条/前 N 条历史等）
 * 在子类里重写 {@link #buildRules()} 自由组合，例如：</p>
 * <pre>
 * return Arrays.asList(AnyRule.of(
 *         new KeywordRule(keywords()),
 *         AllRule.of(
 *                 new SenderRule("May", "Jasmine"),
 *                 HistoryRule.previousIs(WeChatMessageClassifier.OFFER))));
 * </pre>
 * 表示：命中字典关键词，或者（发言人是 May/Jasmine 且上一条是 OFFER）。
 */
public abstract class AbstractKeywordFilter implements MessageTypeFilter {

    /** 消息分析参数数据字典类型 */
    protected static final String DICT_TYPE = "wechat_analysis_config";

    /** 本类型在字典里的参数名（dict_label），关键词放备注(remark)列，逗号分隔 */
    protected abstract String keywordLabel();

    @Override
    public final boolean matches(MessageContext ctx) {
        if (ctx == null || ctx.getCurrent() == null) {
            return false;
        }
        List<Rule> rules = buildRules();
        if (rules.isEmpty()) {
            return false;
        }
        for (Rule r : rules) {
            if (!r.test(ctx)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 组装本类型的规则链（默认 AND 语义：所有规则都命中才算命中；空链=不命中）。
     * 想改成“任一命中即命中”，返回一个 {@code AnyRule.of(...)} 的列表即可。
     */
    protected List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<>();
        rules.add(new KeywordRule(keywords()));
        return rules;
    }

    /** 读取本类型的关键词列表（字典 remark 列，逗号分隔，去空白、去空项）。 */
    protected Set<String> keywords() {
        Set<String> keys = new HashSet<>();
        List<SysDictData> list = DictUtils.getDictCache(DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (keywordLabel().equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getRemark())) {
                    for (String k : d.getRemark().split(",")) {
                        if (StringUtils.isNotBlank(k)) {
                            keys.add(k.trim());
                        }
                    }
                }
            }
        }
        return keys;
    }

    protected static String nvl(String s) {
        return s == null ? "" : s;
    }
}
