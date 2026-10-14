package com.ruoyi.wexinAuto.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.wexinAuto.analysis.HardRuleClassifier;
import com.ruoyi.wexinAuto.analysis.MessageContext;
import com.ruoyi.wexinAuto.analysis.WeChatLlmClassifier;
import com.ruoyi.wexinAuto.analysis.WeChatMessageClassifier;
import com.ruoyi.wexinAuto.config.WeChatGatewayProperties;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import com.ruoyi.wexinAuto.model.WeChatSendResult;
import com.ruoyi.wexinAuto.msg.WeChatMessageHandler;
import com.ruoyi.wexinAuto.msg.WeChatMessageParser;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 微信消息网关服务。
 * 负责用 ProcessBuilder 拉起 {@code wx_gateway.py}：
 *  <ul>
 *    <li>fetch(...)    —— 按时间(+可选会话)主动拉取，读完 stdout 即返回，不常驻；</li>
 *    <li>start/stop/status —— 实时监听的生命周期管理；每收到一条消息交给
 *        {@link WeChatMessageHandler} 逐条处理。</li>
 *  </ul>
 */
@Service
@Slf4j
public class WeChatGatewayService {

    private final WeChatGatewayProperties props;
    private final WeChatMessageHandler handler;
    private final WeChatLlmClassifier llm;

    private final Object listenLock = new Object();
    private Process listenProcess;
    private Thread listenThread;
    private volatile boolean listening = false;
    private final AtomicLong listenCount = new AtomicLong(0);
    private final AtomicLong filteredCount = new AtomicLong(0);

    /** 分类上下文窗口：参与判断的前 N 条历史消息（后续可移动到数据字典配置） */
    private static final int ANALYSIS_WINDOW = 10;

    /** 监听路径：每个会话最近 N 条已分类消息（供依赖上下文的规则使用；监听线程单线程读写） */
    private final Map<String, Deque<WeChatMessage>> listenHistory = new ConcurrentHashMap<>();

    private final ObjectMapper jackson = new ObjectMapper();

    @Autowired
    public WeChatGatewayService(WeChatGatewayProperties props, WeChatMessageHandler handler,
                                  WeChatLlmClassifier llm) {
        this.props = props;
        this.handler = handler;
        this.llm = llm;
    }

