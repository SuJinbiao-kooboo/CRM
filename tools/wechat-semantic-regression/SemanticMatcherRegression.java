import com.ruoyi.wexinAuto.analysis.SemanticMatcher;
import com.ruoyi.wexinAuto.model.WeChatMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 无框架回归脚本：验证 INQ -> OFFER -> TRACE 的关键边界。
 * 运行方式见同目录 README.md；不需要启动 Spring。
 */
public final class SemanticMatcherRegression {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        Set<String> selfNames = new HashSet<>(Arrays.asList("Eke", "May", "Jin", "Jasmine"));

        check(SemanticMatcher.isSelf("@May", selfNames), "selfNames supports @ prefix");
        check(SemanticMatcher.isSelf("@Jin", selfNames), "Jin is self");
        check(SemanticMatcher.isSelf("Jasmine", selfNames), "Jasmine is self");
        check(!SemanticMatcher.isSelf("May 星禾 SSD GPU", selfNames), "selfNames must match exactly");

        check(SemanticMatcher.looksLikeInquiry("MBD-H13SSL-N-B 24pcs 有货吗"), "external INQ is valid");
        check(SemanticMatcher.looksLikeInquiry("找 64G 5600 100条，有货来报"), "our outbound INQ is valid");
        check(SemanticMatcher.looksLikeInquiry("你们报多少"), "elliptical price question is INQ");
        check(SemanticMatcher.looksLikeInquiry("客户询问是否有便宜的拆机内存条"), "Chinese category inquiry is INQ");
        check(!SemanticMatcher.looksLikeInquiry("现货出 5090风扇200片"), "broadcast is not INQ");
        check(!SemanticMatcher.looksLikeInquiry("含税出60 张5090 风扇卡要的滴滴"),
                "tax-included broadcast is not INQ");
        check(!SemanticMatcher.looksLikeInquiry("周四现货速览 小海豚 7A40 3.84T"),
                "stock digest broadcast is not INQ");
        check(!SemanticMatcher.looksLikeInquiry("100pcs"), "quantity only is not INQ");
        check(!SemanticMatcher.looksLikeInquiry("价格很好"), "statement is not INQ");
        check(!SemanticMatcher.looksLikeInquiry("MBD-H13SSL-N-B 24pcs 660USD"), "priced statement is not INQ");
        check(SemanticMatcher.looksLikeInquiry("MBD-H13SSL-N-B 目标价660USD"), "target price is INQ");

        WeChatMessage customerInq = msg("客户甲", "MBD-H13SSL-N-B 24pcs 有货吗", "2026-10-22 10:00:00");
        WeChatMessage selfOffer = msg("Eke", "有，24pcs 660USD，2周交期", "2026-10-22 10:05:00");
        check(SemanticMatcher.isReplyTo(selfOffer, customerInq, true, 180, 30),
                "our OFFER links to external INQ");

        WeChatMessage selfInq = msg("May", "找 64G 5600 100条，有货来报", "2026-10-22 10:00:00");
        WeChatMessage supplierOffer = msg("供应商甲", "有货，100条 320USD，明天发", "2026-10-22 10:05:00");
        check(SemanticMatcher.isReplyTo(supplierOffer, selfInq, true, 180, 30),
                "supplier OFFER links to our outbound INQ");
        check(SemanticMatcher.isReplyTo(msg("Jasmine", "5955WX 485USD 现货", "2026-10-22 10:05:00"),
                msg("供应商乙", "RFQ: 5955WX 100pcs", "2026-10-22 10:00:00"), true, 180, 30),
                "our OFFER links to supplier inbound INQ");
        check(SemanticMatcher.isReplyTo(msg("客户乙", "现货 24pcs 660USD", "2026-10-22 10:05:00"),
                customerInq, true, 180, 30), "normal stock reply is not treated as broadcast");
        check(!SemanticMatcher.isReplyTo(msg("群友丙", "5090风扇 100条 320USD", "2026-10-22 10:05:00"),
                selfInq, true, 180, 30), "different product plus same quantity is not a reply");

