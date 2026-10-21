package com.ruoyi.web.controller.crm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.crm.domain.CrmOrder;
import com.ruoyi.crm.domain.dto.DocGenerateReq;
import com.ruoyi.crm.service.ICrmOrderService;
import com.ruoyi.crm.service.IDocGenerateService;
import com.alibaba.fastjson2.JSON;

import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * CRM 订单（PO 等单据）控制器
 */
@RestController
@RequestMapping("/crm/order")
public class OrderController extends BaseController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private ICrmOrderService orderService;

    @Autowired
    private IDocGenerateService docGenerateService;

    /** 单据类型定义（驱动"单据生成"页面动态表单） */
    @PreAuthorize("@ss.hasPermi('crm:order:list')")
    @GetMapping("/doc/types")
    public AjaxResult docTypes() {
        return success(docGenerateService.listTypes());
    }

    /** 生成单据 Excel（支持上传来源文件，如供应商装箱单） */
    @PreAuthorize("@ss.hasPermi('crm:order:generate')")
    @Log(title = "CRM单据生成", businessType = BusinessType.EXPORT)
    @PostMapping("/doc/generate")
    public void docGenerateExcel(HttpServletResponse response,
                                 @RequestParam("payload") String payload,
                                 @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        DocGenerateReq req = JSON.parseObject(payload, DocGenerateReq.class);
        writeFile(response, docGenerateService.generateExcel(req, file),
                docGenerateService.fileName(req, "xlsx"), XLSX_CONTENT_TYPE);
    }

    /** 生成单据 PDF */
    @PreAuthorize("@ss.hasPermi('crm:order:generate')")
    @Log(title = "CRM单据生成", businessType = BusinessType.EXPORT)
    @PostMapping("/doc/generatePdf")
    public void docGeneratePdf(HttpServletResponse response,
                               @RequestParam("payload") String payload,
                               @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        DocGenerateReq req = JSON.parseObject(payload, DocGenerateReq.class);
        writeFile(response, docGenerateService.generatePdf(req, file),
                docGenerateService.fileName(req, "pdf"), "application/pdf");
    }

    @PreAuthorize("@ss.hasPermi('crm:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(CrmOrder order) {
        startPage();
        List<CrmOrder> list = orderService.selectOrderList(order);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('crm:order:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(orderService.selectOrderById(id));
    }

    /** 生成下一个订单编号（用于页面展示，未保存时会占用一个流水号） */
    @PreAuthorize("@ss.hasPermi('crm:order:add')")
    @GetMapping("/nextOrderNo")
    public AjaxResult nextOrderNo(@RequestParam(value = "orderType", required = false) String orderType) {
        return success(orderService.generateOrderNo(orderType));
    }

    /** 选择供应商后解析 Seller 信息块 */
    @PreAuthorize("@ss.hasPermi('crm:order:list')")
    @GetMapping("/resolveSeller/{supplierId}")
    public AjaxResult resolveSeller(@PathVariable Long supplierId) {
        Map<String, Object> result = orderService.resolveSeller(supplierId);
        return success(result);
    }

    @PreAuthorize("@ss.hasPermi('crm:order:add')")
    @Log(title = "CRM订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody CrmOrder order) {
        return toAjax(orderService.insertOrder(order));
    }

    @PreAuthorize("@ss.hasPermi('crm:order:edit')")
    @Log(title = "CRM订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody CrmOrder order) {
        return toAjax(orderService.updateOrder(order));
    }

    @PreAuthorize("@ss.hasPermi('crm:order:remove')")
    @Log(title = "CRM订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(orderService.deleteOrderByIds(ids));
    }

    /** 生成 PO 单据 Excel */
    @PreAuthorize("@ss.hasPermi('crm:order:generate')")
    @Log(title = "CRM订单", businessType = BusinessType.EXPORT)
    @PostMapping("/generateExcel")
    public void generateExcel(HttpServletResponse response, @RequestBody CrmOrder order) throws IOException {
        byte[] data = orderService.generateExcel(order);
        String base = StringUtils.isEmpty(order.getOrderNo()) ? "PO" : order.getOrderNo();
        writeFile(response, data, base + ".xlsx", XLSX_CONTENT_TYPE);
    }

    /** 生成 PO 单据 PDF */
    @PreAuthorize("@ss.hasPermi('crm:order:generate')")
    @Log(title = "CRM订单", businessType = BusinessType.EXPORT)
    @PostMapping("/generatePdf")
    public void generatePdf(HttpServletResponse response, @RequestBody CrmOrder order) throws IOException {
        byte[] data = orderService.generatePdf(order);
        String base = StringUtils.isEmpty(order.getOrderNo()) ? "PO" : order.getOrderNo();
        writeFile(response, data, base + ".pdf", "application/pdf");
    }

    private void writeFile(HttpServletResponse response, byte[] data, String filename, String contentType)
            throws IOException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setContentType(contentType);
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded);
        response.setHeader("Content-Length", String.valueOf(data.length));
        try (OutputStream out = response.getOutputStream()) {
            out.write(data);
            out.flush();
        } catch (IOException e) {
            throw new ServiceException("单据下载失败：" + e.getMessage());
        }
    }
}
