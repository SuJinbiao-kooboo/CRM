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
     * AI智能录入（返回入库明细）：逻辑同 aiEntryOffers，额外返回本次成功入库的Offer列表（含料号/价格），
     * 供录入完成后做近1个月同料号价格比较
     */
    List<CrmOffer> aiEntryOffersReturning(String supplierCode, String supplierName, String inqOfferType, Double profitRatio, String content);

    /**
     * AI录入后比价：查询料号集合最近1个月内（Offer日期口径：库存日期优先、为空回退创建时间）的INQ/OFFER记录，
     * 按Offer日期倒序返回；每条含 物料编号/供应商编号/成本价格/Offer价格/Offer日期/详情/数量/交期/DC/类型；
     * 料号为空时返回空列表（不抛异常，避免影响录入成功提示）
     */
    List<Map<String, Object>> compareRecentOffers(List<String> partNumbers);

    /**
     * AI料号查询：调用AI从物料内容中提取完整料号，查询各料号最近1个月内的INQ/OFFER历史记录，按料号分组返回
     * 每组格式：{ partNumber, offers: [{ supplierName, inqOfferType, quantity, priceOffer, createTime, offerDate, deliveryTime, productDetail }] }
     */
    List<Map<String, Object>> aiQueryHistory(String content);

    /**
     * 复制Offer：查询最近days天内的INQ/OFFER记录，按品牌排序、相同料号取成本最低（无价格记录也保留），
     * 组装为"料号 数量(带pcs) 品牌 DC 交期 详情 报价(带USD)"制表符分隔文本返回，首行为英文表头
     * （不输出成本，内容会发给客户）
     */
    String buildCopyOfferText(int days);

    /**
     * 一键复制Offer（AI录入比价/AI查询共用）：按料号集合+最近天数（1=当天0点至当前，N=N-1天前0点至当前；Offer日期口径）查询Offer记录，
     * 每个料号取Offer价格最低（同价取Offer日期最新）的一条；默认输出 料号/数量(带pcs)/Offer价格(带USD)，
     * extraFields 勾选（supplierCode/productDetail/deliveryTime/dc）后按固定顺序追加对应列，
     * 组装为制表符分隔文本返回，首行为英文表头（随勾选字段变化）
     */
    String buildAiQueryCopyText(List<String> partNumbers, int days, List<String> extraFields);
}
