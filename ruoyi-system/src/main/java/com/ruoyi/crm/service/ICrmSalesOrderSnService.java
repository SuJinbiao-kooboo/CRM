package com.ruoyi.crm.service;

import java.util.List;
import java.util.Map;

import com.ruoyi.crm.domain.CrmSalesOrderSn;
import com.ruoyi.crm.domain.dto.CrmSalesOrderSnBatchReq;

public interface ICrmSalesOrderSnService {

    List<CrmSalesOrderSn> selectSnList(CrmSalesOrderSn query);

    Map<String, Object> batchInsert(CrmSalesOrderSnBatchReq req);

    int updateSn(CrmSalesOrderSn sn);

    int deleteSnByIds(Long[] ids);
}
