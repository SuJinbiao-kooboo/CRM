package com.ruoyi.crm.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 邮件发送任务实体类
 *
 * @author ruoyi
 */
@Data
public class SubscribeEmailDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 邮箱地址
     */
    private String email;


    private Integer status;

}