package com.crm.inventory.service;

import cn.hutool.core.date.DateUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.util.ListUtils;
import com.crm.inventory.domain.ImportStockDTO;
import com.crm.inventory.domain.InvStock;
import com.crm.inventory.mapper.InvStockMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class ImportStockListener implements ReadListener<ImportStockDTO> {

    private static final int BATCH_COUNT = 100;
    private List<InvStock> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
    private final InvStockMapper invStockMapper;
    private final Map<String, Integer> columnMapping;

    public ImportStockListener(InvStockMapper invStockMapper, Map<String, Integer> columnMapping) {
        this.invStockMapper = invStockMapper;
        this.columnMapping = columnMapping;
    }

    @Override
    public void invoke(ImportStockDTO data, AnalysisContext context) {
        InvStock stock = new InvStock();

        // 根据列映射设置字段值
        if (columnMapping.containsKey("stockDate")) {
            stock.setStockDate(DateUtil.parse(data.getStockDate()));
        }
        if (columnMapping.containsKey("productCode")) {
            stock.setProductCode(data.getMaterialNumber());
        }
        if (columnMapping.containsKey("productDetail")) {
            stock.setProductDetail(data.getMaterialDetails());
        }
        if (columnMapping.containsKey("price")) {
            stock.setPrice(data.getUnitPrice());
        }
        if (columnMapping.containsKey("quantity")) {
            stock.setQuantity(data.getQuantity());
        }
        if (columnMapping.containsKey("deliveryTime")) {
            stock.setDeliveryTime(data.getDeliveryDate());
        }
        if (columnMapping.containsKey("remark")) {
            stock.setRemark(data.getRemarks());
        }
        if (columnMapping.containsKey("supplier")) {
            stock.setSupplier(data.getSupplier());
        }
        if (columnMapping.containsKey("brand")) {
            stock.setBrand(data.getBrand());
        }
        
        // 设置默认值
        stock.setStatus(1);
        
        cachedDataList.add(stock);
        if (cachedDataList.size() >= BATCH_COUNT) {
            saveData();
            cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        saveData();
        log.info("Excel导入完成");
    }

    private void saveData() {
        if (!cachedDataList.isEmpty()) {
            invStockMapper.batchInsert(cachedDataList);
        }
    }
}