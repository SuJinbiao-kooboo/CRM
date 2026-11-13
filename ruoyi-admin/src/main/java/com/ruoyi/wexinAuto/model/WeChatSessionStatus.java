package com.ruoyi.wexinAuto.model;

import lombok.Data;

/** Windows 当前交互桌面状态，用于发送前锁屏预检。 */
@Data
public class WeChatSessionStatus {

    /** 是否可操作微信窗口；false 时只暂停，不消耗发送重试次数。 */
    private boolean unlocked = true;

    /** 当前输入桌面名称，正常为 Default，锁屏时通常是 Winlogon。 */
    private String desktop = "";

    /** 检测结果说明。 */
    private String reason = "";
}
