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

/** 好价格Offer定时推送：复用“复制Offer”规则；同日内容不变去重，按品牌拆分并限速重试。 */
@Slf4j
@Component("offerGoodPushScheduler")
public class OfferGoodPushScheduler {

    private static final int RECENT_DAYS = 2;
    private static final String LAST_SIGNATURE_KEY = "offer.good.push.last.signature";
    /** 单条飞书文本正文上限（字符）。按完整行切分，3000字符可稳定低于飞书文本消息上限。 */
    private static final int MESSAGE_TEXT_LIMIT = 3000;
    /** 多条消息间的最小间隔，避免触发飞书机器人限流。 */
    private static final long MESSAGE_INTERVAL_MS = 1200L;
    /** 首次发送 + 最多3次重试。 */
    private static final int SEND_MAX_ATTEMPTS = 4;
    private static final long RETRY_BASE_MS = 2000L;

    private final ICrmOfferService offerService;
    private final ISysConfigService sysConfigService;
    private final AiChannelService aiChannelService;

    public OfferGoodPushScheduler(ICrmOfferService offerService, ISysConfigService sysConfigService,
                                  AiChannelService aiChannelService) {
        this.offerService = offerService;
        this.sysConfigService = sysConfigService;
        this.aiChannelService = aiChannelService;
    }

    /** 9:00-21:00每小时执行；同一自然日内容一致时只推送一次，内容变化后按品牌分多条发送。 */
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

        Map<String, String> brandTexts = offerService.buildAiQueryCopyTextByBrand(
                selectedPartNumbers, RECENT_DAYS, Collections.<String>emptyList());
        if (brandTexts == null || brandTexts.isEmpty()) {
            throw new ServiceException("好价格Offer推送失败：未能生成按品牌拆分的Offer文本");
        }
        String threshold = BigDecimal.valueOf(offerService.getPriceAdvantageThreshold())
                .stripTrailingZeros().toPlainString();
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String signature = buildSignature(today, threshold, brandTexts);
        String lastSignature = sysConfigService.selectConfigByKey(LAST_SIGNATURE_KEY);
        if (signature.equals(lastSignature)) {
            log.info("好价格Offer推送：今日内容未变化，跳过重复推送（{}个品牌，{}个料号）",
                    brandTexts.size(), selectedPartNumbers.size());
            return;
        }

