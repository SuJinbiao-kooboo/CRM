package com.ruoyi.crm.service;

import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.dto.CrmSendEmailTaskDTO;

import java.util.List;

public interface ICrmSupplierSendOfferService {

    List<String> listToOfferEmail();

    List<CrmSendEmailTaskDTO> listEmailSendResults();
}
