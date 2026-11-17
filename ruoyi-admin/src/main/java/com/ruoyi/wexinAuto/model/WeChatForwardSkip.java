package com.ruoyi.wexinAuto.model;

import lombok.Data;

/** 微信转发选择框里未找到或未勾选的目标。 */
@Data
public class WeChatForwardSkip {
    private String name;
    private String reason;
}
