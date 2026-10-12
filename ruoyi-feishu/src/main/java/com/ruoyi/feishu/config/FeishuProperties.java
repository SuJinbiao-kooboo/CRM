package com.ruoyi.feishu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书机器人配置项，对应 application.yml 中的 feishu: 前缀。
 * 所有字段均可在配置文件中覆盖，便于按环境切换。
 */
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {

    /** 是否随项目启动自动开启飞书机器人（长连接）。false 时默认关闭，可后续通过接口开启。 */
    private boolean enabled = true;

    /** 飞书自建应用的 App ID */
    private String appId = "";

    /** 飞书自建应用的 App Secret */
    private String appSecret = "";

    /** 机器人展示名称 */
    private String botName = "Eke";

    /** 飞书开放平台地址（国内用 open.feishu.cn，国际版 Lark 可换 larksuite.com 域名） */
    private String domain = "https://open.feishu.cn";

    /** 定时自动汇报间隔（分钟），0 表示关闭自动汇报 */
    private long reportIntervalMinutes = 0;

    /** 自测目标：接收测试消息的会话 id（chat_id / open_id / user_id） */
    private String selfTestReceiveId = "";

    /** 自测目标接收类型：chat_id / open_id / user_id */
    private String selfTestReceiveType = "chat_id";

    /** “飞书指挥 Codex”子配置 */
    private Codex codex = new Codex();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getBotName() { return botName; }
    public void setBotName(String botName) { this.botName = botName; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public long getReportIntervalMinutes() { return reportIntervalMinutes; }
    public void setReportIntervalMinutes(long reportIntervalMinutes) { this.reportIntervalMinutes = reportIntervalMinutes; }

    public String getSelfTestReceiveId() { return selfTestReceiveId; }
    public void setSelfTestReceiveId(String selfTestReceiveId) { this.selfTestReceiveId = selfTestReceiveId; }

    public String getSelfTestReceiveType() { return selfTestReceiveType; }
    public void setSelfTestReceiveType(String selfTestReceiveType) { this.selfTestReceiveType = selfTestReceiveType; }

    public Codex getCodex() { return codex; }
    public void setCodex(Codex codex) { this.codex = codex; }

    /** 配置是否具备启动条件 */
    public boolean isReady() {
        return appId != null && !appId.isEmpty() && appSecret != null && !appSecret.isEmpty();
    }

    /**
     * “飞书指挥 Codex”子配置，对应 feishu.codex: 前缀。
     */
    @Data
    public static class Codex {

        /** 是否启用“飞书指挥 Codex”：false 时收到 codex 指令会当作普通回答处理 */
        private boolean enabled = true;

        /** codex 可执行文件路径；默认使用 PATH 中的 codex */
        private String bin = "codex";

        /** Codex 的工作目录（通常是目标项目根目录） */
        private String workdir = "C:/Project/RuoYi-Vue";

        /** 沙箱模式：read-only / workspace-write / danger-full-access */
        private String sandbox = "danger-full-access";

        /** 单次执行超时（秒），0 表示使用默认 300 秒 */
        private long timeoutSeconds = 300;
    }
}
