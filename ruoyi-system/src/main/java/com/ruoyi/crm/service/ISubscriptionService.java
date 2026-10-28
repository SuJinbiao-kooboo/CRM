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

    /** 批量更新订阅状态(1-生效/0-失效)，返回影响行数。 */
    int updateStatus(List<Long> ids, int status);

    /** 批量物理删除订阅记录，返回删除行数。 */
    int deleteSubscriptions(List<Long> ids);
}
