package com.ruoyi.email.service;

import java.util.Map;

/**
 * 企业邮箱读取 服务层
 *
 * @author ruoyi
 */
public interface IEkeEmailReadService
{
    /**
     * 读取企业邮箱最近一段时间的邮件并分析类型
     * <p>
     * 不传入参时：以参数配置 eke_email_last_read_time 作为开始时间读取邮件，
     * 读取成功后把该参数更新为本次接口调用时间；<br>
     * 传入入参时：以入参时间作为开始时间读取邮件，不读取、不更新 eke_email_last_read_time。
     *
     * @param startTimeStr 可选入参：开始时间（yyyy-MM-dd HH:mm:ss）
     * @return 读取结果（实际开始时间、接口调用时间、邮件列表、是否更新参数配置等）
     */
    public Map<String, Object> readEmails(String startTimeStr);
}
