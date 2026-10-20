package com.ruoyi.crm.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.crm.domain.CrmOrder;

public interface CrmOrderMapper {

    List<CrmOrder> selectOrderList(CrmOrder order);

    CrmOrder selectOrderById(Long id);

    int insertOrder(CrmOrder order);

    int updateOrder(CrmOrder order);

    int deleteOrderByIds(Long[] ids);

    /** 取编号序列当前值（行锁，用于并发下生成不重复流水号） */
    Integer selectSeqForUpdate(@Param("seqKey") String seqKey);

    int insertSeq(@Param("seqKey") String seqKey);

    int incrementSeq(@Param("seqKey") String seqKey);
}
