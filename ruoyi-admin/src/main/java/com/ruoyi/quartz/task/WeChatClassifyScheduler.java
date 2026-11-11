package com.ruoyi.quartz.task;

import com.ruoyi.wexinAuto.service.WeChatGatewayService;
import com.ruoyi.wexinAuto.service.WeChatOfferTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信自动化调度：拉取、分类、录入、任务生成、发送、TRACE 和清理各自独立扫描。
 * 扫描间隔统一从 wechat_analysis_config 读取，单执行器运行。
 */
@Slf4j
@Component("weChatClassifyScheduler")
public class WeChatClassifyScheduler {

    private final WeChatGatewayService gateway;
    private final WeChatOfferTaskService taskService;

    public WeChatClassifyScheduler(WeChatGatewayService gateway, WeChatOfferTaskService taskService) {
        this.gateway = gateway;
        this.taskService = taskService;
    }

    /** 轮询拉取微信消息：默认每 5 秒检查一次，实际拉取间隔由字典 pollIntervalSec 控制。 */
    public void pollMessages() {
        try {
            gateway.pollOnce(null);
        } catch (Exception e) {
            log.error("定时轮询拉取失败", e);
        }
    }

    /** 未分类消息扫描：默认每 10 秒检查一次，触发条件由字典 classifyTriggerCount / classifyMaxIdleSec 控制。 */
    public void scanUnclassified() {
        try {
            gateway.recoverUnclassified();
        } catch (Exception e) {
            log.error("定时分类未分类消息失败", e);
        }
    }

    /** TRACE 飞书预警补扫。 */
    public void scanTraceAlerts() {
        try {
            gateway.scanTraceAlerts();
        } catch (Exception e) {
            log.error("定时TRACE预警扫描失败", e);
        }
    }

    /** 分类/录入/任务失败聚合告警：09:00-21:30 每10分钟检查一次。 */
    public void alertFailures() {
        try {
            Map<String, Object> result = gateway.alertFailureBurst();
            if (Boolean.TRUE.equals(result.get("alertSent"))) {
                log.info("微信自动化失败聚合告警已发送: {}", result.get("message"));
            }
        } catch (Exception e) {
            log.error("定时微信自动化失败聚合告警失败", e);
        }
    }

    /** 历史消息清理：默认每 6 小时执行一次。 */
    public void purgeOldMessages() {
        try {
            gateway.purgeOldMessages();
        } catch (Exception e) {
            log.error("定时清理历史消息失败", e);
        }
    }

    /** 已分类INQ/OFFER自动录入crm_offer：默认每 10 秒检查一次，实际间隔由字典 offerEntryScanSec 控制。 */
    public void scanOfferEntry() {
        try {
            Map<String, Object> result = gateway.offerEntryOnce(null);
            if (Boolean.TRUE.equals(result.get("running"))) {
                log.info("微信INQ/OFFER自动录入跳过: {}", result.get("message"));
            } else if (result.containsKey("scanned")) {
                log.info("微信INQ/OFFER自动录入完成: {}", result.get("message"));
            }
        } catch (Exception e) {
            log.error("定时INQ/OFFER自动录入失败", e);
        }
    }

    /** 任务生成扫描：默认每 10 秒检查一次，实际间隔由 taskGenerateScanSec 控制。 */
    public void scanTaskGenerate() {
        try {
            taskService.generateOnce(null);
        } catch (Exception e) {
            log.error("定时生成微信询报价任务失败", e);
        }
    }

    /** 自动发送扫描：默认每 10 秒检查一次，实际间隔由 taskSendScanSec 控制。 */
    public void scanTaskSend() {
        try {
            taskService.sendOnce(false);
        } catch (Exception e) {
            log.error("定时发送微信询报价任务失败", e);
        }
    }

}
