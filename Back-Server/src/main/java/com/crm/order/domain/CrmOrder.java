package com.crm.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单信息实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_order")
public class CrmOrder extends BaseEntity {
    
    /** 订单ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 订单编号 */
    private String orderCode;
    
    /** 客户ID */
    private Long customerId;
    
    /** 供应商ID */
    private Long supplierId;
    
    /** 订单日期 */
    private Date orderDate;
    
    /** 供应商发货日期 */
    private Date supplierShipDate;
    
    /** 供应商到货日期 */
    private Date supplierArrivalDate;
    
    /** 我方发货日期 */
    private Date ourShipDate;
    
    /** 客户收货日期 */
    private Date customerReceiveDate;
    
    /** 交付日期 */
    private Date deliveryDate;
    
    /** 订单状态 */
    private String orderStatus;
    
    /** 订单类型 */
    private String orderType;
    
    /** 紧急程度 */
    private String urgencyLevel;
    
    /** 业务员 */
    private Long salesman;
    
    /** 订单总金额 */
    private BigDecimal totalAmount;
    
    /** 利润 */
    private BigDecimal profit;
    
    /** 利润率 */
    private BigDecimal profitRate;
    
    /** 付款方式 */
    private String paymentMethod;
    
    /** 物流方式 */
    private String logisticsMethod;
    
    /** 收货地址 */
    private String deliveryAddress;
    
    /** 特殊要求 */
    private String specialRequirements;
}
