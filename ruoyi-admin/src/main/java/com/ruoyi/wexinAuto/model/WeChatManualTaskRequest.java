package com.ruoyi.wexinAuto.model;

import lombok.Data;

import java.util.List;

/**
 * 手工创建微信询报价任务请求。
 *
 * <p>content 为用户粘贴的原文，groupSide 决定读取供应商的销售群还是采购群，
 * supplierIds 为弹窗中从左侧移入右侧的供应商主键列表。</p>
 */
@Data
public class WeChatManualTaskRequest {
    /** 要发送到微信群的消息原文，保留换行和制表符。 */
    private String content;
    /** 群方向：sales=销售群，purchase=采购群。 */
    private String groupSide;
    /** 已选择的供应商 ID；后端会去重并排除未关联兜底供应商。 */
    private List<Long> supplierIds;
}
