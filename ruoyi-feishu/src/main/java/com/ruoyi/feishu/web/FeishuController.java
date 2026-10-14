package com.ruoyi.feishu.web;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.feishu.bot.FeishuBotManager;
import com.ruoyi.feishu.codex.CodexRunResult;
import com.ruoyi.feishu.codex.CodexRunner;
import com.ruoyi.feishu.config.FeishuProperties;
import com.ruoyi.feishu.core.FeishuMessageSender;
import com.ruoyi.feishu.core.FeishuResult;
import com.ruoyi.feishu.dto.CodexRunBody;
import com.ruoyi.feishu.dto.FeishuSendFileBody;
import com.ruoyi.feishu.dto.FeishuReceiveBody;
import com.ruoyi.feishu.dto.FeishuSendQuestionBody;
import com.ruoyi.feishu.dto.FeishuSendReportBody;
import com.ruoyi.feishu.dto.FeishuSendTextBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 飞书机器人控制接口。
 * <p>
 * 类上加了 {@link Anonymous}，因此这些接口免登录、免权限，可直接调用（适合自测/集成）。
 * 若需要收紧访问控制，请去掉 @Anonymous 并按需补回 @PreAuthorize 权限注解。
 * </p>
 */
@Anonymous
@RestController
@RequestMapping("/feishu")
public class FeishuController extends BaseController {

    private final FeishuBotManager botManager;
    private final FeishuMessageSender sender;
    private final FeishuProperties props;
    private final CodexRunner codexRunner;

    public FeishuController(FeishuBotManager botManager, FeishuMessageSender sender,
                            FeishuProperties props, CodexRunner codexRunner) {
        this.botManager = botManager;
        this.sender = sender;
        this.props = props;
        this.codexRunner = codexRunner;
    }

    /**
     * 查询机器人运行状态（是否在线、配置摘要、最近错误等）。
     */
    @GetMapping("/bot/status")
    public AjaxResult status() {
        return AjaxResult.success(botManager.getStatus());
    }

    /**
     * 开启机器人（建立长连接）。
     */
    @PostMapping("/bot/start")
    public AjaxResult start() {
        boolean ok = botManager.start();
        return ok
                ? AjaxResult.success("机器人已开启/已在运行中")
                : AjaxResult.error(String.valueOf(botManager.getStatus().get("lastError")));
    }

    /**
     * 关闭机器人（断开长连接）。
     */
    @PostMapping("/bot/stop")
    public AjaxResult stop() {
        botManager.stop();
        return AjaxResult.success("机器人已关闭");
    }

    /**
     * 发送一条纯文本消息。
     * @param body 示例：{"receiveIdType":"chat_id","receiveId":"oc_xxx","text":"你好"}
     */
    @PostMapping("/message/sendText")
    public AjaxResult sendText(@RequestBody FeishuSendTextBody body) {
        String type = resolveType(body);
        String id = resolveId(body);
        String text = isBlank(body.getText())
                ? "自测消息：" + props.getBotName()
                : body.getText().trim();
        return toAjax(sender.sendText(type, id, text));
    }

    /**
     * 发送一张 ABCD 选择题卡片。
     * @param body 示例：{"receiveIdType":"chat_id","receiveId":"oc_xxx","question":"今天先处理哪件？"}
     */
    @PostMapping("/message/sendQuestion")
    public AjaxResult sendQuestion(@RequestBody FeishuSendQuestionBody body) {
        String type = resolveType(body);
        String id = resolveId(body);
        String question = isBlank(body.getQuestion())
                ? props.getBotName() + " 来了！今天你想让我先帮你处理哪件事？"
                : body.getQuestion().trim();
        return toAjax(sender.sendQuestion(type, id, question));
    }

    /**
     * 发送一份工作汇报（文本）。
     * @param body 示例：{"receiveIdType":"chat_id","receiveId":"oc_xxx"}
     */
    @PostMapping("/message/sendReport")
    public AjaxResult sendReport(@RequestBody FeishuSendReportBody body) {
        String type = resolveType(body);
        String id = resolveId(body);
        return toAjax(sender.sendReport(type, id));
    }

    /**
     * 触发一次 Codex 非交互执行（自测用）。
     * @param body 示例：{"prompt":"帮我看看项目里的飞书模块代码结构"}
     */
    @PostMapping("/codex/run")
    public AjaxResult runCodex(@RequestBody CodexRunBody body) {
        if (body == null || body.getPrompt() == null || body.getPrompt().trim().isEmpty()) {
            return AjaxResult.error("指令(prompt)不能为空");
        }
        CodexRunResult r = codexRunner.run(body.getPrompt().trim());
        return r.isSuccess() ? AjaxResult.success(r.getMessage()) : AjaxResult.error(r.getError());
    }

    /**
     * 发送一个本地文件作为“文件消息”（自测用）。
     * @param body 示例：{"receiveIdType":"chat_id","receiveId":"oc_xxx","filePath":"C:/tmp/report.docx"}
     */
    @PostMapping("/message/sendFile")
    public AjaxResult sendFile(@RequestBody FeishuSendFileBody body) {
        String type = resolveType(body);
        String id = resolveId(body);
        if (body.getFilePath() == null || body.getFilePath().trim().isEmpty()) {
            return AjaxResult.error("文件路径(filePath)不能为空");
        }
        if (!Files.exists(Paths.get(body.getFilePath().trim()))) {
            return AjaxResult.error("文件不存在: " + body.getFilePath());
        }
        FeishuResult r = sender.sendFile(type, id, Paths.get(body.getFilePath().trim()));
        return r.isSuccess() ? AjaxResult.success(r.getMsg(), r.getData()) : AjaxResult.error(r.getMsg());
    }

    /* ---------- 私有辅助 ---------- */

    /** 接收者类型：body 未传时回退到配置项 self-test-receive-type */
    private String resolveType(FeishuReceiveBody body) {
        return isBlank(body.getReceiveIdType()) ? props.getSelfTestReceiveType() : body.getReceiveIdType().trim();
    }

    /** 接收者 id：body 未传时回退到配置项 self-test-receive-id（仍为空则由下层返回错误） */
    private String resolveId(FeishuReceiveBody body) {
        return isBlank(body.getReceiveId()) ? props.getSelfTestReceiveId() : body.getReceiveId().trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 把 FeishuResult 转为 RuoYi 的 AjaxResult */
    private AjaxResult toAjax(FeishuResult r) {
        return r.isSuccess() ? AjaxResult.success(r.getMsg(), r.getData()) : AjaxResult.error(r.getMsg());
    }
}
