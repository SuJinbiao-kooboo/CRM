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
import com.ruoyi.crm.util.OfferNoStockUtils;
import com.ruoyi.crm.util.PnUtils;
import com.ruoyi.crm.util.OfferTextFormatter;
import com.ruoyi.wexinAuto.config.WeChatAutomationConfig;
import com.ruoyi.wexinAuto.mapper.WeChatOfferTaskMapper;
import com.ruoyi.wexinAuto.model.PartyMatch;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import com.ruoyi.wexinAuto.model.WeChatManualTaskRequest;
import com.ruoyi.wexinAuto.model.WeChatOfferTask;
import com.ruoyi.wexinAuto.model.WeChatForwardResult;
import com.ruoyi.wexinAuto.model.WeChatForwardSkip;
import com.ruoyi.wexinAuto.model.WeChatSendItem;
import com.ruoyi.wexinAuto.model.WeChatSendResult;
import com.ruoyi.wexinAuto.model.WeChatSessionStatus;
import com.ruoyi.wexinAuto.model.WeChatTaskTarget;
import com.ruoyi.wexinAuto.model.WeChatTaskPreview;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.atomic.AtomicLong;

/** 微信询报价任务生成、审批和逐目标发送。 */
@Slf4j
@Service
public class WeChatOfferTaskService {

    public static final String TASK_SUPPLIER_INQUIRY = "SUPPLIER_INQUIRY";
    public static final String TASK_CUSTOMER_REPLY = "CUSTOMER_REPLY";
    public static final String TASK_CUSTOMER_PUSH = "CUSTOMER_PUSH";
    public static final String TASK_MANUAL_SEND = "MANUAL_SEND";
    public static final String TASK_UNMAPPED_CHAT = "UNMAPPED_CHAT";
    public static final String TASK_SOURCE_AUTO = "消息自动";
    public static final String TASK_SOURCE_MANUAL = "人工手动";
    public static final String TASK_SOURCE_AUTO_INQUIRY_OFFER = "自动询Offer";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED_PENDING_SEND";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_NO_ACTION = "NO_ACTION";
    /** 逐目标直发：每个群单独调用一次发送。 */
    public static final String DELIVERY_DIRECT = "DIRECT";
    /** 微信转发：先发到文件传输助手，再每批最多 9 个目标点“分别发送”。 */
    public static final String DELIVERY_FORWARD = "FORWARD";

    private static final int TASK_NOT_CREATED = 0;
    private static final int TASK_INSERTED = 1;
    private static final int TASK_EXISTS = 2;
    private static final int TARGET_FIELD_MAX_LENGTH = 300;
    private static final int MANUAL_CONTENT_MAX_LENGTH = 100000;
    private static final String AUTO_INQUIRY_SOURCE_USERNAME = "AUTO_INQUIRY_OFFER";
    private static final String AUTO_INQUIRY_OPERATOR = "auto_inquiry_offer";
    private static final AtomicLong MANUAL_SEQUENCE =
            new AtomicLong(System.currentTimeMillis() * 1000L);

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

    /**
     * 手工创建任务弹窗的供应商候选。
     *
     * @param keyword   供应商名称/编码/别名模糊词
     * @param direction 合作方向（可空）
     * @param groupSide sales=销售群，purchase=采购群，all/空=两种群任一即可
     * @return 启用的、具备对应群配置的供应商；排除 UNLINKED 兜底供应商
     */
    public List<CrmSupplier> listManualTaskSuppliers(String keyword, String direction, String groupSide) {
        return supplierMapper.selectManualTaskSuppliers(StringUtils.trimToEmpty(keyword),
                StringUtils.trimToEmpty(direction), normalizeManualListGroupSide(groupSide));
    }

