package com.ruoyi.crm.domain;

import java.math.BigDecimal;

import com.ruoyi.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRM 订单物料明细（对应 PO 模板明细表的 9 列）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmOrderItem extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 行号（NO.） */
    private Integer lineNo;

    /** Brand */
    private String brand;

    /** ITEM */
    private String itemName;

    /** Spec. */
    private String spec;

    /** QTY(pcs) */
    private BigDecimal qty;

    /** Warranty */
    private String warranty;

    /** U/P(USD) */
    private BigDecimal unitPrice;

    /** AMOUNT(USD) */
    private BigDecimal amount;

    /** Lead time */
    private String leadTime;
}
