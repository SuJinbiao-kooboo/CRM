package com.ruoyi.crm.domain.dto;

import lombok.Data;

/**
 * 单据明细列定义
 */
@Data
public class DocItemColumn {
    private String key;
    private String label;
    /** 是否为自动计算列（如 金额） */
    private Boolean computed;

    public DocItemColumn() {
    }

    public static DocItemColumn of(String key, String label) {
        DocItemColumn c = new DocItemColumn();
        c.setKey(key);
        c.setLabel(label);
        return c;
    }

    public DocItemColumn computed() {
        this.computed = true;
        return this;
    }
}
