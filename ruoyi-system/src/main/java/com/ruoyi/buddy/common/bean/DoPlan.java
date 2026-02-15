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
 * 计划主表
 * </p>
 *
 * @author 亦愘
 * @since 2026-02-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("do_plan")
public class DoPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 计划创建者用户ID
     */
    private Long userId;

    /**
     * 计划标题
     */
    private String title;

    /**
     * 计划类型
     */
    private String type;

    /**
     * 打卡频率
     */
    private String frequency;

    /**
     * 下次打卡时间
     */
    private Date nextCheckTime;

    /**
     * 上次打卡时间
     */
    private Date lastCheckTime;

    /**
     * 开始日期
     */
    private Date startDate;

    /**
     * 结束日期
     */
    private Date endDate;

    /**
     * 计划可见性: public/buddy/private
     */
    private String visibility;

    /**
     * 计划状态: active/paused/archived
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
