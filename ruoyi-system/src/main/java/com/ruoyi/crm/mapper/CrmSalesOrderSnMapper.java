package com.ruoyi.crm.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.crm.domain.CrmSalesOrderSn;

public interface CrmSalesOrderSnMapper {

    List<CrmSalesOrderSn> selectSnList(CrmSalesOrderSn query);

    int countSnNo(@Param("snNo") String snNo, @Param("excludeId") Long excludeId);

    int insertBatchIgnore(@Param("list") List<CrmSalesOrderSn> list);

    int updateSn(CrmSalesOrderSn sn);

    int deleteSnByIds(Long[] ids);

    int deleteSnByOrderIds(Long[] orderIds);
}
