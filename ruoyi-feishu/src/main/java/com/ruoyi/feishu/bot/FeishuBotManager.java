package com.ruoyi.feishu.bot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.ws.Client;
import com.ruoyi.feishu.callback.FeishuMessage;
import com.ruoyi.feishu.callback.FeishuMessageCallback;
import com.ruoyi.feishu.config.FeishuProperties;
import com.ruoyi.feishu.core.FeishuMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.LinkedHashMap;

/**
 * 飞书机器人生命周期管理器（核心）。
 * <p>
 * - 随项目启动：enabled=true 且配置就绪时，@PostConstruct 自动建立长连接；
 * - 提供 start() / stop() / isRunning() / getStatus() 供接口控制开启与关闭；
 * - 收到消息后统一封装为 {@link FeishuMessage} 并分发到所有 {@link FeishuMessageCallback}。
 */
@Component
public class FeishuBotManager {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotManager.class);

    private final FeishuProperties props;
    private final FeishuMessageSender sender;
    private final List<FeishuMessageCallback> callbacks;

    /** 当前长连接客户端（仅用于内存引用 / 停止时反射关闭） */
    private volatile Client wsClient;
    /** 运行状态 */
    private volatile boolean running = false;
    /** 最近一次错误信息，便于排查 */
    private volatile String lastError = "";

    private final Object lock = new Object();

    /** 事件分发线程池（daemon，避免阻塞飞书 ws 接收线程） */
    private final ExecutorService eventExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "feishu-event-dispatcher");
                t.setDaemon(true);
                return t;
            });

    /** 定时汇报线程 */
    private volatile Thread reportThread;

    public FeishuBotManager(FeishuProperties props, FeishuMessageSender sender, List<FeishuMessageCallback> callbacks) {
        this.props = props;
        this.sender = sender;
        this.callbacks = callbacks;
    }

    /**
     * 随项目启动：若配置开启且就绪，则自动连接飞书长连接。
     */
    @PostConstruct
    public void onApplicationStart() {
        if (!props.isEnabled()) {
            log.info("飞书机器人配置 feishu.enabled=false，本次不自动启动。");
            return;
        }
        if (!props.isReady()) {
            log.warn("飞书缺少 appId/appSecret，无法自动启动。");
            return;
        }
        start();
    }

    /**
     * 关闭应用时自动断开长连接。
     */
    @PreDestroy
    public void onApplicationStop() {
        stop();
        eventExecutor.shutdownNow();
    }

    /**
     * 开启飞书机器人（长连接）。
     * @return 是否启动成功
     */
    public boolean start() {
        synchronized (lock) {
            if (running) {
                return true;
            }
            if (!props.isReady()) {
                lastError = "缺少 feishu.app-id / feishu.app-secret";
                log.warn(lastError);
                return false;
            }
            try {
                EventDispatcher dispatcher = new EventDispatcher.Builder("", "")
                        .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                            @Override
                            public void handle(P2MessageReceiveV1 event) {
                                dispatchEvent(event);
                            }
                        })
                        .build();

                Client client = new Client.Builder(props.getAppId(), props.getAppSecret())
                        .eventHandler(dispatcher)
                        .autoReconnect(true)
                        .domain(props.getDomain())
                        .build();

                // 在 daemon 线程中建立连接，避免阻塞 Spring 启动
                Thread connectThread = new Thread(() -> {
                    try {
                        log.info("飞书机器人开始建立长连接...");
                        client.start();
                    } catch (Exception e) {
                        lastError = "长连接建立异常: " + e.getMessage();
                        log.error(lastError, e);
                    }
                }, "feishu-ws");
                connectThread.setDaemon(true);
                connectThread.start();

                wsClient = client;
                running = true;
                lastError = "";
                log.info("飞书机器人已开启（长连接）。");
                startReportThread();
                return true;
            } catch (Exception e) {
                lastError = "开启失败: " + e.getMessage();
                log.error(lastError, e);
                return false;
            }
        }
    }

    /**
     * 关闭飞书机器人（断开长连接）。
     * 说明：SDK 的 Client 仅暴露 start()，停止通过反射调用其 protected disconnect() 实现。
     */
    public void stop() {
        synchronized (lock) {
            stopReportThread();
            Client c = wsClient;
            wsClient = null;
            running = false;
            if (c != null) {
                try {
                    Method m = Client.class.getDeclaredMethod("disconnect");
                    m.setAccessible(true);
                    m.invoke(c);
                    log.info("飞书机器人已关闭（长连接已断开）。");
                } catch (Exception e) {
                    log.warn("关闭长连接时出现异常（可忽略，进程退出会自行回收）: {}", e.getMessage());
                }
            }
        }
    }

    /** 当前是否运行中 */
    public boolean isRunning() {
        return running;
    }

    /**
     * 运行状态快照，供状态接口返回。
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", running);
        m.put("enabled", props.isEnabled());
        m.put("ready", props.isReady());
        m.put("botName", props.getBotName());
        m.put("appId", mask(props.getAppId()));
        m.put("domain", props.getDomain());
        m.put("reportIntervalMinutes", props.getReportIntervalMinutes());
        m.put("lastError", lastError);
        return m;
    }

    /**
     * 飞书长连接事件回调：收到消息后封装并异步分发给所有回调实现。
     */
    private void dispatchEvent(P2MessageReceiveV1 event) {
        P2MessageReceiveV1Data data = event.getEvent();
        if (data == null) {
            return;
        }
        FeishuMessage msg = toMessage(data);
        if (msg.getChatId() == null || msg.getChatId().isEmpty()) {
            return;
        }
        // 忽略机器人/系统自身的消息，避免自问自答
        if ("app".equals(msg.getSenderType()) || "system".equals(msg.getSenderType())) {
            return;
        }
        eventExecutor.submit(() -> {
            for (FeishuMessageCallback cb : callbacks) {
                try {
                    if (cb.supports(msg.getMessageType())) {
                        cb.onMessage(msg);
                    }
                } catch (Exception e) {
                    log.error("回调处理消息失败: {}", cb.getClass().getName(), e);
                }
            }
        });
    }

    /**
     * 把 SDK 事件模型转换成统一的 {@link FeishuMessage}。
     */
    private FeishuMessage toMessage(P2MessageReceiveV1Data data) {
        FeishuMessage m = new FeishuMessage();
        EventMessage em = data.getMessage();
        if (em != null) {
            m.setMessageId(em.getMessageId());
            m.setChatId(em.getChatId());
            m.setChatType(em.getChatType());
            m.setMessageType(em.getMessageType());
            m.setContent(em.getContent());
            if ("text".equals(em.getMessageType()) && em.getContent() != null) {
                try {
                    JSONObject o = JSON.parseObject(em.getContent());
                    m.setTextContent(o.getString("text"));
                } catch (Exception ignore) {
                    m.setTextContent(em.getContent());
                }
            }
        }
        EventSender es = data.getSender();
        if (es != null) {
            m.setSenderType(es.getSenderType());
            if (es.getSenderId() != null) {
                m.setSenderId(es.getSenderId().getOpenId() != null ? es.getSenderId().getOpenId() : es.getSenderId().getUserId());
            }
        }
        return m;
    }

    /**
     * 定时自动汇报：间隔大于 0 且配置了自测接收者时生效。
     */
    private void startReportThread() {
        stopReportThread();
        long interval = props.getReportIntervalMinutes();
        if (interval <= 0 || props.getSelfTestReceiveId() == null || props.getSelfTestReceiveId().isEmpty()) {
            return;
        }
        reportThread = new Thread(() -> {
            long millis = interval * 60_000L;
            try {
                Thread.sleep(millis); // 首次运行先按间隔等待
            } catch (InterruptedException ie) {
                return;
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    sender.sendReport(props.getSelfTestReceiveType(), props.getSelfTestReceiveId());
                    log.info("定时汇报已发送");
                } catch (Exception e) {
                    log.error("定时汇报失败", e);
                }
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }, "feishu-report");
        reportThread.setDaemon(true);
        reportThread.start();
    }

    private void stopReportThread() {
        if (reportThread != null) {
            reportThread.interrupt();
            reportThread = null;
        }
    }

    /** 隐藏 appId 中间部分，仅用于展示，避免泄露完整信息 */
    private String mask(String appId) {
        if (appId == null || appId.length() <= 4) {
            return "****";
        }
        return appId.substring(0, 2) + "***" + appId.substring(appId.length() - 2);
    }
}
