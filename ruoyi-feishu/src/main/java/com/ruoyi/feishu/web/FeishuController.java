package com.ruoyi.feishu.web;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.feishu.bot.FeishuBotManager;
import com.ruoyi.feishu.config.FeishuProperties;
import com.ruoyi.feishu.core.FeishuMessageSender;
import com.ruoyi.feishu.core.FeishuResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 飞书机器人控制接口。
 * <p>
 * 提供：状态查询、开启/关闭、消息发送自测。权限点：feishu:bot:manage（超级管理员拥有全部权限）。
 */
@RestController
@RequestMapping("/feishu")
public class FeishuController extends BaseController {

    private final FeishuBotManager botManager;
    private final FeishuMessageSender sender;
    private final FeishuProperties props;

    public FeishuController(FeishuBotManager botManager, FeishuMessageSender sender, FeishuProperties props) {
        this.botManager = botManager;
        this.sender = sender;
        this.props = props;
    }

    /**
     * 查询机器人运行状态。
     */
    @PreAuthorize("@ss.hasPermi('feishu:bot:manage')")
    @GetMapping("/bot/status")
    public AjaxResult status() {
        return AjaxResult.success(botManager.getStatus());
    }

    /**
     * 开启机器人。
     */
    @PreAuthorize("@ss.hasPermi('feishu:bot:manage')")
    @PostMapping("/bot/start")
    public AjaxResult start() {
        boolean ok = botManager.start();
        return ok ? AjaxResult.success("机器人已开启/已在运行中") : AjaxResult.error(botManager.getStatus().get("lastError").toString());
    }

    /**
     * 关闭机器人。
     */
    @PreAuthorize("@ss.hasPermi('feishu:bot:manage')")
    @PostMapping("/bot/stop")
    public AjaxResult stop() {
        botManager.stop();
        return AjaxResult.success("机器人已关闭");
    }

    /**
     * 发送一条文本消息（自测用）。
     * @param body 示例：{"receiveIdType":"chat_id","receiveId":"oc_xxx","text":"hello"}
     */
    @PreAuthorize("@ss.hasPermi('feishu:bot:manage')")
    @PostMapping("/message/sendText")
    public AjaxResult sendText(@RequestBody Map<String, Object> body) {
        String type = str(body.get("receiveIdType"), props.getSelfTestReceiveType());
        String id = str(body.get("receiveId"), props.getSelfTestReceiveId());
        String text = str(body.get("text"), "自测消息：" + props.getBotName());
        return toAjax(sender.sendText(type, id, text));
    }

    /**
     * 发送一张 ABCD 选择题卡片（自测用）。
     * @param body 示例：{"receiveIdType":"chat_id","receiveId":"oc_xxx","question":"今天先处理哪件？"}
     */
    @PreAuthorize("@ss.hasPermi('feishu:bot:manage')")
    @PostMapping("/message/sendQuestion")
    public AjaxResult sendQuestion(@RequestBody Map<String, Object> body) {
        String type = str(body.get("receiveIdType"), props.getSelfTestReceiveType());
        String id = str(body.get("receiveId"), props.getSelfTestReceiveId());
        String question = str(body.get("question"), props.getBotName() + " 来了！今天你想让我先帮你处理哪件事？");
        return toAjax(sender.sendQuestion(type, id, question));
    }

    /**
     * 发送工作汇报（自测用）。
     * @param body 示例：{"receiveIdType":"chat_id","receiveId":"oc_xxx"}
     */
    @PreAuthorize("@ss.hasPermi('feishu:bot:manage')")
    @PostMapping("/message/sendReport")
    public AjaxResult sendReport(@RequestBody Map<String, Object> body) {
        String type = str(body.get("receiveIdType"), props.getSelfTestReceiveType());
        String id = str(body.get("receiveId"), props.getSelfTestReceiveId());
        return toAjax(sender.sendReport(type, id));
    }

    /** 简单取值辅助 */
    private String str(Object v, String def) {
        return v == null || v.toString().trim().isEmpty() ? def : v.toString().trim();
    }

    /** 把 FeishuResult 转为 RuoYi 的 AjaxResult */
    private AjaxResult toAjax(FeishuResult r) {
        return r.isSuccess() ? AjaxResult.success(r.getMsg(), r.getData()) : AjaxResult.error(r.getMsg());
    }
}
