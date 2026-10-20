package com.ruoyi.crm.domain;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRM 订单主表（PO/PI/装箱单/委托单/SN List/CI 等单据的公共主表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 订单编号，如 PO20261020-001 */
    @Excel(name = "订单编号")
    private String orderNo;

    /** 单据类型：PO/PI/PL/ENTRUST/SN/CI */
    @Excel(name = "单据类型")
    private String orderType;

    /** 订单日期（Order Date） */
    @Excel(name = "订单日期", width = 30, dateFormat = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date orderDate;

    /** 供应商ID（crm_supplier.id） */
    private Long sellerId;

    /** 供应商联系人ID */
    private Long sellerContactId;

    /** 供应商信息块文本（Seller 区域，可人工编辑后生成） */
    private String sellerSnapshot;

    /** 买方ID */
    private Long buyerId;

    /** 买方信息块文本 */
    private String buyerSnapshot;

    /** 币种 */
    @Excel(name = "币种")
    private String currency;

    /** 合计金额 */
    @Excel(name = "合计金额")
    private BigDecimal totalAmount;

    /** 状态：0草稿 1已生成 2作废 */
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已生成,2=作废")
    private String status;

    /** 供应商名称（列表展示用，联表查询） */
    private String sellerName;

    /** 物料明细 */
    private List<CrmOrderItem> items;

    /** 单据生成时的模板标识（默认按 orderType 取），可选 */
    private String templateKey;
}
