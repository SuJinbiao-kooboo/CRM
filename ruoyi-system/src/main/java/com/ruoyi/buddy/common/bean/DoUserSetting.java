package com.ruoyi.buddy.common.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户设置
 * </p>
 *
 * @author 亦愘
 * @since 2026-02-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("do_user_setting")
public class DoUserSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId;

    /**
     * 资料可见范围: public/buddy/private
     */
    private String profileVisibility;

    /**
     * 允许被邀请为搭子(1是/0否)
     */
    private Boolean allowBuddyInvite;

    /**
     * 通知偏好JSON
     */
    private String notificationPref;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
