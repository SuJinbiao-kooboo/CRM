package com.ruoyi.wexinAuto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审批页按发送目标展示的合并预览，不落库。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeChatTaskPreview {
    private String targetName;
    private String partyName;
    private String text;
}
