package com.ruoyi.web.controller.crm;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.crm.domain.CrmSalesOrderSn;
import com.ruoyi.crm.domain.dto.CrmSalesOrderSnBatchReq;
import com.ruoyi.crm.service.ICrmSalesOrderSnService;

/**
 * 订单需求管理-SN管理
 */
@RestController
@RequestMapping("/crm/salesOrderSn")
public class SalesOrderSnController extends BaseController {

    @Autowired
    private ICrmSalesOrderSnService snService;

    @PreAuthorize("@ss.hasPermi('crm:salesOrderSn:list')")
    @GetMapping("/list")
    public TableDataInfo list(CrmSalesOrderSn query) {
        startPage();
        List<CrmSalesOrderSn> list = snService.selectSnList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrderSn:add')")
    @Log(title = "销售订单SN", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public AjaxResult batchInsert(@RequestBody CrmSalesOrderSnBatchReq req) {
        return success(snService.batchInsert(req));
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrderSn:edit')")
    @Log(title = "销售订单SN", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CrmSalesOrderSn sn) {
        snService.updateSn(sn);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrderSn:remove')")
    @Log(title = "销售订单SN", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(snService.deleteSnByIds(ids));
    }
}
