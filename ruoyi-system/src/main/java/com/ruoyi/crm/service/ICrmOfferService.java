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

    /** AI智能录入：调用AI把物料内容整理为结构化数据并批量入库，返回成功条数 */
    int aiEntryOffers(String supplierCode, String supplierName, String inqOfferType, Double profitRatio, String content);

    /**
     * AI料号查询：调用AI从物料内容中提取完整料号，查询各料号最近半年内的INQ/OFFER历史记录，按料号分组返回
     * 每组格式：{ partNumber, offers: [{ supplierName, inqOfferType, quantity, priceOffer, createTime, deliveryTime, productDetail }] }
     */
    List<Map<String, Object>> aiQueryHistory(String content);

    /**
     * 复制Offer：查询最近days天内的INQ/OFFER记录，按品牌排序、相同料号取成本最低（无价格记录也保留），
     * 组装为"料号 数量(带pcs) 品牌 DC 交期 详情 报价(带USD)"制表符分隔文本返回，首行为英文表头
     * （不输出成本，内容会发给客户）
     */
    String buildCopyOfferText(int days);

    /**
     * AI查询复制：按料号集合+最近天数（1=当天0点至当前，N=N-1天前0点至当前）查询Offer记录，
     * 每个料号取报价最低（同价取最新）的一条，货况按详情/备注/质保是否含"拆机"判断（不确定默认全新），
     * 组装为"料号 报价(带USD) 数量(带pcs) 交期 DC 货况"制表符分隔文本返回，首行为英文表头
     */
    String buildAiQueryCopyText(List<String> partNumbers, int days);
}
