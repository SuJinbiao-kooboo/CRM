package com.ruoyi.web.controller.crm;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.ruoyi.crm.domain.CrmSupplierAttachment;
import com.ruoyi.crm.service.ICrmSupplierAttachmentService;

/**
 * 供应商附件 Controller（附件内容入库，支持软删除）
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/crm/supplier/attachment")
public class SupplierAttachmentController extends BaseController
{
    @Autowired
    private ICrmSupplierAttachmentService attachmentService;

    /**
     * 上传附件：文件内容写入附件表。
     * 编辑已有供应商时传 supplierId；新增供应商时 supplierId 暂空，保存供应商后回填。
     */
    @PreAuthorize("@ss.hasPermi('crm:supplier:add') or @ss.hasPermi('crm:supplier:edit')")
    @Log(title = "供应商附件", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "supplierId", required = false) Long supplierId)
    {
        CrmSupplierAttachment attachment = attachmentService.upload(file, supplierId);
        // 仅向前端返回轻量信息（不含附件内容）
        Map<String, Object> data = new HashMap<>();
        data.put("id", attachment.getId());
        data.put("fileName", attachment.getFileName());
        data.put("createTime", attachment.getCreateTime());
        return AjaxResult.success(data);
    }

    /**
     * 删除附件：软删除，仅将删除标记置为 1，不物理删除记录
     */
    @PreAuthorize("@ss.hasPermi('crm:supplier:edit')")
    @Log(title = "供应商附件", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        return toAjax(attachmentService.softDelete(id));
    }

    /**
     * 下载附件：按附件ID从数据库读取附件内容并输出为下载流。
     * 使用 POST 方式，便于前端携带认证头并处理错误信息。
     */
    @PreAuthorize("@ss.hasPermi('crm:supplier:query')")
    @PostMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws Exception
    {
        CrmSupplierAttachment attachment = attachmentService.selectById(id);
        if (attachment == null || attachment.getFileContent() == null)
        {
            throw new ServiceException("附件不存在或已删除");
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        FileUtils.setAttachmentResponseHeader(response, attachment.getFileName());
        response.getOutputStream().write(attachment.getFileContent());
    }

    /**
     * 预览附件：将 Excel（xls/xlsx/csv）和 Word（docx）解析为 HTML 片段返回，
     * 供前端弹窗展示；PDF/图片等由前端浏览器原生预览。
     * 注意：previewHtml 返回 String，不能直接传 AjaxResult.success(Object) 单参——
     * 重载解析会选中更具体的 success(String msg) 把 HTML 塞进 msg 字段、data 为 null，
     * 必须用双参 success(String msg, Object data) 显式把内容放入 data
     */
    @PreAuthorize("@ss.hasPermi('crm:supplier:query')")
    @PostMapping("/preview/{id}")
    public AjaxResult preview(@PathVariable Long id)
    {
        return AjaxResult.success("操作成功", attachmentService.previewHtml(id));
    }
}
