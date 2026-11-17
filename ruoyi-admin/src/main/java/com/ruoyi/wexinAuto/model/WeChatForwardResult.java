package com.ruoyi.wexinAuto.model;

import lombok.Data;

import java.util.List;

/** wx_gateway.py forward_send 的单次转发结果。 */
@Data
public class WeChatForwardResult {
    private boolean ok;
    private String status;
    private String message;
    private List<String> sent;
    private List<String> failed;
    private List<WeChatForwardSkip> skipped;
}
