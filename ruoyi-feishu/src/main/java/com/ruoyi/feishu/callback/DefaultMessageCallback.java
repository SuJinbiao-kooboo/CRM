package com.ruoyi.feishu.callback;

import com.ruoyi.feishu.codex.CodexRunResult;
import com.ruoyi.feishu.codex.CodexRunner;
import com.ruoyi.feishu.config.FeishuProperties;
import com.ruoyi.feishu.core.FeishuMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 默认消息回调：把收到的文本消息解析为命令并处理。
 * <p>支持：帮助 / 汇报 / 提问 / A/B/C/D 选项；其余自然语言请求会自动交给本机 Codex 实际执行，
 * 并在完成后把结果回传到会话。</p>
 * 这里的回复都发回到消息来源的会话（chat_id）。
 */
@Component
public class DefaultMessageCallback implements FeishuMessageCallback {

    private static final Logger log = LoggerFactory.getLogger(DefaultMessageCallback.class);

    private final FeishuProperties props;
    private final FeishuMessageSender sender;
    private final FeishuTaskExecutor taskExecutor;
    private final CodexRunner codexRunner;

    public DefaultMessageCallback(FeishuProperties props, FeishuMessageSender sender,
                                  FeishuTaskExecutor taskExecutor, CodexRunner codexRunner) {
        this.props = props;
        this.sender = sender;
        this.taskExecutor = taskExecutor;
        this.codexRunner = codexRunner;
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
            // “codex <指令>” 命令：显式交给本机 Codex 非交互执行
            if (isCodexCommand(text, low)) {
                String prompt = text.substring("codex".length()).trim();
                if (prompt.isEmpty()) {
                    sender.sendTextToChat(chatId, "请按格式发送：`codex 你的指令`");
                    return;
                }
                runCodexAsync(chatId, prompt);
                return;
            }
            // 其余内容：
            //  - 单个 A/B/C/D：当作选择题选项，转后台任务处理
            //  - 其它自然语言：自动交给 Codex 实际执行并回传结果
            if (text.length() == 1 && "abcd".contains(low)) {
                String reply = taskExecutor.execute(low.toUpperCase(), null);
                sender.sendTextToChat(chatId, reply);
                return;
            }
            runCodexAsync(chatId, text);
        } catch (Exception e) {
            log.error("处理飞书消息失败: {}", text, e);
        }
    }

    /**
     * 异步交给本机 Codex 执行，先回一句“处理中”，完成后把结果回传。
     * 放在异步线程中执行，避免阻塞飞书事件回调线程。
     */
    private void runCodexAsync(String chatId, String prompt) {
        sender.sendTextToChat(chatId,
                "🤖 收到，正在调用 **Codex** 处理，可能需要几分钟，完成后我会把结果回传～");
        CompletableFuture.runAsync(() -> {
            try {
                CodexRunResult r = codexRunner.run(prompt);
                sender.sendTextToChat(chatId, buildCodexReply(r));
            } catch (Exception e) {
                log.error("Codex 异步执行/回传失败", e);
                sender.sendTextToChat(chatId, "❌ Codex 处理失败：" + e.getMessage());
            }
        });
    }

    /** 判断是否为“codex <指令>”命令（前缀 codex + 空格） */
    private boolean isCodexCommand(String text, String low) {
        return low.startsWith("codex")
                && text.length() > "codex".length()
                && Character.isWhitespace(text.charAt("codex".length()));
    }

    /** 把 Codex 运行结果拼成可发送的文本 */
    private String buildCodexReply(CodexRunResult r) {
        if (r.isSuccess()) {
            String msg = r.getMessage() == null ? "" : r.getMessage().trim();
            msg = truncate(msg, 3500);
            msg = msg.isEmpty() ? "（Codex 没有返回内容）" : msg;
            return "✅ **Codex 执行完成**\n\n" + msg;
        }
        String err = truncate(r.getError() == null ? "" : r.getError(), 2000);
        return "❌ **Codex 执行失败**\n"
                + (r.isTimedOut() ? "（超时）" : "（退出码 " + r.getExitCode() + "）")
                + "\n\n" + err;
    }

    /** 简单截断，避免单条飞书消息过长 */
    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max) + "……（内容过长已截断）";
    }

    private String helpText() {
        return "你好，我是 **" + props.getBotName() + "** 🤖\n"
                + "我可以：\n"
                + "　• 主动汇报工作：回复 `汇报`\n"
                + "　• 向你提问并给出 A/B/C/D 选项：回复 `提问`\n"
                + "　• **直接派活**：直接输入你的需求（比如 `帮我看看某某`），我会调用本机 Codex 实际处理并回传结果\n"
                + "　• 也可显式加前缀：`codex 你的指令`\n\n"
                + "试试回复：`汇报`、`提问`，或直接说 `帮我看看 D 盘有哪些大文件`";
    }
}
