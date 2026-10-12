package com.ruoyi.feishu.callback;

import com.ruoyi.feishu.config.FeishuProperties;
import com.ruoyi.feishu.core.FeishuMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认消息回调：把收到的文本消息解析为命令并处理。
 * 支持：帮助 / 汇报 / 提问；其余内容当作“对问题的回答”进入后台处理。
 * 这里的回复都发回到消息来源的会话（chat_id）。
 */
@Component
public class DefaultMessageCallback implements FeishuMessageCallback {

    private static final Logger log = LoggerFactory.getLogger(DefaultMessageCallback.class);

    private final FeishuProperties props;
    private final FeishuMessageSender sender;
    private final FeishuTaskExecutor taskExecutor;

    public DefaultMessageCallback(FeishuProperties props, FeishuMessageSender sender, FeishuTaskExecutor taskExecutor) {
        this.props = props;
        this.sender = sender;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void onMessage(FeishuMessage message) {
        String text = message.getTextContent() == null ? "" : message.getTextContent().trim();
        if (text.isEmpty()) {
            return;
        }
        String chatId = message.getChatId();
        String low = text.toLowerCase();

        try {
            if (low.equals("汇报") || low.equals("工作报告") || low.equals("report")) {
                sender.sendTextToChat(chatId, sender.buildReportText());
                return;
            }
            if (low.equals("提问") || low.equals("question")) {
                String question = props.getBotName() + " 来了！今天你想让我先帮你处理哪件事？";
                sender.sendQuestionToChat(chatId, question);
                return;
            }
            if (low.equals("帮助") || low.equals("help") || low.equals("你好") || low.equals("hi") || low.equals("hello")) {
                sender.sendTextToChat(chatId, helpText());
                return;
            }
            // 其余内容当作“对问题的回答”：A/B/C/D 或自定义文字
            String optionKey = null;
            String customText = null;
            if (text.length() == 1 && "abcd".contains(low)) {
                optionKey = low.toUpperCase();
            } else {
                customText = text;
            }
            String reply = taskExecutor.execute(optionKey, customText);
            sender.sendTextToChat(chatId, reply);
        } catch (Exception e) {
            log.error("处理飞书消息失败: {}", text, e);
        }
    }

    private String helpText() {
        return "你好，我是 **" + props.getBotName() + "** 🤖\n"
                + "我可以：\n"
                + "　• 主动汇报工作：回复 `汇报`\n"
                + "　• 向你提问并给出 A/B/C/D 选项：回复 `提问`\n"
                + "　• 你也可以直接回复 `A`/`B`/`C`/`D` 或输入自定义内容，我会转回后台处理\n\n"
                + "试试回复：`汇报` 或 `提问`";
    }
}
