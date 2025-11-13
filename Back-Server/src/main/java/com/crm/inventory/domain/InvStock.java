package com.crm.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crm.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存信息实体类
 * 
 * @author crm
 * @date 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock")
public class InvStock extends BaseEntity {
    
    /** 库存ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /** 库存日期 */
    private Date stockDate;
    
    /** 料号 */
    private String productCode;
    
    /** 物料详情 */
    private String productDetail;
    
    /** 单价 */
    private BigDecimal price;
    
    /** 数量 */
    private Integer quantity;
    
    /** 交期 */
    private String deliveryTime;
    
    /** 备注 */
    private String remark;
    
    /** 工作表名称 */
    private String sheetName;
    
    /** 供应商 */
    private String supplier;
    
    /** 品牌 */
    private String brand;
    
    /** 标签 */
    private String tags;
    
    /** 状态 0-无效 1-有效 */
    private Integer status;

}