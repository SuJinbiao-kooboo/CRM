package com.ruoyi.web.controller.crm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.crm.domain.CrmSalesOrderFile;
import com.ruoyi.crm.service.ICrmSalesOrderFileService;

/**
 * 销售订单附件：文件内容入库，上传即保存。
 */
@RestController
@RequestMapping("/crm/salesOrder/attachment")
public class SalesOrderFileController extends BaseController {

    @Autowired
    private ICrmSalesOrderFileService fileService;

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:edit')")
    @Log(title = "销售订单附件", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam("orderId") Long orderId,
                             @RequestParam("fieldKey") String fieldKey) {
        CrmSalesOrderFile attachment = fileService.upload(file, orderId, fieldKey);
        Map<String, Object> data = new HashMap<>();
        data.put("id", attachment.getId());
        data.put("orderId", attachment.getOrderId());
        data.put("fieldKey", attachment.getFieldKey());
        data.put("fileName", attachment.getFileName());
        data.put("contentType", attachment.getContentType());
        data.put("fileSize", attachment.getFileSize());
        data.put("createTime", attachment.getCreateTime());
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:query')")
    @GetMapping("/list/{orderId}")
    public AjaxResult list(@PathVariable Long orderId) {
        List<CrmSalesOrderFile> files = fileService.selectListByOrderId(orderId);
        return success(files);
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:query')")
    @PostMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws Exception {
        CrmSalesOrderFile attachment = fileService.selectById(id);
        if (attachment == null || attachment.getFileContent() == null) {
            throw new ServiceException("附件不存在或已删除");
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        FileUtils.setAttachmentResponseHeader(response, attachment.getFileName());
        response.getOutputStream().write(attachment.getFileContent());
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:query')")
    @PostMapping("/preview/{id}")
    public AjaxResult preview(@PathVariable Long id) {
        return AjaxResult.success("操作成功", fileService.previewHtml(id));
    }

    @PreAuthorize("@ss.hasPermi('crm:salesOrder:edit')")
    @Log(title = "销售订单附件", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(fileService.softDelete(id));
    }
}
