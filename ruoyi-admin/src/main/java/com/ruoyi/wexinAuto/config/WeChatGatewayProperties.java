package com.ruoyi.wexinAuto.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信网关配置。默认值可直接使用；想改路径就在 application.yml 里覆盖：
 * <pre>
 * wechat:
 *   python: C:/Software/Python/python.exe
 *   script: C:/Project/RuoYi-Vue/wechat-gateway/wx_gateway.py
 *   media:  C:/Project/RuoYi-Vue/wechat-gateway/media
 * </pre>
 */
@Getter
@Component
public class WeChatGatewayProperties {

    /** Python 可执行文件 */
    @Value("${wechat.python:C:/Software/Python/python.exe}")
    private String python;

    /** 消息网关脚本 */
    @Value("${wechat.script:C:/Project/RuoYi-Vue/wechat-gateway/wx_gateway.py}")
    private String script;

    /** 媒体(图片/语音/视频/文件)落地目录 */
    @Value("${wechat.media:C:/Project/RuoYi-Vue/wechat-gateway/media}")
    private String mediaDir;


    // ---------- 批量发送(拟人节奏/安全) ----------
    /** 拟人节奏档位 calm/fast/off */
    @Value("${wechat.send.rhythm:calm}")
    private String sendRhythm;

    /** 等待抖动倍率下限(>=1.0) */
    @Value("${wechat.send.napMin:1.0}")
    private double sendNapMin;

    /** 等待抖动倍率上限 */
    @Value("${wechat.send.napMax:1.6}")
    private double sendNapMax;

    /** 条与条之间随机间隔下限(秒) */
    @Value("${wechat.send.intervalMin:6}")
    private double sendIntervalMin;

    /** 条与条之间随机间隔上限(秒) */
    @Value("${wechat.send.intervalMax:15}")
    private double sendIntervalMax;

    /** 单批上限(超限拒绝发送) */
    @Value("${wechat.send.maxBatch:20}")
    private int sendMaxBatch;

    /** 是否回读确认(防重复) */
    @Value("${wechat.send.verify:true}")
    private boolean sendVerify;
}




