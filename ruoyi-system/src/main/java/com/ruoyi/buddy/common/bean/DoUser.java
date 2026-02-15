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
 * 用户主表
 * </p>
 *
 * @author 亦愘
 * @since 2026-02-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("do_user")
public class DoUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 微信openid
     */
    private String openid;

    /**
     * 微信unionid
     */
    private String unionid;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机
     */
    private String phone;

    /**
     * 上次登录时间
     */
    private Date lastLoginTime;

    /**
     * 上次登录时间
     */
    private Date firstLoginTime;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
