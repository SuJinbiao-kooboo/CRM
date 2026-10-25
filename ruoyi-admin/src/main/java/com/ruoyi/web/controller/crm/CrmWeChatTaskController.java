package com.ruoyi.web.controller.crm;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.wexinAuto.model.WeChatOfferTask;
import com.ruoyi.wexinAuto.service.WeChatOfferTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 微信询报价任务审批与发送入口。 */
@RestController
@RequestMapping("/crm/wechat-task")
public class CrmWeChatTaskController extends BaseController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private WeChatOfferTaskService taskService;

    @PreAuthorize("@ss.hasPermi('wechat:task:list')")
    @GetMapping("/list")
    public TableDataInfo list(WeChatOfferTask query) {
        if (StringUtils.isBlank(query.getBeginTime())) {
            query.setBeginTime(LocalDateTime.now().minusDays(30).format(TIME));
        }
        startPage();
        return getDataTable(taskService.selectTaskList(query));
    }

    @PreAuthorize("@ss.hasPermi('wechat:task:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(taskService.selectTaskById(id));
    }

    @PreAuthorize("@ss.hasPermi('wechat:task:approve')")
    @Log(title = "微信询报价任务审批", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/approve")
    public AjaxResult approve(@PathVariable Long id, @RequestBody WeChatOfferTask form) {
        form.setId(id);
        return toAjax(taskService.approve(form));
    }

    @PreAuthorize("@ss.hasPermi('wechat:task:generate')")
    @Log(title = "微信询报价任务生成", businessType = BusinessType.UPDATE)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody(required = false) Map<String, Object> body) {
        Object chat = body == null ? null : body.get("chat");
        return success(taskService.generateOnce(chat == null ? null : String.valueOf(chat).trim(), true));
    }

    @PreAuthorize("@ss.hasPermi('wechat:task:send')")
    @Log(title = "微信询报价任务发送", businessType = BusinessType.UPDATE)
    @PostMapping("/send")
    public AjaxResult send() {
        return success(taskService.sendOnce(true));
    }

    @PreAuthorize("@ss.hasPermi('wechat:task:send')")
    @Log(title = "微信询报价任务确认未发送", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/confirm-not-sent")
    public AjaxResult confirmNotSent(@PathVariable Long id) {
        return toAjax(taskService.confirmNotSent(id));
    }
}
