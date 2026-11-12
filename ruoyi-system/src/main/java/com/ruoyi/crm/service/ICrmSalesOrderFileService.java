package com.ruoyi.crm.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.crm.domain.CrmSalesOrderFile;

public interface ICrmSalesOrderFileService {

    CrmSalesOrderFile upload(MultipartFile file, Long orderId, String fieldKey);

    List<CrmSalesOrderFile> selectListByOrderId(Long orderId);

    CrmSalesOrderFile selectById(Long id);

    String previewHtml(Long id);

    int softDelete(Long id);
}
