package com.ruoyi.wexinAuto.service;

/** 重试策略独立自检，不依赖 JUnit，可直接运行 main。 */
public class WeChatEntryRetryPolicyTest {

    public static void main(String[] args) {
        eq(4, WeChatEntryRetryPolicy.maxAttempts(3), "3次重试应有4次总尝试");
        eq(60, WeChatEntryRetryPolicy.delaySeconds(1, 60), "第1次退避");
        eq(120, WeChatEntryRetryPolicy.delaySeconds(2, 60), "第2次退避");
        eq(240, WeChatEntryRetryPolicy.delaySeconds(3, 60), "第3次退避");
        eq(false, WeChatEntryRetryPolicy.exhausted(3, 3), "第3次失败仍可重试");
        eq(true, WeChatEntryRetryPolicy.exhausted(4, 3), "第4次失败停止重试");
        System.out.println("WeChatEntryRetryPolicyTest PASSED");
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "：expected=" + expected + "，actual=" + actual);
        }
    }
}
