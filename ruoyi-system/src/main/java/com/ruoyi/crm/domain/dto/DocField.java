package com.ruoyi.crm.domain.dto;

import java.util.List;

import lombok.Data;

/**
 * 单据生成页面字段定义（驱动前端动态表单）
 */
@Data
public class DocField {
    /** 字段 key */
    private String key;
    /** 中文标签 */
    private String label;
    /** 控件类型：text / textarea / date / select / supplier / file / number */
    private String type;
    /** 是否必填 */
    private Boolean required;
    /** 默认值 */
    private String defaultValue;
    /** 占位提示 */
    private String placeholder;
    /** select 选项 */
    private List<String> options;

    public DocField() {
    }

    public DocField(String key, String label, String type) {
        this.key = key;
        this.label = label;
        this.type = type;
    }

    public static DocField of(String key, String label, String type) {
        return new DocField(key, label, type);
    }

    public DocField req() {
        this.required = true;
        return this;
    }

    public DocField options(List<String> options) {
        this.options = options;
        return this;
    }

    public DocField def(String value) {
        this.defaultValue = value;
        return this;
    }

    public DocField ph(String value) {
        this.placeholder = value;
        return this;
    }
}
