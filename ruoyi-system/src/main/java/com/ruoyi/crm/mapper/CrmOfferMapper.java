package com.ruoyi.crm.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.crm.domain.CrmOffer;

public interface CrmOfferMapper {
    List<CrmOffer> selectOfferList(CrmOffer offer);
    CrmOffer selectOfferById(Long id);
    int insertOffer(CrmOffer offer);
    int updateOffer(CrmOffer offer);
    int deleteOfferByIds(Long[] ids);
    int deleteOfferById(Long id);
    int batchUpdate(@Param("ids") List<Long> ids, @Param("offer") CrmOffer offer);
}

