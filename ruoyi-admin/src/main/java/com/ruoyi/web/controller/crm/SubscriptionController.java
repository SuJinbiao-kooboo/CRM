package com.ruoyi.web.controller.crm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.crm.service.ISubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件订阅(portal.t_subscription)——供应商管理"Offer邮箱"弹窗。
 */
@RestController
@RequestMapping("/crm/subscription")
public class SubscriptionController extends BaseController {

    @Autowired
    private ISubscriptionService subscriptionService;

    /** 分页查询（id 倒排），支持按邮箱/模块/状态筛选。 */
    @PreAuthorize("@ss.hasPermi('crm:supplier:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(value = "email", required = false) String email,
                              @RequestParam(value = "module", required = false) String module,
                              @RequestParam(value = "status", required = false) Integer status) {
        startPage();
        List<Map<String, Object>> list = subscriptionService.selectSubscriptionList(email, module, status);
        return getDataTable(list);
    }

    /** 批量添加订阅邮箱：一行一个邮箱，module 默认 hk，status 默认 1。 */
    @PreAuthorize("@ss.hasPermi('crm:supplier:edit')")
    @Log(title = "Offer邮箱", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    public AjaxResult add(@RequestBody Map<String, Object> body) {
        Object emailsObj = body.get("emails");
        List<String> emails = new ArrayList<>();
        if (emailsObj instanceof List) {
            for (Object o : (List<?>) emailsObj) {
                if (o != null) {
                    emails.add(String.valueOf(o));
                }
            }
        }
        Map<String, Object> result = subscriptionService.addSubscriptions(emails);
        return AjaxResult.success("导入完成", result);
    }

    /** 生效/失效：批量更新状态(1-生效/0-失效)。 */
    @PreAuthorize("@ss.hasPermi('crm:supplier:edit')")
    @Log(title = "Offer邮箱", businessType = BusinessType.UPDATE)
    @PostMapping("/status")
    public AjaxResult updateStatus(@RequestBody Map<String, Object> body) {
        Object idsObj = body.get("ids");
        List<Long> ids = parseIds(idsObj);
        int status = "0".equals(String.valueOf(body.get("status"))) ? 0 : 1;
        int n = subscriptionService.updateStatus(ids, status);
        Map<String, Object> result = new HashMap<>();
        result.put("count", n);
        return AjaxResult.success("操作成功", result);
    }

    /** 物理删除订阅记录。 */
    @PreAuthorize("@ss.hasPermi('crm:supplier:edit')")
    @Log(title = "Offer邮箱", businessType = BusinessType.DELETE)
    @PostMapping("/delete")
    public AjaxResult delete(@RequestBody Map<String, Object> body) {
        List<Long> ids = parseIds(body.get("ids"));
        int n = subscriptionService.deleteSubscriptions(ids);
        Map<String, Object> result = new HashMap<>();
        result.put("count", n);
        return AjaxResult.success("删除成功", result);
    }

    private List<Long> parseIds(Object idsObj) {
        List<Long> ids = new ArrayList<>();
        if (idsObj instanceof List) {
            for (Object o : (List<?>) idsObj) {
                try {
                    ids.add(Long.valueOf(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ids;
    }
}
