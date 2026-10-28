package com.ruoyi.crm.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * portal 库邮件订阅表(portal.t_subscription) 数据访问。
 * 供应商管理"Offer邮箱"弹窗使用：分页查询、批量添加、失效(删除)。
 */
public interface SubscriptionMapper {

    /** 分页查询订阅信息，id 倒序返回。 */
    List<Map<String, Object>> selectSubscriptionList(@Param("email") String email,
                                                     @Param("module") String module,
                                                     @Param("status") Integer status);

    /** 插入一条订阅记录（email+module 唯一键冲突时抛 DuplicateKeyException，由调用方统计失败）。 */
    int insertSubscription(@Param("email") String email,
                           @Param("module") String module,
                           @Param("status") int status);

    /** 查询指定邮箱+模块的订阅状态；不存在返回 null，存在返回 status(1-已订阅/0-已取消)。 */
    Integer selectStatusByEmail(@Param("email") String email, @Param("module") String module);

    /** 将已取消的订阅重新激活为已订阅(status=1)。 */
    int reactivateSubscription(@Param("email") String email, @Param("module") String module);

    /** 按主键批量将订阅状态置为无效(status=0)。 */
    int invalidateSubscriptions(@Param("ids") List<Long> ids);
}
