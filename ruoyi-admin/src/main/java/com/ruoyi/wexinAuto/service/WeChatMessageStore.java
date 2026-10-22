package com.ruoyi.wexinAuto.service;

import com.ruoyi.wexinAuto.mapper.WeChatMessageMapper;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 微信消息持久化：所有消息(监听+主动拉取)统一入库，
 * 保留最近 {@code msgRetentionDays} 天，过期由定时任务清理。
 */
@Slf4j
@Service
public class WeChatMessageStore {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WeChatMessageMapper mapper;

    public WeChatMessageStore(WeChatMessageMapper mapper) {
        this.mapper = mapper;
    }

    /** 单批最大行数：避免单条 INSERT 过长(大轮拉取时 5000 行会触发慢 SQL/超包限制) */
    private static final int UPSERT_BATCH = 500;

    /** 批量入库(幂等：username+local_id 重复时只更新非分类字段)，内部自动分批并对失败批次做二分重试。 */
    public int saveAll(List<WeChatMessage> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        normalizeServerIds(list);
        int total = list.size();
        int ok = 0;
        for (int i = 0; i < total; i += UPSERT_BATCH) {
            List<WeChatMessage> part = list.subList(i, Math.min(i + UPSERT_BATCH, total));
            ok += upsertPart(new ArrayList<>(part), i);
        }
        log.info("微信消息入库 {} 条(写入/更新 {})", total, ok);
        return ok;
    }

    /** 黑名单消息入库(只落库、不做分类/处理；重复按唯一键跳过)，失败批次自动拆分重试。 */
    public int saveBlackAll(List<WeChatMessage> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        normalizeServerIds(list);
        int total = list.size();
        int ok = 0;
        for (int i = 0; i < total; i += 500) {
            List<WeChatMessage> part = list.subList(i, Math.min(i + 500, total));
            ok += insertBlackPart(new ArrayList<>(part), i);
        }
        log.info("黑名单消息入库 {} 条(写入/更新 {})", total, ok);
        return ok;
    }

    /** 一条异常不能拖垮整批：先二分拆分，最终定位到具体坏数据并继续写其余消息。 */
    private int upsertPart(List<WeChatMessage> part, int offset) {
        if (part.isEmpty()) {
            return 0;
        }
        try {
            return mapper.batchUpsert(part);
        } catch (Exception e) {
            if (part.size() == 1) {
                WeChatMessage m = part.get(0);
                log.error("微信消息单条入库失败 offset={} username={} localId={} serverIdLength={}",
                        offset, m.getUsername(), m.getLocalId(),
                        m.getServerId() == null ? 0 : m.getServerId().length(), e);
                return 0;
            }
            int mid = part.size() / 2;
            int ok = upsertPart(new ArrayList<>(part.subList(0, mid)), offset);
            ok += upsertPart(new ArrayList<>(part.subList(mid, part.size())), offset + mid);
            return ok;
        }
    }

    private int insertBlackPart(List<WeChatMessage> part, int offset) {
        if (part.isEmpty()) {
            return 0;
        }
        try {
            return mapper.insertBlackBatch(part);
        } catch (Exception e) {
            if (part.size() == 1) {
                WeChatMessage m = part.get(0);
                log.error("黑名单消息单条入库失败 offset={} username={} localId={} serverIdLength={}",
                        offset, m.getUsername(), m.getLocalId(),
                        m.getServerId() == null ? 0 : m.getServerId().length(), e);
                return 0;
            }
            int mid = part.size() / 2;
            int ok = insertBlackPart(new ArrayList<>(part.subList(0, mid)), offset);
            ok += insertBlackPart(new ArrayList<>(part.subList(mid, part.size())), offset + mid);
            return ok;
        }
    }

    /** server_id 是来源去重键；统一限制在 255 字符，防止单条超长导致整批失败。 */
    private void normalizeServerIds(List<WeChatMessage> list) {
        for (WeChatMessage m : list) {
            if (m != null && m.getServerId() != null && m.getServerId().length() > 255) {
                m.setServerId(m.getServerId().substring(0, 255));
            }
        }
    }

    /** 单条入库。 */
    public void save(WeChatMessage m) {
        if (m == null) {
            return;
        }
        normalizeServerIds(Collections.singletonList(m));
        try {
            mapper.batchUpsert(java.util.Collections.singletonList(m));
        } catch (Exception e) {
            log.error("微信消息入库失败: username={} localId={}", m.getUsername(), m.getLocalId(), e);
        }
    }

    /** 回写单条消息分类结果。 */
    public void updateCategory(WeChatMessage m) {
        if (m == null || m.getMsgCategory() == null || m.getMsgCategory().isEmpty()) {
            return;
        }
        try {
            mapper.updateCategory(m);
        } catch (Exception e) {
            log.warn("消息分类回写失败: username={} localId={}", m.getUsername(), m.getLocalId(), e);
        }
    }

    /** 标记分类黑名单会话：OTHER/FILTER + status=3，避免继续被扫描和自动录入。 */
    public void updateFiltered(WeChatMessage m, String reason) {
        if (m == null) {
            return;
        }
        try {
            mapper.updateFiltered(m, reason);
            m.setMsgCategory("OTHER");
            m.setCategorySource("FILTER");
            m.setCategoryReason(reason);
            m.setStatus(3);
            m.setRelatInq(null);
            m.setRelatOffer(null);
        } catch (Exception e) {
            log.warn("消息过滤状态回写失败: username={} localId={}", m.getUsername(), m.getLocalId(), e);
        }
    }

