package com.ruoyi.web.controller.crm;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.ruoyi.crm.domain.dto.CrmSupplierVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.service.ICrmSupplierService;

@RestController
@RequestMapping("/crm/supplier")
public class SupplierController extends BaseController {

    @Autowired
    private ICrmSupplierService supplierService;

    @PreAuthorize("@ss.hasPermi('crm:supplier:list')")
    @GetMapping("/list")
    public TableDataInfo list(CrmSupplier supplier) {
        startPage();
        List<CrmSupplierVO> list = supplierService.selectSupplierListJoined(supplier);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:list')")
    @GetMapping("/simpleList")
    public TableDataInfo simpleList(CrmSupplier supplier) {
        startPage();
        List<CrmSupplier> list = supplierService.selectSupplierSimpleList(supplier);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:export')")
    @Log(title = "供应商管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, CrmSupplier supplier, @RequestParam(value = "ids", required = false) Long[] ids) {
        List<CrmSupplierVO> list;
        if (ids != null && ids.length > 0) {
            list = supplierService.selectSupplierListJoined(new CrmSupplier());
            list.removeIf(s -> !contains(ids, s.getId()));
        } else {
            list = supplierService.selectSupplierListJoined(supplier);
        }
        ExcelUtil<CrmSupplierVO> util = new ExcelUtil<>(CrmSupplierVO.class);
        util.exportExcel(response, list, "供应商数据");
    }

    private boolean contains(Long[] ids, Long id) {
        for (Long v : ids) { if (v != null && v.equals(id)) return true; }
        return false;
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(supplierService.selectSupplierById(id));
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:query')")
    @GetMapping(value = "/detail/{id}")
    public AjaxResult getDetail(@PathVariable Long id) {
        return AjaxResult.success(supplierService.selectSupplierById(id));
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:add')")
    @Log(title = "供应商管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CrmSupplier supplier) {
        supplier.setCreateBy(SecurityUtils.getUsername());
        if (supplier.getFollowUpBy() == null || supplier.getFollowUpBy().isEmpty()) {
            supplier.setFollowUpBy(SecurityUtils.getUsername());
        }
        return toAjax(supplierService.insertSupplier(supplier));
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:edit')")
    @Log(title = "供应商管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CrmSupplier supplier) {
        supplier.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(supplierService.updateSupplier(supplier));
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:list')")
    @GetMapping("/options")
    public AjaxResult options(CrmSupplier supplier) {
        List<CrmSupplier> crmSuppliers = supplierService.selectSupplierOptions(supplier);
        return AjaxResult.success(crmSuppliers);
    }

    @PreAuthorize("@ss.hasPermi('crm:supplier:remove')")
    @Log(title = "供应商管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(supplierService.deleteSupplierByIds(ids));
    }
}
