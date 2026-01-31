package com.ruoyi.crm.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.crm.domain.CrmOffer;

public interface ICrmOfferService {
    List<CrmOffer> selectOfferList(CrmOffer offer);

    CrmOffer selectOfferById(Long id);

    int insertOffer(CrmOffer offer);

    int updateOffer(CrmOffer offer);

    int deleteOfferByIds(Long[] ids);

    int batchUpdate(List<Long> ids, CrmOffer offer);

    Map<String, Object> importOffers(MultipartFile file, String supplierCode, String supplierName, String inqOfferType, Map<String, String> colMap, Double profitRatio);
}
