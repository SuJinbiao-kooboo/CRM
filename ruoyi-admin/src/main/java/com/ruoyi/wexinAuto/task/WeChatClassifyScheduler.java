package com.ruoyi.wexinAuto.task;

import com.ruoyi.wexinAuto.service.WeChatGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 微信消息定时任务：
 * <ul>
 *   <li>每 5 秒：轮询拉取微信增量消息入库(status=0)，实际间隔由字典 pollIntervalSec 控制(默认10秒)；</li>
 *   <li>每 10 秒：扫描未分类消息，满足 classifyTriggerCount 条或 classifyMaxIdleSec 秒未处理时，
 *       分类最早 classifyBatchSize 条(默认20条)，分类回写后 status=1；</li>
 *   <li>每 30 秒：扫描各会话监听缓冲，超过 aiFlushMinutes 分钟未分类的调起 AI 分类；</li>
 *   <li>每 10 秒：扫描已分类(status=1)的 INQ/OFFER 消息，按字典参数 AI 录入 crm_offer；</li>
 *   <li>每 6 小时：清理超过 msgRetentionDays 天的历史消息。</li>
 * </ul>
 * 所有参数都在数据字典 wechat_analysis_config 配置，改字典即生效。
 */
@Slf4j
@Component
public class WeChatClassifyScheduler {

    private final WeChatGatewayService gateway;

    public WeChatClassifyScheduler(WeChatGatewayService gateway) {
        this.gateway = gateway;
    }

    /** 轮询拉取微信消息：默认每 5 秒检查一次，实际拉取间隔由字典 pollIntervalSec 控制。 */
    @Scheduled(fixedDelayString = "${wechat.poll.scanMs:5000}")
    public void pollMessages() {
        try {
            gateway.pollOnce(null);
        } catch (Exception e) {
            log.error("定时轮询拉取失败", e);
        }
    }

    /** 未分类消息扫描：默认每 10 秒检查一次，触发条件由字典 classifyTriggerCount / classifyMaxIdleSec 控制。 */
    @Scheduled(fixedDelayString = "${wechat.classify.scanMs:10000}")
    public void scanUnclassified() {
        try {
            gateway.recoverUnclassified();
        } catch (Exception e) {
            log.error("定时分类未分类消息失败", e);
        }
    }

    /** 缓冲超时触发：默认每 30 秒扫描一次。 */
    @Scheduled(fixedDelayString = "${wechat.classify.scanMs:30000}")
    public void flushDueBuffers() {
        try {
            gateway.flushDueBuffers();
        } catch (Exception e) {
            log.error("定时扫描缓冲失败", e);
        }
    }

    /** 未分类消息回收：默认每 2 分钟一次。 */
    @Scheduled(fixedDelayString = "${wechat.classify.recoverMs:120000}")
    public void recoverUnclassified() {
        try {
            gateway.recoverUnclassified();
        } catch (Exception e) {
            log.error("定时回收未分类消息失败", e);
        }
    }

    /** 历史消息清理：默认每 6 小时执行一次。 */
    @Scheduled(fixedDelayString = "${wechat.classify.purgeMs:21600000}")
    public void purgeOldMessages() {
        try {
            gateway.purgeOldMessages();
        } catch (Exception e) {
            log.error("定时清理历史消息失败", e);
        }
    }

    /** 已分类INQ/OFFER自动录入crm_offer：默认每 10 秒检查一次，实际间隔由字典 offerEntryScanSec 控制。 */
    @Scheduled(fixedDelayString = "${wechat.offer.entry.scanMs:10000}")
    public void scanOfferEntry() {
        try {
            gateway.offerEntryOnce(null);
        } catch (Exception e) {
            log.error("定时录入INQ/OFFER失败", e);
        }
    }
}
