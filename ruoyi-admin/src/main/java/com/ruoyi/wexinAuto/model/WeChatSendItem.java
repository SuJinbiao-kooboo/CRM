package com.ruoyi.wexinAuto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 网关逐条发送项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeChatSendItem {
    private String name;
    private String text;
}
