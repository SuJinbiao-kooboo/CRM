package com.ruoyi.feishu.dto;

import lombok.Data;

/**
 * 发送工作汇报的请求体。
 * 通常只需接收者信息，不需要额外入参。
 */
@Data
public class FeishuSendReportBody extends FeishuReceiveBody {
    // 预留扩展位：后续如需指定汇报类型/模板，可在此增加字段
}
