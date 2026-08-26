package com.ruoyi.crm.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.crm.domain.CrmSupplierAttachment;

/**
 * 供应商附件 Service（附件内容入库，支持软删除）
 *
 * @author ruoyi
 */
public interface ICrmSupplierAttachmentService
{
    /** 上传附件：读取文件内容写入附件表，supplierId 允许为空（新增供应商时暂空） */
    CrmSupplierAttachment upload(MultipartFile file, Long supplierId);

    /** 查询供应商名下的正常（未删除）附件列表，不含附件内容 */
    List<CrmSupplierAttachment> selectListBySupplierId(Long supplierId);

    /** 下载时按ID查询附件（含附件内容） */
    CrmSupplierAttachment selectById(Long id);

    /** 预览附件：将 Excel/Word 等办公文档解析为 HTML 片段返回（PDF/图片由前端原生预览） */
    String previewHtml(Long id);

    /** 软删除单个附件：仅标记删除，不物理删除记录 */
    int softDelete(Long id);
}
