package com.ruoyi.crm.domain.dto;

import java.util.List;

import lombok.Data;

/**
 * 单据类型定义（类型 + 字段 + 明细列 + 上传项），用于驱动前端生成表单
 */
@Data
public class DocTypeDef {
    /** 类型标识 */
    private String key;
    /** 显示名称 */
    private String name;
    /** 是否含明细表 */
    private boolean items;
    /** 是否支持从上传文件解析明细并回显到页面（不直接写入文件，用户确认后再生成） */
    private boolean parseItems;
    /** 上传项（来源文件） */
    private List<DocUpload> uploads;
    /** 文件名规则说明 */
    private String fileNameHint;
    /** 字段 */
    private List<DocField> fields;
    /** 明细列 */
    private List<DocItemColumn> itemColumns;
}