package com.crm.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_order_item")
public class CrmOrderItem extends BaseEntity {
    
    /** 明细ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 订单ID */
    private Long orderId;
    
    /** 产品料号 */
    private String productCode;
    
    /** 产品名称 */
    private String productName;
    
    /** 规格型号 */
    private String specification;
    
    /** 品牌 */
    private String brand;
    
    /** 数量 */
    private Integer quantity;
    
    /** 单价 */
    private BigDecimal unitPrice;
    
    /** 总价 */
    private BigDecimal totalPrice;
    
    /** 供应商ID */
    private Long supplierId;
    
    /** 交期 */
    private String deliveryTime;
    
    /** 备注信息 */
    private String remark;
}