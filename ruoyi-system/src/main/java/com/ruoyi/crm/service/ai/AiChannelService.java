package com.ruoyi.crm.service.ai;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI 通道统一配置 + 调用服务（全局唯一配置来源）。
 *
 * <p>项目内所有大模型调用（Offer AI 录入、微信消息 AI 分类等）统一走本服务，
 * 配置只维护在系统参数 {@code sys_config}（前缀 sys.ai.），不再分散到数据字典/application.yml：</p>
 * <ul>
 *   <li>主渠道（默认第三方平台）：sys.ai.base.url / sys.ai.api.key / sys.ai.model</li>
 *   <li>备用渠道（主渠道故障自动切换，默认 DeepSeek 官方）：sys.ai.backup.base.url /
 *       sys.ai.backup.api.key / sys.ai.backup.model</li>
 *   <li>切换策略：sys.ai.backup.enabled / sys.ai.fail.threshold / sys.ai.backup.check.sec</li>
 *   <li>总开关与超时：sys.ai.enabled / sys.ai.timeout.ms</li>
 *   <li>故障告警：sys.ai.alert.webhook（飞书机器人，可空）/ sys.ai.alert.cooldown.sec</li>
 * </ul>
 *
 * <p>防呆逻辑：主渠道连续失败达到阈值自动切到备用渠道；备用模式下周期性探测主渠道，
 * 恢复后自动切回；切换/恢复/备用也失败都会推送告警（未配置 webhook 时只记日志）。</p>
 */
@Component
public class AiChannelService {

    private static final Logger log = LoggerFactory.getLogger(AiChannelService.class);

    /** 总开关：1/true=启用AI能力 */
    public static final String KEY_ENABLED = "sys.ai.enabled";
    /** 主渠道接口根地址（不要以 / 结尾） */
    public static final String KEY_BASE_URL = "sys.ai.base.url";
    /** 主渠道 API Key */
    public static final String KEY_API_KEY = "sys.ai.api.key";
    /** 主渠道模型名 */
    public static final String KEY_MODEL = "sys.ai.model";
    /** 备用渠道总开关 */
    public static final String KEY_BACKUP_ENABLED = "sys.ai.backup.enabled";
    /** 备用渠道接口根地址 */
    public static final String KEY_BACKUP_BASE_URL = "sys.ai.backup.base.url";
    /** 备用渠道 API Key（DeepSeek 官方 Key，敏感，仅线上维护） */
    public static final String KEY_BACKUP_API_KEY = "sys.ai.backup.api.key";
    /** 备用渠道模型名 */
    public static final String KEY_BACKUP_MODEL = "sys.ai.backup.model";
    /** 主渠道连续失败切换阈值（次） */
    public static final String KEY_FAIL_THRESHOLD = "sys.ai.fail.threshold";
    /** 备用模式下探测主渠道恢复的间隔（秒） */
    public static final String KEY_BACKUP_CHECK_SEC = "sys.ai.backup.check.sec";
    /** 调用超时（毫秒） */
    public static final String KEY_TIMEOUT_MS = "sys.ai.timeout.ms";
    /** 故障告警飞书 Webhook（可空，空则仅记日志） */
    public static final String KEY_ALERT_WEBHOOK = "sys.ai.alert.webhook";
    /** 同类告警冷却（秒） */
    public static final String KEY_ALERT_COOLDOWN_SEC = "sys.ai.alert.cooldown.sec";

    /** 默认主渠道：第三方聚合平台（OpenAI 兼容） */
    private static final String DEFAULT_BASE_URL = "https://token-factory.hon-maas.com/v1";
    /** 默认主渠道模型 */
    private static final String DEFAULT_MODEL = "deepseek-v4.1-flash-1017";
    /** 默认备用渠道：DeepSeek 官方 */
    private static final String DEFAULT_BACKUP_BASE_URL = "https://api.deepseek.com";
    /** 默认备用渠道模型（DeepSeek 官方模型名） */
    private static final String DEFAULT_BACKUP_MODEL = "deepseek-chat";
    /** 默认连续失败切换阈值 */
    private static final int DEFAULT_FAIL_THRESHOLD = 3;
    /** 默认主渠道恢复探测间隔（秒） */
    private static final int DEFAULT_BACKUP_CHECK_SEC = 300;
    /** 默认调用超时（毫秒）：8分钟，需与前端 axios 超时、nginx proxy_read_timeout 匹配 */
    private static final int DEFAULT_TIMEOUT_MS = 8 * 60 * 1000;
    /** 默认告警冷却（秒） */
    private static final int DEFAULT_ALERT_COOLDOWN_SEC = 600;