        List<GoodOfferMessage> messages = buildMessages(
                brandTexts, threshold, selectedPartNumbers.size(), cpuCount);
        int sentCount = 0;
        int failedCount = 0;
        for (int i = 0; i < messages.size(); i++) {
            GoodOfferMessage message = messages.get(i);
            boolean sent = sendWithRetry(message);
            if (sent) {
                sentCount++;
                log.info("好价格Offer飞书消息已发送：品牌={}，分段={}/{}，本批料号={}",
                        message.brand, message.partIndex, message.partTotal, message.partCount);
            } else {
                failedCount++;
                log.error("好价格Offer飞书消息发送失败：品牌={}，分段={}/{}，已重试{}次",
                        message.brand, message.partIndex, message.partTotal, SEND_MAX_ATTEMPTS - 1);
            }
            if (i < messages.size() - 1) {
                sleepQuietly(MESSAGE_INTERVAL_MS);
            }
        }
        if (failedCount > 0) {
            throw new ServiceException("好价格Offer飞书推送失败：成功" + sentCount + "条，失败"
                    + failedCount + "条，共" + messages.size() + "条；下次任务会继续重试（可能重发已成功品牌）");
        }
        sysConfigService.updateConfigByKey(LAST_SIGNATURE_KEY, signature);
        log.info("好价格Offer已按品牌推送飞书：{}个品牌，{}条消息，{}个料号，其中CPU{}个",
                brandTexts.size(), messages.size(), selectedPartNumbers.size(), cpuCount);
    }

    private String buildSignature(String today, String threshold, Map<String, String> brandTexts) {
        StringBuilder source = new StringBuilder();
        source.append(today).append('\n').append(threshold).append('\n');
        for (Map.Entry<String, String> entry : brandTexts.entrySet()) {
            source.append(entry.getKey()).append('\n').append(entry.getValue()).append('\n');
        }
        return Md5Utils.hash(source.toString());
    }

    private List<GoodOfferMessage> buildMessages(Map<String, String> brandTexts, String threshold,
                                                  int totalPartNumbers, int cpuCount) {
        List<GoodOfferMessage> messages = new ArrayList<>();
        int brandIndex = 0;
        int brandTotal = brandTexts.size();
        for (Map.Entry<String, String> entry : brandTexts.entrySet()) {
            brandIndex++;
            String brand = entry.getKey();
            List<String> chunks = splitTextByCompleteLines(entry.getValue(), MESSAGE_TEXT_LIMIT);
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                int partCount = countOfferRows(chunk);
                String title = "【好价格Offer】" + brand + " · " + partCount + " 个料号";
                if (chunks.size() > 1) {
                    title += "（" + (i + 1) + "/" + chunks.size() + "）";
                }
                StringBuilder detail = new StringBuilder();
                detail.append("价格优势阈值：").append(threshold).append(" USD\n")
                        .append("品牌：").append(brand).append("（").append(brandIndex)
                        .append("/").append(brandTotal).append("）\n")
                        .append("本批料号：").append(partCount).append(" 个（今日共")
                        .append(totalPartNumbers).append(" 个，其中CPU ").append(cpuCount).append(" 个）\n");
                if (chunks.size() > 1) {
                    detail.append("分段：").append(i + 1).append("/").append(chunks.size()).append('\n');
                }
                detail.append('\n').append(chunk);
                messages.add(new GoodOfferMessage(brand, title, detail.toString(),
                        partCount, i + 1, chunks.size()));
            }
        }
        return messages;
    }

    /** 按完整行分段；后续分段保留 M&E 日期、表头和品牌行，避免数据列错位。 */
    private List<String> splitTextByCompleteLines(String text, int limit) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        if (text.length() <= limit) {
            return Collections.singletonList(text);
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        int prefixCount = Math.min(1, lines.length);
        if (lines.length > 1 && lines[1].startsWith("Part No.")) {
            prefixCount = 2;
        }
        if (lines.length > prefixCount) {
            String possibleBrand = lines[prefixCount].trim();
            if (!possibleBrand.isEmpty() && !possibleBrand.contains("\t")) {
                prefixCount++;
            }
        }
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < prefixCount; i++) {
            if (i > 0) {
                prefix.append('\n');
            }
            prefix.append(lines[i]);
        }
        List<String> bodies = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = prefixCount; i < lines.length; i++) {
            String line = lines[i];
            int projected = prefix.length() + 1 + current.length()
                    + (current.length() > 0 ? 1 : 0) + line.length();
            if (current.length() > 0 && projected > limit) {
                bodies.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0 || bodies.isEmpty()) {
            bodies.add(current.toString());
        }
        List<String> chunks = new ArrayList<>();
        for (String body : bodies) {
            chunks.add((prefix.length() > 0 ? prefix.toString() + "\n" : "") + body);
        }
        return chunks;
    }

    private int countOfferRows(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String line : text.split("\\R")) {
            if (line.contains("\t") && !line.startsWith("Part No.")) {
                count++;
            }
        }
        return count;
    }

    private boolean sendWithRetry(GoodOfferMessage message) {
        for (int attempt = 1; attempt <= SEND_MAX_ATTEMPTS; attempt++) {
            if (aiChannelService.sendText(message.title, message.detail)) {
                return true;
            }
            if (attempt < SEND_MAX_ATTEMPTS) {
                long delay = RETRY_BASE_MS * (1L << (attempt - 1));
                log.warn("好价格Offer飞书消息发送失败，{}毫秒后第{}次重试：品牌={}，分段={}/{}",
                        delay, attempt, message.brand, message.partIndex, message.partTotal);
                sleepQuietly(delay);
            }
        }
        return false;
    }

    private void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("好价格Offer飞书限速等待被中断");
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static final class GoodOfferMessage {
        private final String brand;
        private final String title;
        private final String detail;
        private final int partCount;
        private final int partIndex;
        private final int partTotal;

        private GoodOfferMessage(String brand, String title, String detail,
                                 int partCount, int partIndex, int partTotal) {
            this.brand = brand;
            this.title = title;
            this.detail = detail;
            this.partCount = partCount;
            this.partIndex = partIndex;
            this.partTotal = partTotal;
        }
    }
}
