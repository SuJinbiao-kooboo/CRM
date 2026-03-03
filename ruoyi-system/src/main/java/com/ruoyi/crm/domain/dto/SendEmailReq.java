package com.ruoyi.crm.domain.dto;

import com.ruoyi.crm.domain.CrmOffer;
import lombok.Data;

import java.util.Collection;
import java.util.List;

@Data
public class SendEmailReq {

    private Collection<CrmOffer> offers;
    private CrmOffer condition;
    private List<String> emailGroups;

}

