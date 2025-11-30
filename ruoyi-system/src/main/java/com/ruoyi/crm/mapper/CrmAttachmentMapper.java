package com.ruoyi.crm.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.crm.domain.CrmAttachment;

public interface CrmAttachmentMapper {
    List<CrmAttachment> selectBy(@Param("fromType") String fromType, @Param("fromId") Long fromId);
    int insertBatch(List<CrmAttachment> list);
    int deleteBy(@Param("fromType") String fromType, @Param("fromId") Long fromId);
}

