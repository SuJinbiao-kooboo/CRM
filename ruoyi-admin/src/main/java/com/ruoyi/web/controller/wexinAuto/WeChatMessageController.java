package com.ruoyi.web.controller.wexinAuto;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import com.ruoyi.wexinAuto.model.WeChatSendResult;
import com.ruoyi.wexinAuto.service.WeChatGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 微信消息网关接口（桌面版微信 via wx_gateway.py）。
 *
 * <p>前置：微信 4.1.15.13 已登录、主窗口打开；首次运行需从进程内存提取密钥。</p>
 * <ul>
 *   <li>GET  /wechat/messages        主动拉取：since(可选,含到秒) chat(可选) media(可选)</li>
 *   <li>POST /wechat/listen/start   启动实时监听（之后逐条回调 WeChatMessageHandler）</li>
 *   <li>GET  /wechat/listen/status  查询监听状态</li>
 *   <li>POST /wechat/listen/stop    停止监听</li>
 * </ul>
 * <p>鉴权：本控制器已用 {@code @Anonymous} 加入白名单，可匿名访问（/wechat/**）。</p>
 */
@RestController
@Anonymous
@RequestMapping("/wechat")
@Slf4j
public class WeChatMessageController {

    private final WeChatGatewayService gateway;

    @Autowired
    public WeChatMessageController(WeChatGatewayService gateway) {
        this.gateway = gateway;
    }

    /**
     * 主动拉取聊天记录。
     *
     * @param since 起始时间(含该秒起)，如 2026-10-05 12:00:00；不填=全部
     * @param chat  群名/好友名，可选；不填=所有会话
     * @param media 是否同时下载媒体(图片/语音/视频/文件)
     */
    @GetMapping("/messages")
    public AjaxResult messages(@RequestParam(required = false) String since,
                               @RequestParam(required = false) String chat,
                               @RequestParam(required = false, defaultValue = "false") boolean media) {
        List<WeChatMessage> list = gateway.fetch(since, chat, media);
        return AjaxResult.success(list);
    }

    /**
     * 启动实时监听（所有会话新消息）。每收到一条消息会调用一次你的 Handler。
     */
    @PostMapping("/listen/start")
    public AjaxResult listenStart(@RequestParam(required = false, defaultValue = "false") boolean media) {
        Map<String, Object> r = gateway.startListen(media);
        return AjaxResult.success(r);
    }

    /**
     * 查询监听状态：{running:bool, processed:int}
     */
    @GetMapping("/listen/status")
    public AjaxResult listenStatus() {
        return AjaxResult.success(gateway.listenStatus());
    }

    /**
     * 停止实时监听，返回已处理条数。
     */
    @PostMapping("/listen/stop")
    public AjaxResult listenStop() {
        Map<String, Object> r = gateway.stopListen();
        return AjaxResult.success(r);
    }

    /**
     * 发送消息（单条，底层即批量发送(targets=[1])）。
     * body: { "name": "文件传输助手", "text": "hello" }
     */
    @PostMapping("/send")
    public AjaxResult send(@RequestBody Map<String, Object> body) {
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        String text = body.get("text") == null ? null : String.valueOf(body.get("text"));
        List<String> names = new ArrayList<>();
        names.add(name);
        List<WeChatSendResult> r = gateway.batchSend(names, text);
        return AjaxResult.success(r);
    }

    /**
     * 批量发送。
     * body: { "names": ["张三", "李四"], "text": "你好，{name}！" }
     * text 含 {name} 会自动替换成目标名；拟人节奏/间隔/上限等由配置字典控制。
     * 想发给单人，names 传一个元素即可。
     */
    @PostMapping("/batch_send")
    public AjaxResult batchSend(@RequestBody Map<String, Object> body) {
        Object namesObj = body.get("names");
        if (namesObj == null || ((List<?>) namesObj).isEmpty()) {
            return AjaxResult.error("names 不能为空");
        }
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) namesObj;
        String template = body.get("text") == null ? null : String.valueOf(body.get("text"));
        List<WeChatSendResult> r = gateway.batchSend(names, template);
        return AjaxResult.success(r);
    }
}






