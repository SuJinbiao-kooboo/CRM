package com.ruoyi.wexinAuto.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 批量发送的单条结果（与 wx_gateway.py batch_send 输出的 NDJSON 字段对应）。
 */
@Data
public class WeChatSendResult {

    /** 是否发送成功 */
    private boolean ok;

    /** 在原批次中的序号 */
    private int index;

    /** 目标会话名 */
    private String name;

    /** 实际发送的文本 */
    private String text;

    /** 状态：成功/失败/错误/异常 */
    private String status;

    /** 结果描述 */
    private String message;

    /** 耗时(毫秒) */
    @JsonProperty("duration_ms")
    private long durationMs;
}
