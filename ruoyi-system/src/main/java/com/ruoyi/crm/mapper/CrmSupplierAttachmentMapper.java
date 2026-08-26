package com.ruoyi.crm.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.crm.domain.CrmSupplierAttachment;

/**
 * 供应商附件 Mapper（附件内容入库，支持软删除）
 *
 * @author ruoyi
 */
public interface CrmSupplierAttachmentMapper
{
    /** 上传附件：插入一条附件记录（del_flag 固定为 0） */
    int insert(CrmSupplierAttachment attachment);

    /** 供应商保存成功后回填供应商ID（新增供应商场景：上传时供应商尚未创建） */
    int updateSupplierId(@Param("id") Long id, @Param("supplierId") Long supplierId);

    /** 软删除单个附件：仅将删除标记置为 1，不物理删除记录 */
    int softDelete(@Param("id") Long id);

    /** 供应商删除时，软删除其名下全部正常附件 */
    int softDeleteBySupplierId(@Param("supplierId") Long supplierId);

    /** 查询供应商名下的正常（未删除）附件列表，不含附件内容，避免大字段传输 */
    List<CrmSupplierAttachment> selectListBySupplierId(@Param("supplierId") Long supplierId);

    /** 下载时按ID查询附件（含附件内容） */
    CrmSupplierAttachment selectById(@Param("id") Long id);
}
