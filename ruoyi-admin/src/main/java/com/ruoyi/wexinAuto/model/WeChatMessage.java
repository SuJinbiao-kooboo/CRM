package com.ruoyi.wexinAuto.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信消息（与 wechat-gateway/wx_gateway.py 输出的 NDJSON 字段一一对应）。
 */
@Data
public class WeChatMessage {

    /** 库表自增主键（入库/查询后才有值） */
    private Long id;

    /** 会话（微信号/群id） */
    private String username;

    /** 会话显示名 */
    private String name;

    /** 库内消息 ID */
    private long localId;

    /** 微信服务器全局消息 ID（账号内唯一，推荐做去重主键） */
    private String serverId;

    /** 消息类型：文本/图片/语音/视频/文件/系统… */
    private String type;

    /** 消息分类（第二层分析时赋值）：INQ询价 / OFFER报价 / TRACE跟进 / OTHER其他；未分析为空 */
    @JsonProperty("msg_category")
    private String msgCategory;

    /** 分类来源：LLM=大模型分类 / CACHE=缓存命中 / FILTER=会话过滤 */
    @JsonProperty("category_source")
    private String categorySource;

    /** 分类理由（代码根据 AI 分类与关联编号生成，便于人工复核） */
    @JsonProperty("category_reason")
    private String categoryReason;

    /** 消息处理状态：0=未分类 1=已分类待处理 2=已处理(录入/预警) 3=无需处理或转人工 4=已发送 */
    @JsonProperty("status")
    private Integer status;
    /** AI录入失败次数：连续失败达 offerEntryMaxFail 次时置 status=3(无需处理)；0=未失败 */
    @JsonProperty("entry_fail_count")
    private Integer entryFailCount;

    /** 自动录入状态：PENDING/PROCESSING/SUCCESS/SUCCESS_WARN/FAILED/DEAD/SKIPPED/NOT_APPLICABLE */
    @JsonProperty("entry_status")
    private String entryStatus;

    /** 自动录入状态说明：成功条数、AI查询结果或跳过/失败原因 */
    @JsonProperty("entry_message")
    private String entryMessage;

    /** 本条微信消息本次/最近一次成功写入 crm_offer 的行数 */
    @JsonProperty("entry_offer_count")
    private Integer entryOfferCount;

    /** 本条微信消息生成的 crm_offer 主键列表(逗号分隔，便于审计) */
    @JsonProperty("entry_offer_ids")
    private String entryOfferIds;

    /** 最近一次录入状态更新时间 */
    @JsonProperty("entry_at")
    private String entryAt;

    /** 关联INQ(JSON: id/server_id/sender/content/create_time)：OFFER=回应的INQ；TRACE=对应的INQ */
    @JsonProperty("relat_inq")
    private String relatInq;

    /** 关联OFFER(JSON: id/server_id/sender/content/create_time)：TRACE=对应的OFFER；OFFER自身为空 */
    @JsonProperty("relat_offer")
    private String relatOffer;
    /** 发言人显示名 */
    private String sender;

    /** 正文（媒体消息为占位+md5）；引用/卡片消息为此消息的原文 */
    private String content;

    /** 被引用内容（引用消息）：文本截前256字；图片/表情等只给【图片】【表情】等说明 */
    private String quote;

    /** 被引用类型：text/image/emoji/voice/video/card/other（非引用为空） */
    private String quoteKind;

    /** 已下载媒体绝对路径（未下载为空） */
    private String mediaPath;

    /** 创建时间，格式 yyyy-MM-dd HH:mm:ss（本地时区） */
    private String createTime;

    /** 排序键（增量断点） */
    private long sortSeq;

    /** 媒体消息 md5 */
    private String md5;
}
