package com.ruoyi.crm.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.crm.domain.CrmOffer;

public interface CrmOfferMapper {
    List<CrmOffer> selectOfferList(CrmOffer offer);
    CrmOffer selectOfferById(Long id);
    /** 按主键批量查询微信自动录入生成的报价行。 */
    List<CrmOffer> selectByIds(@Param("list") List<Long> ids);
    /** 双向前缀匹配外部客户 INQ（时间窗口按消息发送时间）。 */
    List<CrmOffer> selectMatchingInq(@Param("pn") String pn, @Param("since") String since,
                                     @Param("pattern") String pattern);
    /** 同料号全部有效报价历史，供统一好价格算法使用。 */
    List<CrmOffer> selectPriceHistoryByPn(@Param("pn") String pn);
    /**
     * AI料号查询：按用户提供的料号左前缀模糊匹配（不区分大小写），查询最近1个月内（Offer日期口径）的INQ/OFFER历史记录
     * @param partNumbers 料号前缀集合（已转大写）
     */
    List<CrmOffer> selectHistoryByPartNumbers(@Param("list") List<String> partNumbers);
    /**
     * 复制Offer：查询最近days天内（创建时间 >= now - days天）所有INQ/OFFER记录
     * 排序由service层重新处理（品牌升序→料号升序→成本升序→创建时间倒序），相同料号取成本最低由service层去重
     * @param days 最近天数
     */
    List<CrmOffer> selectCopyOffers(@Param("days") int days);
    /**
     * 一键复制Offer：按料号集合精确匹配（upper不区分大小写），查询最近days天内（Offer日期口径：库存日期优先、为空回退创建时间）
     * 的Offer记录，报价升序→Offer日期倒序（同料号同价取最新），每个料号取报价最低由service层去重
     * @param list 料号集合（已转大写）
     * @param days 最近天数（1=当天0点至当前，N=N-1天前0点至当前）
     */
    List<CrmOffer> selectRecentOffersByPartNumbers(@Param("list") List<String> list, @Param("days") int days);
    /**
     * AI录入后比价：按料号集合精确匹配（upper不区分大小写），查询最近1个月内
     * （Offer日期口径：库存日期优先，为空回退创建时间）的INQ/OFFER记录，按Offer日期倒序返回
     * @param list 料号集合（已转大写）
     */
    List<CrmOffer> selectRecentByPartNumbers(@Param("list") List<String> list);
    /**
     * 复制Offer（当日料号比价）：查询当日（创建时间>=今天0点）录入的全部Offer去重料号（大写）
     */
    List<String> selectTodayOfferedPartNumbers(@Param("days") int days);
    int insertOffer(CrmOffer offer);
    /**
     * 统计某条微信消息已录入的 Offer 行数（按来源关联字段精确匹配，供自动录入幂等判断）。
     */
    int countByWechat(@Param("serverId") String serverId, @Param("username") String username, @Param("localId") long localId);
    /** 按微信来源查询已录入行，供任务生成复用。 */
    List<CrmOffer> selectByWechat(@Param("serverId") String serverId, @Param("username") String username,
                                  @Param("localId") long localId);
    int updateOffer(CrmOffer offer);
    int invalidateOfferByIds(@Param("list") List<Long> ids);
    int deleteOfferByIds(Long[] ids);
    int deleteOfferById(Long id);
    int batchUpdate(@Param("ids") List<Long> ids, @Param("offer") CrmOffer offer);
}

