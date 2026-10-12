package com.ruoyi.feishu.callback;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 默认后台任务处理器（示例）。
 * 接入真实业务时实现 {@link FeishuTaskExecutor} 并用 @Primary 覆盖，或直接修改本类逻辑。
 */
@Component
public class DefaultTaskExecutor implements FeishuTaskExecutor {

    /** ABCD 选项文案（与卡片保持一致） */
    private static final String[] OPTIONS = {"处理今天的邮件", "整理工作汇报", "安排明天的日程", "汇总项目进度"};

    @Override
    public String execute(String optionKey, String customText) {
        String desc;
        if (customText != null && !customText.trim().isEmpty()) {
            desc = customText.trim();
        } else if (optionKey != null && optionKey.length() == 1) {
            int idx = optionKey.charAt(0) - 'A';
            desc = (idx >= 0 && idx < OPTIONS.length) ? OPTIONS[idx] : optionKey;
        } else {
            desc = optionKey;
        }
        // ===== 在这里接入真实后台任务逻辑 =====
        String taskId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "✅ 收到！\n"
                + "选择内容：**" + desc + "**\n"
                + "已进入后台处理，任务编号 #" + taskId + "。\n"
                + "（示例逻辑，接入真实系统后替换 DefaultTaskExecutor.execute）";
    }
}
