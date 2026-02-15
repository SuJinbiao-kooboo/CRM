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
 * 打卡记录
 * </p>
 *
 * @author 亦愘
 * @since 2026-02-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("do_checkin")
public class DoCheckin implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 媒体链接(COS/OSS)
     */
    private String mediaUrl;

    /**
     * 心情
     */
    private String mood;

    /**
     * 难度(0-9)
     */
    private Byte difficulty;

    /**
     * 打卡状态/no_check、checked、buddy_confirm(搭子已确认)
     */
    private String checkStatus;

    /**
     * 是否补卡(1是/0否)
     */
    private Boolean isSupplement;

    /**
     * 打卡日期
     */
    private Date checkDate;

    /**
     * 打卡期数
     */
    private Integer checkPeriod;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
