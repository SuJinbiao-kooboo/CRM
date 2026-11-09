package com.ruoyi.wexinAuto.service;

/** 微信 INQ/OFFER 自动录入的重试节奏：首次失败后按指数退避重试。 */
public final class WeChatEntryRetryPolicy {

    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int DEFAULT_BASE_DELAY_SEC = 60;
    private static final int MAX_RETRY_DELAY_SEC = 3600;

    private WeChatEntryRetryPolicy() {
    }

    public static int normalizeMaxRetries(Integer configured) {
        if (configured == null) {
            return DEFAULT_MAX_RETRIES;
        }
        return Math.max(0, Math.min(10, configured));
    }

    public static int normalizeBaseDelaySec(Integer configured) {
        if (configured == null) {
            return DEFAULT_BASE_DELAY_SEC;
        }
        return Math.max(1, Math.min(600, configured));
    }

    public static int maxAttempts(int retryMax) {
        return normalizeMaxRetries(retryMax) + 1;
    }

    public static boolean exhausted(int failureCount, int retryMax) {
        return failureCount >= maxAttempts(retryMax);
    }

    /** 第1次失败后等待 base，第2次 base*2，第3次 base*4。 */
    public static int delaySeconds(int failureCount, int baseDelaySec) {
        int exponent = Math.max(0, Math.min(20, failureCount - 1));
        long delay = (long) normalizeBaseDelaySec(baseDelaySec) * (1L << exponent);
        return (int) Math.min(delay, MAX_RETRY_DELAY_SEC);
    }
}
