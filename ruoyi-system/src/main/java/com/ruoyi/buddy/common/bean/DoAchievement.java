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
 * 成就定义
 * </p>
 *
 * @author 亦愘
 * @since 2026-02-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("do_achievement")
public class DoAchievement implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 成就编码
     */
    private String code;

    /**
     * 成就名称
     */
    private String name;

    /**
     * 成就描述
     */
    private String description;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