    /** 单次请求内部重试次数 */
    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private ISysConfigService sysConfigService;

    /** 当前渠道连续失败次数（跨调用累计，成功后清零） */
    private final AtomicInteger failCount = new AtomicInteger();

    /** 当前是否处于备用渠道 */
    private volatile boolean backupActive = false;

    /** 备用模式下上次探测主渠道的时间戳 */
    private volatile long lastProbeAt = 0L;

    /** 告警冷却：key=事件类型, value=上次发送时间 */
    private final Map<String, Long> alertLastAt = new ConcurrentHashMap<>();

    /** 正在发送中的告警，避免定时扫描与实时分类并发重复推送。 */
    private final Set<String> alertInFlight = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------
    // 配置读取
    // ------------------------------------------------------------------

    /** AI 能力是否可用：总开关开启且主渠道 Key 已配置 */
    public boolean enabled() {
        return configBool(KEY_ENABLED, true) && !isBlank(config(KEY_API_KEY, null));
    }

    /** 主渠道模型名（用于日志展示/单模型场景） */
    public String primaryModel() {
        return config(KEY_MODEL, DEFAULT_MODEL);
    }

    /** 当前生效渠道名：PRIMARY / BACKUP */
    public String currentChannelName() {
        return backupActive ? "BACKUP" : "PRIMARY";
    }

    /** 默认调用超时（毫秒） */
    public int timeoutMs() {
        return configInt(KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
    }

    /** 当前生效渠道的连接信息（base/key/model） */
    private Map<String, String> activeChannel() {
        Map<String, String> channel = new LinkedHashMap<>();
        if (backupActive) {
            String backupKey = config(KEY_BACKUP_API_KEY, null);
            if (!isBlank(backupKey)) {
                channel.put("name", "BACKUP");
                channel.put("base", config(KEY_BACKUP_BASE_URL, DEFAULT_BACKUP_BASE_URL));
                channel.put("key", backupKey);
                channel.put("model", config(KEY_BACKUP_MODEL, DEFAULT_BACKUP_MODEL));
                return channel;
            }
            // 备用渠道未配置 Key（被清空），回退主渠道
            backupActive = false;
        }
        channel.put("name", "PRIMARY");
        channel.put("base", config(KEY_BASE_URL, DEFAULT_BASE_URL));
        channel.put("key", config(KEY_API_KEY, ""));
        channel.put("model", config(KEY_MODEL, DEFAULT_MODEL));
        return channel;
    }

    // ------------------------------------------------------------------
    // 统一调用入口
    // ------------------------------------------------------------------

    /**
     * 以当前生效渠道调用 OpenAI 兼容的 /chat/completions，自动填充 payload.model，
     * 失败自动重试并在达到阈值后切换备用渠道。
     *
     * @param payload   请求体（无需传 model，本方法会按当前渠道覆盖）
     * @param timeoutMs 超时（毫秒），&lt;=0 时使用 sys.ai.timeout.ms
     * @return 响应体文本
     * @throws ServiceException 主/备渠道全部失败时抛出
     */
    public String chatCompletions(Map<String, Object> payload, int timeoutMs) {
        if (payload == null) {
            throw new ServiceException("AI请求体为空");
        }
        if (!enabled()) {
            throw new ServiceException("未配置AI接口参数，请在系统参数配置(" + KEY_API_KEY + ")中配置后重试");
        }
        int timeout = timeoutMs > 0 ? timeoutMs : timeoutMs();
        String lastError = null;
        // 最多两轮：第一轮当前渠道（默认主渠道），若中途切换到备用渠道则立即用备用渠道再试一轮
        for (int round = 0; round < 2; round++) {
            // 备用模式下先尝试探测主渠道是否恢复（恢复后自动切回）
            maybeProbeMainChannel();
            Map<String, String> channel = activeChannel();
            payload.put("model", channel.get("model"));
            String body = JSON.toJSONString(payload);
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                if (attempt > 1) {
                    sleepQuietly(2000L * (attempt - 1));
                }
                try {
                    HttpResponse resp = HttpRequest.post(channel.get("base") + "/chat/completions")
                            .header("Authorization", "Bearer " + channel.get("key"))
                            .header("Content-Type", "application/json")
                            .body(body)
                            .timeout(timeout)
                            .execute();
                    String text = resp.body();
                    if (resp.isOk()) {
                        handleChannelSuccess();
                        return text;
                    }
                    lastError = "HTTP " + resp.getStatus() + " " + errorMessage(text);
                    log.warn("AI接口返回异常({}, 第{}次): {}", channel.get("name"), attempt, lastError);
                } catch (Exception e) {
                    lastError = e.toString();
                    log.warn("AI接口调用异常({}, 第{}次): {}", channel.get("name"), attempt, lastError);
                }
            }
            boolean wasBackup = "BACKUP".equals(channel.get("name"));
            handleChannelFail();
            // 未切换（无可用备用渠道）或本轮已在备用渠道，则不再重试
            if (!backupActive || wasBackup) {
                throw new ServiceException("AI接口调用失败(" + channel.get("name") + ")：" + lastError);
            }
            log.warn("已切换到备用渠道，本轮请求立即改用备用渠道重试");
        }
        throw new ServiceException("AI接口调用失败：主/备渠道均不可用 " + lastError);
    }

