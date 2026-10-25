package com.ruoyi.web.controller.crm;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import com.ruoyi.wexinAuto.service.WeChatGatewayService;
import com.ruoyi.wexinAuto.service.WeChatMessageStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 微信聊天记录查询与人工排障入口。 */
@RestController
@RequestMapping("/crm/wechat-message")
public class CrmWeChatMessageController extends BaseController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private WeChatGatewayService gateway;

    @Autowired
    private WeChatMessageStore messageStore;

    @PreAuthorize("@ss.hasPermi('wechat:message:list')")
    @GetMapping("/list")
    public TableDataInfo list(WeChatMessage query) {
        if (StringUtils.isBlank(query.getBeginTime())) {
            query.setBeginTime(LocalDateTime.now().minusDays(30).format(TIME));
        }
        startPage();
        return getDataTable(messageStore.selectMessageList(query));
    }

    @PreAuthorize("@ss.hasPermi('wechat:message:query')")
    @GetMapping("/stats")
    public AjaxResult stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("poll", gateway.pollStatus());
        data.put("entry", gateway.offerEntryStatus());
        return success(data);
    }

    /** 指定 1～20 条消息做人工分类；不带消息键时仍受 classifyEnabled 控制。 */
    @PreAuthorize("@ss.hasPermi('wechat:message:classify')")
    @Log(title = "微信消息分类", businessType = BusinessType.UPDATE)
    @PostMapping("/classify/run")
    public AjaxResult classifyRun(@RequestBody(required = false) Map<String, Object> body) {
        List<String> keys = stringList(body == null ? null : body.get("keys"));
        boolean dryRun = Boolean.TRUE.equals(body == null ? null : body.get("dryRun"))
                || "true".equalsIgnoreCase(String.valueOf(body == null ? null : body.get("dryRun")));
        if (!keys.isEmpty()) {
            return success(gateway.classifyKeys(keys, dryRun));
        }
        if (dryRun) {
            return error("只读回放必须指定1～20条消息键");
        }
        return success(gateway.classifyOnce(text(body, "chat")));
    }

    @PreAuthorize("@ss.hasPermi('wechat:message:classify')")
    @PostMapping("/classify/cache/clear")
    public AjaxResult clearClassifyCache() {
        return success(gateway.clearClassifyCache());
    }

    @PreAuthorize("@ss.hasPermi('wechat:message:classify')")
    @PostMapping("/trace/scan")
    public AjaxResult scanTrace() {
        return success(gateway.scanTraceAlerts());
    }

    @PreAuthorize("@ss.hasPermi('wechat:message:entry')")
    @GetMapping("/entry/status")
    public AjaxResult entryStatus() {
        return success(gateway.offerEntryStatus());
    }

    /** 人工触发录入；带消息键时绕过消息年龄窗口，但仍执行分类来源、群映射和方向校验。 */
    @PreAuthorize("@ss.hasPermi('wechat:message:entry')")
    @Log(title = "微信消息AI录入", businessType = BusinessType.UPDATE)
    @PostMapping("/entry/run")
    public AjaxResult entryRun(@RequestBody(required = false) Map<String, Object> body) {
        List<String> keys = stringList(body == null ? null : body.get("keys"));
        if (!keys.isEmpty()) {
            return success(gateway.offerEntryKeys(keys));
        }
        return success(gateway.offerEntryOnce(text(body, "chat"), true));
    }

    /** 人工重排必须指定会话或1～20条消息键，避免误操作全量。 */
    @PreAuthorize("@ss.hasPermi('wechat:message:entry')")
    @Log(title = "微信消息录入重排", businessType = BusinessType.UPDATE)
    @PostMapping("/entry/requeue")
    public AjaxResult requeueEntry(@RequestBody(required = false) Map<String, Object> body) {
        List<String> keys = stringList(body == null ? null : body.get("keys"));
        if (!keys.isEmpty()) {
            return success(gateway.requeueOfferEntryKeys(keys));
        }
        String chat = text(body, "chat");
        if (StringUtils.isBlank(chat)) {
            return error("请指定会话或1～20条消息键");
        }
        return success(gateway.requeueOfferEntry(chat));
    }

    @PreAuthorize("@ss.hasPermi('wechat:message:poll')")
    @Log(title = "微信消息拉取", businessType = BusinessType.OTHER)
    @PostMapping("/poll/run")
    public AjaxResult pollRun(@RequestBody(required = false) Map<String, Object> body) {
        return success(gateway.pollOnce(text(body, "chat"), text(body, "since"), true));
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static List<String> stringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                addKey(out, item);
            }
        } else if (value != null) {
            for (String item : String.valueOf(value).split(",")) {
                addKey(out, item);
            }
        }
        return out;
    }

    private static void addKey(List<String> out, Object value) {
        if (value == null) {
            return;
        }
        String key = String.valueOf(value).trim();
        if (!key.isEmpty() && !out.contains(key)) {
            out.add(key);
        }
    }
}
