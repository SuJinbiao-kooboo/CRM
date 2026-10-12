package com.ruoyi.feishu.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.feishu.config.FeishuProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 飞书消息发送的高层封装，供回调处理与 Controller 复用。
 * 提供：发送文本、发送 ABCD 选择题卡片、发送工作汇报，以及交互卡片的构建。
 */
@Component
public class FeishuMessageSender {

    private final FeishuApiClient apiClient;
    private final FeishuProperties props;

    /** ABCD 选项文案（可自行替换 / 扩展） */
    public static final String[] OPTIONS = {"处理今天的邮件", "整理工作汇报", "安排明天的日程", "汇总项目进度"};

    public FeishuMessageSender(FeishuApiClient apiClient, FeishuProperties props) {
        this.apiClient = apiClient;
        this.props = props;
    }

    /**
     * 发送纯文本消息（最常用）。
     * @param receiveIdType chat_id / open_id / user_id
     * @param receiveId     接收者 id
     * @param text          文本内容（飞书支持 markdown 语法）
     */
    public FeishuResult sendText(String receiveIdType, String receiveId, String text) {
        return apiClient.sendText(receiveIdType, receiveId, text);
    }

    /**
     * 快捷：向某个 chat 发送文本。
     */
    public FeishuResult sendTextToChat(String chatId, String text) {
        return sendText("chat_id", chatId, text);
    }

    /**
     * 发送一张“向用户提问 + ABCD 选项”的交互卡片。
     * 说明：当前长连接版本不接收卡片按钮回调，因此卡片展示选项并提示用户“直接回复 A/B/C/D 或自定义文字”。
     * @param receiveIdType 接收者类型
     * @param receiveId     接收者 id
     * @param question      问题正文
     */
    public FeishuResult sendQuestion(String receiveIdType, String receiveId, String question) {
        return apiClient.sendCard(receiveIdType, receiveId,
                buildQuestionCard(question, OPTIONS).toJSONString());
    }

    /**
     * 快捷：向某个 chat 发送选择题卡片。
     */
    public FeishuResult sendQuestionToChat(String chatId, String question) {
        return sendQuestion("chat_id", chatId, question);
    }

    /**
     * 发送工作汇报（文本消息）。
     */
    public FeishuResult sendReport(String receiveIdType, String receiveId) {
        return apiClient.sendText(receiveIdType, receiveId, buildReportText());
    }

    /**
     * 构建 ABCD 选择题卡片 JSON。
     * @param question 问题
     * @param options  选项文案数组（缺省选项用 A/B/C/D 展示）
     */
    public JSONObject buildQuestionCard(String question, String[] options) {
        JSONObject card = new JSONObject();
        JSONObject config = new JSONObject();
        config.put("wide_screen_mode", true);
        card.put("config", config);

        JSONObject header = new JSONObject();
        header.put("template", "blue");
        JSONObject title = new JSONObject();
        title.put("tag", "plain_text");
        title.put("content", props.getBotName() + " 来提问啦");
        header.put("title", title);
        card.put("header", header);

        JSONArray elements = new JSONArray();
        // 问题正文
        JSONObject div = new JSONObject();
        div.put("tag", "div");
        JSONObject md = new JSONObject();
        md.put("tag", "lark_md");
        StringBuilder content = new StringBuilder("**").append(question == null ? "" : question).append("**\n");
        String[] letters = {"A", "B", "C", "D"};
        for (int i = 0; i < options.length; i++) {
            String letter = i < letters.length ? letters[i] : String.valueOf(i + 1);
            content.append("\n").append(letter).append(". ").append(options[i]);
        }
        content.append("\n\n请直接回复 **A / B / C / D** 或输入自定义内容。");
        md.put("content", content.toString());
        div.put("text", md);
        elements.add(div);
        card.put("elements", elements);
        return card;
    }

    /**
     * 构建工作汇报文本。
     */
    public String buildReportText() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "📊 **" + props.getBotName() + " · 工作汇报**\n"
                + "时间：" + now + "\n"
                + "--------------------------------\n"
                + "✅ 今日已完成：\n"
                + "　1. 待接入任务一\n"
                + "　2. 待接入任务二\n"
                + "🔄 进行中：\n"
                + "　1. 待接入\n"
                + "⏭️ 待办：\n"
                + "　1. 待接入\n"
                + "--------------------------------\n"
                + "（示例模板，接入真实数据逻辑后替换 buildReportText）";
    }
}
