package com.ruoyi.crm.service;

import java.util.List;
import java.util.Map;

/**
 * 邮件订阅(portal.t_subscription) 业务接口。
 */
public interface ISubscriptionService {

    /** 分页查询订阅信息，id 倒排。 */
    List<Map<String, Object>> selectSubscriptionList(String email, String module, Integer status);

    /**
     * 批量添加订阅邮箱（module 默认 hk，status 默认 1）。
     * 返回 { total, success, fail, failEmails }，唯一键冲突/邮箱格式非法计入失败。
     */
    Map<String, Object> addSubscriptions(List<String> emails);

    /** 批量将订阅置为无效(status=0)，返回影响行数。 */
    int invalidateSubscriptions(List<Long> ids);
}