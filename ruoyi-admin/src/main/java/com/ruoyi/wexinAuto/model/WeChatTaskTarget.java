package com.ruoyi.wexinAuto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 任务发送目标，持久化为 JSON 数组。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeChatTaskTarget {
    private String name;
    private String partyCode;
    private String partyName;
}
