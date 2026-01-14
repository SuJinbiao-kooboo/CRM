package com.ruoyi.crm.domain.dto;

import com.ruoyi.crm.domain.CrmOffer;
import lombok.Data;

import java.util.List;

@Data
public class SendEmailReq {

    private List<CrmOffer> offers;
    private List<String> emailGroups;

}

