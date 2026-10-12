package com.ruoyi.feishu.dto;

import lombok.Data;

/**
 * 触发“飞书指挥 Codex”的请求体。
 */
@Data
public class CodexRunBody {

    /** 要交给 Codex 执行的指令，例如：分析并修复 xxx bug */
    private String prompt;
}
