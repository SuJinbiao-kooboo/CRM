package com.ruoyi.feishu.core;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.feishu.config.FeishuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    /**
     * 上传文件到飞书，换取 file_key（发送文件消息前必须先上传）。
     * @param fileType 文件类型：stream（通用） / pdf / doc / xls / ppt / opus / mp4
     * @param fileName 展示用文件名
     * @param file     本地文件路径
     * @return 上传结果，成功后 data 为 file_key 字符串
     */
    public FeishuResult uploadFile(String fileType, String fileName, Path file) {
        String boundary = "----FeishuBoundary" + Long.toHexString(System.currentTimeMillis());
        try {
            byte[] fileBytes;
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                fileBytes = bos.toByteArray();
            }

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(body, StandardCharsets.UTF_8));
            // 文件类型字段
            writeFormField(writer, boundary, "file_type", fileType, null);
            // 文件名
            writeFormField(writer, boundary, "file_name", fileName, null);
            // 关闭 writer 前先把文件二进制写入（writer 与 body 共用底层流，需要注意顺序）
            // 这里先把文本部分 flush，再直接操作 body
            writer.flush();
            // 文件内容分块
            body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(fileBytes);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
            // 结束边界
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            body.flush();

            String urlStr = trimEnd(props.getDomain(), "/") + "/open-apis/im/v1/files";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + getTenantAccessToken());
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Content-Length", String.valueOf(body.size()));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toByteArray());
            }
            int httpCode = conn.getResponseCode();
            InputStream is = httpCode >= 200 && httpCode < 300 ? conn.getInputStream() : conn.getErrorStream();
            String respBody = readAll(is);
            conn.disconnect();
            JSONObject resp = JSONObject.parseObject(respBody);
            if (resp != null && Integer.valueOf(0).equals(resp.getInteger("code"))) {
                String key = resp.getJSONObject("data") == null ? null : resp.getJSONObject("data").getString("file_key");
                if (key == null || key.isEmpty()) {
                    return FeishuResult.error("上传成功但未返回 file_key: " + respBody);
                }
                return FeishuResult.success(key);
            }
            return FeishuResult.error(resp == null ? ("HTTP " + httpCode) : (resp.getInteger("code") + " " + resp.getString("msg")));
        } catch (Exception e) {
            log.error("飞书上传文件失败: {}", file, e);
            return FeishuResult.error("上传文件异常: " + e.getMessage());
        }
    }

    /** 写入一个 multipart 文本字段 */
    private static void writeFormField(BufferedWriter w, String boundary, String name, String value, String fileName) throws Exception {
        if (fileName == null) {
            w.write("--" + boundary + "\r\n");
            w.write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
            w.write(value);
            w.write("\r\n");
        } else {
            w.write("--" + boundary + "\r\n");
            w.write("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n");
            w.write("Content-Type: application/octet-stream\r\n\r\n");
            w.write(value);
            w.write("\r\n");
        }
        w.flush();
    }

    /**
     * 把本地文件作为“文件消息”发送给某个会话。
     * 内部：先上传拿 file_key，再通过 im/v1/messages 发送 msg_type=file。
     * @param receiveIdType chat_id / open_id / user_id
     * @param receiveId     接收者 id
     * @param file          本地文件路径
     * @return 发送结果
     */
    public FeishuResult sendFile(String receiveIdType, String receiveId, Path file) {
        if (file == null || !Files.exists(file)) {
            return FeishuResult.error("待发送文件不存在: " + (file == null ? "null" : file.toString()));
        }
        String fileName = file.getFileName().toString();
        String fileType = guessFileType(fileName);
        try {
            FeishuResult up = uploadFile(fileType, fileName, file);
            if (!up.isSuccess()) {
                return up;
            }
            String fileKey = String.valueOf(up.getData());
            JSONObject content = new JSONObject();
            content.put("file_key", fileKey);
            return sendMessage(receiveIdType, receiveId, "file", content.toJSONString());
        } catch (Exception e) {
            log.error("飞书发送文件失败: {}", file, e);
            return FeishuResult.error("发送文件异常: " + e.getMessage());
        }
    }

    /** 根据扩展名推断飞书要求的 file_type，默认 stream（通用文件） */
    private static String guessFileType(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (name.endsWith(".pdf")) return "pdf";
        if (name.endsWith(".doc") || name.endsWith(".docx")) return "doc";
        if (name.endsWith(".xls") || name.endsWith(".xlsx")) return "xls";
        if (name.endsWith(".ppt") || name.endsWith(".pptx")) return "ppt";
        if (name.endsWith(".opus")) return "opus";
        if (name.endsWith(".mp4")) return "mp4";
        return "stream";
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
