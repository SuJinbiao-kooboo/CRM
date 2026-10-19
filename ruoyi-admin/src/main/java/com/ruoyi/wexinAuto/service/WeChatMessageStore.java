package com.ruoyi.wexinAuto.service;

import com.ruoyi.wexinAuto.mapper.WeChatMessageMapper;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /** 批量入库(幂等：username+local_id 重复时只更新非分类字段)，内部自动分批。 */
    public void saveAll(List<WeChatMessage> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int total = list.size();
        int ok = 0;
        for (int i = 0; i < total; i += UPSERT_BATCH) {
            List<WeChatMessage> part = list.subList(i, Math.min(i + UPSERT_BATCH, total));
            try {
                ok += mapper.batchUpsert(part);
            } catch (Exception e) {
                log.error("微信消息入库失败(第{}-{}条)", i, i + part.size(), e);
            }
        }
        if (ok > 0 || total > 0) {
            log.info("微信消息入库 {} 条(成功 {})", total, ok);
        }
    }

    /** 黑名单消息入库(只落库、不做分类/处理；重复按唯一键跳过)，内部自动分批。 */
    public void saveBlackAll(List<WeChatMessage> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int total = list.size();
        for (int i = 0; i < total; i += 500) {
            List<WeChatMessage> part = list.subList(i, Math.min(i + 500, total));
            try {
                mapper.insertBlackBatch(part);
            } catch (Exception e) {
                log.error("黑名单消息入库失败(第{}-{}条)", i, i + part.size(), e);
            }
        }
        log.info("黑名单消息入库 {} 条", total);
    }

    /** 单条入库。 */
    public void save(WeChatMessage m) {
        if (m == null) {
            return;
        }
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
    public List<WeChatMessage> queryProcessable(String chat, List<String> chats, List<String> categories, int max) {
        if (categories == null || categories.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        try {
            return mapper.queryProcessable(chat, chats, categories, max);
        } catch (Exception e) {
            log.error("查询待录入消息失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /** 待录入消息统计：{cnt=条数, earliest=最早待录入消息时间}。 */
    public java.util.Map<String, Object> processableStats(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        try {
            return mapper.processableStats(categories);
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
