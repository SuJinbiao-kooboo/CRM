package com.ruoyi.wexinAuto.analysis;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.utils.DictUtils;
import com.ruoyi.wexinAuto.model.WeChatMessage;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 固定标记器：数据字典配置的「固定发OFFER的群/人」直接标 OFFER，
 * 以及对固定 OFFER 的回复(跟随窗口内且无明显询价词)也标 OFFER。
 * 字典未配置(空)时本类不生效，一切交给 AI 判断。
 */
public final class FixedOfferMarker {

    private static final String DICT_TYPE = "wechat_analysis_config";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FixedOfferMarker() {
    }

    /** 简单询价信号词：命中则不做「固定OFFER跟随」标记，交给 AI。 */
    private static final String[] INQ_HINTS = {
            "inq", "rfq", "询", "有货吗", "有没有", "什么价", "多少钱", "怎么卖", "报个价", "请报价",
            "价格多少", "有offer吗", "有offer", "在吗", "在么", "? ", "？", "给具体价格"
    };

    /** 对严格按时间正序的消息序列(含已分类上下文)做固定标记，返回标记数。 */
    public static int markFixed(List<WeChatMessage> sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return 0;
        }
        Set<String> senders = names("offerFixedSenders");
        Set<String> groups = names("offerFixedGroups");
        if (senders.isEmpty() && groups.isEmpty()) {
            return 0;
        }
        int windowMin = intCfg("offerFollowWindowMin", 5);
        int marked = 0;
        WeChatMessage prevFixed = null;
        for (WeChatMessage m : sequence) {
            if (m.getMsgCategory() != null && !m.getMsgCategory().isEmpty()) {
                if ("OFFER".equals(m.getMsgCategory())
                        && ("FIXED".equals(m.getCategorySource()) || "GROUP-FIXED".equals(m.getCategorySource()))) {
                    prevFixed = m;
                } else {
                    prevFixed = null;
                }
                continue;
            }
            String name = StringUtils.trimToEmpty(m.getName());
            String sender = StringUtils.trimToEmpty(m.getSender());
            boolean groupHit = !groups.isEmpty() && groupHits(groups, name);
            if (groupHit || (!senders.isEmpty() && senders.contains(sender))) {
                m.setMsgCategory("OFFER");
                m.setCategorySource(groupHit ? "GROUP-FIXED" : "FIXED");
                prevFixed = m;
                marked++;
                continue;
            }
            // 跟随窗口：上一条是固定OFFER，本条无明显询价词 → 也算 OFFER
            boolean follow = prevFixed != null && windowMin > 0
                    && withinWindow(prevFixed, m, windowMin) && !hasInqHint(m.getContent());
            if (follow) {
                m.setMsgCategory("OFFER");
                m.setCategorySource("FIXED-FOLLOW");
                prevFixed = m;
                marked++;
            }
        }
        return marked;
    }

    private static boolean groupHits(Set<String> groups, String name) {
        for (String g : groups) {
            if (name != null && (name.equals(g) || name.contains(g) || g.contains(name))) {
                return true;
            }
        }
        return false;
    }

    private static boolean withinWindow(WeChatMessage prev, WeChatMessage cur, int windowMin) {
        try {
            if (StringUtils.isBlank(prev.getCreateTime()) || StringUtils.isBlank(cur.getCreateTime())) {
                return false;
            }
            LocalDateTime a = LocalDateTime.parse(prev.getCreateTime().trim(), FMT);
            LocalDateTime b = LocalDateTime.parse(cur.getCreateTime().trim(), FMT);
            return !b.isBefore(a) && java.time.Duration.between(a, b).toMinutes() <= windowMin;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasInqHint(String content) {
        if (StringUtils.isBlank(content)) {
            return true; // 空白内容不跟随
        }
        String c = content.toLowerCase();
        for (String w : INQ_HINTS) {
            if (c.contains(w.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> names(String label) {
        Set<String> out = new HashSet<>();
        List<SysDictData> list = DictUtils.getDictCache(DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
                    for (String n : d.getDictValue().split(",")) {
                        if (StringUtils.isNotBlank(n)) {
                            out.add(n.trim());
                        }
                    }
                }
            }
        }
        return out;
    }

    private static int intCfg(String label, int def) {
        List<SysDictData> list = DictUtils.getDictCache(DICT_TYPE);
        if (list != null) {
            for (SysDictData d : list) {
                if (label.equals(d.getDictLabel()) && StringUtils.isNotBlank(d.getDictValue())) {
                    try {
                        return Integer.parseInt(d.getDictValue().trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return def;
    }
}