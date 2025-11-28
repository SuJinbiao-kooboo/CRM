package com.crm.inventory.controller;

import com.alibaba.excel.EasyExcel;
import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.common.core.page.TableDataInfo;
import com.crm.inventory.domain.InvStock;
import com.crm.inventory.service.IInvStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.*;

@RestController
@RequestMapping("/inventory/stock")
public class InvStockController extends BaseController {

    @Autowired
    private IInvStockService invStockService;

    /**
     * 查询库存信息列表
     */
    @GetMapping("/list")
    public TableDataInfo list(InvStock invStock) {
        startPage();
        List<InvStock> list = invStockService.selectInvStockList(invStock);
        return getDataTable(list);
    }

    /**
     * 获取库存详细信息
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(invStockService.getById(id));
    }

    /**
     * 导出库存信息
     */
    @GetMapping("/export")
    public void export(HttpServletResponse response, InvStock invStock, 
                       @RequestParam(value = "exportFields", required = false) String exportFields) {
        List<InvStock> list = invStockService.selectInvStockList(invStock);
        try {
            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "库存数据_" + System.currentTimeMillis() + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + java.net.URLEncoder.encode(fileName, "UTF-8"));
            
            // 使用EasyExcel导出
            EasyExcel.write(response.getOutputStream())
                .head(head(exportFields))
                .sheet("库存数据")
                .doWrite(dataList(list, exportFields));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取可导出字段列表
     */
    @GetMapping("/exportFields")
    public AjaxResult getExportFields() {
        List<Map<String, Object>> fields = new ArrayList<>();
        
        // 从数据库实体类中读取所有字段信息
        fields.add(createFieldInfo("stockDate", "库存日期", "Date"));
        fields.add(createFieldInfo("productCode", "产品编码", "String"));
        fields.add(createFieldInfo("productDetail", "产品详情", "String"));
        fields.add(createFieldInfo("price", "单价", "BigDecimal"));
        fields.add(createFieldInfo("quantity", "数量", "Integer"));
        fields.add(createFieldInfo("deliveryTime", "交货时间", "String"));
        fields.add(createFieldInfo("supplier", "供应商", "String"));
        fields.add(createFieldInfo("brand", "品牌", "String"));
        fields.add(createFieldInfo("productType", "产品类型", "String"));
        fields.add(createFieldInfo("productDetailCode", "产品明细编号", "String"));
        fields.add(createFieldInfo("inqOfferType", "Inq/Offer类型", "String"));
        fields.add(createFieldInfo("remark", "备注", "String"));
        fields.add(createFieldInfo("sheetName", "工作表名称", "String"));
        fields.add(createFieldInfo("tags", "标签", "String"));
        fields.add(createFieldInfo("status", "状态", "Integer"));
        
        return success(fields);
    }

    /**
     * 创建字段信息
     */
    private Map<String, Object> createFieldInfo(String fieldName, String displayName, String dataType) {
        Map<String, Object> field = new HashMap<>();
        field.put("fieldName", fieldName);
        field.put("displayName", displayName);
        field.put("dataType", dataType);
        field.put("defaultSelected", true); // 默认选中
        return field;
    }

    /**
     * 定义表头（支持字段选择）
     */
    private List<List<String>> head(String exportFields) {
        List<List<String>> list = new ArrayList<>();
        
        // 如果没有指定字段，则使用默认字段
        if (exportFields == null || exportFields.isEmpty()) {
            return getDefaultHead();
        }
        
        // 解析选中的字段
        String[] fields = exportFields.split(",");
        for (String field : fields) {
            String displayName = getFieldDisplayName(field);
            list.add(Arrays.asList(displayName));
        }
        
        return list;
    }

    /**
     * 获取默认表头
     */
    private List<List<String>> getDefaultHead() {
        List<List<String>> list = new ArrayList<>();
        list.add(Arrays.asList("库存日期"));
        list.add(Arrays.asList("产品编码"));
        list.add(Arrays.asList("产品详情"));
        list.add(Arrays.asList("单价"));
        list.add(Arrays.asList("数量"));
        list.add(Arrays.asList("交货时间"));
        list.add(Arrays.asList("供应商"));
        list.add(Arrays.asList("品牌"));
        list.add(Arrays.asList("产品类型"));
        list.add(Arrays.asList("产品明细编号"));
        list.add(Arrays.asList("Inq/Offer类型"));
        list.add(Arrays.asList("备注"));
        list.add(Arrays.asList("状态"));
        return list;
    }

    /**
     * 根据字段名获取显示名称
     */
    private String getFieldDisplayName(String fieldName) {
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("stockDate", "库存日期");
        fieldMap.put("productCode", "产品编码");
        fieldMap.put("productDetail", "产品详情");
        fieldMap.put("price", "单价");
        fieldMap.put("quantity", "数量");
        fieldMap.put("deliveryTime", "交货时间");
        fieldMap.put("supplier", "供应商");
        fieldMap.put("brand", "品牌");
        fieldMap.put("productType", "产品类型");
        fieldMap.put("productDetailCode", "产品明细编号");
        fieldMap.put("inqOfferType", "Inq/Offer类型");
        fieldMap.put("remark", "备注");
        fieldMap.put("sheetName", "工作表名称");
        fieldMap.put("tags", "标签");
        fieldMap.put("status", "状态");
        
        return fieldMap.getOrDefault(fieldName, fieldName);
    }

    /**
     * 准备数据（支持字段选择）
     */
    private List<List<Object>> dataList(List<InvStock> list, String exportFields) {
        List<List<Object>> dataList = new ArrayList<>();
        
        // 如果没有指定字段，则使用默认字段
        if (exportFields == null || exportFields.isEmpty()) {
            return getDefaultDataList(list);
        }
        
        // 解析选中的字段
        String[] fields = exportFields.split(",");
        
        for (InvStock stock : list) {
            List<Object> data = new ArrayList<>();
            for (String field : fields) {
                data.add(getFieldValue(stock, field));
            }
            dataList.add(data);
        }
        return dataList;
    }

    /**
     * 获取默认数据列表
     */
    private List<List<Object>> getDefaultDataList(List<InvStock> list) {
        List<List<Object>> dataList = new ArrayList<>();
        for (InvStock stock : list) {
            List<Object> data = new ArrayList<>();
            data.add(stock.getStockDate());
            data.add(stock.getProductCode());
            data.add(stock.getProductDetail());
            data.add(stock.getPrice());
            data.add(stock.getQuantity());
            data.add(stock.getDeliveryTime());
            data.add(stock.getSupplier());
            data.add(stock.getBrand());
            data.add(stock.getProductType());
            data.add(stock.getProductDetailCode());
            data.add(stock.getInqOfferType());
            data.add(stock.getRemark());
            data.add(stock.getStatus() == 1 ? "有效" : "无效");
            dataList.add(data);
        }
        return dataList;
    }

    /**
     * 根据字段名获取字段值
     */
    private Object getFieldValue(InvStock stock, String fieldName) {
        switch (fieldName) {
            case "stockDate": return stock.getStockDate();
            case "productCode": return stock.getProductCode();
            case "productDetail": return stock.getProductDetail();
            case "price": return stock.getPrice();
            case "quantity": return stock.getQuantity();
            case "deliveryTime": return stock.getDeliveryTime();
            case "supplier": return stock.getSupplier();
            case "brand": return stock.getBrand();
            case "productType": return stock.getProductType();
            case "productDetailCode": return stock.getProductDetailCode();
            case "inqOfferType": return stock.getInqOfferType();
            case "remark": return stock.getRemark();
            case "sheetName": return stock.getSheetName();
            case "tags": return stock.getTags();
            case "status": return stock.getStatus() == 1 ? "有效" : "无效";
            default: return "";
        }
    }

    /**
     * 新增库存信息
     */
    @PostMapping
    public AjaxResult add(@RequestBody InvStock invStock) {
        return toAjax(invStockService.save(invStock));
    }

    /**
     * 修改库存信息
     */
    @PutMapping
    public AjaxResult edit(@RequestBody InvStock invStock) {
        return toAjax(invStockService.updateById(invStock));
    }

    /**
     * 删除库存信息
     */
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(invStockService.removeByIds(java.util.Arrays.asList(ids)));
    }

