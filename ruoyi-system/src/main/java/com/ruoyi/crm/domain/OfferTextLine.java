package com.ruoyi.crm.domain;

import lombok.Data;

/** 一键复制 Offer 与微信自动发送共用的单行文本数据。 */
@Data
public class OfferTextLine {
    private String partNumber;
    private Integer quantity;
    private String brand;
    private Double price;
    private String supplierCode;
    private String productDetail;
    private String deliveryTime;
    private String dc;
    private String spec;
    /** 人工审核后的完整行；非空时优先于结构化字段。 */
    private String rawLine;
}
