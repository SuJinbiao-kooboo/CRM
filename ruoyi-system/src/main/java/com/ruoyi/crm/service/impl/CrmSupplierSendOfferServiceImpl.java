package com.ruoyi.crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.dto.CrmSupplierVO;
import com.ruoyi.crm.service.ICrmSupplierSendOfferService;
import com.ruoyi.crm.service.ICrmSupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CrmSupplierSendOfferServiceImpl implements ICrmSupplierSendOfferService {

    @Autowired
    private ICrmSupplierService crmSupplierService;

    @Override
    public List<String> listToOfferEmail() {

        List<CrmSupplierVO> crmSuppliers = crmSupplierService.selectSupplierListJoined(new CrmSupplier());
        Map<String, List<CrmSupplierVO>> supplierMap = crmSuppliers.stream().collect(Collectors.groupingBy(CrmSupplierVO::getSupplierCode));
        List<String> contactList = new ArrayList<>();

        for (Map.Entry<String, List<CrmSupplierVO>> entry : supplierMap.entrySet()) {
            List<CrmSupplierVO> valueList = entry.getValue();
            valueList.forEach(o->o.setOtherContactFirst(StrUtil.nullToDefault(o.getOtherContactFirst(), StrUtil.EMPTY)));
            Map<String, List<CrmSupplierVO>> collect = valueList.stream().collect(Collectors.groupingBy(CrmSupplierVO::getOtherContactFirst));
            collect.entrySet().forEach((entryItem)->{
                contactList.add(StrUtil.join(StrUtil.COMMA, entryItem.getValue().stream().map(CrmSupplierVO::getEmail).filter(StrUtil::isNotEmpty).collect(Collectors.toList())));
            });
        }
        return contactList;
    }
}
