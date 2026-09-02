package com.ruoyi.crm.service;

import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.dto.CrmSendEmailTaskDTO;

import java.util.List;

public interface ICrmSupplierSendOfferService {

    /**
     * 获取Offer邮件的收件人列表并写入发送任务表
     *
     * @param testSend true=测试发送（仅发到字典配置的测试邮箱）；false=正式发送（订阅邮箱+允许推送的供应商邮箱）
     */
    List<String> listToOfferEmail(boolean testSend);

    List<CrmSendEmailTaskDTO> listEmailSendResults();
}
