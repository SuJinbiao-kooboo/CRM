package com.ruoyi.crm.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRM 销售订单（订单需求管理）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrmSalesOrder extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date orderDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date bookingDate;

    private String bookingMonth;
    private String cooperationStatus;
    private String pn;
    private String desp;
    private String productTypes;
    private String status;
    private BigDecimal quantity;

    private Long customerId;
    private String customerPoNo;
    private String piMeNo;
    private String customerPiNo;
    private BigDecimal salePrice;
    private BigDecimal salesAmount;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date paymentReceivedDate;

    private Long supplierId;
    private String poMeNo;
    private String supplierPiNo;
    private BigDecimal costPrice;
    private BigDecimal purchaseAmount;
    private BigDecimal priceDiff;
    private BigDecimal gp;
    private BigDecimal profitRate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date paymentDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date arrivalDate;

    private BigDecimal logisticsFee;
    private String cartonSpec;
    private BigDecimal grossWeight;
    private String signReceiptFile;
    private String photoFile;
    private String remark;
    private String logisticsReconciliation;

    private Long customerContactId;
    private Long supplierContactId;

    private String poCustomerFile;
    private String piOurFile;
    private String poOurFile;
    private String piSupplierFile;
    private String plSupplierFile;
    private String plOurFile;
    private String ciSupplierFile;
    private String ciOurFile;
    private String entrustFile;
    private String snFile;

    private String customerName;
    private String supplierName;
    private String customerContactName;
    private String supplierContactName;

    /** 数据库附件数量（列表展示用，不落库） */
    private Integer attachmentCount;
}
