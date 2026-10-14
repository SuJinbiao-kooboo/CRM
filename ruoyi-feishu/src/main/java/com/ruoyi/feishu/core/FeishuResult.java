package com.ruoyi.feishu.core;

/**
 * 飞书相关操作的统一返回结构，供各封装接口使用。
 * 业务接口如需与前端交互，可在 Controller 层再包装为 RuoYi 的 AjaxResult。
 */
public class FeishuResult {

    /** 是否成功 */
    private boolean success;

    /** 飞书返回的业务码（0 表示成功） */
    private Integer code;

    /** 说明信息 */
    private String msg;

    /** 附加数据（如返回的消息 id 等） */
    private Object data;

    public static FeishuResult success() {
        return new FeishuResult(true, 0, "操作成功", null);
    }

    public static FeishuResult success(Object data) {
        return new FeishuResult(true, 0, "操作成功", data);
    }

    public static FeishuResult error(String msg) {
        return new FeishuResult(false, -1, msg, null);
    }

    public static FeishuResult error(Integer code, String msg) {
        return new FeishuResult(false, code, msg, null);
    }

    public FeishuResult() {
    }

    public FeishuResult(boolean success, Integer code, String msg, Object data) {
        this.success = success;
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
