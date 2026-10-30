package com.ruoyi.quartz.task;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.wexinAuto.service.WeChatGatewayService;
import com.ruoyi.wexinAuto.service.WeChatOfferTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 微信自动化调度：拉取、分类、录入、任务生成、发送、TRACE 和清理各自独立扫描。
 * 扫描间隔统一从 wechat_analysis_config 读取，单执行器运行。
 */
@Slf4j
@Component("weChatClassifyScheduler")
public class WeChatClassifyScheduler {

    private static final String ALLOWED_IPS_KEY = "wechat.scheduler.allowedIps";
    private static final long IP_DENIED_LOG_INTERVAL_MS = 60_000L;

    private final WeChatGatewayService gateway;
    private final WeChatOfferTaskService taskService;
    private final ISysConfigService configService;
    private volatile long lastIpDeniedLogAt;

    public WeChatClassifyScheduler(WeChatGatewayService gateway, WeChatOfferTaskService taskService,
            ISysConfigService configService) {
        this.gateway = gateway;
        this.taskService = taskService;
        this.configService = configService;
    }

    /** 轮询拉取微信消息：默认每 5 秒检查一次，实际拉取间隔由字典 pollIntervalSec 控制。 */
    public void pollMessages() {
        if (!canRunOnCurrentHost("拉取微信消息")) {
            return;
        }
        try {
            gateway.pollOnce(null);
        } catch (Exception e) {
            log.error("定时轮询拉取失败", e);
        }
    }

    /** 未分类消息扫描：默认每 10 秒检查一次，触发条件由字典 classifyTriggerCount / classifyMaxIdleSec 控制。 */
    public void scanUnclassified() {
        if (!canRunOnCurrentHost("微信消息分类")) {
            return;
        }
        try {
            gateway.recoverUnclassified();
        } catch (Exception e) {
            log.error("定时分类未分类消息失败", e);
        }
    }

    /** TRACE 飞书预警补扫。 */
    public void scanTraceAlerts() {
        if (!canRunOnCurrentHost("TRACE预警")) {
            return;
        }
        try {
            gateway.scanTraceAlerts();
        } catch (Exception e) {
            log.error("定时TRACE预警扫描失败", e);
        }
    }

    /** 历史消息清理：默认每 6 小时执行一次。 */
    public void purgeOldMessages() {
        if (!canRunOnCurrentHost("微信历史消息清理")) {
            return;
        }
        try {
            gateway.purgeOldMessages();
        } catch (Exception e) {
            log.error("定时清理历史消息失败", e);
        }
    }

    /** 已分类INQ/OFFER自动录入crm_offer：默认每 10 秒检查一次，实际间隔由字典 offerEntryScanSec 控制。 */
    public void scanOfferEntry() {
        if (!canRunOnCurrentHost("INQ/OFFER自动录入")) {
            return;
        }
        try {
            gateway.offerEntryOnce(null);
        } catch (Exception e) {
            log.error("定时录入INQ/OFFER失败", e);
        }
    }

    /** 任务生成扫描：默认每 10 秒检查一次，实际间隔由 taskGenerateScanSec 控制。 */
    public void scanTaskGenerate() {
        if (!canRunOnCurrentHost("微信询报价任务生成")) {
            return;
        }
        try {
            taskService.generateOnce(null);
        } catch (Exception e) {
            log.error("定时生成微信询报价任务失败", e);
        }
    }

    /** 自动发送扫描：默认每 10 秒检查一次，实际间隔由 taskSendScanSec 控制。 */
    public void scanTaskSend() {
        if (!canRunOnCurrentHost("微信询报价任务发送")) {
            return;
        }
        try {
            taskService.sendOnce(false);
        } catch (Exception e) {
            log.error("定时发送微信询报价任务失败", e);
        }
    }

    private boolean canRunOnCurrentHost(String taskName) {
        Set<String> allowedIps = configuredAllowedIps();
        Set<String> localIps = localIpv4Addresses();
        if (!allowedIps.isEmpty() && !Collections.disjoint(allowedIps, localIps)) {
            return true;
        }
        warnIpDenied(taskName, allowedIps, localIps);
        return false;
    }

    private Set<String> configuredAllowedIps() {
        String value = configService.selectConfigByKey(ALLOWED_IPS_KEY);
        if (StringUtils.isEmpty(value)) {
            return Collections.emptySet();
        }
        Set<String> allowedIps = new LinkedHashSet<>();
        for (String ip : value.split("[,，;；\\s]+")) {
            if (StringUtils.isNotEmpty(ip)) {
                allowedIps.add(ip.trim());
            }
        }
        return allowedIps;
    }

    private Set<String> localIpv4Addresses() {
        Set<String> localIps = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        localIps.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("读取本机内网IP失败", e);
        }
        return localIps;
    }

    private synchronized void warnIpDenied(String taskName, Set<String> allowedIps, Set<String> localIps) {
        long now = System.currentTimeMillis();
        if (now - lastIpDeniedLogAt < IP_DENIED_LOG_INTERVAL_MS) {
            return;
        }
        lastIpDeniedLogAt = now;
        if (allowedIps.isEmpty()) {
            log.warn("微信定时任务[{}]未执行：系统参数 {} 未配置本机IP白名单，本机IPv4={}", taskName,
                    ALLOWED_IPS_KEY, localIps);
            return;
        }
        log.warn("微信定时任务[{}]未执行：本机IPv4={} 不在白名单 {} 中", taskName, localIps, allowedIps);
    }
}
