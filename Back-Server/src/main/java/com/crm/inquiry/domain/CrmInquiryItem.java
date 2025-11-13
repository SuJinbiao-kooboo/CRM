package com.crm.inquiry.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 询价明细实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_inquiry_item")
public class CrmInquiryItem extends BaseEntity {
    
    /** 明细ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 询价ID */
    private Long inquiryId;
    
    /** 产品料号 */
    private String productCode;
    
    /** 产品名称 */
    private String productName;
    
    /** 规格 */
    private String specification;
    
    /** 品牌 */
    private String brand;
    
    /** 数量 */
    private Integer quantity;
    
    /** 目标价格 */
    private BigDecimal targetPrice;
    
    /** 交期要求 */
    private String deliveryRequirement;
    
    /** 质量要求 */
    private String qualityRequirement;
    
    /** 备注 */
    private String remark;
}