package com.ruoyi.feishu.core;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.feishu.config.FeishuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 飞书开放平台 HTTP 客户端：负责 tenant_access_token 的获取/缓存，以及通用 API 调用。
 * <p>
 * 说明：
 * 1. token 有效期为 2 小时，这里提前 120 秒过期并加锁线程安全刷新；
 * 2. 发送消息走 HTTP API（主动发起），因此即使长连接未开启也仍可发消息；
 * 3. 使用原生 HttpURLConnection，避免引入额外 HTTP 客户端依赖。
 */
@Component
public class FeishuApiClient {

    private static final Logger log = LoggerFactory.getLogger(FeishuApiClient.class);

    private final FeishuProperties props;

    /** 缓存的 tenant_access_token */
    private volatile String tenantAccessToken;
    /** token 过期时间点（毫秒时间戳），提前 120 秒失效 */
    private volatile long tokenExpireAt;

    private final Object tokenLock = new Object();

    public FeishuApiClient(FeishuProperties props) {
        this.props = props;
    }

    /**
     * 获取租户级访问令牌（带缓存与线程安全的预过期刷新）。
     * @return tenant_access_token
     */
    public String getTenantAccessToken() {
        if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireAt) {
            return tenantAccessToken;
        }
        synchronized (tokenLock) {
            // 双重检查，避免并发重复请求
            if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireAt) {
                return tenantAccessToken;
            }
            JSONObject body = new JSONObject();
            body.put("app_id", props.getAppId());
            body.put("app_secret", props.getAppSecret());
            JSONObject resp = request("POST", "/open-apis/auth/v3/tenant_access_token/internal", null, body.toJSONString());
            if (resp == null || !Integer.valueOf(0).equals(resp.getInteger("code"))) {
                throw new IllegalStateException("获取飞书 token 失败: " + (resp == null ? "无响应" : resp.toJSONString()));
            }
            int expire = resp.getInteger("expire") == null ? 7200 : resp.getInteger("expire");
            // 提前 120 秒过期，留出刷新余量
            tenantAccessToken = resp.getString("tenant_access_token");
            tokenExpireAt = System.currentTimeMillis() + (expire - 120) * 1000L;
            log.info("飞书 tenant_access_token 已刷新，有效期 {} 秒", expire);
            return tenantAccessToken;
        }
    }

    /**
     * 封装一次 HTTP 请求（POST / GET），自动附加 Bearer token（token 接口除外）。
     * @param method  HTTP 方法
     * @param path    接口路径，如 /open-apis/im/v1/messages
     * @param query   查询串（不含 ?），可为 null
     * @param json    POST body，可为 null
     * @return 解析后的响应 JSONObject（含 code/msg/data）
     */
    public JSONObject request(String method, String path, String query, String json) {
        try {
            String urlStr = trimEnd(props.getDomain(), "/") + path + (query == null || query.isEmpty() ? "" : "?" + query);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            // token 接口本身用 app_id/app_secret 换取，不需要 Authorization
            if (!path.contains("tenant_access_token")) {
                conn.setRequestProperty("Authorization", "Bearer " + getTenantAccessToken());
            }
            if (json != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int httpCode = conn.getResponseCode();
            InputStream is = httpCode >= 200 && httpCode < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(is);
            conn.disconnect();
            if (log.isDebugEnabled()) {
                log.debug("飞书 {} {} -> {} body={}", method, path, httpCode, body);
            }
            return JSONObject.parseObject(body);
        } catch (Exception e) {
            log.error("飞书 API 调用异常: {} {}", method, path, e);
            return null;
        }
    }

    /**
     * 发送任意类型消息（低层封装）。
     * @param receiveIdType 接收者类型：chat_id / open_id / user_id
     * @param receiveId     接收者 id
     * @param msgType       消息类型：text / interactive 等
     * @param contentJson   消息内容 JSON 字符串
     * @return 发送结果
     */
    public FeishuResult sendMessage(String receiveIdType, String receiveId, String msgType, String contentJson) {
        if (receiveId == null || receiveId.isEmpty()) {
            return FeishuResult.error("接收者 id 为空");
        }
        JSONObject body = new JSONObject();
        body.put("receive_id", receiveId);
        body.put("msg_type", msgType);
        body.put("content", contentJson);
        JSONObject resp = request("POST", "/open-apis/im/v1/messages",
                "receive_id_type=" + receiveIdType, body.toJSONString());
        if (resp == null) {
            return FeishuResult.error("请求飞书无响应");
        }
        if (Integer.valueOf(0).equals(resp.getInteger("code"))) {
            return FeishuResult.success(resp.get("data"));
        }
        return FeishuResult.error(resp.getInteger("code"), resp.getString("msg"));
    }

    /**
     * 发送一条纯文本消息。
     */
    public FeishuResult sendText(String receiveIdType, String receiveId, String text) {
        JSONObject content = new JSONObject();
        content.put("text", text == null ? "" : text);
        return sendMessage(receiveIdType, receiveId, "text", content.toJSONString());
    }

    /**
     * 发送一张自定义 JSON 卡片（interactive）。
     * @param receiveIdType 接收者类型
     * @param receiveId     接收者 id
     * @param cardJson      卡片 JSON 字符串（可直接使用 buildQuestionCard 生成）
     */
    public FeishuResult sendCard(String receiveIdType, String receiveId, String cardJson) {
        return sendMessage(receiveIdType, receiveId, "interactive", cardJson);
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String trimEnd(String s, String suffix) {
        if (s != null && s.endsWith(suffix)) {
            return s.substring(0, s.length() - suffix.length());
        }
        return s;
    }
}