    // ------------------------------------------------------------------
    // 主动拉取：按时间(到秒,闭区间) + 可选群/好友名
    // ------------------------------------------------------------------
    public List<WeChatMessage> fetch(String since, String chat, boolean media, Integer limit) {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getPython());
        cmd.add(props.getScript());
        cmd.add("dump");
        if (StringUtils.isNotBlank(since)) {
            cmd.add("--since");
            cmd.add(since);
        }
        if (StringUtils.isNotBlank(chat)) {
            cmd.add("--chat");
            cmd.add(chat);
        }
        if (media) {
            cmd.add("--media");
            cmd.add(props.getMediaDir());
        }
        return applyAnalysis(runProcess(cmd, true), limit);
    }

    // ------------------------------------------------------------------
    // 实时监听：生命周期
    // ------------------------------------------------------------------
    public Map<String, Object> startListen(boolean media, boolean fresh) {
        synchronized (listenLock) {
            if (listening) {
                return statusMap("已在监听中", true);
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(props.getPython());
            cmd.add(props.getScript());
            cmd.add("listen");
            if (fresh) {
                cmd.add("--fresh");
                listenHistory.clear(); // fresh=只从此刻开始，历史上下文一并清空
            }
            if (media) {
                cmd.add("--media");
                cmd.add(props.getMediaDir());
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                listenProcess = p;
                listening = true;
                listenCount.set(0);
                listenThread = new Thread(() -> readLinesUntilStop(p), "wechat-listen");
                listenThread.setDaemon(true);
                listenThread.start();
                log.info("微信监听已启动：{}", cmd);
                return statusMap("监听已启动", true);
            } catch (Exception e) {
                listening = false;
                log.error("启动微信监听失败", e);
                return statusMap("启动失败: " + e.getMessage(), false);
            }
        }
    }

    public Map<String, Object> stopListen() {
        synchronized (listenLock) {
            if (!listening) {
                return statusMap("当前未在监听", false);
            }
            Process p = listenProcess;
            listening = false;
            if (p != null && p.isAlive()) {
                p.destroy();
            }
            listenProcess = null;
            log.info("微信监听已停止，共处理 {} 条", listenCount.get());
            return statusMap("监听已停止", false);
        }
    }

    public Map<String, Object> listenStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", listening);
        m.put("processed", listenCount.get());
        m.put("filtered", filteredCount.get());
        return m;
    }

    public boolean isListening() {
        return listening;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------
    private void readLinesUntilStop(Process p) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (WeChatMessageParser.isJsonLine(line)) {
                    dispatch(WeChatMessageParser.parse(line));
                } else if (StringUtils.isNotBlank(line)) {
                    log.info("[wx_gateway.listen] {}", line);
                }
            }
        } catch (IOException e) {
            if (listening) {
                log.error("读取监听输出失败", e);
            }
        } finally {
            synchronized (listenLock) {
                listening = false;
                listenProcess = null;
            }
        }
    }

    private void dispatch(WeChatMessage msg) {
        try {
            if (!passAnalysis(msg)) {
                filteredCount.incrementAndGet();
                log.info("过滤器丢弃消息: name={}, serverId={}", msg.getName(), msg.getServerId());
                return;
            }
            // 同会话最近 N 条已分类消息作为上下文，供依赖历史的规则使用
            String key = msg.getUsername();
            Deque<WeChatMessage> dq = listenHistory.get(key);
            List<WeChatMessage> history = dq == null ? Collections.emptyList() : new ArrayList<>(dq);
            String hard = HardRuleClassifier.classify(msg);
            if (hard != null) {
                msg.setMsgCategory(hard);
                msg.setCategorySource("HARD");
            } else {
                msg.setMsgCategory(WeChatMessageClassifier.classify(new MessageContext(msg, history)));
                msg.setCategorySource("RULE");
            }
            Deque<WeChatMessage> dq2 = listenHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
            dq2.addLast(msg);
            while (dq2.size() > ANALYSIS_WINDOW) {
                dq2.removeFirst();
            }
            handler.handle(msg);
            listenCount.incrementAndGet();
        } catch (Exception e) {
            log.error("处理微信消息失败: {}", e.getMessage(), e);
            // 单条失败不中断监听
        }
    }

    private List<WeChatMessage> runProcess(List<String> cmd, boolean collect) {
        List<WeChatMessage> out = collect ? new ArrayList<>() : null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (WeChatMessageParser.isJsonLine(line)) {
                        if (out != null) {
                            out.add(WeChatMessageParser.parse(line));
                        }
                    } else if (StringUtils.isNotBlank(line)) {
                        log.info("[wx_gateway] {}", line);
                    }
                }
            }
            int code = p.waitFor();
            log.info("wx_gateway 进程退出码: {}", code);
        } catch (Exception e) {
            log.error("wx_gateway 执行失败", e);
            throw new IllegalStateException("微信网关执行失败: " + e.getMessage(), e);
        }
        return out == null ? new ArrayList<>() : out;
    }

    private Map<String, Object> statusMap(String msg, boolean running) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", msg);
        m.put("running", running);
        m.put("processed", listenCount.get());
        m.put("filtered", filteredCount.get());
        return m;
    }

    // ------------------------------------------------------------------
    // 批量发送：拟人节奏 + 安全参数全部来自配置字典
    // ------------------------------------------------------------------
    /** 发送参数数据字典类型（sys_dict_type.dict_type） */
    private static final String SEND_DICT_TYPE = "wechat_send_config";

    // ------------------------------------------------------------------
    // 消息分析：第一层(黑/白名单按 name 过滤) + 第二层(INQ/OFFER/TRACE/OTHER 打标签)
    // ------------------------------------------------------------------
    /** 消息分析参数数据字典类型（sys_dict_type.dict_type） */
    private static final String ANALYSIS_DICT_TYPE = "wechat_analysis_config";

    /** 读取字典里的名单（dict_label=whiteNames/blackNames，逗号拼接） */
    private Set<String> analysisNames(String label) {
        Set<String> names = new HashSet<>();
        List<SysDictData> list = DictUtils.getDictCache(ANALYSIS_DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
                    for (String n : d.getDictValue().split(",")) {
                        if (StringUtils.isNotBlank(n)) {
                            names.add(n.trim());
                        }
                    }
                }
            }
        }
        return names;
    }

    /** 第一层过滤器：白名单非空=只放行白名单；否则只过滤黑名单，其余放行。 */
    private boolean passAnalysis(WeChatMessage m) {
        if (m == null) {
            return false;
        }
        Set<String> white = analysisNames("whiteNames");
        Set<String> black = analysisNames("blackNames");
        String name = StringUtils.trimToEmpty(m.getName());
        if (!white.isEmpty()) {
            return white.contains(name);
        }
        return !black.contains(name);
    }

    /** 应用两层分析：过滤 + 业务类型打标签。 */
    private List<WeChatMessage> applyAnalysis(List<WeChatMessage> list, Integer limitPerChat) {
        List<WeChatMessage> out = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return out;
        }
        // 按会话分组，组内按时间正序，逐条分类；分类管线：
        //   缓存命中 → 硬规则(HARD) → 大模型精判(LLM，仅灰色地带) → 规则链兜底(RULE)
        Map<String, List<WeChatMessage>> byChat = new LinkedHashMap<>();
        for (WeChatMessage m : list) {
            String key = m.getUsername() == null ? "" : m.getUsername();
            byChat.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        for (List<WeChatMessage> chatMsgs : byChat.values()) {
            chatMsgs.sort(Comparator.comparingLong(WeChatMessage::getSortSeq));
            if (limitPerChat != null && limitPerChat > 0 && chatMsgs.size() > limitPerChat) {
                chatMsgs = new ArrayList<>(chatMsgs.subList(chatMsgs.size() - limitPerChat, chatMsgs.size()));
            }
            String chatName = chatMsgs.get(0).getName();
            Deque<WeChatMessage> window = new ArrayDeque<>();
            List<WeChatMessage> grays = new ArrayList<>();
            for (WeChatMessage m : chatMsgs) {
                if (!passAnalysis(m)) {
                    continue;
                }
                String cached = llm.cached(m);
                if (cached != null) {
                    m.setMsgCategory(cached);
                    m.setCategorySource("CACHE");
                } else {
                    String hard = HardRuleClassifier.classify(m);
                    if (hard != null) {
                        m.setMsgCategory(hard);
                        m.setCategorySource("HARD");
                    } else {
                        grays.add(m);
                    }
                }
                window.addLast(m);
                while (window.size() > ANALYSIS_WINDOW) {
                    window.removeFirst();
                }
                out.add(m);
            }
            // 灰色地带交给大模型精判（整群消息作为上下文，只覆盖灰色条目）
            if (!grays.isEmpty() && llm.enabled()) {
                llm.classify(chatMsgs, chatName);
                for (WeChatMessage m : grays) {
                    if (m.getMsgCategory() != null) {
                        m.setCategorySource("LLM");
                    }
                }
            }
            // 大模型不可用/无结果时回退规则链（尽力而为，不阻塞返回）
            for (WeChatMessage m : grays) {
                if (m.getMsgCategory() == null) {
                    m.setMsgCategory(WeChatMessageClassifier.classify(m));
                    m.setCategorySource("RULE");
                }
            }
        }
        return out;
    }

    public List<WeChatSendResult> batchSend(List<String> names, String template) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("names", names);
        if (template != null && !template.trim().isEmpty()) {
            cfg.put("template", template);
        }
        // 拟人节奏 + 安全参数：优先读 RuoYi 数据字典(wechat_send_config)，
        // 字典缺失该项时回退到 wechat.send.* 的 @Value 默认值。
        cfg.put("rhythm", sendCfg("rhythm", props.getSendRhythm()));
        cfg.put("nap", Arrays.asList(sendDouble("napMin", props.getSendNapMin()),
                                     sendDouble("napMax", props.getSendNapMax())));
        cfg.put("interval", Arrays.asList(sendDouble("intervalMin", props.getSendIntervalMin()),
                                          sendDouble("intervalMax", props.getSendIntervalMax())));
        cfg.put("max_batch", sendInt("maxBatch", props.getSendMaxBatch()));
        cfg.put("verify", sendBool("verify", props.isSendVerify()));
        return runBatchSend(cfg);
    }

    /** 读取数据字典键值（dict_label=参数名，dict_value=参数值）；查不到返回默认值。 */
    private String sendCfg(String key, String def) {
        List<SysDictData> list = DictUtils.getDictCache(SEND_DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (key.equals(d.getDictLabel())) {
                    String v = d.getDictValue();
                    if (v != null && !v.trim().isEmpty()) {
                        return v.trim();
                    }
                }
            }
        }
        return def;
    }

    private double sendDouble(String key, double def) {
        try {
            return Double.parseDouble(sendCfg(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int sendInt(String key, int def) {
        try {
            return Integer.parseInt(sendCfg(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private boolean sendBool(String key, boolean def) {
        String v = sendCfg(key, String.valueOf(def));
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private List<WeChatSendResult> runBatchSend(Map<String, Object> cfg) {
        List<WeChatSendResult> out = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(props.getPython(), props.getScript(), "batch_send");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
                w.write(jackson.writeValueAsString(cfg));
                w.newLine();
                w.flush();
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (WeChatMessageParser.isJsonLine(line)) {
                        try {
                            out.add(jackson.readValue(line, WeChatSendResult.class));
                        } catch (IOException e) {
                            log.warn("解析发送结果失败: {}", line, e);
                        }
                    } else if (StringUtils.isNotBlank(line)) {
                        log.info("[wx_gateway.batch_send] {}", line);
                    }
                }
            }
            int code = p.waitFor();
            log.info("wx_gateway batch_send 退出码: {}", code);
        } catch (Exception e) {
            log.error("批量发送执行失败", e);
            throw new IllegalStateException("批量发送失败: " + e.getMessage(), e);
        }
        return out;
    }
}





