package com.ruoyi.feishu.codex;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Codex 调用的运行结果。
 */
@Data
public class CodexRunResult {

    /** 是否成功（退出码为 0 视为成功） */
    private boolean success;

    /** 进程退出码；未执行或异常时为 -1 */
    private int exitCode = -1;

    /** 是否因超时被强制终止 */
    private boolean timedOut;

    /** Codex 的最终回答（来自 -o 输出文件，尽量精简为最终结论） */
    private String message = "";

    /** 失败时的错误信息（退出码 / 异常堆栈摘要 / 命令超出日志） */
    private String error = "";

    /** 本次执行产出的文件（outputDirectory 下新增或修改的文件绝对路径） */
    private List<String> producedFiles = new ArrayList<>();
}
