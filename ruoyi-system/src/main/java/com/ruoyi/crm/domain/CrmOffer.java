package com.ruoyi.crm.domain;

import java.util.Date;

import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

@Data
public class CrmOffer extends BaseEntity {
    @Excel(name = "主键ID", cellType = ColumnType.NUMERIC)
    private Long id;
    @Excel(name = "产品编码")
    private String productCode;
    @Excel(name = "产品品牌")
    private String productBrand;
    @Excel(name = "库存日期")
    private Date stockDate;
    @Excel(name = "产品详情")
    private String productDetail;
    @Excel(name = "单价-成本")
    private Double priceCost;
    @Excel(name = "单价-报价")
    private Double priceOffer;
    @Excel(name = "价格单位")
    private String priceUnit;
    @Excel(name = "数量")
    private Integer quantity;
    @Excel(name = "交货时间")
    private String deliveryTime;
    @Excel(name = "备注")
    private String remark;
    @Excel(name = "来源表名")
    private String sheetName;
    @Excel(name = "MOQ数量")
    private Integer moqQuantity;
    @Excel(name = "质保详情")
    private String warrantyDetail;
    @Excel(name = "DC")
    private String dc;
    @Excel(name = "供应商编码")
    private String supplierCode;
    @Excel(name = "供应商名称")
    private String supplierName;
    @Excel(name = "标签1")
    private String tagsFirst;
    @Excel(name = "标签2")
    private String tagsSecond;
    @Excel(name = "标签3")
    private String tagsThird;
    @Excel(name = "标签4")
    private String tagsSi;
    @Excel(name = "状态", readConverterExp = "0=无效,1=有效")
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    @Excel(name = "产品类型")
    private String productType;
    @Excel(name = "产品明细编号")
    private String productDetailCode;
    @Excel(name = "询报价类型")
    private String inqOfferType;

      @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("productCode", getProductCode())
                .append("productBrand", getProductBrand())
                .append("stockDate", getStockDate())
                .append("productDetail", getProductDetail())
                .append("priceCost", getPriceCost())
                .append("priceOffer", getPriceOffer())
                .append("priceUnit", getPriceUnit())
                .append("quantity", getQuantity())
                .append("deliveryTime", getDeliveryTime())
                .append("remark", getRemark())
                .append("sheetName", getSheetName())
                .append("moqQuantity", getMoqQuantity())
                .append("warrantyDetail", getWarrantyDetail())
                .append("dc", getDc())
                .append("supplierCode", getSupplierCode())
                .append("supplierName", getSupplierName())
                .append("tagsFirst", getTagsFirst())
                .append("tagsSecond", getTagsSecond())
                .append("tagsThird", getTagsThird())
                .append("tagsSi", getTagsSi())
                .append("status", getStatus())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("productType", getProductType())
                .append("productDetailCode", getProductDetailCode())
                .append("inqOfferType", getInqOfferType())
                .toString();
    }
}