    /** 使用默认超时调用 */
    public String chatCompletions(Map<String, Object> payload) {
        return chatCompletions(payload, 0);
    }

    // ------------------------------------------------------------------
    // 渠道故障切换 + 告警
    // ------------------------------------------------------------------

    /** 当前渠道调用成功：连续失败计数清零 */
    private void handleChannelSuccess() {
        failCount.set(0);
    }

    /** 当前渠道调用失败：主渠道连续失败达阈值后切备用渠道；备用也失败则告警 */
    private void handleChannelFail() {
        int f = failCount.incrementAndGet();
        if (backupActive) {
            alert("BACKUP_DOWN", "【AI接口-告警】备用渠道请求失败，AI能力暂不可用。",
                    "备用渠道: " + config(KEY_BACKUP_BASE_URL, "") + " / " + config(KEY_BACKUP_MODEL, ""));
            return;
        }
        if (!configBool(KEY_BACKUP_ENABLED, true)) {
            return;
        }
        int threshold = Math.max(1, configInt(KEY_FAIL_THRESHOLD, DEFAULT_FAIL_THRESHOLD));
        if (f < threshold) {
            return;
        }
        synchronized (this) {
            if (backupActive) {
                return;
            }
            if (isBlank(config(KEY_BACKUP_API_KEY, null))) {
                alert("MAIN_DOWN", "【AI接口-告警】主渠道连续失败 " + f + " 次，但未配置备用渠道 Key，AI能力暂停。",
                        "主渠道: " + config(KEY_BASE_URL, "") + " / " + config(KEY_MODEL, ""));
                return;
            }
            backupActive = true;
            failCount.set(0);
            log.warn("AI主渠道连续失败 {} 次，已切换到备用渠道", f);
            alert("SWITCHED", "【AI接口-告警】主渠道连续失败 " + f + " 次，已自动切换到备用渠道。",
                    "主渠道: " + config(KEY_BASE_URL, "") + " / " + config(KEY_MODEL, "")
                            + "\n备用渠道: " + config(KEY_BACKUP_BASE_URL, "") + " / " + config(KEY_BACKUP_MODEL, ""));
        }
    }

