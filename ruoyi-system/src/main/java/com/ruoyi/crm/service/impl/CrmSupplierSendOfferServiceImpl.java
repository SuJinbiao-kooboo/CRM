package com.ruoyi.crm.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.dto.CrmSendEmailTaskDTO;
import com.ruoyi.crm.domain.dto.CrmSupplierVO;
import com.ruoyi.crm.domain.dto.SubscribeEmailDTO;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.crm.service.ICrmSupplierSendOfferService;
import com.ruoyi.crm.service.ICrmSupplierService;
import com.ruoyi.system.service.ISysDictDataService;
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
    @Autowired
    private ISysDictDataService dictDataService;

    /**
     * 获取收件人列表并写入发送任务表（发送模式由前端弹窗单选决定，不再依赖字典 email_test_date 日期开关）
     *
     * @param testSend true=测试发送（仅发到字典 email_test_account 配置的测试邮箱）；false=正式发送（订阅邮箱+供应商邮箱）
     */
    @Override
    public List<String> listToOfferEmail(boolean testSend) {
        List<String> contactList = new ArrayList<>();
        if (testSend) {
            // 测试发送：只发到测试邮箱，避免打扰正式收件人
            contactList.add(dictDataService.selectDictLabel("crm_email_template_dict", "email_test_account"));
        } else {
            // 正式发送：订阅邮箱列表 + 允许推送的供应商邮箱
            fillContactList(contactList);
        }

        contactList = contactList.stream().filter(StrUtil::isNotEmpty).collect(Collectors.toList());

        supplierMapper.deleteAllTask();
        supplierMapper.batchInsertWithDefault(DateUtil.formatDateTime(new Date()), contactList);
//        return contactList.subList(58, contactList.size());
        return contactList;
    }

    private void fillContactList(List<String> contactList) {
        List<SubscribeEmailDTO> subscribeEmailDTOS = supplierMapper.selectSubscribeEmailList();
        List<String> cancelEmailList = subscribeEmailDTOS.stream().filter(o -> 0 == o.getStatus()).map(SubscribeEmailDTO::getEmail).collect(Collectors.toList());
        List<String> subscribeEmailList = subscribeEmailDTOS.stream().filter(o -> 1 == o.getStatus()).map(SubscribeEmailDTO::getEmail).collect(Collectors.toList());
        String emailSupplier = dictDataService.selectDictLabel("crm_email_template_dict", "email_add_supplier");

        Map<String, List<CrmSupplierVO>> supplierMap = new HashMap<>();
        if(Boolean.TRUE.toString().equals(emailSupplier)){
            List<CrmSupplierVO> crmSuppliers = crmSupplierService.selectSupplierListJoined(new CrmSupplier());
            supplierMap = crmSuppliers.stream().filter(o -> !cancelEmailList.contains(o.getEmail())).collect(Collectors.groupingBy(CrmSupplierVO::getSupplierCode));
        }

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
    }

    @Override
    public List<CrmSendEmailTaskDTO> listEmailSendResults() {
        return supplierMapper.selectEmailResultList();
    }
}
