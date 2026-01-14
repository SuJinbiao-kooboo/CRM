package com.ruoyi.crm.service;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.domain.dto.SendEmailReq;
import com.ruoyi.crm.service.impl.CrmSendOfferServiceImpl;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ICrmSendOfferService {

    AjaxResult sendExcelEmail(SendEmailReq req);

}
