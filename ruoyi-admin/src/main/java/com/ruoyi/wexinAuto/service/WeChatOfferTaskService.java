package com.ruoyi.wexinAuto.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.crm.domain.CrmOffer;
import com.ruoyi.crm.domain.CrmSupplier;
import com.ruoyi.crm.domain.OfferTextLine;
import com.ruoyi.crm.mapper.CrmOfferMapper;
import com.ruoyi.crm.mapper.CrmSupplierMapper;
import com.ruoyi.crm.service.ICrmOfferService;
import com.ruoyi.crm.util.PnUtils;
import com.ruoyi.crm.util.OfferTextFormatter;
import com.ruoyi.wexinAuto.config.WeChatAutomationConfig;
import com.ruoyi.wexinAuto.mapper.WeChatOfferTaskMapper;
import com.ruoyi.wexinAuto.model.PartyMatch;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import com.ruoyi.wexinAuto.model.WeChatOfferTask;
import com.ruoyi.wexinAuto.model.WeChatSendItem;
import com.ruoyi.wexinAuto.model.WeChatSendResult;
import com.ruoyi.wexinAuto.model.WeChatTaskTarget;
import com.ruoyi.wexinAuto.model.WeChatTaskPreview;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 微信询报价任务生成、审批和逐目标发送。 */
@Slf4j
@Service
public class WeChatOfferTaskService {

    public static final String TASK_SUPPLIER_INQUIRY = "SUPPLIER_INQUIRY";
    public static final String TASK_CUSTOMER_REPLY = "CUSTOMER_REPLY";
    public static final String TASK_CUSTOMER_PUSH = "CUSTOMER_PUSH";
    public static final String TASK_UNMAPPED_CHAT = "UNMAPPED_CHAT";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED_PENDING_SEND";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_NO_ACTION = "NO_ACTION";

    private static final int TASK_NOT_CREATED = 0;
    private static final int TASK_INSERTED = 1;
    private static final int TASK_EXISTS = 2;

    private final WeChatOfferTaskMapper taskMapper;
    private final CrmOfferMapper offerMapper;
    private final CrmSupplierMapper supplierMapper;
    private final WeChatMessageStore messageStore;
    private final WeChatPartyService partyService;
    private final ICrmOfferService offerService;
    private final WeChatAutomationConfig config;
    private final WeChatGatewayService gateway;
    private final ObjectMapper jackson = new ObjectMapper();
    private volatile long lastGenerateAt;
    private volatile long lastSendAt;

    public WeChatOfferTaskService(WeChatOfferTaskMapper taskMapper, CrmOfferMapper offerMapper,
                                  CrmSupplierMapper supplierMapper, WeChatMessageStore messageStore,
                                  WeChatPartyService partyService, ICrmOfferService offerService,
                                  WeChatAutomationConfig config, WeChatGatewayService gateway) {
        this.taskMapper = taskMapper;
        this.offerMapper = offerMapper;
        this.supplierMapper = supplierMapper;
        this.messageStore = messageStore;
        this.partyService = partyService;
        this.offerService = offerService;
        this.config = config;
        this.gateway = gateway;
    }

    public List<WeChatOfferTask> selectTaskList(WeChatOfferTask query) {
        List<WeChatOfferTask> rows = taskMapper.selectTaskList(query);
        for (WeChatOfferTask task : rows) {
            fillTargets(task);
        }
        return rows;
    }

    public WeChatOfferTask selectTaskById(Long id) {
        WeChatOfferTask task = taskMapper.selectTaskById(id);
        if (task == null) {
            throw new ServiceException("任务不存在：" + id);
        }
        fillTargets(task);
        fillPreviews(task);
        return task;
    }

    /** 扫描已完成录入的消息，按料号生成待审批任务。 */
    public synchronized Map<String, Object> generateOnce(String chat) {
        return generateOnce(chat, false);
    }