    /**
     * 批量更新库存信息
     */
    @PutMapping("/batchUpdate")
    public AjaxResult batchUpdate(@RequestBody Map<String, Object> updateData) {
        try {
            List<Long> ids = (List<Long>) updateData.get("ids");
            Map<String, Object> updateFields = (Map<String, Object>) updateData.get("updateFields");
            
            // 过滤掉空值
            Map<String, Object> filteredFields = new HashMap<>();
            for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                    filteredFields.put(entry.getKey(), entry.getValue());
                }
            }
            
            if (filteredFields.isEmpty()) {
                return error("请至少填写一个要更新的字段");
            }
            
            return toAjax(invStockService.batchUpdate(ids, filteredFields));
        } catch (Exception e) {
            return error("批量更新失败: " + e.getMessage());
        }
    }

    /**
     * 导入库存数据
     */
    @PostMapping("/importData")
    public AjaxResult importData(
            @RequestParam("file") MultipartFile file,
            @RequestParam("stockDateCol") String stockDateCol,
            @RequestParam("productCodeCol") String productCodeCol,
            @RequestParam(value = "productDetailCol", required = false) String productDetailCol,
            @RequestParam(value = "priceCol", required = false) String priceCol,
            @RequestParam(value = "quantityCol", required = false) String quantityCol,
            @RequestParam(value = "deliveryTimeCol", required = false) String deliveryTimeCol,
            @RequestParam(value = "remarkCol", required = false) String remarkCol,
            @RequestParam(value = "supplierCol", required = false) String supplierCol,
            @RequestParam(value = "brandCol", required = false) String brandCol) {
        
        // 构建列映射关系
        Map<String, Integer> columnMapping = new HashMap<>();
        columnMapping.put("stockDate", columnLetterToIndex(stockDateCol));
        columnMapping.put("productCode", columnLetterToIndex(productCodeCol));
        
        if (productDetailCol != null) {
            columnMapping.put("productDetail", columnLetterToIndex(productDetailCol));
        }
        if (priceCol != null) {
            columnMapping.put("price", columnLetterToIndex(priceCol));
        }
        if (quantityCol != null) {
            columnMapping.put("quantity", columnLetterToIndex(quantityCol));
        }
        if (deliveryTimeCol != null) {
            columnMapping.put("deliveryTime", columnLetterToIndex(deliveryTimeCol));
        }
        if (remarkCol != null) {
            columnMapping.put("remark", columnLetterToIndex(remarkCol));
        }
        if (supplierCol != null) {
            columnMapping.put("supplier", columnLetterToIndex(supplierCol));
        }
        if (brandCol != null) {
            columnMapping.put("brand", columnLetterToIndex(brandCol));
        }
        
        try {
            String result = invStockService.importStock(file, columnMapping);
            return success(result);
        } catch (Exception e) {
            return error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 将Excel列字母转换为索引（A->0, B->1,...）
     */
    private int columnLetterToIndex(String columnLetter) {
        return columnLetter.toUpperCase().charAt(0) - 'A';
    }
}