    /**
     * 手工创建一条微信任务：同一份粘贴内容发送到所选供应商对应的销售群或采购群。
     *
     * <p>系统将多个群写入同一条任务的 send_target，沿用现有审批、批量审批和发送扫描流程。
     * 发送目标数量不设上限，单个群名/伙伴字段最长 300 字符。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createManualTask(WeChatManualTaskRequest request) {
        return createManualTask(request, false);
    }

    /**
     * 手工创建任务，并可选择直接置为“已审批待发送”。
     *
     * @param request          手工任务内容、群方向和供应商
     * @param directlyApproved 是否跳过待审批，直接进入已审批待发送
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createManualTask(WeChatManualTaskRequest request, boolean directlyApproved) {
        if (request == null) {
            throw new ServiceException("请求不能为空");
        }
        String content = StringUtils.trimToEmpty(request.getContent());
        if (content.isEmpty()) {
            throw new ServiceException("请粘贴要发送的消息内容");
        }
        if (content.length() > MANUAL_CONTENT_MAX_LENGTH) {
            throw new ServiceException("消息内容不能超过" + MANUAL_CONTENT_MAX_LENGTH + "个字符");
        }
        String groupSide = normalizeManualGroupSide(request.getGroupSide());
        Set<Long> supplierIds = new LinkedHashSet<>();
        if (request.getSupplierIds() != null) {
            for (Long id : request.getSupplierIds()) {
                if (id != null) {
                    supplierIds.add(id);
                }
            }
        }
        if (supplierIds.isEmpty()) {
            throw new ServiceException("请至少选择一个供应商");
        }

        Map<Long, CrmSupplier> selected = new HashMap<>();
        for (CrmSupplier supplier : supplierMapper.selectAllForWeChat()) {
            if (supplier != null && supplierIds.contains(supplier.getId())
                    && !partyService.isUnlinkedSupplier(supplier)) {
                selected.put(supplier.getId(), supplier);
            }
        }
        if (selected.size() != supplierIds.size()) {
            throw new ServiceException("部分供应商不存在、已停用或属于未关联兜底供应商");
        }

        Map<String, WeChatTaskTarget> targets = new LinkedHashMap<>();
        for (Long supplierId : supplierIds) {
            CrmSupplier supplier = selected.get(supplierId);
            if (supplier.getStatus() == null || supplier.getStatus() != 1
                    || StringUtils.isBlank(supplier.getSupplierCode())) {
                throw new ServiceException("供应商不可用：" + StringUtils.defaultString(supplier.getSupplierName()));
            }
            String rawGroups = "purchase".equals(groupSide)
                    ? supplier.getPurchaseGroup() : supplier.getSalesGroup();
            List<String> groups = partyService.groups(rawGroups);
            if (groups.isEmpty()) {
                throw new ServiceException("供应商未配置" + ("purchase".equals(groupSide) ? "采购群：" : "销售群：")
                        + StringUtils.defaultString(supplier.getSupplierName()));
            }
            for (String group : groups) {
                String targetName = StringUtils.trimToEmpty(group);
                if (targetName.length() > TARGET_FIELD_MAX_LENGTH) {
                    throw new ServiceException("群名超过" + TARGET_FIELD_MAX_LENGTH + "个字符："
                            + StringUtils.defaultString(supplier.getSupplierName()));
                }
                WeChatTaskTarget target = new WeChatTaskTarget(targetName,
                        supplier.getSupplierCode(), supplier.getSupplierName());
                targets.putIfAbsent(targetName, target);
            }
        }
        if (targets.isEmpty()) {
            throw new ServiceException("所选供应商没有可发送的群");
        }
        validateTargets(new ArrayList<>(targets.values()));

        long localId = MANUAL_SEQUENCE.incrementAndGet();
        String operator = currentOperator();
        WeChatOfferTask task = new WeChatOfferTask();
        task.setSourceUsername("MANUAL");
        task.setSourceLocalId(localId);
        task.setSourceMessageTime(new Date());
        task.setSourceCategory("OTHER");
        task.setTaskSource(TASK_SOURCE_MANUAL);
        task.setTaskType(TASK_MANUAL_SEND);
        task.setSendType("WECHAT");
        task.setDeliveryMode(DELIVERY_FORWARD);
        task.setOriTarget("手工创建（" + ("purchase".equals(groupSide) ? "采购群" : "销售群") + "）");
        task.setOriMsg(content);
        task.setPnOriginal("MANUAL");
        task.setPnNormalized("MANUAL");
        task.setQty(0);
        try {
            task.setSendTarget(jackson.writeValueAsString(new ArrayList<>(targets.values())));
        } catch (Exception e) {
            throw new ServiceException("发送目标序列化失败");
        }
        task.setSendTargetSuccess("[]");
        task.setSendContent(content);
        task.setHandleStatus(directlyApproved ? STATUS_APPROVED : STATUS_PENDING_APPROVAL);
        task.setSendAttempts(0);
        task.setRemark("手工创建，目标供应商 " + supplierIds.size() + " 家，目标群 " + targets.size() + " 个");
        task.setCreateBy(operator);
        task.setUpdateBy(operator);
        if (taskMapper.insertTaskIgnore(task) <= 0 || task.getId() == null) {
            throw new ServiceException("手工任务创建失败");
        }
        if (directlyApproved) {
            task.setApprovedBy(operator);
            task.setApprovedAt(new Date());
            task.setApprovalReason("人工手动创建并直接审批");
            if (taskMapper.updateApproval(task) <= 0) {
                throw new ServiceException("手工任务直接审批失败");
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", task.getId());
        out.put("supplierCount", supplierIds.size());
        out.put("targetCount", targets.size());
        out.put("taskSource", TASK_SOURCE_MANUAL);
        out.put("handleStatus", task.getHandleStatus());
        out.put("message", "手工任务已创建：供应商 " + supplierIds.size() + " 家，发送目标 "
                + targets.size() + " 个，状态为" + (directlyApproved ? "已审批待发送" : "待审批"));
        return out;
    }

    /**
     * 自动询Offer定时任务：按单个供应商创建待审批任务，供应商的多个销售群写入同一条任务。
     *
     * <p>sourceLocalId 为当天 yyyyMMdd，pnNormalized 携带供应商 ID，
     * 配合 wechat_offer_task.uk_source_task_pn 实现同日幂等；Quartz 重跑或人工补跑不会重复建任务。
     * 即使供应商开启“自动审批”，这里也固定创建“待审批”，不自动发送。</p>
     *
     * @param supplier 已启用且开启自动询Offer的供应商
     * @param content  祝福语 + 询价正文
     * @param dayKey   当天日期 yyyyMMdd，用于同日去重
     * @return 本次创建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createAutoInquiryOfferTask(CrmSupplier supplier, String content, String dayKey) {
        if (supplier == null || supplier.getId() == null) {
            throw new ServiceException("供应商不能为空");
        }
        if (supplier.getStatus() == null || supplier.getStatus() != 1
                || StringUtils.isBlank(supplier.getSupplierCode())
                || partyService.isUnlinkedSupplier(supplier)) {
            throw new ServiceException("供应商不可用：" + StringUtils.defaultString(supplier.getSupplierName()));
        }
        String normalizedContent = StringUtils.trimToEmpty(content);
        if (normalizedContent.isEmpty()) {
            throw new ServiceException("自动询Offer内容不能为空");
        }
        if (normalizedContent.length() > MANUAL_CONTENT_MAX_LENGTH) {
            throw new ServiceException("自动询Offer内容不能超过" + MANUAL_CONTENT_MAX_LENGTH + "个字符");
        }
        String dateKey = StringUtils.trimToEmpty(dayKey);
        if (!dateKey.matches("\\d{8}")) {
            throw new ServiceException("自动询Offer日期标识必须为yyyyMMdd");
        }

        Map<String, WeChatTaskTarget> targetMap = new LinkedHashMap<>();
        for (String group : partyService.groups(supplier.getSalesGroup())) {
            String targetName = StringUtils.trimToEmpty(group);
            if (targetName.isEmpty()) {
                continue;
            }
            requireTargetFieldLength("群名", targetName);
            targetMap.putIfAbsent(targetName, new WeChatTaskTarget(targetName,
                    supplier.getSupplierCode(), supplier.getSupplierName()));
        }
        List<WeChatTaskTarget> allTargets = new ArrayList<>(targetMap.values());
        if (allTargets.isEmpty()) {
            throw new ServiceException("供应商未配置销售群：" + StringUtils.defaultString(supplier.getSupplierName()));
        }

        List<Long> taskIds = new ArrayList<>();
        int duplicateCount = 0;
        validateTargets(allTargets);
        String taskKey = AUTO_INQUIRY_SOURCE_USERNAME + "_" + supplier.getId();

        WeChatOfferTask task = new WeChatOfferTask();
        task.setSourceUsername(AUTO_INQUIRY_SOURCE_USERNAME);
        task.setSourceLocalId(Long.parseLong(dateKey));
        task.setSourceMessageTime(new Date());
        task.setSourceCategory("OTHER");
        task.setTaskSource(TASK_SOURCE_AUTO_INQUIRY_OFFER);
        task.setTaskType(TASK_MANUAL_SEND);
        task.setSendType("WECHAT");
        task.setDeliveryMode(DELIVERY_FORWARD);
        task.setOriTarget("自动询Offer（销售群）");
        task.setOriMsg(normalizedContent);
        task.setPnOriginal(taskKey);
        task.setPnNormalized(taskKey);
        task.setQty(0);
        try {
            task.setSendTarget(jackson.writeValueAsString(allTargets));
        } catch (Exception e) {
            throw new ServiceException("发送目标序列化失败");
        }
        task.setSendTargetSuccess("[]");
        task.setSendContent(normalizedContent);
        task.setHandleStatus(STATUS_PENDING_APPROVAL);
        task.setSendAttempts(0);
        task.setRemark("自动询Offer，供应商 " + supplier.getSupplierCode() + " "
                + StringUtils.defaultString(supplier.getSupplierName()) + "，目标群 " + allTargets.size() + " 个");
        task.setCreateBy(AUTO_INQUIRY_OPERATOR);
        task.setUpdateBy(AUTO_INQUIRY_OPERATOR);

        if (taskMapper.insertTaskIgnore(task) > 0 && task.getId() != null) {
            taskIds.add(task.getId());
        } else {
            duplicateCount++;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("supplierId", supplier.getId());
        out.put("supplierCode", supplier.getSupplierCode());
        out.put("supplierName", supplier.getSupplierName());
        out.put("created", taskIds.size());
        out.put("duplicates", duplicateCount);
        out.put("taskIds", taskIds);
        out.put("targetCount", allTargets.size());
        out.put("handleStatus", STATUS_PENDING_APPROVAL);
        out.put("message", "自动询Offer任务完成：创建 " + taskIds.size() + " 条，重复跳过 "
                + duplicateCount + " 条，状态为待审批");
        return out;
    }

    private String normalizeManualGroupSide(String groupSide) {
        String value = StringUtils.trimToEmpty(groupSide).toLowerCase(Locale.ROOT);
        if ("purchase".equals(value)) {
            return "purchase";
        }
        if (value.isEmpty() || "sales".equals(value)) {
            return "sales";
        }
        throw new ServiceException("groupSide只能为sales或purchase");
    }

    /** 供应商候选查询允许一次读取销售群和采购群，前端再做即时筛选。 */
    private String normalizeManualListGroupSide(String groupSide) {
        String value = StringUtils.trimToEmpty(groupSide).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "all";
        }
        if ("sales".equals(value) || "purchase".equals(value) || "all".equals(value)) {
            return value;
        }
        throw new ServiceException("groupSide只能为sales、purchase或all");
    }

    /** 发送目标落库前统一校验：目标数量不设上限，群名/伙伴编码/伙伴名称每个字段最多 300 字符。 */
    private void validateTargets(List<WeChatTaskTarget> targets) {
        Set<String> names = new HashSet<>();
        if (targets == null) {
            return;
        }
        for (WeChatTaskTarget target : targets) {
            if (target == null || StringUtils.isBlank(target.getName())) {
                throw new ServiceException("发送目标群名不能为空");
            }
            String name = StringUtils.trimToEmpty(target.getName());
            if (!names.add(name)) {
                throw new ServiceException("发送目标群名重复：" + name);
            }
            requireTargetFieldLength("群名", target.getName());
            requireTargetFieldLength("伙伴编码", target.getPartyCode());
            requireTargetFieldLength("伙伴名称", target.getPartyName());
        }
    }

    private void requireTargetFieldLength(String field, String value) {
        if (value != null && value.length() > TARGET_FIELD_MAX_LENGTH) {
            throw new ServiceException(field + "超过" + TARGET_FIELD_MAX_LENGTH + "个字符");
        }
    }

    /** 任务列表页的两个自动开关状态，配置统一存放在 wechat_analysis_config。 */
    public Map<String, Object> taskSwitches() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("autoTaskGenerateEnabled", config.bool("autoTaskGenerateEnabled", false));
        out.put("autoSendEnabled", config.bool("autoSendEnabled", false));
        return out;
    }

    public Map<String, Object> updateTaskGenerateEnabled(boolean enabled) {
        Map<String, Object> out = gateway.updateTaskGenerateEnabled(enabled);
        out.put("autoTaskGenerateEnabled", enabled);
        return out;
    }

    public Map<String, Object> updateTaskSendEnabled(boolean enabled) {
        Map<String, Object> out = gateway.updateTaskSendEnabled(enabled);
        out.put("autoSendEnabled", enabled);
        return out;
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
        out.put("mode", manual ? "MANUAL" : "SCHEDULED");
        if (!enabled && !manual) {
            out.put("message", "任务生成已关闭(autoTaskGenerateEnabled=false)");
            return out;
        }
        int batch = Math.max(1, Math.min(100, config.integer("taskGenerateBatchSize", 20)));
        int retention = Math.max(1, config.integer("msgRetentionDays", 30));
        int timeout = Math.max(30, config.integer("processingTimeoutSec", 600));
        long now = System.currentTimeMillis();
        String since = fmtTime(now - retention * 86400000L);
        String until = fmtTime(now);
        String chatScope = StringUtils.isBlank(chat) ? "全部聊天对象" : "指定聊天对象：" + chat;
        out.put("chatScope", chatScope);
        out.put("retentionDays", retention);
        out.put("batchSize", batch);
        out.put("since", since);
        out.put("until", until);
        out.put("conditions", Arrays.asList(
                "消息分类成功：classify_status=SUCCESS，来源为 LLM/CACHE/MANUAL，分类为 INQ 或 OFFER",
                "消息创建时间：create_time >= " + since + "（扫描截至 " + until + "，最近 " + retention + " 天滚动窗口）",
                "AI 录入完成：entry_status=SUCCESS/SUCCESS_WARN，且 task_status=PENDING/FAILED",
                "特殊异常：群未映射/群映射冲突且 entry_status=SKIPPED，或任务生成处理超时",
                "排序后单次最多领取 " + batch + " 条（taskGenerateBatchSize）"
        ));
        if (!manual) {
            long scanSec = Math.max(5, config.integer("taskGenerateScanSec", 60));
            if (lastGenerateAt > 0 && System.currentTimeMillis() - lastGenerateAt < scanSec * 1000L) {
                out.put("message", "未到任务生成扫描间隔(taskGenerateScanSec=" + scanSec + "s)");
                return out;
            }
            lastGenerateAt = System.currentTimeMillis();
        }
        Map<String, Object> rawStats = messageStore.taskGenerateStats(since, StringUtils.trimToNull(chat), timeout);
        Map<String, Object> stats = normalizeGenerateStats(rawStats);
        out.put("statsAvailable", !rawStats.isEmpty());
        out.put("stats", stats);
        out.put("candidateTotal", stats.get("readyToGenerate"));
        List<WeChatMessage> rows = messageStore.queryTaskCandidates(since, batch, StringUtils.trimToNull(chat), timeout);
        Map<String, Boolean> autoApproveBySupplierCode = loadAutoApproveSettings();
        List<Long> autoApproveTaskIds = new ArrayList<>();
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
                GenerationResult result = generateMessage(message, autoApproveBySupplierCode, autoApproveTaskIds);
                if (result.handled > 0) {
                    generatedMessages++;
                }
                generatedTasks += result.created;
                String taskStatus;
                String failureReason = "";
                if (result.manual) {
                    manualMessages++;
                    taskStatus = "MANUAL_REVIEW";
                    failureReason = taskFailureReason(result.message);
                } else if (result.handled > 0) {
                    taskStatus = "GENERATED";
                } else {
                    skippedMessages++;
                    taskStatus = "SKIPPED";
                    if (!"SELF_OUTBOUND_ONLY".equals(result.message)) {
                        failureReason = taskFailureReason(result.message);
                    }
                }
                messageStore.updateTaskState(message.getUsername(), message.getLocalId(),
                        taskStatus, result.message, failureReason);
            } catch (Exception e) {
                failedMessages++;
                log.error("任务生成失败: chat={} localId={}", message.getName(), message.getLocalId(), e);
                String failureReason = clip("任务生成失败：" + StringUtils.defaultIfBlank(
                        e.getMessage(), e.getClass().getSimpleName()), 1000);
                messageStore.updateTaskState(message.getUsername(), message.getLocalId(), "FAILED",
                        failureReason, failureReason);
            }
        }
        out.put("scanned", rows.size());
        out.put("generatedMessages", generatedMessages);
        out.put("generatedTasks", generatedTasks);
        out.put("manualMessages", manualMessages);
        out.put("skippedMessages", skippedMessages);
        out.put("failedMessages", failedMessages);
        Map<String, Object> autoApproveResult = null;
        if (!autoApproveTaskIds.isEmpty()) {
            try {
                autoApproveResult = batchApprove(autoApproveTaskIds, "供应商自动审批");
                out.put("autoApprove", autoApproveResult);
            } catch (Exception e) {
                log.error("微信询报价任务自动审批失败: ids={}", autoApproveTaskIds, e);
                out.put("autoApproveError", clip(StringUtils.defaultIfBlank(
                        e.getMessage(), e.getClass().getSimpleName()), 1000));
            }
        }
        long entryPending = longValue(stats.get("taskEntryPending"));
        String summary = "任务生成完成：扫描前符合条件 " + stats.get("readyToGenerate") + " 条，本次扫描 "
                + rows.size() + " 条，生成 " + generatedTasks + " 条任务";
        if (autoApproveResult != null) {
            summary += "；自动审批 " + autoApproveResult.get("approved") + " 条";
        }
        if (entryPending > 0) {
            summary += "；另有 " + entryPending + " 条已分类 INQ/OFFER 仍在等待 AI 录入(entry_status=PENDING)，尚未进入任务生成";
        }
        out.put("message", summary);
        return out;
    }

    private Map<String, Object> normalizeGenerateStats(Map<String, Object> raw) {
        Map<String, Object> stats = new LinkedHashMap<>();
        String[] keys = {"classifiedTotal", "taskPending", "taskEntryPending", "taskEntryProcessing",
                "taskEntryFailed", "taskEntrySkipped", "entryReady", "taskGenerated", "taskManualReview",
                "taskSkipped", "taskFailed", "taskFailureRecorded", "readyToGenerate"};
        for (String key : keys) {
            stats.put(key, longValue(raw.get(key)));
        }
        return stats;
    }

    private long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(StringUtils.defaultString(value == null ? null : value.toString(), "0"));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private GenerationResult generateMessage(WeChatMessage message,
                                             Map<String, Boolean> autoApproveBySupplierCode,
                                             List<Long> autoApproveTaskIds) throws Exception {
        GenerationResult result = new GenerationResult();
        PartyMatch party = partyService.resolve(message.getName());
        if (party.isUnmappedOrAmbiguous()) {
            result.manual = true;
            result.message = party.getMessage();
            return result;
        }
        if (!party.isOk()) {
            result.manual = true;
            result.message = party.getStatus() + ": " + party.getMessage();
            return result;
        }
        // 未关联供应商仅用于归档、来源审计和告警，不自动生成对外回复/推送任务。
        if (partyService.isUnlinkedSupplier(party)) {
            result.manual = true;
            result.message = "UNLINKED_SUPPLIER";
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
            if (externalInquiry) {
                int taskResult;
                if (isCooperationAtLeastB(party.getSupplier())) {
                    Set<String> directions = partyService.directionTokens(offer.getProductType(), offer.getProductBrand());
                    if (directions.isEmpty()) {
                        addReason(manualReasons, "UNKNOWN_PRODUCT_TYPE");
                        continue;
                    }
                    List<CrmSupplier> suppliers = partyService.directionTargets(offer.getProductType(),
                            offer.getProductBrand(), "sales", party.getSupplier().getId());
                    List<WeChatTaskTarget> targets = targets(suppliers, "sales", message.getName());
                    if (targets.isEmpty()) {
                        skips.add(pn + ":NO_TARGET");
                        continue;
                    }
                    taskResult = createTask(message, offer, TASK_SUPPLIER_INQUIRY, targets, null, "供应商询价",
                            autoApproveBySupplierCode, autoApproveTaskIds);
                } else {
                    CrmOffer replyOffer = findSystemReplyOffer(offer);
                    List<WeChatTaskTarget> targets = Collections.singletonList(new WeChatTaskTarget(
                            message.getName(), party.getSupplier().getSupplierCode(), party.getSupplier().getSupplierName()));
                    String sendContent = replyOffer == null ? noStockLine(offer) : systemReplyLine(offer, replyOffer);
                    String remark = replyOffer == null ? "客户回复No stock" : "客户回复系统报价";
                    taskResult = createTask(message, offer, TASK_CUSTOMER_REPLY, targets,
                            offerSnapshot(message, offer), remark, sendContent,
                            replyOffer == null ? null : replyOffer.getPriceOffer(),
                            systemReplySnapshot(offer, replyOffer), autoApproveBySupplierCode,
                            autoApproveTaskIds);
                }
                if (taskResult == TASK_INSERTED) {
                    result.created++;
                }
                if (taskResult > 0) {
                    result.handled++;
                }
            } else if (externalOffer) {
                Set<String> directions = partyService.directionTokens(offer.getProductType(), offer.getProductBrand());
                if (directions.isEmpty()) {
                    addReason(manualReasons, "UNKNOWN_PRODUCT_TYPE");
                    continue;
                }
                if (!hasUsdOfferPrice(offer)) {
                    addReason(manualReasons, "NO_PRICE");
                    continue;
                }
                MatchResult match = matchCustomers(message, party.getSupplier(), offer);
                if (!match.targets.isEmpty()) {
                    int taskResult = createTask(message, offer, TASK_CUSTOMER_REPLY, match.targets, match.json,
                            "客户回复", autoApproveBySupplierCode, autoApproveTaskIds);
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
                        int taskResult = createTask(message, offer, TASK_CUSTOMER_PUSH, pushTargets, null,
                                "客户推送", autoApproveBySupplierCode, autoApproveTaskIds);
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

    private String taskFailureReason(String message) {
        return clip(StringUtils.defaultIfBlank(message, "任务未生成（未返回原因）"), 1000);
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

    private int createTask(WeChatMessage message, CrmOffer offer, String taskType,
                           List<WeChatTaskTarget> targets, String matchedJson, String remark,
                           Map<String, Boolean> autoApproveBySupplierCode,
                           List<Long> autoApproveTaskIds) throws Exception {
        return createTask(message, offer, taskType, targets, matchedJson, remark, null, null, null,
                autoApproveBySupplierCode, autoApproveTaskIds);
    }

    private int createTask(WeChatMessage message, CrmOffer offer, String taskType,
                           List<WeChatTaskTarget> targets, String matchedJson, String remark,
                           String sendContent, Double offerPrice, String offerJson,
                           Map<String, Boolean> autoApproveBySupplierCode,
                           List<Long> autoApproveTaskIds) throws Exception {
        if (targets == null || targets.isEmpty()) {
            return TASK_NOT_CREATED;
        }
        String pnOriginal = StringUtils.trimToEmpty(offer.getProductCode());
        String pnNormalized = PnUtils.normalize(pnOriginal);
        if (pnNormalized.isEmpty()) {
            return TASK_NOT_CREATED;
        }
        WeChatOfferTask task = baseTask(message);
        task.setTaskType(taskType);
        task.setDeliveryMode(deliveryModeForTaskType(taskType));
        task.setPnOriginal(pnOriginal);
        task.setPnNormalized(pnNormalized);
        task.setBrand(offer.getProductBrand());
        task.setProductType(offer.getProductType());
        task.setSpec(offer.getSpec());
        task.setQty(offer.getQuantity());
        task.setOriPrice(offer.getPriceCost());
        task.setOfferPrice(offerPrice == null ? offer.getPriceOffer() : offerPrice);
        task.setSendTarget(jackson.writeValueAsString(targets));
        task.setSendTargetSuccess("[]");
        task.setSendContent(StringUtils.isNotBlank(sendContent)
                ? sendContent : columnLine(offer, !TASK_SUPPLIER_INQUIRY.equals(taskType)));
        task.setMatchedInqJson(matchedJson);
        task.setOfferJson(StringUtils.isNotBlank(offerJson) ? offerJson : offerSnapshot(message, offer));
        task.setRemark(remark);
        if (taskMapper.insertTaskIgnore(task) > 0) {
            if (task.getId() != null && allTargetsAutoApprove(targets, autoApproveBySupplierCode)) {
                autoApproveTaskIds.add(task.getId());
            }
            return TASK_INSERTED;
        }
        return taskMapper.selectByUnique(task.getSourceUsername(), task.getSourceLocalId(),
                task.getTaskType(), task.getPnNormalized(), task.getSendType()) != null
                ? TASK_EXISTS : TASK_NOT_CREATED;
    }

    /** 对外批量发送走微信转发，客户回复保持逐目标直发。 */
    private String deliveryModeForTaskType(String taskType) {
        if (TASK_SUPPLIER_INQUIRY.equals(taskType)
                || TASK_CUSTOMER_PUSH.equals(taskType)
                || TASK_MANUAL_SEND.equals(taskType)) {
            return DELIVERY_FORWARD;
        }
        return DELIVERY_DIRECT;
    }

    /**
     * 自动审批是任务级动作：仅当所有发送目标对应的供应商都开启自动审批时才整条自动审批，
     * 任一目标未开启则保持待人工审批，避免把未确认的发送对象一并放行。
     */
    private boolean allTargetsAutoApprove(List<WeChatTaskTarget> targets,
                                          Map<String, Boolean> autoApproveBySupplierCode) {
        if (targets == null || targets.isEmpty()
                || autoApproveBySupplierCode == null || autoApproveBySupplierCode.isEmpty()) {
            return false;
        }
        for (WeChatTaskTarget target : targets) {
            String code = target == null ? "" : StringUtils.trimToEmpty(target.getPartyCode());
            if (code.isEmpty() || !Boolean.TRUE.equals(autoApproveBySupplierCode.get(code.toUpperCase(Locale.ROOT)))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Boolean> loadAutoApproveSettings() {
        Map<String, Boolean> settings = new HashMap<>();
        try {
            for (CrmSupplier supplier : supplierMapper.selectAllForWeChat()) {
                if (supplier != null && StringUtils.isNotBlank(supplier.getSupplierCode())) {
                    settings.put(StringUtils.upperCase(supplier.getSupplierCode()),
                            supplier.getAutoApprove() != null && supplier.getAutoApprove() == 1);
                }
            }
        } catch (Exception e) {
            log.warn("读取供应商自动审批配置失败，本次全部按人工审批处理: {}", e.getMessage());
        }
        return settings;
    }

    private WeChatOfferTask baseTask(WeChatMessage message) {
        WeChatOfferTask task = new WeChatOfferTask();
        task.setSourceUsername(message.getUsername());
        task.setSourceLocalId(message.getLocalId());
        task.setSourceServerId(message.getServerId());
        task.setSourceMessageTime(parseDate(message.getCreateTime()));
        task.setSourceCategory(StringUtils.upperCase(StringUtils.defaultString(message.getMsgCategory())));
        task.setTaskSource(TASK_SOURCE_AUTO);
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
        String since = matchSince();
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

    /** 合作等级包含 S/A/B 时按高等级客户处理；为空或 C/D/E 时直接使用系统Offer回复。 */
    private boolean isCooperationAtLeastB(CrmSupplier supplier) {
        if (supplier == null || StringUtils.isBlank(supplier.getCooperationLevel())) {
            return false;
        }
        for (String level : supplier.getCooperationLevel().split("[,，/;；\\s]+")) {
            String value = StringUtils.upperCase(StringUtils.trimToEmpty(level));
            if ("S".equals(value) || "A".equals(value) || "B".equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 系统Offer回退：窗口默认2个自然日（昨天00:00至今），由 inqMatchDayNo 配置。 */
    private CrmOffer findSystemReplyOffer(CrmOffer inquiry) {
        List<CrmOffer> offers = offerMapper.selectReplyOffers(PnUtils.normalize(inquiry.getProductCode()),
                matchSince(), PnUtils.escapeLikePrefix(inquiry.getProductCode()));
        if (offers == null) {
            return null;
        }
        for (CrmOffer offer : offers) {
            if (hasUsdOfferPrice(offer) && PnUtils.prefixMatch(inquiry.getProductCode(), offer.getProductCode())) {
                return offer;
            }
        }
        return null;
    }

    private String matchSince() {
        int days = config.matchDayNo();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, -(days - 1));
        return fmtTime(calendar.getTimeInMillis());
    }

    private String systemReplyLine(CrmOffer inquiry, CrmOffer replyOffer) {
        OfferTextLine line = new OfferTextLine();
        line.setPartNumber(inquiry.getProductCode());
        line.setQuantity(inquiry.getQuantity() != null ? inquiry.getQuantity() : replyOffer.getQuantity());
        line.setPrice(replyOffer.getPriceOffer());
        return OfferTextFormatter.formatRow(line, true, Collections.<String>emptyList());
    }

    private String noStockLine(CrmOffer inquiry) {
        String quantity = inquiry.getQuantity() == null ? "" : inquiry.getQuantity() + "pcs";
        return StringUtils.defaultString(inquiry.getProductCode()) + "\t" + quantity + "\tNo stock";
    }

    private String systemReplySnapshot(CrmOffer inquiry, CrmOffer replyOffer) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("inquiryOfferId", inquiry.getId());
            snapshot.put("pn", inquiry.getProductCode());
            snapshot.put("quantity", inquiry.getQuantity());
            snapshot.put("noStock", replyOffer == null);
            if (replyOffer != null) {
                snapshot.put("replyOfferId", replyOffer.getId());
                snapshot.put("supplierCode", replyOffer.getSupplierCode());
                snapshot.put("replyPn", replyOffer.getProductCode());
                snapshot.put("priceOffer", replyOffer.getPriceOffer());
                snapshot.put("priceUnit", replyOffer.getPriceUnit());
                snapshot.put("availableQuantity", replyOffer.getQuantity());
                snapshot.put("stockDate", replyOffer.getStockDate());
                snapshot.put("deliveryTime", replyOffer.getDeliveryTime());
            }
            return jackson.writeValueAsString(snapshot);
        } catch (Exception e) {
            return null;
        }
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
        String status = requireValidStatus(form.getHandleStatus());
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
        validateTargets(parseTargets(form.getSendTarget()));
        if (form.getOfferPrice() == null) {
            form.setOfferPrice(existing.getOfferPrice());
        } else if (!TASK_MANUAL_SEND.equals(existing.getTaskType())
                && !TASK_SUPPLIER_INQUIRY.equals(existing.getTaskType())
                && !java.util.Objects.equals(form.getOfferPrice(), existing.getOfferPrice())
                && (form.getSendContent() == null || StringUtils.equals(form.getSendContent(),
                        StringUtils.defaultString(existing.getSendContent())))) {
            form.setSendContent(approvedLine(existing, form.getOfferPrice()));
        }
        if (form.getSendContent() == null) {
            form.setSendContent(existing.getSendContent());
        }
        String operator = currentOperator();
        form.setHandleStatus(status);
        form.setApprovalReason(clip(form.getApprovalReason(), 500));
        form.setApprovedBy(operator);
        form.setApprovedAt(new Date());
        form.setUpdateBy(operator);
        return taskMapper.updateApproval(form);
    }

    /** 定时任务无登录上下文时使用系统操作人；人工审批仍记录当前登录用户。 */
    private String currentOperator() {
        try {
            String username = SecurityUtils.getUsername();
            return StringUtils.defaultIfBlank(username, "wechat_auto");
        } catch (Exception e) {
            return "wechat_auto";
        }
    }

    /** 兼容供应商自动审批：未指定目标状态时默认审批为已审批待发送。 */
    public Map<String, Object> batchApprove(List<Long> ids, String approvalReason) {
        return batchApprove(ids, STATUS_APPROVED, approvalReason);
    }

    /** 列表多选审批：将所选任务统一更新为指定状态；逐条保留原发送目标和发送内容。 */
    public Map<String, Object> batchApprove(List<Long> ids, String handleStatus, String approvalReason) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("请选择要审批的任务");
        }
        String targetStatus = StringUtils.isBlank(handleStatus)
                ? STATUS_APPROVED : requireValidStatus(handleStatus);
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int updated = 0;
        int failed = 0;
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        for (Long id : uniqueIds) {
            if (id == null) {
                continue;
            }
            WeChatOfferTask existing = taskMapper.selectTaskById(id);
            if (existing == null) {
                failed++;
                errors.add("任务 #" + id + " 不存在");
                continue;
            }
            try {
                WeChatOfferTask form = new WeChatOfferTask();
                form.setId(id);
                form.setHandleStatus(targetStatus);
                form.setApprovalReason(approvalReason);
                if (approve(form) > 0) {
                    updated++;
                } else {
                    failed++;
                    errors.add("任务 #" + id + " 审批保存失败");
                }
            } catch (Exception e) {
                failed++;
                errors.add("任务 #" + id + "：" + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
            }
        }
        out.put("approved", updated);
        out.put("updated", updated);
        out.put("skipped", 0);
        out.put("failed", failed);
        out.put("errors", errors);
        out.put("targetStatus", targetStatus);
        out.put("message", "批量审批完成：成功 " + updated + " 条，失败 " + failed + " 条");
        return out;
    }

    private String requireValidStatus(String status) {
        String normalized = StringUtils.trimToEmpty(status);
        if (!STATUS_PENDING_APPROVAL.equals(normalized) && !STATUS_APPROVED.equals(normalized)
                && !STATUS_SENT.equals(normalized) && !STATUS_NO_ACTION.equals(normalized)) {
            throw new ServiceException("任务状态不合法");
        }
        return normalized;
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

    /** 人工删除任务记录；不撤回已发送消息，也不回退来源消息的录入/任务状态。 */
    public int deleteTask(Long id) {
        if (id == null) {
            throw new ServiceException("任务ID不能为空");
        }
        WeChatOfferTask task = taskMapper.selectTaskById(id);
        if (task == null) {
            throw new ServiceException("任务不存在：" + id);
        }
        return taskMapper.deleteTaskById(id);
    }

    /** 批量删除任务；revertSource=true 时仅回退来源消息的任务生成状态，便于重新生成。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchRemove(List<Long> ids, boolean revertSource) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("请选择要删除的任务");
        }
        Set<String> sourceKeys = new LinkedHashSet<>();
        int deleted = 0;
        int missing = 0;
        for (Long id : new LinkedHashSet<>(ids)) {
            if (id == null) {
                continue;
            }
            WeChatOfferTask task = taskMapper.selectTaskById(id);
            if (task == null) {
                missing++;
                continue;
            }
            if (task.getSourceLocalId() != null && StringUtils.isNotBlank(task.getSourceUsername())) {
                sourceKeys.add(task.getSourceUsername() + ":" + task.getSourceLocalId());
            }
            deleted += taskMapper.deleteTaskById(id);
        }
        int reverted = revertSource ? messageStore.resetTaskState(new ArrayList<>(sourceKeys)) : 0;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", deleted);
        out.put("missing", missing);
        out.put("reverted", reverted);
        out.put("revertSource", revertSource);
        String message = "批量删除完成：删除 " + deleted + " 条任务";
        if (revertSource) {
            message += "，已回退 " + reverted + " 条来源消息到 PENDING，可重新生成任务";
        }
        if (missing > 0) {
            message += "，另有 " + missing + " 条任务已不存在";
        }
        out.put("message", message);
        return out;
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
            long scanSec = Math.max(30, config.integer("taskSendScanSec", 600));
            if (lastSendAt > 0 && System.currentTimeMillis() - lastSendAt < scanSec * 1000L) {
                out.put("message", "未到发送扫描间隔(taskSendScanSec=" + scanSec + "s)");
                return out;
            }
            lastSendAt = System.currentTimeMillis();
        }
        int maxAttempts = Math.max(1, config.integer("taskSendMaxAttempts", 3));
        int scanMax = Math.max(1, Math.min(500, config.integer("taskSendScanBatchSize", 50)));
        List<WeChatOfferTask> candidates = taskMapper.selectSendCandidates(maxAttempts, scanMax);
        List<WeChatOfferTask> directPending = new ArrayList<>();
        List<WeChatOfferTask> forwardPending = new ArrayList<>();
        for (WeChatOfferTask task : candidates) {
            fillTargets(task);
            if (hasUnsentTarget(task)) {
                if (DELIVERY_FORWARD.equals(deliveryModeOf(task))) {
                    forwardPending.add(task);
                } else {
                    directPending.add(task);
                }
            }
        }
        if (directPending.isEmpty() && forwardPending.isEmpty()) {
            out.put("scannedTasks", candidates.size());
            out.put("sendGroups", 0);
            out.put("sentGroups", 0);
            out.put("failedGroups", 0);
            out.put("message", "没有待发送任务");
            return out;
        }
        WeChatSessionStatus session = gateway.sessionStatus();
        if (!session.isUnlocked()) {
            out.put("scannedTasks", candidates.size());
            out.put("sendGroups", 0);
            out.put("sentGroups", 0);
            out.put("failedGroups", 0);
            out.put("paused", true);
            out.put("message", "Windows 已锁屏或交互桌面不可用，发送已暂停：" + session.getReason()
                    + "。本轮未点击微信、未消耗重试次数，解锁后自动继续");
            return out;
        }
        String expectedAccount = config.str("autoSendAccount", "Jin");
        String currentAccount = gateway.currentSendAccount();
        if (!expectedAccount.equalsIgnoreCase(currentAccount)) {
            String message = StringUtils.isBlank(currentAccount)
                    ? "无法读取微信当前登录账号，已暂停自动发送（未消耗重试次数）"
                    : "微信当前登录账号是 " + currentAccount + "，不是 " + expectedAccount + "，已停止自动发送";
            log.warn(message);
            throw new ServiceException(message);
        }
        Map<String, Object> forwardStats = new LinkedHashMap<>();
        if (!forwardPending.isEmpty()) {
            forwardStats = sendForwardRound(forwardPending);
            if (Boolean.TRUE.equals(forwardStats.get("paused"))) {
                out.put("scannedTasks", candidates.size());
                out.putAll(forwardStats);
                return out;
            }
        }
        if (directPending.isEmpty()) {
            out.put("scannedTasks", candidates.size());
            out.putAll(forwardStats);
            out.put("sendGroups", 0);
            out.put("sentGroups", 0);
            out.put("failedGroups", 0);
            if (!out.containsKey("message")) {
                out.put("message", "没有待发送任务");
            }
            return out;
        }
        List<SendGroup> groups = buildGroups(directPending);
        int totalGroups = groups.size();
        int roundMax = Math.max(1, Math.min(50, config.integer("taskSendRoundMax", 3)));
        if (groups.size() > roundMax) {
            groups = new ArrayList<>(groups.subList(0, roundMax));
        }
        int chunkSize = Math.max(1, Math.min(20, config.integer("taskSendBatchSize", 3)));
        int sentGroups = 0;
        int failedGroups = 0;
        int timedOutGroups = 0;
        int deferredGroups = 0;
        for (int start = 0; start < groups.size(); start += chunkSize) {
            List<SendGroup> batch = groups.subList(start, Math.min(start + chunkSize, groups.size()));
            Set<Long> attemptTaskIds = new LinkedHashSet<>();
            Set<Long> attemptedTaskIds = new LinkedHashSet<>();
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
            } catch (WeChatGatewayService.BatchSendAbortedException e) {
                log.warn("网关未产出任何发送结果，按普通失败处理并继续重试: {}", e.getMessage());
                for (SendGroup group : batch) {
                    applyRetryableFailure(group, e.getMessage());
                }
                releasePausedAttempts(attemptTaskIds, new LinkedHashSet<>());
                failedGroups += batch.size();
                out.put("scannedTasks", candidates.size());
                out.put("sendGroups", groups.size());
                out.put("sentGroups", sentGroups);
                out.put("failedGroups", failedGroups);
                out.put("message", "网关未产出结果（微信窗口不可用），已按普通失败处理，下一轮继续重试: "
                        + e.getMessage());
                return out;
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
            boolean sessionPaused = false;
            int pausedGroups = 0;
            String pauseReason = "";
            int timeoutIndex = -1;
            for (WeChatSendResult result : results) {
                if (result != null && "SEND_TIMEOUT".equalsIgnoreCase(result.getStatus())) {
                    timeoutIndex = result.getIndex();
                    break;
                }
            }
            for (int i = 0; i < batch.size(); i++) {
                SendGroup group = batch.get(i);
                WeChatSendResult result = byIndex.get(i);
                boolean deferredByTimeout = timeoutIndex >= 0 && i > timeoutIndex;
                if (result == null) {
                    if (deferredByTimeout) {
                        deferredGroups++;
                    } else {
                        missingResult = true;
                        applyFailure(group, "GATEWAY_RESULT_UNKNOWN: 网关未返回该目标结果");
                        for (WeChatOfferTask task : group.tasks) {
                            unknownTaskIds.add(task.getId());
                            attemptedTaskIds.add(task.getId());
                        }
                        failedGroups++;
                    }
                } else if (result.isOk()) {
                    applySuccess(group);
                    for (WeChatOfferTask task : group.tasks) {
                        attemptedTaskIds.add(task.getId());
                    }
                    sentGroups++;
                    if (StringUtils.isNotBlank(group.target.getPartyCode())) {
                        followCodes.add(group.target.getPartyCode());
                    }
                } else if ("SESSION_LOCKED".equalsIgnoreCase(result.getStatus())) {
                    sessionPaused = true;
                    pausedGroups++;
                    pauseReason = StringUtils.defaultIfBlank(result.getMessage(), "Windows 交互桌面不可用");
                } else if ("SEND_TIMEOUT".equalsIgnoreCase(result.getStatus())) {
                    applyFailure(group, "GATEWAY_SEND_TIMEOUT: 单条发送超过网关超时阈值，进程已强制终止");
                    for (WeChatOfferTask task : group.tasks) {
                        attemptedTaskIds.add(task.getId());
                    }
                    failedGroups++;
                    timedOutGroups++;
                } else if ("SEND_ABORTED".equalsIgnoreCase(result.getStatus()) || deferredByTimeout) {
                    deferredGroups++;
                } else {
                    applyFailure(group, StringUtils.defaultIfBlank(result.getMessage(), result.getStatus()));
                    for (WeChatOfferTask task : group.tasks) {
                        attemptedTaskIds.add(task.getId());
                    }
                    failedGroups++;
                }
            }
            updateFollowUp(followCodes);
            int releasedAttempts = releasePausedAttempts(attemptTaskIds, attemptedTaskIds);
            if (timedOutGroups > 0) {
                out.put("scannedTasks", candidates.size());
                out.put("sendGroups", groups.size());
                out.put("sentGroups", sentGroups);
                out.put("failedGroups", failedGroups);
                out.put("timedOutGroups", timedOutGroups);
                out.put("deferredGroups", deferredGroups);
                out.put("releasedAttempts", releasedAttempts);
                out.put("paused", true);
                out.put("message", "微信发送超时，已强制终止网关并停止本轮自动发送；超时目标结果未知，已暂停自动重试，请人工核实；后续未执行目标已释放重试次数，下一轮继续");
                return out;
            }
            if (sessionPaused) {
                out.put("scannedTasks", candidates.size());
                out.put("sendGroups", groups.size());
                out.put("sentGroups", sentGroups);
                out.put("failedGroups", failedGroups);
                out.put("pausedGroups", pausedGroups);
                out.put("paused", true);
                out.put("releasedAttempts", releasedAttempts);
                out.put("message", "Windows 已锁屏或交互桌面不可用，发送已暂停：" + pauseReason
                        + "。未发送的任务未消耗重试次数，解锁后自动继续");
                return out;
            }
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
        out.put("pendingGroups", Math.max(0, totalGroups - groups.size()));
        out.put("sentGroups", sentGroups);
        out.put("failedGroups", failedGroups);
        String directMessage = "发送扫描完成：本轮目标组 " + groups.size() + "/" + totalGroups
                + "，成功 " + sentGroups + "，失败 " + failedGroups
                + (totalGroups > groups.size() ? "，其余目标顺延下一轮" : "");
        for (String key : Arrays.asList("forwardGroups", "forwardTotalGroups", "forwardSentTargets",
                "forwardCompletedGroups", "forwardFailedGroups")) {
            if (forwardStats.containsKey(key)) {
                out.put(key, forwardStats.get(key));
            }
        }
        if (forwardStats.containsKey("message")) {
            directMessage += "；" + forwardStats.get("message");
        }
        out.put("message", directMessage);
        return out;
    }

    /**
     * 转发模式：相同任务类型 + 相同正文合并为一组。先发到文件传输助手，
     * 再按 forwardChunkSize（微信上限 9）逐个批次点击“分别发送”。
     * 每批成功即写 send_target_success，下一批失败或进程中断不会重发已成功目标。
     */
    private Map<String, Object> sendForwardRound(List<WeChatOfferTask> tasks) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<ForwardGroup> allGroups = buildForwardGroups(tasks);
        int totalGroups = allGroups.size();
        int roundMax = Math.max(1, Math.min(50, config.integer("taskSendRoundMax", 3)));
        List<ForwardGroup> groups = allGroups.size() > roundMax
                ? new ArrayList<>(allGroups.subList(0, roundMax)) : allGroups;
        String sourceChat = gateway.forwardSourceChat();
        int chunk = gateway.forwardChunkSize();
        int sentTargets = 0;
        int completedGroups = 0;
        int failedGroups = 0;
        boolean paused = false;
        String pauseMessage = "";
        outer:
        for (ForwardGroup group : groups) {
            if (StringUtils.isBlank(group.text)) {
                for (ForwardTarget target : group.targets.values()) {
                    for (Long taskId : target.targetByTask.keySet()) {
                        taskMapper.markUnknown(taskId, "GATEWAY_RESULT_UNKNOWN: 转发内容为空，未发送");
                    }
                }
                failedGroups++;
                continue;
            }
            Set<Long> attemptTaskIds = new LinkedHashSet<>();
            for (WeChatOfferTask task : group.tasks) {
                attemptTaskIds.add(task.getId());
                taskMapper.markSendAttempt(task.getId(), new Date());
            }
            int groupSent = 0;
            String groupError = null;
            boolean groupUnknown = false;
            boolean sessionPaused = false;
            while (!group.targets.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String name : group.targets.keySet()) {
                    names.add(name);
                    if (names.size() >= chunk) {
                        break;
                    }
                }
                if (names.isEmpty()) {
                    break;
                }
                WeChatForwardResult result;
                try {
                    result = gateway.forwardSend(sourceChat, group.text, names);
                } catch (Exception e) {
                    groupUnknown = true;
                    groupError = "GATEWAY_RESULT_UNKNOWN: " + e.getMessage();
                    break;
                }
                if (result == null) {
                    groupUnknown = true;
                    groupError = "GATEWAY_RESULT_UNKNOWN: 网关未返回转发结果";
                    break;
                }
                if ("SESSION_LOCKED".equalsIgnoreCase(result.getStatus())) {
                    sessionPaused = true;
                    pauseMessage = StringUtils.defaultIfBlank(result.getMessage(),
                            "Windows 已锁屏或交互桌面不可用");
                    break;
                }
                int applied = applyForwardSuccess(group, result.getSent());
                groupSent += applied;
                sentTargets += applied;
                if (result.isOk()) {
                    if (applied == 0) {
                        groupError = "网关返回成功但未返回任何已发送目标";
                        break;
                    }
                    continue;
                }
                if (group.targets.isEmpty()) {
                    break;
                }
                String message = StringUtils.defaultIfBlank(result.getMessage(), result.getStatus());
                if (result.getSkipped() != null && !result.getSkipped().isEmpty()) {
                    StringBuilder skipped = new StringBuilder();
                    for (WeChatForwardSkip item : result.getSkipped()) {
                        if (item == null || StringUtils.isBlank(item.getName())) {
                            continue;
                        }
                        if (skipped.length() > 0) {
                            skipped.append("；");
                        }
                        skipped.append(item.getName());
                        if (StringUtils.isNotBlank(item.getReason())) {
                            skipped.append("(").append(item.getReason()).append(")");
                        }
                    }
                    if (skipped.length() > 0) {
                        message = message + "；未发送目标: " + skipped;
                    }
                }
                if (isForwardRetryable(result.getStatus())) {
                    groupError = message;
                    break;
                }
                groupUnknown = true;
                groupError = "GATEWAY_RESULT_UNKNOWN: " + message;
                break;
            }
            if (sessionPaused) {
                for (Long taskId : attemptTaskIds) {
                    taskMapper.releaseSendAttempt(taskId);
                }
                paused = true;
                pauseMessage += "。本轮转发已暂停，未发送目标解锁后继续";
                break outer;
            }
            if (groupUnknown) {
                Set<Long> unknownTaskIds = new LinkedHashSet<>();
                for (ForwardTarget target : group.targets.values()) {
                    unknownTaskIds.addAll(target.targetByTask.keySet());
                }
                markUnknown(unknownTaskIds, clip(groupError
                        + "；为避免重复发送已停止自动重试，请核实后确认未发送", 1000));
                failedGroups++;
                paused = true;
                pauseMessage = groupError + "。后续转发已暂停，请人工核实";
                break outer;
            }
            if (groupError != null) {
                for (ForwardTarget target : group.targets.values()) {
                    for (WeChatOfferTask task : target.tasks) {
                        taskMapper.updateSendResult(task.getId(), task.getSendTargetSuccess(),
                                STATUS_APPROVED, clip(groupError, 1000), null);
                    }
                }
                failedGroups++;
            }
            if (groupSent > 0) {
                for (WeChatOfferTask task : group.tasks) {
                    if (hasUnsentTarget(task)) {
                        taskMapper.releaseSendAttempt(task.getId());
                    }
                }
            }
            if (group.targets.isEmpty()) {
                completedGroups++;
            }
        }
        out.put("forwardGroups", groups.size());
        out.put("forwardTotalGroups", totalGroups);
        out.put("forwardSentTargets", sentTargets);
        out.put("forwardCompletedGroups", completedGroups);
        out.put("forwardFailedGroups", failedGroups);
        out.put("message", "转发模式：本组 " + groups.size() + "/" + totalGroups
                + "，成功目标 " + sentTargets + "，完成组 " + completedGroups
                + (paused ? "，已暂停" : "")
                + (totalGroups > groups.size() ? "，其余顺延下一轮" : ""));
        if (paused) {
            out.put("paused", true);
            out.put("message", pauseMessage);
        }
        return out;
    }

    private List<ForwardGroup> buildForwardGroups(List<WeChatOfferTask> tasks) {
        Map<String, ForwardGroup> groups = new LinkedHashMap<>();
        for (WeChatOfferTask task : tasks) {
            if (!DELIVERY_FORWARD.equals(deliveryModeOf(task))) {
                continue;
            }
            String text = StringUtils.defaultIfBlank(task.getSendContent(), task.getOriMsg()).trim();
            String key = StringUtils.defaultString(task.getTaskType()) + "\u0000" + text;
            ForwardGroup group = groups.computeIfAbsent(key, ignored -> {
                ForwardGroup value = new ForwardGroup();
                value.taskType = task.getTaskType();
                value.text = text;
                return value;
            });
            group.tasks.add(task);
            Set<String> success = new HashSet<>();
            for (WeChatTaskTarget target : task.getSuccessTargets()) {
                success.add(target.getName());
            }
            for (WeChatTaskTarget target : task.getTargets()) {
                if (target == null || StringUtils.isBlank(target.getName())
                        || success.contains(target.getName())) {
                    continue;
                }
                ForwardTarget item = group.targets.computeIfAbsent(target.getName(), ignored -> {
                    ForwardTarget value = new ForwardTarget();
                    value.target = target;
                    return value;
                });
                item.tasks.add(task);
                item.targetByTask.put(task.getId(), target);
            }
        }
        List<ForwardGroup> result = new ArrayList<>();
        for (ForwardGroup group : groups.values()) {
            if (!group.targets.isEmpty()) {
                result.add(group);
            }
        }
        return result;
    }

    private int applyForwardSuccess(ForwardGroup group, List<String> sent) {
        if (sent == null || sent.isEmpty()) {
            return 0;
        }
        int applied = 0;
        for (String name : sent) {
            ForwardTarget target = group.targets.remove(name);
            if (target == null) {
                continue;
            }
            for (WeChatOfferTask task : target.tasks) {
                SendGroup single = new SendGroup();
                single.taskType = group.taskType;
                single.target = target.targetByTask.get(task.getId());
                single.tasks.add(task);
                applySuccess(single);
            }
            applied++;
        }
        return applied;
    }

    private boolean isForwardRetryable(String status) {
        return "SEED_FAILED".equalsIgnoreCase(status)
                || "NO_PICKER".equalsIgnoreCase(status)
                || "COUNT_MISMATCH".equalsIgnoreCase(status)
                || "BAD_TARGETS".equalsIgnoreCase(status)
                || "PARTIAL".equalsIgnoreCase(status)
                || "UNAVAILABLE".equalsIgnoreCase(status);
    }

    private String deliveryModeOf(WeChatOfferTask task) {
        return StringUtils.defaultIfBlank(task.getDeliveryMode(), DELIVERY_DIRECT).toUpperCase(Locale.ROOT);
    }

    private static final class ForwardGroup {
        private String taskType;
        private String text;
        private final List<WeChatOfferTask> tasks = new ArrayList<>();
        private final Map<String, ForwardTarget> targets = new LinkedHashMap<>();
    }

    private static final class ForwardTarget {
        private WeChatTaskTarget target;
        private final List<WeChatOfferTask> tasks = new ArrayList<>();
        private final Map<Long, WeChatTaskTarget> targetByTask = new LinkedHashMap<>();
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
            if (TASK_MANUAL_SEND.equals(group.taskType)) {
                LinkedHashSet<String> contents = new LinkedHashSet<>();
                group.tasks.sort(Comparator.comparing(WeChatOfferTask::getId));
                for (WeChatOfferTask task : group.tasks) {
                    String content = StringUtils.defaultIfBlank(task.getSendContent(), task.getOriMsg());
                    if (StringUtils.isNotBlank(content)) {
                        contents.add(content);
                    }
                }
                group.text = String.join("\n", contents);
                continue;
            }
            boolean includePrice = !TASK_SUPPLIER_INQUIRY.equals(group.taskType);
            Map<String, OfferTextLine> rows = new LinkedHashMap<>();
            LinkedHashSet<String> noStockLines = new LinkedHashSet<>();
            boolean onlyNoStock = true;
            group.tasks.sort(Comparator.comparing(WeChatOfferTask::getId));
            for (WeChatOfferTask task : group.tasks) {
                String line = StringUtils.defaultIfBlank(task.getSendContent(), task.getPnOriginal());
                if (StringUtils.isNotBlank(line)) {
                    if (OfferNoStockUtils.isNoStockResponse(line)) {
                        noStockLines.add(line);
                    } else {
                        onlyNoStock = false;
                    }
                    OfferTextLine row = new OfferTextLine();
                    row.setPartNumber(task.getPnOriginal());
                    row.setQuantity(task.getQty());
                    row.setBrand(task.getBrand());
                    row.setPrice(task.getOfferPrice());
                    row.setRawLine(line);
                    rows.putIfAbsent(StringUtils.defaultString(task.getPnNormalized()) + "\u0000" + line, row);
                } else {
                    onlyNoStock = false;
                }
            }
            if (onlyNoStock && !noStockLines.isEmpty()) {
                group.text = String.join("\n", noStockLines);
            } else {
                group.text = OfferTextFormatter.formatSendText(new ArrayList<>(rows.values()), includePrice,
                        Collections.<String>emptyList());
            }
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
        String message = StringUtils.defaultIfBlank(error, "网关返回发送失败");
        // 工控机操作微信时，网关可能在消息已经发出后因回读确认失败而返回非成功。
        // 此时若继续自动重试，会造成同一条消息重复发送；统一标记为结果未知，交由人工确认。
        String value = message.startsWith("GATEWAY_RESULT_UNKNOWN")
                ? message
                : "GATEWAY_RESULT_UNKNOWN: " + message;
        value = clip(value + "；为避免重复发送已停止自动重试，请核实后确认未发送", 1000);
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

    /**
     * 网关未产出任何发送结果就异常退出（如微信主窗口不存在）：本轮确定一条都没发出去，
     * 按普通失败记录并释放本次尝试次数，让队列下一轮继续重试。
     */
    private void applyRetryableFailure(SendGroup group, String error) {
        String message = clip("GATEWAY_SEND_ABORTED: "
                + StringUtils.defaultIfBlank(error, "网关未产出任何发送结果"), 1000);
        for (WeChatOfferTask task : group.tasks) {
            taskMapper.updateSendResult(task.getId(), task.getSendTargetSuccess(),
                    STATUS_APPROVED, message, null);
        }
    }

    /** 锁屏导致没有真正执行发送时，回滚本批预增的尝试次数。 */
    private int releasePausedAttempts(Set<Long> attemptTaskIds, Set<Long> attemptedTaskIds) {
        int released = 0;
        for (Long taskId : attemptTaskIds) {
            if (!attemptedTaskIds.contains(taskId)) {
                taskMapper.releaseSendAttempt(taskId);
                released++;
            }
        }
        return released;
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
