package com.ruoyi.web.controller.email;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.email.service.IEkeEmailReadService;

/**
 * 企业邮箱邮件读取 控制器
 * <p>
 * 接口说明：GET /email/read
 * <ul>
 *   <li>不传 time：以参数配置 eke_email_last_read_time 作为开始时间读取邮件，读取成功后把该参数更新为本次接口调用时间；</li>
 *   <li>传 time（格式 yyyy-MM-dd HH:mm:ss）：以传入时间作为开始时间读取邮件，不读取、不更新 eke_email_last_read_time。</li>
 * </ul>
 * 邮箱账号密码等连接信息在 application.yml 的 eke.mail.read 节点配置。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/email")
public class EkeEmailReadController extends BaseController
{
    @Autowired
    private IEkeEmailReadService ekeEmailReadService;

    /**
     * 读取企业邮箱最近一段时间的邮件，提取正文、发件人、附件并分析邮件类型
     *
     * @param time 可选入参：开始时间，格式 yyyy-MM-dd HH:mm:ss
     * @return 邮件读取结果（实际开始时间、接口调用时间、邮件列表、是否更新参数配置等）
     */
    @Anonymous
    @GetMapping("/read")
    public AjaxResult read(@RequestParam(value = "time", required = false) String time)
    {
        Map<String, Object> result = ekeEmailReadService.readEmails(time);
        int count = (int) result.get("count");
        return AjaxResult.success("读取邮件成功，共 " + count + " 封", result);
    }
}
