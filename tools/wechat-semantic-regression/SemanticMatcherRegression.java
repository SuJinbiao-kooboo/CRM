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
        Set<String> selfNames = new HashSet<>(Arrays.asList("Eke", "May"));

        check(SemanticMatcher.isSelf("@May", selfNames), "selfNames supports @ prefix");
        check(!SemanticMatcher.isSelf("May 星禾 SSD GPU", selfNames), "selfNames must match exactly");

        check(SemanticMatcher.looksLikeInquiry("MBD-H13SSL-N-B 24pcs 有货吗"), "valid customer INQ");
        check(SemanticMatcher.looksLikeInquiry("客户询问是否有便宜的拆机内存条"), "Chinese category inquiry is INQ");
        check(!SemanticMatcher.looksLikeInquiry("现货出 5090风扇200片"), "broadcast is not INQ");
        check(!SemanticMatcher.looksLikeInquiry("100pcs"), "quantity only is not INQ");
        check(!SemanticMatcher.looksLikeInquiry("价格很好"), "statement is not INQ");

        WeChatMessage inq = msg("客户甲", "MBD-H13SSL-N-B 24pcs 有货吗", "2026-10-22 10:00:00");
        WeChatMessage offer = msg("Eke", "有，24pcs 660USD，2周交期", "2026-10-22 10:05:00");
        check(SemanticMatcher.isReplyTo(offer, inq, true, true, 180, 30), "valid offer links to INQ");

        WeChatMessage acknowledgement = msg("Eke", "好的", "2026-10-22 10:05:00");
        check(!SemanticMatcher.isReplyTo(acknowledgement, inq, true, true, 180, 30),
                "acknowledgement without price is not OFFER");
        acknowledgement.setQuote("MBD-H13SSL-N-B 24pcs 有货吗");
        check(!SemanticMatcher.isReplyTo(acknowledgement, inq, true, true, 180, 30),
                "quote alone without quote evidence is not OFFER");

        WeChatMessage broadcast = msg("Eke", "现货出 5090风扇200片 100USD", "2026-10-22 10:05:00");
        check(!SemanticMatcher.isReplyTo(broadcast, inq, true, true, 180, 30),
                "broadcast is not a direct OFFER");

        WeChatMessage stale = msg("Eke", "24pcs 660USD", "2026-10-22 13:30:00");
        check(!SemanticMatcher.isReplyTo(stale, inq, true, true, 180, 30),
                "offer outside max window is rejected");

        WeChatMessage longOffer = msg("Eke", "有 24pcs 价格660USD 增值税另计 交期大概两周左右 可以安排发货",
                "2026-10-22 10:10:00");
        check(SemanticMatcher.isReplyTo(longOffer, inq, true, true, 180, 30),
                "matching quantity and price stays linked");

        check(SemanticMatcher.isFollowUpTo(msg("客户甲", "660美金含税吗？交期能提前吗", "2026-10-22 10:10:00"),
                offer, true, 180, 30), "valid TRACE links to OFFER");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "在吗？", "2026-10-22 10:10:00"),
                offer, true, 180, 30), "generic greeting is not TRACE");
        WeChatMessage quotedGreeting = msg("客户甲", "在吗？", "2026-10-22 10:10:00");
        quotedGreeting.setQuote("有，24pcs 660USD，2周交期");
        check(!SemanticMatcher.isFollowUpTo(quotedGreeting, offer, true, 180, 30),
                "quoted greeting still needs a business follow-up cue");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "100pcs", "2026-10-22 10:10:00"),
                offer, true, 180, 30), "quantity statement is not TRACE");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "客户都要下单出去了我们还没货", "2026-10-22 10:10:00"),
                offer, true, 180, 30), "statement is not TRACE");
        check(SemanticMatcher.isFollowUpTo(msg("客户甲", "交期多久？", "2026-10-22 10:20:00"),
                offer, true, 180, 30), "business question links to OFFER");
        check(!SemanticMatcher.isFollowUpTo(msg("客户甲", "交期多久？", "2026-10-22 11:00:00"),
                offer, true, 180, 30), "short group follow-up outside short window is rejected");

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
