package com.ruoyi.crm.domain.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 单据生成请求
 */
@Data
public class DocGenerateReq {
    /** 单据类型：PO/PI/CI/PL/SN/ENTRUST */
    private String docType;
    /** 单据编号 */
    private String orderNo;
    /** 日期 yyyy-MM-dd */
    private String dateText;
    /** 供应商ID */
    private Long sellerId;
    /** Seller 信息块 */
    private String sellerText;
    /** 买方ID */
    private Long buyerId;
    /** Buyer 信息块 */
    private String buyerText;
    /** SN 文本（每行一个） */
    private String snText;
    /** 装箱单 SHIP TO */
    private String shipTo;
    /** 客户 PO 号（用于装箱单文件名） */
    private String customerPoNo;
    /** 其他类型专用字段 */
    private Map<String, String> extras;
    /** 明细 */
    private List<Map<String, Object>> items;
}
