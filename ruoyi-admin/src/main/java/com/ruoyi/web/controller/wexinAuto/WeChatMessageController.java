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
 *   <li>GET  /wechat/messages        主动拉取：since(可选,含到秒) chat(可选) media(可选) status(可选)</li>
 *   <li>POST /wechat/poll/run       手动触发一次轮询拉取(可选 chat=群/好友名只拉该会话)</li>
 *   <li>GET  /wechat/poll/status    轮询拉取/分类参数与状态</li>
 *   <li>POST /wechat/classify/run   手动触发一次分类(可选 chat=群/好友名只处理该会话)</li>
 *   <li>POST /wechat/offer/entry/run 手动触发一次INQ/OFFER自动录入(可选 chat=群/好友名只录入该会话)</li>
 *   <li>GET  /wechat/offer/entry/status INQ/OFFER录入参数与状态</li>
 *   <li>POST /wechat/offer/entry/requeue 人工确认后重排 SKIPPED/FAILED/DEAD 消息</li>
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
                               @RequestParam(required = false, defaultValue = "false") boolean media,
                               @RequestParam(required = false) Integer limit,
                               @RequestParam(required = false) Integer status) {
        List<WeChatMessage> list = gateway.fetch(since, chat, media, limit, status);
        return AjaxResult.success(list);
    }

    /**
     * 手动触发一次轮询拉取：把微信消息拉入 wechat_message 库(status=0)。
     *
     * @param chat  群名/好友名，可选；不填=所有会话增量；填了=只拉该会话
     * @param since 拉取起点时间(格式 yyyy-MM-dd HH:mm:ss，含该秒)，可选；
     *              不填=按轮询水位只拉新增；填了=重新拉该时刻之后的所有消息，
     *              已入库的按唯一键 username+local_id 自动跳过，不会重复处理
     */
    @PostMapping("/poll/run")
    public AjaxResult pollRun(@RequestParam(required = false) String chat,
                              @RequestParam(required = false) String since) {
        return AjaxResult.success(gateway.pollOnce(chat, since, true));
    }

    /**
     * 轮询拉取/分类配置与运行状态（含未分类消息数、水位时间），便于 Postman 观察。
     */
    @GetMapping("/poll/status")
    public AjaxResult pollStatus() {
        return AjaxResult.success(gateway.pollStatus());
    }

    /**
     * 手动触发一次分类：对最早未分类(status=0)的 classifyBatchSize 条消息做 AI 分类并回写。
     *
     * @param chat 群名/好友名，可选；不填=全部会话；填了=只处理该会话最早未分类的消息
     */
    @PostMapping("/classify/run")
    public AjaxResult classifyRun(@RequestParam(required = false) String chat) {
        return AjaxResult.success(gateway.classifyOnce(chat));
    }

    /** 清空AI分类缓存（调整提示词/语义规则后，重跑历史消息前调用）。 */
    @PostMapping("/classify/cache/clear")
    public AjaxResult clearClassifyCache() {
        return AjaxResult.success(gateway.clearClassifyCache());
    }

    /** 手动补扫 TRACE 飞书预警：只推送已关联 INQ/OFFER 且尚未成功处理的客户二次询问。 */
    @PostMapping("/trace/alert/scan")
    public AjaxResult traceAlertScan() {
        return AjaxResult.success(gateway.scanTraceAlerts());
    }

    /**
     * 手动触发一次 INQ/OFFER 自动录入：把已分类未处理(status=1)且分类命中 offerEntryCategories
     * 的消息按 Offer 列表 AI 录入逻辑写入 crm_offer（写入 wechat_server_id 来源关联），成功后 status=2。
     *
     * @param chat 群名/好友名，可选；不填=全部会话(注意按 offerEntryScanSec 限频)；填了=立即只处理该会话
     */
    @PostMapping("/offer/entry/run")
    public AjaxResult offerEntryRun(@RequestParam(required = false) String chat) {
        return AjaxResult.success(gateway.offerEntryOnce(chat));
    }

    /**
     * INQ/OFFER 自动录入参数与运行状态（含待录入消息数），便于 Postman 观察。
     */
    @GetMapping("/offer/entry/status")
    public AjaxResult offerEntryStatus() {
        return AjaxResult.success(gateway.offerEntryStatus());
    }

    /**
     * 人工确认后重新入队：只处理 SKIPPED/FAILED/DEAD 的 INQ/OFFER，
     * 下一轮会重新执行 AI查询、AI录入和比价，不会绕过语义门禁。
     *
     * @param chat 群名/好友名，可选；不填=全部会话
     */
    @PostMapping("/offer/entry/requeue")
    public AjaxResult offerEntryRequeue(@RequestParam(required = false) String chat) {
        return AjaxResult.success(gateway.requeueOfferEntry(chat));
    }

    /**
     * 启动实时监听（所有会话新消息）。每收到一条消息会调用一次你的 Handler。
     */
    @PostMapping("/listen/start")
    public AjaxResult listenStart(@RequestParam(required = false, defaultValue = "false") boolean media,
                               @RequestParam(required = false, defaultValue = "false") boolean fresh) {
        Map<String, Object> r = gateway.startListen(media, fresh);
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






