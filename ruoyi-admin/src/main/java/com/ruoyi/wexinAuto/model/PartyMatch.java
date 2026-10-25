package com.ruoyi.wexinAuto.model;

import com.ruoyi.crm.domain.CrmSupplier;

/** 微信会话到交易对手的唯一映射结果。 */
public class PartyMatch {

    public static final String OK = "OK";
    public static final String UNMAPPED = "UNMAPPED_CHAT";
    public static final String AMBIGUOUS = "AMBIGUOUS_CHAT";
    public static final String INACTIVE = "INACTIVE_PARTY";
    public static final String MISSING_CODE = "MISSING_PARTY_CODE";

    private final String status;
    private final CrmSupplier supplier;
    private final String message;

    private PartyMatch(String status, CrmSupplier supplier, String message) {
        this.status = status;
        this.supplier = supplier;
        this.message = message;
    }

    public static PartyMatch ok(CrmSupplier supplier) {
        return new PartyMatch(OK, supplier, null);
    }

    public static PartyMatch of(String status, String message) {
        return new PartyMatch(status, null, message);
    }

    public String getStatus() {
        return status;
    }

    public CrmSupplier getSupplier() {
        return supplier;
    }

    public String getMessage() {
        return message;
    }

    public boolean isOk() {
        return OK.equals(status);
    }

    public boolean isUnmappedOrAmbiguous() {
        return UNMAPPED.equals(status) || AMBIGUOUS.equals(status);
    }
}
