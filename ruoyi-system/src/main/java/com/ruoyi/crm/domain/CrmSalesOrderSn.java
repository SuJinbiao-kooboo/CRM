package com.ruoyi.crm.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * CRM 销售订单 SN
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrmSalesOrderSn extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long orderId;
    private String orderNo;
    private String pn;
    private String poMeNo;
    private String piMeNo;
    private String snNo;
    private String cartonNo;
}