    /** 备用模式下周期性探测主渠道是否恢复；恢复后自动切回并告警 */
    private void maybeProbeMainChannel() {
        if (!backupActive) {
            return;
        }
        long checkMs = Math.max(30, configInt(KEY_BACKUP_CHECK_SEC, DEFAULT_BACKUP_CHECK_SEC)) * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastProbeAt < checkMs) {
            return;
        }
        synchronized (this) {
            if (now - lastProbeAt < checkMs) {
                return;
            }
            lastProbeAt = now;
        }
        if (probeMain()) {
            synchronized (this) {
                backupActive = false;
                failCount.set(0);
            }
            log.info("AI主渠道已恢复，切回主渠道");
            alert("RECOVERED", "【AI接口-恢复】主渠道已恢复，已自动切回主渠道。",
                    "主渠道: " + config(KEY_BASE_URL, "") + " / " + config(KEY_MODEL, ""));
        }
    }

    /** 用主渠道配置发一个最小请求探测连通性（不占用备用渠道状态） */
    private boolean probeMain() {
        String base = config(KEY_BASE_URL, DEFAULT_BASE_URL);
        String key = config(KEY_API_KEY, null);
        String model = config(KEY_MODEL, DEFAULT_MODEL);
        if (isBlank(base) || isBlank(key)) {
            return false;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("max_tokens", 1);
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", "ping");
            payload.put("messages", new Object[] { msg });
            HttpResponse resp = HttpRequest.post(base + "/chat/completions")
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .body(payload.toJSONString())
                    .timeout(12000)
                    .execute();
            return resp.isOk();
        } catch (Exception e) {
            log.debug("主渠道连通性探测失败: {}", e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 告警（飞书 Webhook，可空）
    // ------------------------------------------------------------------

    /**
     * 发送告警：同事件在冷却期内不重复推送；未配置 webhook 时仅记日志。
     *
     * @return true=飞书确认接收成功；false=未配置、冷却中、HTTP失败或飞书返回业务错误
     */
    public boolean alert(String event, String title, String detail) {
        log.warn("{}\n{}", title, detail);
        String webhook = config(KEY_ALERT_WEBHOOK, null);
        if (isBlank(webhook)) {
            return false;
        }
        long cooldownMs = Math.max(0, configInt(KEY_ALERT_COOLDOWN_SEC, DEFAULT_ALERT_COOLDOWN_SEC)) * 1000L;
        long now = System.currentTimeMillis();
        Long last = alertLastAt.get(event);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        if (!alertInFlight.add(event)) {
            return false;
        }
        try {
            Map<String, Object> content = new HashMap<>();
            content.put("text", title + "\n" + detail);
            Map<String, Object> body = new HashMap<>();
            body.put("msg_type", "text");
            body.put("content", content);
            HttpResponse resp = HttpRequest.post(webhook)
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(body))
                    .timeout(10000)
                    .execute();
            if (!resp.isOk()) {
                log.warn("AI告警推送飞书失败: HTTP {}", resp.getStatus());
                return false;
            }
            boolean accepted = feishuAccepted(resp.body());
            if (!accepted) {
                log.warn("AI告警推送飞书未确认成功: {}", resp.body());
            } else {
                // 只有真正发送成功才进入冷却；发送失败必须允许下一轮立即重试。
                alertLastAt.put(event, now);
            }
            return accepted;
        } catch (Exception e) {
            log.warn("AI告警推送飞书异常: {}", e.toString());
            return false;
        } finally {
            alertInFlight.remove(event);
        }
    }

    /** 飞书机器人 HTTP 200 仍可能返回业务错误，必须同时校验 code/StatusCode。 */
    private boolean feishuAccepted(String body) {
        if (isBlank(body)) {
            return false;
        }
        try {
            JSONObject result = JSON.parseObject(body);
            if (result == null) {
                return false;
            }
            Object code = result.get("code");
            if (code != null) {
                return "0".equals(String.valueOf(code).trim());
            }
            Object statusCode = result.get("StatusCode");
            if (statusCode != null) {
                return "0".equals(String.valueOf(statusCode).trim());
            }
            String msg = result.getString("msg");
            return "success".equalsIgnoreCase(msg == null ? "" : msg.trim());
        } catch (Exception e) {
            log.debug("解析飞书告警响应失败: {}", e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    /** 从错误响应体提取 message，提取不到时返回截断后的原文 */
    private String errorMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject root = JSON.parseObject(body);
            JSONObject error = root == null ? null : root.getJSONObject("error");
            if (error != null && error.getString("message") != null) {
                return error.getString("message");
            }
        } catch (Exception ignored) {
            // 非JSON响应，返回原文
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String config(String key, String def) {
        try {
            String v = sysConfigService.selectConfigByKey(key);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        } catch (Exception e) {
            log.warn("读取参数{}失败，使用默认值{}", key, def, e);
        }
        return def;
    }

    private boolean configBool(String key, boolean def) {
        String v = config(key, def ? "true" : "false");
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private int configInt(String key, int def) {
        try {
            String v = config(key, null);
            if (v != null) {
                return Integer.parseInt(v);
            }
        } catch (Exception e) {
            log.warn("读取整数参数{}失败，使用默认值{}", key, def, e);
        }
        return def;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