        WeChatMessage sameSenderPrice = msg("May", "100条 320USD", "2026-10-22 10:05:00");
        check(!SemanticMatcher.isReplyTo(sameSenderPrice, selfInq, true, 180, 30),
                "same sender self-answer is not OFFER");
        WeChatMessage unknownSender = msg("", "有货，100条 320USD", "2026-10-22 10:05:00");
        check(!SemanticMatcher.isReplyTo(unknownSender, selfInq, true, 180, 30),
                "unknown sender is not a confirmed reply");

        WeChatMessage acknowledgement = msg("Eke", "好的", "2026-10-22 10:05:00");
        check(!SemanticMatcher.isReplyTo(acknowledgement, customerInq, true, 180, 30),
                "acknowledgement without price is not OFFER");
        acknowledgement.setQuote("MBD-H13SSL-N-B 24pcs 有货吗");
        check(!SemanticMatcher.isReplyTo(acknowledgement, customerInq, true, 180, 30),
                "quote alone without quote evidence is not OFFER");

        WeChatMessage broadcast = msg("Eke", "现货出 5090风扇200片 100USD", "2026-10-22 10:05:00");
        check(!SemanticMatcher.isReplyTo(broadcast, customerInq, true, 180, 30),
                "broadcast is not a direct OFFER");

        WeChatMessage stale = msg("Eke", "24pcs 660USD", "2026-10-22 13:30:00");
        check(!SemanticMatcher.isReplyTo(stale, customerInq, true, 180, 30),
                "offer outside max window is rejected");

        WeChatMessage longOffer = msg("Eke", "有 24pcs 价格660USD 增值税另计 交期大概两周左右 可以安排发货",
                "2026-10-22 10:10:00");
        check(SemanticMatcher.isReplyTo(longOffer, customerInq, true, 180, 30),
                "matching quantity and price stays linked");

        check(SemanticMatcher.isFollowUpTo(msg("客户甲", "660美金含税吗？交期能提前吗", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "valid question TRACE links to OFFER");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "在吗？", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "generic greeting is not TRACE");
        WeChatMessage quotedGreeting = msg("客户甲", "在吗？", "2026-10-22 10:10:00");
        quotedGreeting.setQuote("有，24pcs 660USD，2周交期");
        check(!SemanticMatcher.isFollowUpTo(quotedGreeting, selfOffer, true, 180, 30),
                "quoted greeting still needs a business follow-up cue");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "100pcs", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "quantity statement is not TRACE");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "客户都要下单出去了我们还没货", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "unrelated statement is not TRACE");
        check(SemanticMatcher.isFollowUpTo(msg("客户甲", "交期多久？", "2026-10-22 10:20:00"),
                selfOffer, true, 180, 30), "business question links to OFFER");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "交期多久？", "2026-10-22 11:00:00"),
                selfOffer, true, 180, 30), "short group follow-up outside short window is rejected");
        check(SemanticMatcher.isFollowUpTo(msg("客户甲", "这个价格可以，按24pcs安排吧", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "statement TRACE confirming price and quantity");
        check(SemanticMatcher.isFollowUpTo(msg("客户甲", "24pcs 660USD 可以", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "short acceptance with linked quantity is TRACE");
        check(SemanticMatcher.isFollowUpTo(msg("供应商甲", "这个价能做，明天确认交期", "2026-10-22 10:10:00"),
                supplierOffer, true, 180, 30), "our statement TRACE can follow supplier offer");
        check(SemanticMatcher.isFollowUpTo(msg("客户甲", "ok, proceed with 24pcs", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "English confirmation with quantity is TRACE");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "价格很好", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "plain price comment is not TRACE");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "可以", "2026-10-22 10:10:00"),
                selfOffer, true, 180, 30), "bare acceptance without quote/context is not TRACE");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "这个价格可以，按24pcs安排吧", "2026-10-22 11:00:00"),
                selfOffer, true, 180, 30), "statement TRACE outside short window is rejected");

        System.out.println("PASSED=" + passed + " FAILED=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static WeChatMessage msg(String sender, String content, String time) {
        WeChatMessage m = new WeChatMessage();
        m.setUsername("123456@chatroom");
        m.setName("语义回归测试群");
        m.setSender(sender);
        m.setContent(content);
        m.setCreateTime(time);
        m.setSortSeq(1L);
        return m;
    }

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.err.println("FAILED: " + name);
        }
    }
}
