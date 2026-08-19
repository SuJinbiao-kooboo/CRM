package com.ruoyi.crm.domain;

import java.util.Date;
import lombok.Data;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 供应商附件（附件内容入库存储）
 *
 * @author ruoyi
 */
@Data
public class CrmSupplierAttachment extends BaseEntity
{
    /** 附件ID */
    private Long id;

    /** 供应商ID（新增供应商时暂为空，保存供应商后回填） */
    private Long supplierId;

    /** 附件名称 */
    private String fileName;

    /** 附件内容（二进制存储，列表查询时不返回该字段） */
    private byte[] fileContent;

    /** 删除标记：0=正常，1=已删除（软删除，不物理删除记录） */
    private String delFlag;

    /** 上传时间 */
    private Date createTime;
}