    /** force=true 用于后台管理页的人工扫描；定时扫描仍受 autoTaskGenerateEnabled 控制。 */
    public synchronized Map<String, Object> generateOnce(String chat, boolean force) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean enabled = config.bool("autoTaskGenerateEnabled", false);
        boolean manual = force || StringUtils.isNotBlank(chat);
        out.put("enabled", enabled);
        if (!enabled && !manual) {
            out.put("message", "任务生成已关闭(autoTaskGenerateEnabled=false)");
            return out;
        }
        if (!manual) {
            long scanSec = Math.max(5, config.integer("taskGenerateScanSec", 60));
            if (lastGenerateAt > 0 && System.currentTimeMillis() - lastGenerateAt < scanSec * 1000L) {
                out.put("message", "未到任务生成扫描间隔(taskGenerateScanSec=" + scanSec + "s)");
                return out;
            }
            lastGenerateAt = System.currentTimeMillis();
        }
        int batch = Math.max(1, Math.min(100, config.integer("taskGenerateBatchSize", 20)));
        int retention = Math.max(1, config.integer("msgRetentionDays", 30));
        int timeout = Math.max(30, config.integer("processingTimeoutSec", 600));
        String since = fmtTime(System.currentTimeMillis() - retention * 86400000L);
        List<WeChatMessage> rows = messageStore.queryTaskCandidates(since, batch, StringUtils.trimToNull(chat), timeout);
        int generatedMessages = 0;
        int generatedTasks = 0;
        int manualMessages = 0;
        int skippedMessages = 0;
        int failedMessages = 0;
        for (WeChatMessage message : rows) {
            if (!messageStore.markTaskProcessing(message.getUsername(), message.getLocalId(), timeout)) {
                continue;
            }
            try {
                GenerationResult result = generateMessage(message);
                if (result.handled > 0) {
                    generatedMessages++;
                }
                generatedTasks += result.created;
                if (result.manual) {
                    manualMessages++;
                    messageStore.updateTaskState(message.getUsername(), message.getLocalId(),
                            "MANUAL_REVIEW", result.message);
                } else if (result.handled > 0) {
                    messageStore.updateTaskState(message.getUsername(), message.getLocalId(),
                            "GENERATED", result.message);
                } else {
                    skippedMessages++;
                    messageStore.updateTaskState(message.getUsername(), message.getLocalId(),
                            "SKIPPED", result.message);
                }
            } catch (Exception e) {
                failedMessages++;
                log.error("任务生成失败: chat={} localId={}", message.getName(), message.getLocalId(), e);
                messageStore.updateTaskState(message.getUsername(), message.getLocalId(), "FAILED",
                        clip("任务生成失败：" + e.getMessage(), 1000));
            }
        }
        out.put("scanned", rows.size());
        out.put("generatedMessages", generatedMessages);
        out.put("generatedTasks", generatedTasks);
        out.put("manualMessages", manualMessages);
        out.put("skippedMessages", skippedMessages);
        out.put("failedMessages", failedMessages);
        out.put("message", "任务生成完成：扫描 " + rows.size() + " 条，生成 " + generatedTasks + " 条任务");
        return out;
    }

    private GenerationResult generateMessage(WeChatMessage message) throws Exception {
        GenerationResult result = new GenerationResult();
        PartyMatch party = partyService.resolve(message.getName());
        if (party.isUnmappedOrAmbiguous()) {
            int taskResult = createUnmappedTask(message, party.getMessage());
            if (taskResult == TASK_INSERTED) {
                result.created++;
            }
            if (taskResult > 0) {
                result.handled++;
            }
            result.message = party.getMessage();
            return result;
        }
        if (!party.isOk()) {
            result.manual = true;
            result.message = party.getStatus() + ": " + party.getMessage();
            return result;
        }
        if (partyService.isSelf(message.getSender())) {
            result.message = "SELF_OUTBOUND_ONLY";
            return result;
        }
        List<CrmOffer> offers = loadEnteredOffers(message);
        if (offers.isEmpty()) {
            result.manual = true;
            result.message = "NO_ENTERED_OFFER";
            return result;
        }
        boolean externalInquiry = "INQ".equalsIgnoreCase(message.getMsgCategory());
        boolean externalOffer = "OFFER".equalsIgnoreCase(message.getMsgCategory());
        List<String> skips = new ArrayList<>();
        List<String> manualReasons = new ArrayList<>();
        for (CrmOffer offer : offers) {
            String pn = PnUtils.normalize(offer.getProductCode());
            if (pn.isEmpty()) {
                addReason(manualReasons, "MISSING_PN");
                continue;
            }
            Set<String> directions = partyService.directionTokens(offer.getProductType(), offer.getProductBrand());
            if (directions.isEmpty()) {
                addReason(manualReasons, "UNKNOWN_PRODUCT_TYPE");
                continue;
            }
            if (externalInquiry) {
                List<CrmSupplier> suppliers = partyService.directionTargets(offer.getProductType(),
                        offer.getProductBrand(), "sales", party.getSupplier().getId());
                List<WeChatTaskTarget> targets = targets(suppliers, "sales", message.getName());
                if (targets.isEmpty()) {
                    skips.add(pn + ":NO_TARGET");
                    continue;
                }
                int taskResult = createTask(message, offer, TASK_SUPPLIER_INQUIRY, targets, null, "供应商询价");
                if (taskResult == TASK_INSERTED) {
                    result.created++;
                }
                if (taskResult > 0) {
                    result.handled++;
                }
            } else if (externalOffer) {
                if (!hasUsdOfferPrice(offer)) {
                    addReason(manualReasons, "NO_PRICE");
                    continue;
                }
                MatchResult match = matchCustomers(message, party.getSupplier(), offer);
                if (!match.targets.isEmpty()) {
                    int taskResult = createTask(message, offer, TASK_CUSTOMER_REPLY, match.targets, match.json, "客户回复");
                    if (taskResult == TASK_INSERTED) {
                        result.created++;
                    }
                    if (taskResult > 0) {
                        result.handled++;
                    }
                }
                boolean advantage = isAdvantage(pn);
                if (advantage || !match.hasMatch) {
                    List<CrmSupplier> customers = partyService.directionTargets(offer.getProductType(),
                            offer.getProductBrand(), "purchase", party.getSupplier().getId());
                    List<WeChatTaskTarget> pushTargets = targets(customers, "purchase", message.getName());
                    if (pushTargets.isEmpty()) {
                        skips.add(pn + ":NO_TARGET");
                    } else {
                        int taskResult = createTask(message, offer, TASK_CUSTOMER_PUSH, pushTargets, null, "客户推送");
                        if (taskResult == TASK_INSERTED) {
                            result.created++;
                        }
                        if (taskResult > 0) {
                            result.handled++;
                        }
                    }
                }
            }
        }
        if (!manualReasons.isEmpty()) {
            result.manual = true;
            String reason = String.join(",", manualReasons);
            result.message = result.handled > 0
                    ? "PARTIAL_GENERATED_MANUAL_REVIEW:" + reason : reason;
        } else if (!skips.isEmpty()) {
            result.message = String.join(",", skips);
        } else if (result.message == null) {
            result.message = result.handled > 0 ? "TASKS_CREATED" : "NO_TARGET";
        }
        return result;
    }

    private List<CrmOffer> loadEnteredOffers(WeChatMessage message) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(message.getEntryOfferIds())) {
            for (String id : message.getEntryOfferIds().split(",")) {
                try {
                    ids.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        List<CrmOffer> offers = ids.isEmpty() ? new ArrayList<>()
                : offerMapper.selectByIds(new ArrayList<>(ids));
        if (offers.isEmpty()) {
            offers = offerMapper.selectByWechat(StringUtils.defaultString(message.getServerId()),
                    message.getUsername(), message.getLocalId());
        }
        return offers;
    }

    private static final class GenerationResult {
        private int created;
        private int handled;
        private boolean manual;
        private String message;
    }

    private static final class MatchResult {
        private final List<WeChatTaskTarget> targets = new ArrayList<>();
        private String json;
        private boolean hasMatch;
    }

    private int createUnmappedTask(WeChatMessage message, String reason) throws Exception {
        WeChatOfferTask task = baseTask(message);
        task.setTaskType(TASK_UNMAPPED_CHAT);
        task.setPnNormalized("");
        task.setSendTarget("[]");
        task.setSendTargetSuccess("[]");
        task.setRemark(clip(reason, 1000));
        if (taskMapper.insertTaskIgnore(task) > 0) {
            return TASK_INSERTED;
        }
        return taskMapper.selectByUnique(task.getSourceUsername(), task.getSourceLocalId(),
                task.getTaskType(), task.getPnNormalized(), task.getSendType()) != null
                ? TASK_EXISTS : TASK_NOT_CREATED;
    }

    private int createTask(WeChatMessage message, CrmOffer offer, String taskType,
                           List<WeChatTaskTarget> targets, String matchedJson, String remark) throws Exception {
        if (targets == null || targets.isEmpty()) {
            return TASK_NOT_CREATED;
        }
        WeChatOfferTask task = baseTask(message);
        task.setTaskType(taskType);
        task.setPnOriginal(offer.getProductCode());
        task.setPnNormalized(PnUtils.normalize(offer.getProductCode()));
        task.setBrand(offer.getProductBrand());
        task.setProductType(offer.getProductType());
        task.setSpec(offer.getSpec());
        task.setQty(offer.getQuantity());
        task.setOriPrice(offer.getPriceCost());
        task.setOfferPrice(offer.getPriceOffer());
        task.setSendTarget(jackson.writeValueAsString(targets));
        task.setSendTargetSuccess("[]");
        task.setSendContent(columnLine(offer, !TASK_SUPPLIER_INQUIRY.equals(taskType)));
        task.setMatchedInqJson(matchedJson);
        task.setOfferJson(offerSnapshot(message, offer));
        task.setRemark(remark);
        if (taskMapper.insertTaskIgnore(task) > 0) {
            return TASK_INSERTED;
        }
        return taskMapper.selectByUnique(task.getSourceUsername(), task.getSourceLocalId(),
                task.getTaskType(), task.getPnNormalized(), task.getSendType()) != null
                ? TASK_EXISTS : TASK_NOT_CREATED;
    }

    private WeChatOfferTask baseTask(WeChatMessage message) {
        WeChatOfferTask task = new WeChatOfferTask();
        task.setSourceUsername(message.getUsername());
        task.setSourceLocalId(message.getLocalId());
        task.setSourceServerId(message.getServerId());
        task.setSourceMessageTime(parseDate(message.getCreateTime()));
        task.setSourceCategory(StringUtils.upperCase(StringUtils.defaultString(message.getMsgCategory())));
        task.setSendType("WECHAT");
        task.setOriTarget(message.getName());
        task.setOriMsg(clip(message.getContent(), 4000));
        task.setHandleStatus(STATUS_PENDING_APPROVAL);
        task.setSendAttempts(0);
        task.setCreateBy("wechat_auto");
        task.setUpdateBy("wechat_auto");
        return task;
    }

    private MatchResult matchCustomers(WeChatMessage source, CrmSupplier sourceSupplier, CrmOffer offer) {
        MatchResult result = new MatchResult();
        int days = config.matchDayNo();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -(days - 1));
        String since = fmtTime(calendar.getTimeInMillis());
        List<CrmOffer> candidates = offerMapper.selectMatchingInq(PnUtils.normalize(offer.getProductCode()),
                since, PnUtils.escapeLikePrefix(offer.getProductCode()));
        if (candidates == null || candidates.isEmpty()) {
            return result;
        }
        List<String> keys = new ArrayList<>();
        for (CrmOffer candidate : candidates) {
            if (candidate.getWechatUsername() != null) {
                keys.add(candidate.getWechatUsername() + ":" + candidate.getWechatLocalId());
            }
        }
        Map<String, WeChatMessage> messages = new HashMap<>();
        for (WeChatMessage message : messageStore.queryByKeys(keys)) {
            messages.put(message.getUsername() + ":" + message.getLocalId(), message);
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        Map<String, WeChatTaskTarget> targets = new LinkedHashMap<>();
        for (CrmOffer candidate : candidates) {
            WeChatMessage inquiryMessage = messages.get(candidate.getWechatUsername() + ":" + candidate.getWechatLocalId());
            if (inquiryMessage == null || partyService.isSelf(inquiryMessage.getSender())
                    || StringUtils.equals(inquiryMessage.getName(), source.getName())) {
                continue;
            }
            PartyMatch customer = partyService.resolve(inquiryMessage.getName());
            if (!customer.isOk() || customer.getSupplier().getId().equals(sourceSupplier.getId())) {
                continue;
            }
            result.hasMatch = true;
            for (WeChatTaskTarget target : targets(
                    Collections.singletonList(customer.getSupplier()), "purchase", source.getName())) {
                targets.putIfAbsent(target.getName(), target);
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("offerId", candidate.getId());
            snapshot.put("customerCode", customer.getSupplier().getSupplierCode());
            snapshot.put("customerName", customer.getSupplier().getSupplierName());
            snapshot.put("chatName", inquiryMessage.getName());
            snapshot.put("sender", inquiryMessage.getSender());
            snapshot.put("messageTime", inquiryMessage.getCreateTime());
            snapshot.put("content", clip(inquiryMessage.getContent(), 800));
            snapshot.put("pn", candidate.getProductCode());
            snapshot.put("quantity", candidate.getQuantity());
            snapshot.put("priceCost", candidate.getPriceCost());
            matched.add(snapshot);
        }
        result.targets.addAll(targets.values());
        try {
            result.json = matched.isEmpty() ? null : jackson.writeValueAsString(matched);
        } catch (Exception e) {
            log.warn("匹配客户INQ快照序列化失败: {}", e.getMessage());
        }
        return result;
    }

    private boolean isAdvantage(String pn) {
        try {
            Boolean value = offerService.priceAdvantageBatch(Collections.singletonList(pn), 2).get(pn);
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            log.warn("好价格判断失败: pn={} error={}", pn, e.getMessage());
            return false;
        }
    }

    private boolean hasUsdOfferPrice(CrmOffer offer) {
        if (offer.getPriceOffer() == null || offer.getPriceOffer() <= 0) {
            return false;
        }
        String currency = StringUtils.trimToEmpty(offer.getPriceUnit()).toUpperCase(Locale.ROOT);
        return currency.isEmpty() || "USD".equals(currency) || "$".equals(currency);
    }

    private void addReason(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    private List<WeChatTaskTarget> targets(List<CrmSupplier> suppliers, String groupField, String sourceGroup) {
        Map<String, WeChatTaskTarget> targets = new LinkedHashMap<>();
        for (CrmSupplier supplier : suppliers) {
            String rawGroups = "sales".equalsIgnoreCase(groupField)
                    ? supplier.getSalesGroup() : supplier.getPurchaseGroup();
            for (String group : partyService.groups(rawGroups)) {
                if (StringUtils.equals(group, sourceGroup)) {
                    continue;
                }
                targets.putIfAbsent(group, new WeChatTaskTarget(group,
                        supplier.getSupplierCode(), supplier.getSupplierName()));
            }
        }
        return new ArrayList<>(targets.values());
    }

    private String columnLine(CrmOffer offer, boolean includePrice) {
        OfferTextLine line = new OfferTextLine();
        line.setPartNumber(offer.getProductCode());
        line.setQuantity(offer.getQuantity());
        line.setPrice(offer.getPriceOffer());
        return OfferTextFormatter.formatRow(line, includePrice, Collections.<String>emptyList());
    }

    /** 审批只改价格时，按任务原格式重建文案；人工修改过文案则保持人工内容。 */
    private String approvedLine(WeChatOfferTask task, Double price) {
        OfferTextLine line = new OfferTextLine();
        line.setPartNumber(task.getPnOriginal());
        line.setQuantity(task.getQty());
        line.setPrice(price);
        return OfferTextFormatter.formatRow(line, !TASK_SUPPLIER_INQUIRY.equals(task.getTaskType()),
                Collections.<String>emptyList());
    }

    private String offerSnapshot(WeChatMessage message, CrmOffer offer) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("offerId", offer.getId());
            snapshot.put("chatName", message.getName());
            snapshot.put("sender", message.getSender());
            snapshot.put("messageTime", message.getCreateTime());
            snapshot.put("category", message.getMsgCategory());
            snapshot.put("content", clip(message.getContent(), 800));
            snapshot.put("pn", offer.getProductCode());
            snapshot.put("brand", offer.getProductBrand());
            snapshot.put("productType", offer.getProductType());
            snapshot.put("quantity", offer.getQuantity());
            snapshot.put("priceCost", offer.getPriceCost());
            snapshot.put("priceOffer", offer.getPriceOffer());
            return jackson.writeValueAsString(snapshot);
        } catch (Exception e) {
            return null;
        }
    }

    public int approve(WeChatOfferTask form) {
        if (form == null || form.getId() == null) {
            throw new ServiceException("任务ID不能为空");
        }
        String status = StringUtils.trimToEmpty(form.getHandleStatus());
        if (!STATUS_PENDING_APPROVAL.equals(status) && !STATUS_APPROVED.equals(status)
                && !STATUS_SENT.equals(status) && !STATUS_NO_ACTION.equals(status)) {
            throw new ServiceException("任务状态不合法");
        }
        WeChatOfferTask existing = taskMapper.selectTaskById(form.getId());
        if (existing == null) {
            throw new ServiceException("任务不存在：" + form.getId());
        }
        try {
            if (form.getTargets() != null) {
                form.setSendTarget(jackson.writeValueAsString(form.getTargets()));
            } else if (form.getSendTarget() == null) {
                form.setSendTarget(existing.getSendTarget());
            }
        } catch (Exception e) {
            throw new ServiceException("发送目标格式错误");
        }
        if (STATUS_APPROVED.equals(status) && parseTargets(form.getSendTarget()).isEmpty()) {
            throw new ServiceException("已审批待发送任务必须至少有一个发送目标");
        }
        if (form.getOfferPrice() == null) {
            form.setOfferPrice(existing.getOfferPrice());
        } else if (!TASK_SUPPLIER_INQUIRY.equals(existing.getTaskType())
                && !java.util.Objects.equals(form.getOfferPrice(), existing.getOfferPrice())
                && (form.getSendContent() == null || StringUtils.equals(form.getSendContent(),
                        StringUtils.defaultString(existing.getSendContent())))) {
            form.setSendContent(approvedLine(existing, form.getOfferPrice()));
        }
        if (form.getSendContent() == null) {
            form.setSendContent(existing.getSendContent());
        }
        form.setHandleStatus(status);
        form.setApprovalReason(clip(form.getApprovalReason(), 500));
        form.setApprovedBy(SecurityUtils.getUsername());
        form.setApprovedAt(new Date());
        form.setUpdateBy(SecurityUtils.getUsername());
        return taskMapper.updateApproval(form);
    }

    public int confirmNotSent(Long id) {
        WeChatOfferTask task = taskMapper.selectTaskById(id);
        if (task == null) {
            throw new ServiceException("任务不存在：" + id);
        }
        if (StringUtils.trimToEmpty(task.getLastError()).startsWith("GATEWAY_RESULT_UNKNOWN")) {
            return taskMapper.confirmNotSent(id);
        }
        throw new ServiceException("只有发送结果未知的任务需要人工确认未发送");
    }

    /** 扫描已审批任务，按任务类型和群名合并后发送。 */
    public synchronized Map<String, Object> sendOnce(boolean manual) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean enabled = config.bool("autoSendEnabled", false);
        out.put("enabled", enabled);
        if (!enabled && !manual) {
            out.put("message", "自动发送已关闭(autoSendEnabled=false)");
            return out;
        }
        if (!manual) {
            long scanSec = Math.max(5, config.integer("taskSendScanSec", 30));
            if (lastSendAt > 0 && System.currentTimeMillis() - lastSendAt < scanSec * 1000L) {
                out.put("message", "未到发送扫描间隔(taskSendScanSec=" + scanSec + "s)");
                return out;
            }
            lastSendAt = System.currentTimeMillis();
        }
        int maxAttempts = Math.max(1, config.integer("taskSendMaxAttempts", 3));
        int scanMax = Math.max(1, Math.min(500, config.integer("taskSendScanBatchSize", 200)));
        List<WeChatOfferTask> candidates = taskMapper.selectSendCandidates(maxAttempts, scanMax);
        List<WeChatOfferTask> pending = new ArrayList<>();
        for (WeChatOfferTask task : candidates) {
            fillTargets(task);
            if (hasUnsentTarget(task)) {
                pending.add(task);
            }
        }
        if (pending.isEmpty()) {
            out.put("scannedTasks", candidates.size());
            out.put("sendGroups", 0);
            out.put("sentGroups", 0);
            out.put("failedGroups", 0);
            out.put("message", "没有待发送任务");
            return out;
        }
        String expectedAccount = config.str("autoSendAccount", "Eke");
        if (!gateway.isExpectedSendAccount(expectedAccount)) {
            throw new ServiceException("微信当前登录账号不是 " + expectedAccount + "，已停止自动发送");
        }
        List<SendGroup> groups = buildGroups(pending);
        int chunkSize = Math.max(1, Math.min(20, config.integer("taskSendBatchSize", 20)));
        int sentGroups = 0;
        int failedGroups = 0;
        for (int start = 0; start < groups.size(); start += chunkSize) {
            List<SendGroup> batch = groups.subList(start, Math.min(start + chunkSize, groups.size()));
            Set<Long> attemptTaskIds = new LinkedHashSet<>();
            List<WeChatSendItem> items = new ArrayList<>();
            for (SendGroup group : batch) {
                items.add(new WeChatSendItem(group.target.getName(), group.text));
                for (WeChatOfferTask task : group.tasks) {
                    attemptTaskIds.add(task.getId());
                }
            }
            for (Long taskId : attemptTaskIds) {
                taskMapper.markSendAttempt(taskId, new Date());
            }
            List<WeChatSendResult> results;
            try {
                results = gateway.batchSendTargets(items);
            } catch (Exception e) {
                log.error("批量发送网关异常，停止本轮自动发送", e);
                markUnknown(attemptTaskIds, clip("GATEWAY_RESULT_UNKNOWN: " + e.getMessage(), 1000));
                out.put("message", "发送结果未知，已停止自动重试：" + e.getMessage());
                out.put("sentGroups", sentGroups);
                out.put("failedGroups", failedGroups);
                return out;
            }
            Map<Integer, WeChatSendResult> byIndex = new HashMap<>();
            for (WeChatSendResult result : results) {
                byIndex.put(result.getIndex(), result);
            }
            Set<String> followCodes = new LinkedHashSet<>();
            Set<Long> unknownTaskIds = new LinkedHashSet<>();
            boolean missingResult = false;
            for (int i = 0; i < batch.size(); i++) {
                SendGroup group = batch.get(i);
                WeChatSendResult result = byIndex.get(i);
                if (result == null) {
                    missingResult = true;
                    applyFailure(group, "GATEWAY_RESULT_UNKNOWN: 网关未返回该目标结果");
                    for (WeChatOfferTask task : group.tasks) {
                        unknownTaskIds.add(task.getId());
                    }
                    failedGroups++;
                } else if (result.isOk()) {
                    applySuccess(group);
                    sentGroups++;
                    if (StringUtils.isNotBlank(group.target.getPartyCode())) {
                        followCodes.add(group.target.getPartyCode());
                    }
                } else {
                    applyFailure(group, StringUtils.defaultIfBlank(result.getMessage(), result.getStatus()));
                    failedGroups++;
                }
            }
            updateFollowUp(followCodes);
            if (missingResult) {
                markUnknown(unknownTaskIds, "GATEWAY_RESULT_UNKNOWN: 网关返回结果不完整");
                out.put("message", "网关返回结果不完整，已停止自动重试");
                out.put("sentGroups", sentGroups);
                out.put("failedGroups", failedGroups);
                return out;
            }
        }
        out.put("scannedTasks", candidates.size());
        out.put("sendGroups", groups.size());
        out.put("sentGroups", sentGroups);
        out.put("failedGroups", failedGroups);
        out.put("message", "发送扫描完成：目标组 " + groups.size() + "，成功 " + sentGroups + "，失败 " + failedGroups);
        return out;
    }

    private static final class SendGroup {
        private String taskType;
        private WeChatTaskTarget target;
        private final List<WeChatOfferTask> tasks = new ArrayList<>();
        private String text;
    }

    private List<SendGroup> buildGroups(List<WeChatOfferTask> tasks) {
        Map<String, SendGroup> groups = new LinkedHashMap<>();
        for (WeChatOfferTask task : tasks) {
            Set<String> success = new HashSet<>();
            for (WeChatTaskTarget target : task.getSuccessTargets()) {
                success.add(target.getName());
            }
            for (WeChatTaskTarget target : task.getTargets()) {
                if (success.contains(target.getName())) {
                    continue;
                }
                String key = StringUtils.defaultString(task.getSendType()) + "\u0000"
                        + task.getTaskType() + "\u0000" + target.getName();
                SendGroup group = groups.computeIfAbsent(key, ignored -> {
                    SendGroup value = new SendGroup();
                    value.taskType = task.getTaskType();
                    value.target = target;
                    return value;
                });
                group.tasks.add(task);
            }
        }
        for (SendGroup group : groups.values()) {
            boolean includePrice = !TASK_SUPPLIER_INQUIRY.equals(group.taskType);
            Map<String, OfferTextLine> rows = new LinkedHashMap<>();
            group.tasks.sort(Comparator.comparing(WeChatOfferTask::getId));
            for (WeChatOfferTask task : group.tasks) {
                String line = StringUtils.defaultIfBlank(task.getSendContent(), task.getPnOriginal());
                if (StringUtils.isNotBlank(line)) {
                    OfferTextLine row = new OfferTextLine();
                    row.setPartNumber(task.getPnOriginal());
                    row.setQuantity(task.getQty());
                    row.setBrand(task.getBrand());
                    row.setPrice(task.getOfferPrice());
                    row.setRawLine(line);
                    rows.putIfAbsent(StringUtils.defaultString(task.getPnNormalized()) + "\u0000" + line, row);
                }
            }
            group.text = OfferTextFormatter.formatSendText(new ArrayList<>(rows.values()), includePrice,
                    Collections.<String>emptyList());
        }
        return new ArrayList<>(groups.values());
    }

    private void applySuccess(SendGroup group) {
        Date now = new Date();
        for (WeChatOfferTask task : group.tasks) {
            List<WeChatTaskTarget> success = parseTargets(task.getSendTargetSuccess());
            boolean exists = false;
            for (WeChatTaskTarget target : success) {
                if (StringUtils.equals(target.getName(), group.target.getName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                success.add(group.target);
            }
            task.setSuccessTargets(success);
            String json;
            try {
                json = jackson.writeValueAsString(success);
            } catch (Exception e) {
                json = task.getSendTargetSuccess();
            }
            task.setSendTargetSuccess(json);
            boolean allDone = true;
            Set<String> successNames = new HashSet<>();
            for (WeChatTaskTarget target : success) {
                successNames.add(target.getName());
            }
            for (WeChatTaskTarget target : task.getTargets()) {
                if (!successNames.contains(target.getName())) {
                    allDone = false;
                    break;
                }
            }
            taskMapper.updateSendResult(task.getId(), json,
                    allDone ? STATUS_SENT : STATUS_APPROVED, null, allDone ? now : null);
        }
    }

    private void applyFailure(SendGroup group, String error) {
        String value = clip(error, 1000);
        for (WeChatOfferTask task : group.tasks) {
            taskMapper.updateSendResult(task.getId(), task.getSendTargetSuccess(),
                    STATUS_APPROVED, value, null);
        }
    }

    private void markUnknown(Set<Long> taskIds, String error) {
        for (Long taskId : taskIds) {
            taskMapper.markUnknown(taskId, error);
        }
    }

    private void updateFollowUp(Set<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        try {
            supplierMapper.updateWechatFollowUpByCodes(new ArrayList<>(codes), new Date(), "AI自动询报价");
        } catch (Exception e) {
            log.warn("更新交易对手跟进时间失败: {}", e.getMessage());
        }
    }

    private boolean hasUnsentTarget(WeChatOfferTask task) {
        Set<String> success = new HashSet<>();
        for (WeChatTaskTarget target : task.getSuccessTargets()) {
            success.add(target.getName());
        }
        for (WeChatTaskTarget target : task.getTargets()) {
            if (!success.contains(target.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 按目标读取同组已审批任务，生成审批页最终文本预览；预览不落库。 */
    private void fillPreviews(WeChatOfferTask task) {
        List<WeChatTaskPreview> previews = new ArrayList<>();
        for (WeChatTaskTarget target : task.getTargets()) {
            if (target == null || StringUtils.isBlank(target.getName())) {
                continue;
            }
            Map<Long, WeChatOfferTask> groupTasks = new LinkedHashMap<>();
            for (WeChatOfferTask approved : taskMapper.selectApprovedForTarget(task.getTaskType(),
                    task.getSendType(), target.getName(), 200)) {
                fillTargets(approved);
                groupTasks.put(approved.getId(), approved);
            }
            groupTasks.put(task.getId(), task);
            String text = "";
            for (SendGroup group : buildGroups(new ArrayList<>(groupTasks.values()))) {
                if (StringUtils.equals(group.target.getName(), target.getName())) {
                    text = group.text;
                    break;
                }
            }
            previews.add(new WeChatTaskPreview(target.getName(), target.getPartyName(), text));
        }
        task.setPreviews(previews);
    }

    private void fillTargets(WeChatOfferTask task) {
        task.setTargets(parseTargets(task.getSendTarget()));
        task.setSuccessTargets(parseTargets(task.getSendTargetSuccess()));
    }

    private List<WeChatTaskTarget> parseTargets(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return jackson.readValue(json, new TypeReference<List<WeChatTaskTarget>>() { });
        } catch (Exception e) {
            log.warn("任务发送目标JSON解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    private Date parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return new Date();
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
        } catch (Exception e) {
            return new Date();
        }
    }

    private String fmtTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time));
    }

    private String fmtPrice(Double price) {
        return price == null ? "" : new BigDecimal(String.valueOf(price)).stripTrailingZeros().toPlainString();
    }

    private String clip(String value, int max) {
        String text = StringUtils.defaultString(value);
        return text.length() > max ? text.substring(0, max) : text;
    }
}
