package com.ruoyi.wexinAuto.model;

import lombok.Data;

import java.util.List;

/** 微信询报价任务批量操作请求。 */
@Data
public class WeChatTaskBatchRequest {
    private List<Long> ids;
    private String handleStatus;
    private String approvalReason;
    private Boolean revertSource;
}
