package com.ruoyi.wexinAuto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;

import java.util.Date;
import java.util.List;

/** 微信询报价待审批任务。 */
@Data
public class WeChatOfferTask extends BaseEntity {
    private Long id;
    private String sourceUsername;
    private Long sourceLocalId;
    private String sourceServerId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sourceMessageTime;
    private String sourceCategory;
    private String taskType;
    private String sendType;
    private String oriTarget;
    private String oriMsg;
    private String pnOriginal;
    private String pnNormalized;
    private String brand;
    private String productType;
    private String spec;
    private Integer qty;
    private Double oriPrice;
    private Double offerPrice;
    private String sendTarget;
    private String sendTargetSuccess;
    private String sendContent;
    private String handleStatus;
    private Integer sendAttempts;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastAttemptAt;
    private String lastError;
    private String matchedInqJson;
    private String offerJson;
    private String approvedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date approvedAt;
    private String approvalReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sentAt;

    private String beginTime;
    private String endTime;
    private String pnKeyword;
    private String queryKeyword;
    private Boolean exceptionOnly;
    private List<WeChatTaskTarget> targets;
    private List<WeChatTaskTarget> successTargets;
    private List<WeChatTaskPreview> previews;
}
