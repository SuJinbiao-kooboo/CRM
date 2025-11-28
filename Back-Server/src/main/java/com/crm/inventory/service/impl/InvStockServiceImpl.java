package com.crm.inventory.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crm.inventory.domain.ImportStockDTO;
import com.crm.inventory.domain.InvStock;
import com.crm.inventory.mapper.InvStockMapper;
import com.crm.inventory.service.IInvStockService;
import com.crm.inventory.service.ImportStockListener;
import com.crm.lead.domain.CrmLead;
import com.crm.lead.mapper.CrmLeadMapper;
import com.crm.lead.service.ICrmLeadService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class InvStockServiceImpl  extends ServiceImpl<InvStockMapper, InvStock> implements IInvStockService {


    @Override
    public List<InvStock> selectInvStockList(InvStock invStock) {
        return baseMapper.selectInvStockList(invStock);
    }

    @Override
    public String importStock(MultipartFile file, Map<String, Integer> columnMapping) {
        try {
            EasyExcel.read(file.getInputStream(), ImportStockDTO.class, 
                new ImportStockListener(baseMapper, columnMapping))
                .sheet()
                .doRead();
            return "导入成功";
        } catch (IOException e) {
            e.printStackTrace();
            return "导入失败: " + e.getMessage();
        }
    }

    @Override
    public boolean batchUpdate(List<Long> ids, Map<String, Object> updateFields) {
        if (ids == null || ids.isEmpty() || updateFields == null || updateFields.isEmpty()) {
            return false;
        }
        
        try {
            // 创建更新对象
            InvStock updateEntity = new InvStock();
            
            // 设置更新时间
            updateEntity.setUpdateTime(new Date());
            
            // 根据字段名设置对应的属性值
            for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                
                // 跳过空值
                if (value == null || value.toString().trim().isEmpty()) {
                    continue;
                }
                
                switch (fieldName) {
                    case "productDetail":
                        updateEntity.setProductDetail((String) value);
                        break;
                    case "price":
                        BigDecimal price = NumberUtil.toBigDecimal(value.toString());
                        if(BigDecimal.ZERO.compareTo(price) != 0) {
                            updateEntity.setPrice(price);
                        }
                        break;
                    case "quantity":
                        BigDecimal quantity = NumberUtil.toBigDecimal(value.toString());
                        if(BigDecimal.ZERO.compareTo(quantity) != 0) {
                            updateEntity.setQuantity(quantity.intValue());
                        }
                        break;
                    case "deliveryTime":
                        updateEntity.setDeliveryTime((String) value);
                        break;
                    case "supplier":
                        updateEntity.setSupplier((String) value);
                        break;
                    case "brand":
                        updateEntity.setBrand((String) value);
                        break;
                    case "productType":
                        updateEntity.setProductType((String) value);
                        break;
                    case "productDetailCode":
                        updateEntity.setProductDetailCode((String) value);
                        break;
                    case "inqOfferType":
                        updateEntity.setInqOfferType((String) value);
                        break;
                    case "remark":
                        updateEntity.setRemark((String) value);
                        break;
                    default:
                        // 忽略未知字段
                        break;
                }
            }
            
            // 检查是否有实际要更新的字段
            if (updateEntity.getProductDetail() == null && 
                updateEntity.getPrice() == null && 
                updateEntity.getQuantity() == null && 
                updateEntity.getDeliveryTime() == null && 
                updateEntity.getSupplier() == null && 
                updateEntity.getBrand() == null && 
                updateEntity.getProductType() == null && 
                updateEntity.getProductDetailCode() == null && 
                updateEntity.getInqOfferType() == null && 
                updateEntity.getRemark() == null) {
                throw new IllegalArgumentException("请至少填写一个有效的要更新的字段");
            }
            
            // 执行批量更新
            return baseMapper.batchUpdate(ids, updateEntity) > 0;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("批量更新失败: " + e.getMessage(), e);
        }
    }
}