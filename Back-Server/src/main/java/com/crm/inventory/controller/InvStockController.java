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
    public void export(HttpServletResponse response, InvStock invStock) {
        List<InvStock> list = invStockService.selectInvStockList(invStock);
        try {
            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "库存数据_" + System.currentTimeMillis() + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + java.net.URLEncoder.encode(fileName, "UTF-8"));
            
            // 使用EasyExcel导出
            EasyExcel.write(response.getOutputStream())
                .head(head())
                .sheet("库存数据")
                .doWrite(dataList(list));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 定义表头
     */
    private List<List<String>> head() {
        List<List<String>> list = new ArrayList<>();
        list.add(Arrays.asList("库存日期"));
        list.add(Arrays.asList("产品编码"));
        list.add(Arrays.asList("产品详情"));
        list.add(Arrays.asList("单价"));
        list.add(Arrays.asList("数量"));
        list.add(Arrays.asList("交货时间"));
        list.add(Arrays.asList("供应商"));
        list.add(Arrays.asList("品牌"));
        list.add(Arrays.asList("备注"));
        list.add(Arrays.asList("状态"));
        return list;
    }

    /**
     * 准备数据
     */
    private List<List<Object>> dataList(List<InvStock> list) {
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
            data.add(stock.getRemark());
            data.add(stock.getStatus() == 1 ? "有效" : "无效");
            dataList.add(data);
        }
        return dataList;
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