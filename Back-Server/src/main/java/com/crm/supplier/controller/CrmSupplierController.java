package com.crm.supplier.controller;

import java.util.List;
import java.util.Arrays;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.crm.common.core.controller.BaseController;
import com.crm.common.core.domain.AjaxResult;
import com.crm.supplier.domain.CrmSupplier;
import com.crm.supplier.domain.CrmSupplierContact;
import com.crm.supplier.domain.SupplierRequest;
import com.crm.supplier.domain.SupplierVO;
import com.crm.supplier.service.ICrmSupplierService;
import com.crm.supplier.service.ICrmSupplierContactService;
import com.crm.common.core.page.TableDataInfo;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * 供应商管理Controller
 * 
 * @author crm
 * @date 2024-01-01
 */
@RestController
@RequestMapping("/supplier/supplier")
public class CrmSupplierController extends BaseController {
    
    @Autowired
    private ICrmSupplierService crmSupplierService;
    
    @Autowired
    private ICrmSupplierContactService contactService;

    /**
     * 查询供应商管理列表（包含联系人信息）
     */
    @GetMapping("/list")
    public TableDataInfo list(SupplierVO supplierVO) {
        startPage();
        List<SupplierVO> list = crmSupplierService.selectSupplierListWithContact(supplierVO);
        return getDataTable(list);
    }

    /**
     * 获取供应商管理详细信息（包含联系人信息）
     */
    @GetMapping(value = "/{supplierId}")
    public AjaxResult getInfo(@PathVariable("supplierId") Long supplierId) {
        CrmSupplier supplier = crmSupplierService.getById(supplierId);
        if (supplier != null) {
            // 获取联系人信息
            List<CrmSupplierContact> contacts = contactService.getContactsBySupplierId(supplierId);
            
            // 创建返回对象，包含供应商信息和所有联系人
            SupplierRequest response = new SupplierRequest();
            response.setSupplier(supplier);
            response.setContacts(contacts);
            
            return success(response);
        }
        return error("供应商不存在");
    }

    /**
     * 新增供应商管理（包含联系人信息）
     */
    @PostMapping
    public AjaxResult add(@RequestBody SupplierRequest request) {
        CrmSupplier supplier = request.getSupplier();
        List<CrmSupplierContact> contacts = request.getContacts();
        
        try {
            boolean success = crmSupplierService.save(supplier);
            if (success && contacts != null && !contacts.isEmpty()) {
                contactService.saveContacts(supplier.getId(), contacts);
            }
            return success ? success("新增成功") : error("新增失败");
        } catch (Exception e) {
            return error("新增失败：" + e.getMessage());
        }
    }

    /**
     * 修改供应商管理（包含联系人信息）
     */
    @PutMapping
    public AjaxResult edit(@RequestBody SupplierRequest request) {
        CrmSupplier supplier = request.getSupplier();
        List<CrmSupplierContact> contacts = request.getContacts();
        
        try {
            boolean success = crmSupplierService.updateById(supplier);
            if (success && contacts != null) {
                contactService.saveContacts(supplier.getId(), contacts);
            }
            return success ? success("修改成功") : error("修改失败");
        } catch (Exception e) {
            return error("修改失败：" + e.getMessage());
        }
    }

    /**
     * 删除供应商管理
     */
    @DeleteMapping("/{supplierIds}")
    public AjaxResult remove(@PathVariable Long[] supplierIds) {
        return toAjax(crmSupplierService.removeByIds(Arrays.asList(supplierIds)));
    }

    /**
     * 导出供应商列表
     */
    @GetMapping("/export")
    public void export(HttpServletResponse response, SupplierVO supplierVO) throws IOException {
        List<SupplierVO> list = crmSupplierService.selectSupplierListWithContact(supplierVO);
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("供应商列表", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), SupplierVO.class)
                .sheet("供应商列表")
                .doWrite(list);
    }
}