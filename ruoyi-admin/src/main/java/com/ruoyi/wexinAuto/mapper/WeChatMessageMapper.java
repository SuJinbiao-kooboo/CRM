package com.ruoyi.wexinAuto.mapper;

import com.ruoyi.wexinAuto.model.WeChatMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 微信消息表(wechat_message) Mapper。
 * 消息全部入库(含监听与主动拉取)，只保留最近 msgRetentionDays 天，由定时任务清理。
 */
public interface WeChatMessageMapper {

    /** 批量插入或更新(唯一键 username+local_id)；已分类的消息不会被重置分类。 */
    int batchUpsert(@Param("list") List<WeChatMessage> list);

    /** 更新单条消息的分类与来源(按 username+local_id)。 */
    int updateCategory(@Param("m") WeChatMessage m);

    /** 按起始时间(+可选会话名)查询，按 sort_seq 正序。 */
    List<WeChatMessage> querySince(@Param("since") String since, @Param("chat") String chat, @Param("status") Integer status, @Param("max") int max);

    /** 查询未分类消息(status=0)，按 sort_seq 正序，最多 max 条。 */
    List<WeChatMessage> queryUnclassified(@Param("max") int max);

    /** 未分类消息统计：{cnt=未分类条数, earliest=最早未分类消息时间}。 */
    java.util.Map<String, Object> unclassifiedStats();

    /**
     * 查询待录入消息(status=1 且分类在 categories 内)，按 sort_seq 正序，最多 max 条；
     * chat 非空=只查该会话；chat 为空且 chats 非空=只查这些会话(chats)；都为空=全部会话。
     */
    List<WeChatMessage> queryProcessable(@Param("chat") String chat, @Param("chats") List<String> chats, @Param("categories") List<String> categories, @Param("max") int max);

    /** 待录入消息统计：{cnt=待录入条数, earliest=最早待录入消息时间}。 */
    java.util.Map<String, Object> processableStats(@Param("categories") List<String> categories);

    /** 更新单条消息录入结果：成功 status=2 / 失败达上限 status=3，按 username+local_id 定位并记录失败次数。 */
    int updateEntryResult(@Param("username") String username, @Param("localId") long localId, @Param("status") int status, @Param("failCount") int failCount);

    /** 批量插入黑名单消息表(pollIgnoreChats命中的消息；唯一键冲突跳过)。 */
    int insertBlackBatch(@Param("list") List<WeChatMessage> list);

    /** 删除 create_time 早于 before 的消息，返回删除条数。 */
    int purgeBefore(@Param("before") String before);
}

