package com.ruoyi.email.domain.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 邮件分类汇总 DTO（接口返回 categories 数组元素）
 * <p>
 * 每个字典类型对应一个分类，未匹配任何字典的邮件归入“未归属”分类并排在最后；
 * 分类下的邮件明细包含发件人、主题。
 *
 * @author ruoyi
 */
@Data
public class EkeEmailCategoryDTO
{
    /** 类型编码（字典value；未归属为 UNKNOWN） */
    private String typeCode;

    /** 类型中文名称（字典label；未归属为“未归属”） */
    private String typeName;

    /** 该分类下的邮件数量 */
    private int count;

    /** 邮件明细列表（每项包含 sender 发件人地址、senderName 发件人名称、subject 邮件主题） */
    private List<Map<String, Object>> mails;
}
