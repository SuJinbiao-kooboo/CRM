package com.ruoyi.email.service.util;

import java.util.List;

import com.ruoyi.common.utils.StringUtils;

/**
 * 邮件类型分析工具
 * <p>
 * 分类优先级：
 * <ol>
 *   <li>发件人邮箱过滤（字典 eke_email_filter_email_special，命中指定邮箱直接归类，如 OFFER/INQ）；</li>
 *   <li>主题关键词匹配（字典 eke_email_type_dict：value=类型编码、label=类型中文名、remark=逗号分隔关键词）；</li>
 *   <li>均未匹配时归为默认类型“未归属”。</li>
 * </ol>
 *
 * @author ruoyi
 */
public class EkeEmailTypeAnalyzer
{
    /** 默认类型编码：所有字典都未匹配时使用 */
    public static final String DEFAULT_TYPE_CODE = "UNKNOWN";

    /** 默认类型中文名称：所有字典都未匹配时的归属 */
    public static final String DEFAULT_TYPE_NAME = "未归属";

    /**
     * 分析邮件类型（发件人邮箱过滤优先，其次按主题匹配）
     * <p>
     * 优先级：1. 发件人邮箱过滤规则（如 OFFER/INQ 指定供应商/客户邮箱，模糊命中直接归类）；
     * 2. 主题关键词匹配；3. 均未匹配时归为默认类型“未归属”。
     *
     * @param subject 邮件主题（可空）
     * @param sender 发件人邮箱地址（可空）
     * @param typeRules 主题匹配规则（顺序即优先级，按 dict_sort 排列）
     * @param emailRules 发件人邮箱过滤规则（第一优先级，优先于主题匹配）
     * @return 匹配结果（类型编码 + 中文名称），所有规则都未匹配时为默认类型“未归属”
     */
    public static TypeResult analyze(String subject, String sender, List<TypeRule> typeRules, List<EmailRule> emailRules)
    {
        // 第一优先级：发件人邮箱过滤规则（模糊匹配），命中直接归类
        String send = StringUtils.lowerCase(StringUtils.trimToEmpty(sender));
        if (emailRules != null)
        {
            for (EmailRule rule : emailRules)
            {
                if (containsAny(send, rule.getEmails()))
                {
                    return new TypeResult(rule.getTypeCode(), rule.getTypeName());
                }
            }
        }
        // 第二优先级：主题关键词匹配
        String subj = StringUtils.lowerCase(StringUtils.trimToEmpty(subject));
        if (typeRules != null)
        {
            for (TypeRule rule : typeRules)
            {
                if (containsAny(subj, rule.getKeywords()))
                {
                    return new TypeResult(rule.getTypeCode(), rule.getTypeName());
                }
            }
        }
        return new TypeResult(DEFAULT_TYPE_CODE, DEFAULT_TYPE_NAME);
    }

    /**
     * 判断文本是否包含任意关键词（不区分大小写）
     * 中文与英文均按“包含”进行模糊匹配，兼容 offers/offering 等词形变化；文本与关键词统一转小写比较，
     * 因此字典关键词可自由使用大写/小写/混合大小写。
     */
    private static boolean containsAny(String text, List<String> keywords)
    {
        if (StringUtils.isEmpty(text) || keywords == null)
        {
            return false;
        }
        for (String keyword : keywords)
        {
            if (StringUtils.isEmpty(keyword))
            {
                continue;
            }
            if (text.contains(keyword.toLowerCase()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 邮件类型匹配规则（由字典数据构建）
     */
    public static class TypeRule
    {
        /** 类型编码（字典value） */
        private final String typeCode;

        /** 类型中文名称（字典label） */
        private final String typeName;

        /** 匹配关键词（字典remark按逗号分割） */
        private final List<String> keywords;

        public TypeRule(String typeCode, String typeName, List<String> keywords)
        {
            this.typeCode = typeCode;
            this.typeName = typeName;
            this.keywords = keywords;
        }

        public String getTypeCode()
        {
            return typeCode;
        }

        public String getTypeName()
        {
            return typeName;
        }

        public List<String> getKeywords()
        {
            return keywords;
        }
    }

    /**
     * 发件人邮箱过滤规则（由字典 eke_email_filter_email_special 构建）
     * <p>
     * 字典 value = 类型编码（与主题字典的 value 一致，如 OFFER/INQ），label = 类型中文名称，
     * remark = 逗号拼接的邮箱地址列表；发件人模糊匹配命中时直接归类为对应类型。
     */
    public static class EmailRule
    {
        /** 类型编码（字典value） */
        private final String typeCode;

        /** 类型中文名称（字典label） */
        private final String typeName;

        /** 邮箱地址列表（字典remark按逗号分割，已统一转小写） */
        private final List<String> emails;

        public EmailRule(String typeCode, String typeName, List<String> emails)
        {
            this.typeCode = typeCode;
            this.typeName = typeName;
            this.emails = emails;
        }

        public String getTypeCode()
        {
            return typeCode;
        }

        public String getTypeName()
        {
            return typeName;
        }

        public List<String> getEmails()
        {
            return emails;
        }
    }

    /**
     * 邮件类型匹配结果
     */
    public static class TypeResult
    {
        /** 类型编码 */
        private final String typeCode;

        /** 类型中文名称 */
        private final String typeName;

        public TypeResult(String typeCode, String typeName)
        {
            this.typeCode = typeCode;
            this.typeName = typeName;
        }

        public String getTypeCode()
        {
            return typeCode;
        }

        public String getTypeName()
        {
            return typeName;
        }
    }
}
