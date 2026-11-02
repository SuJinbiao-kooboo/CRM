package com.ruoyi.quartz.task;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.sign.Md5Utils;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.crm.service.ai.AiChannelService;
import com.ruoyi.system.service.ISysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 好价格 Offer 定时推送：复用“复制 Offer”的料号范围和最低价文本生成逻辑。 */
@Slf4j
@Component("offerGoodPushScheduler")
public class OfferGoodPushScheduler {

    private static final int RECENT_DAYS = 2;
    private static final String LAST_SIGNATURE_KEY = "offer.good.push.last.signature";

    private final ICrmOfferService offerService;
    private final ISysConfigService sysConfigService;
    private final AiChannelService aiChannelService;

    public OfferGoodPushScheduler(ICrmOfferService offerService, ISysConfigService sysConfigService,
                                  AiChannelService aiChannelService) {
        this.offerService = offerService;
        this.sysConfigService = sysConfigService;
        this.aiChannelService = aiChannelService;
    }

    /** 9:00-21:00 每小时执行；同一自然日且推送内容一致时只推送一次。 */
    public void pushGoodOffers() {
        List<Map<String, Object>> groups = offerService.todayCopyGroups(RECENT_DAYS);
        if (groups == null || groups.isEmpty()) {
            log.info("好价格Offer推送：最近{}天没有Offer记录", RECENT_DAYS);
            return;
        }

        Set<String> allPartNumbers = new LinkedHashSet<>();
        for (Map<String, Object> group : groups) {
            String partNumber = value(group.get("partNumber")).toUpperCase();
            if (!partNumber.isEmpty()) {
                allPartNumbers.add(partNumber);
            }
        }
        Map<String, Boolean> advantage = offerService.priceAdvantageBatch(
                new ArrayList<>(allPartNumbers), RECENT_DAYS);

        List<String> selectedPartNumbers = new ArrayList<>();
        int cpuCount = 0;
        for (Map<String, Object> group : groups) {
            String partNumber = value(group.get("partNumber")).toUpperCase();
            if (partNumber.isEmpty()) {
                continue;
            }
            boolean cpu = Boolean.TRUE.equals(group.get("cpu"));
            if (cpu) {
                cpuCount++;
            }
            if (cpu || Boolean.TRUE.equals(advantage.get(partNumber))) {
                selectedPartNumbers.add(partNumber);
            }
        }
        if (selectedPartNumbers.isEmpty()) {
            log.info("好价格Offer推送：最近{}天没有达到阈值的料号", RECENT_DAYS);
            return;
        }

        String text = offerService.buildAiQueryCopyText(
                selectedPartNumbers, RECENT_DAYS, Collections.<String>emptyList());
        String threshold = BigDecimal.valueOf(offerService.getPriceAdvantageThreshold())
                .stripTrailingZeros().toPlainString();
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String signature = Md5Utils.hash(today + "\n" + threshold + "\n" + text);
        String lastSignature = sysConfigService.selectConfigByKey(LAST_SIGNATURE_KEY);
        if (signature.equals(lastSignature)) {
            log.info("好价格Offer推送：今日内容未变化，跳过重复推送");
            return;
        }

        StringBuilder detail = new StringBuilder();
        detail.append("价格优势阈值：").append(threshold).append(" USD\n")
                .append("选择规则：非CPU料号仅推送达到阈值的；CPU料号全部推送，每个料号取最近")
                .append(RECENT_DAYS).append("天最低Offer。\n")
                .append("本次料号：").append(selectedPartNumbers.size())
                .append(" 个（其中CPU ").append(cpuCount).append(" 个）\n\n")
                .append(text);
        String title = "【好价格Offer】最近" + RECENT_DAYS + "天 " + selectedPartNumbers.size() + " 个料号";
        boolean sent = aiChannelService.alert("OFFER_GOOD|" + signature, title, detail.toString());
        if (!sent) {
            throw new ServiceException("好价格Offer飞书推送失败，请检查 sys.ai.alert.webhook 配置");
        }
        sysConfigService.updateConfigByKey(LAST_SIGNATURE_KEY, signature);
        log.info("好价格Offer已推送飞书：{}个料号，其中CPU{}个", selectedPartNumbers.size(), cpuCount);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