    /** 按起始时间(+可选会话名)查询消息(含分类结果)，sort_seq 正序。 */
    public List<WeChatMessage> query(String since, String chat, Integer status, int max) {
        try {
            return mapper.querySince(since, chat, status, max);
        } catch (Exception e) {
            log.error("查询微信消息失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /** 查询未分类消息(供定时任务归并分类)，最多 max 条。 */
    public List<WeChatMessage> queryUnclassified(int max) {
        try {
            return mapper.queryUnclassified(max);
        } catch (Exception e) {
            log.error("查询未分类消息失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /** 查询指定会话最近 max 条消息（含未分类），按时间正序，供20条上下文分类使用。 */
    public List<WeChatMessage> queryRecent(String username, int max) {
        try {
            return mapper.queryRecent(username, Math.max(1, max));
        } catch (Exception e) {
            log.error("查询会话最近消息失败: username={}", username, e);
            return Collections.emptyList();
        }
    }

    /** 查询需要补发飞书预警的 TRACE。 */
    public List<WeChatMessage> queryTraceAlerts(String since, int max) {
        try {
            return mapper.queryTraceAlerts(since, max);
        } catch (Exception e) {
            log.error("查询TRACE预警消息失败", e);
            return Collections.emptyList();
        }
    }

    /** 未分类消息统计：{cnt=条数, earliest=最早未分类消息时间}。 */
    public java.util.Map<String, Object> unclassifiedStats() {
        try {
            return mapper.unclassifiedStats();
        } catch (Exception e) {
            log.error("查询未分类统计失败", e);
            return java.util.Collections.emptyMap();
        }
    }

    /**
     * 查询待录入消息(status=1 且分类在 categories 内)，供 INQ/OFFER 定时 AI 录入消费，sort_seq 正序。
     * chat 非空=只查该会话；chat 为空且 chats 非空=只查这些会话；都为空=全部会话。
     */
    public List<WeChatMessage> queryProcessable(String chat, List<String> chats, List<String> categories,
                                                String since, int minAgeSec, int max) {
        if (categories == null || categories.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            return mapper.queryProcessable(chat, chats, categories, since, Math.max(0, minAgeSec), max);
        } catch (Exception e) {
            log.error("查询待录入消息失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /** 待录入消息统计：{cnt=条数, earliest=最早待录入消息时间}。 */
    public java.util.Map<String, Object> processableStats(List<String> categories, String since) {
        if (categories == null || categories.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        try {
            return mapper.processableStats(categories, since);
        } catch (Exception e) {
            log.error("查询待录入统计失败", e);
            return java.util.Collections.emptyMap();
        }
    }

    /** 回写单条消息录入结果：成功 status=2(已分类处理未发送)；失败达上限 status=3(无需处理)；未达上限保持 status=1 下轮重试。 */
    public void updateEntryResult(String username, long localId, int status, int failCount) {
        try {
            mapper.updateEntryResult(username, localId, status, failCount);
        } catch (Exception e) {
            log.warn("消息录入结果回写失败: username={} localId={}", username, localId, e);
        }
    }

    /** 独立录入状态分布（PENDING/PROCESSING/SUCCESS/FAILED/SKIPPED/DEAD 等）。 */
    public List<java.util.Map<String, Object>> entryStatusStats(String since) {
        try {
            return mapper.entryStatusStats(since);
        } catch (Exception e) {
            log.error("查询录入状态分布失败", e);
            return Collections.emptyList();
        }
    }

    /** 原子领取一条待录入消息，防止定时任务和手动触发重复处理。 */
    public boolean markEntryProcessing(String username, long localId) {
        try {
            return mapper.markEntryProcessing(username, localId) > 0;
        } catch (Exception e) {
            log.warn("消息录入领取失败: username={} localId={}", username, localId, e);
            return false;
        }
    }

    /** 回写独立录入状态、审计信息与消息处理状态。 */
    public void updateEntryState(String username, long localId, int messageStatus, String entryStatus,
                                 String entryMessage, int offerCount, String offerIds, int failCount) {
        try {
            mapper.updateEntryState(username, localId, messageStatus, entryStatus,
                    entryMessage, offerCount, offerIds, failCount);
        } catch (Exception e) {
            log.warn("消息录入状态回写失败: username={} localId={}", username, localId, e);
        }
    }

    /** 人工重排被跳过/失败/终止的消息。 */
    public int requeueEntry(String chat) {
        try {
            return mapper.requeueEntry(chat);
        } catch (Exception e) {
            log.error("录入队列重排失败: chat={}", chat, e);
            return 0;
        }
    }

    /** 清理 create_time 早于 now-retentionDays 的消息，返回删除条数。 */
    public int purgeOlderThanDays(int retentionDays) {
        int days = Math.max(1, retentionDays);
        String before = LocalDateTime.now().minusDays(days).format(FMT);
        int n = mapper.purgeBefore(before);
        if (n > 0) {
            log.info("定时清理微信消息 {} 条(早于 {})", n, before);
        }
        return n;
    }
}
