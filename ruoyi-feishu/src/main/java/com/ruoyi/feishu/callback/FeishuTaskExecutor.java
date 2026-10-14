package com.ruoyi.feishu.callback;

/**
 * 用户在 ABCD 选项或自定义回答后的“后台任务处理”入口。
 * 业务方可注入自己的实现来接入真实任务；缺省使用 {@link DefaultTaskExecutor}。
 */
public interface FeishuTaskExecutor {

    /**
     * 处理用户的选择。
     * @param optionKey  用户选择的选项键：A/B/C/D，自定义回答时为 null
     * @param customText 用户输入的自定义内容（自定义回答时非空）
     * @return 要回复给用户的文本
     */
    String execute(String optionKey, String customText);
}
