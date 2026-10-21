package com.ruoyi.crm.domain.dto;

import lombok.Data;

/**
 * 单据生成页面的上传项定义（如装箱单来源文件、委托单的 PO/PL 文件）
 */
@Data
public class DocUpload {
    /** 表单字段名，与请求 part 名称一致 */
    private String key;
    /** 中文标签 */
    private String label;
    /** 是否必填 */
    private Boolean required;
    /** 说明文字 */
    private String hint;

    public DocUpload() {
    }

    public static DocUpload of(String key, String label) {
        DocUpload u = new DocUpload();
        u.setKey(key);
        u.setLabel(label);
        return u;
    }

    public DocUpload req() {
        this.required = true;
        return this;
    }

    public DocUpload hint(String value) {
        this.hint = value;
        return this;
    }
}