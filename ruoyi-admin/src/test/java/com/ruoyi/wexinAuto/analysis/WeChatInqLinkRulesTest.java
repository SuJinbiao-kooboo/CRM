package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 微信短报价关联规则自检，不依赖 JUnit，可直接运行 main。 */
public class WeChatInqLinkRulesTest {

    public static void main(String[] args) {
        testWrongOldInqCanBeCorrected();
        testPriceOnlyDetection();
        testExplicitPartNumberNotOverridden();
        System.out.println("WeChatInqLinkRulesTest PASSED");
    }

    private static void testWrongOldInqCanBeCorrected() {
        List<WeChatMessage> input = new ArrayList<>();
        input.add(message(654058L, "Eke", "inq EPYC 7713 (64C 128T), 2k", 1_000_000L));
        input.add(message(753062L, "Eke", "inq: 8358  14pcs  TP: 1850USD", 1_000_000L + 47_000L));
        input.add(message(753118L, "赵荷枝-CPU Power/芯力量", "拆  2040", 1_000_000L + 94_000L));

        Set<String> selfNames = new HashSet<>(Arrays.asList("Eke", "May", "Jin", "Jasmine"));
        int candidate = WeChatInqLinkRules.latestSelfInquiryIndex(input, 2, selfNames, 30 * 60 * 1000L);
        eq(1, candidate, "应选最近一条我方询价，而不是前一天旧询价");
        int oldRelation = WeChatInqLinkRules.relationIndex(input,
                "{\"id\":654058,\"sender\":\"Eke\",\"content\":\"inq EPYC 7713\"}");
        eq(0, oldRelation, "旧关联下标应为0");
        if (oldRelation >= candidate) {
            throw new AssertionError("旧关联必须被识别为比最近我方询价更早");
        }
    }

    private static void testPriceOnlyDetection() {
        eq(true, WeChatInqLinkRules.isPriceOnlyReply("拆  2040"), "拆+单价格");
        eq(true, WeChatInqLinkRules.isPriceOnlyReply("14pcs 2040 USD"), "数量+价格");
        eq(true, WeChatInqLinkRules.isPriceOnlyReply("2040含税"), "价格+税务说明");
        eq(false, WeChatInqLinkRules.isPriceOnlyReply("inq: 8358 14pcs TP:1850USD"), "询价消息不能当报价");
        eq(false, WeChatInqLinkRules.isPriceOnlyReply("8358 14pcs 1850"), "三元数字清单不自动改关联");
        eq(false, WeChatInqLinkRules.isPriceOnlyReply("6760P 46pcs 7900拆"), "有明确料号不自动改关联");
    }

    private static void testExplicitPartNumberNotOverridden() {
        eq(false, WeChatInqLinkRules.isPriceOnlyReply("M321RAJA0MB2-CJR 128G 570PCS $4263/pc"),
                "完整料号报价不应触发短报价校正");
    }

    private static WeChatMessage message(long id, String sender, String content, long sortSeq) {
        WeChatMessage message = new WeChatMessage();
        message.setId(id);
        message.setSender(sender);
        message.setContent(content);
        message.setSortSeq(sortSeq);
        message.setMsgCategory("OTHER");
        return message;
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + "：expected=" + expected + "，actual=" + actual);
        }
    }
}
