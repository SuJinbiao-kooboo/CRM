package com.ruoyi.crm.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.dto.CrmSupplierVO;
import com.ruoyi.crm.domain.dto.SubscribeEmailDTO;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.crm.service.ICrmSupplierSendOfferService;
import com.ruoyi.crm.service.ICrmSupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CrmSupplierSendOfferServiceImpl implements ICrmSupplierSendOfferService {

    @Autowired
    private ICrmSupplierService crmSupplierService;
    @Autowired
    private CrmSupplierMapper supplierMapper;

    @Override
    public List<String> listToOfferEmail() {

        List<SubscribeEmailDTO> subscribeEmailDTOS = supplierMapper.selectSubscribeEmailList();
        List<String> cancelEmailList = subscribeEmailDTOS.stream().filter(o -> 0 == o.getStatus()).map(SubscribeEmailDTO::getEmail).collect(Collectors.toList());
        List<String> subscribeEmailList = subscribeEmailDTOS.stream().filter(o -> 1 == o.getStatus()).map(SubscribeEmailDTO::getEmail).collect(Collectors.toList());
        List<CrmSupplierVO> crmSuppliers = crmSupplierService.selectSupplierListJoined(new CrmSupplier());
        Map<String, List<CrmSupplierVO>> supplierMap = crmSuppliers.stream().filter(o -> !cancelEmailList.contains(o.getEmail())).collect(Collectors.groupingBy(CrmSupplierVO::getSupplierCode));
        List<String> contactList = new ArrayList<>();

        for (Map.Entry<String, List<CrmSupplierVO>> entry : supplierMap.entrySet()) {
            List<CrmSupplierVO> valueList = entry.getValue();
            valueList.forEach(o->o.setOtherContactFirst(StrUtil.nullToDefault(o.getOtherContactFirst(), StrUtil.EMPTY)));
            Map<String, List<CrmSupplierVO>> collect = valueList.stream().collect(Collectors.groupingBy(CrmSupplierVO::getOtherContactFirst));
            collect.entrySet().forEach((entryItem)->{
                subscribeEmailList.removeAll(entryItem.getValue().stream().map(CrmSupplierVO::getEmail).collect(Collectors.toList()));
                contactList.add(StrUtil.join(StrUtil.COMMA, entryItem.getValue().stream().map(CrmSupplierVO::getEmail).filter(StrUtil::isNotEmpty).collect(Collectors.toList())));
            });
        }

        contactList.addAll(subscribeEmailList);

        supplierMapper.deleteAllTask();
        supplierMapper.batchInsertWithDefault(DateUtil.formatDateTime(new Date()), contactList);

        return contactList;
    }
}
