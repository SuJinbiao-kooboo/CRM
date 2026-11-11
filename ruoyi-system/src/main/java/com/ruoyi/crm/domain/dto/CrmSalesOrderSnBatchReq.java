package com.ruoyi.crm.domain.dto;

import java.util.List;

import lombok.Data;

/**
 * SN 批量录入请求
 */
@Data
public class CrmSalesOrderSnBatchReq {
    private Long orderId;
    private String cartonNo;
    private List<String> sns;
}
