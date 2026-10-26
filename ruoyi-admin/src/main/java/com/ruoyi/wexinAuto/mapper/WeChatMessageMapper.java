package com.ruoyi.wexinAuto.mapper;

import com.ruoyi.wexinAuto.model.WeChatMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 微信消息表 Mapper。分类、录入、任务生成使用各自独立状态，旧 status 仅保留兼容。
 */
public interface WeChatMessageMapper {

    int batchUpsert(@Param("list") List<WeChatMessage> list);

    int updateCategory(@Param("m") WeChatMessage m);

    int updateFiltered(@Param("m") WeChatMessage m, @Param("reason") String reason);

    int markClassifyProcessing(@Param("username") String username, @Param("localId") long localId,
                               @Param("timeoutSec") int timeoutSec, @Param("cooldownSec") int cooldownSec);

    int markClassifyFailed(@Param("username") String username, @Param("localId") long localId,
                           @Param("reason") String reason);

    int markClassifySkipped(@Param("username") String username, @Param("localId") long localId,
                            @Param("reason") String reason);

    List<WeChatMessage> querySince(@Param("since") String since, @Param("chat") String chat,
                                   @Param("status") Integer status, @Param("max") int max);

    List<WeChatMessage> queryClassifyCandidates(@Param("max") int max, @Param("cooldownSec") int cooldownSec,
                                                @Param("timeoutSec") int timeoutSec);

    List<WeChatMessage> queryUnclassified(@Param("max") int max);

    List<WeChatMessage> queryByKeys(@Param("keys") List<String> keys);

    List<WeChatMessage> queryRecent(@Param("username") String username, @Param("max") int max);

    java.util.Map<String, Object> unclassifiedStats();

    List<WeChatMessage> queryTraceAlerts(@Param("since") String since, @Param("max") int max);

    List<WeChatMessage> queryProcessable(@Param("chat") String chat, @Param("chats") List<String> chats,
                                         @Param("categories") List<String> categories,
                                         @Param("since") String since, @Param("minAgeSec") int minAgeSec,
                                         @Param("max") int max, @Param("timeoutSec") int timeoutSec);

    java.util.Map<String, Object> processableStats(@Param("categories") List<String> categories,
                                                    @Param("since") String since);

    List<java.util.Map<String, Object>> entryStatusStats(@Param("since") String since);

    int markEntryProcessing(@Param("username") String username, @Param("localId") long localId,
                            @Param("timeoutSec") int timeoutSec);

    int updateEntryState(@Param("username") String username, @Param("localId") long localId,
                         @Param("messageStatus") int messageStatus, @Param("entryStatus") String entryStatus,
                         @Param("entryMessage") String entryMessage, @Param("offerCount") int offerCount,
                         @Param("offerIds") String offerIds, @Param("failCount") int failCount);

    int updateEntryOnly(@Param("username") String username, @Param("localId") long localId,
                        @Param("entryStatus") String entryStatus, @Param("entryMessage") String entryMessage,
                        @Param("offerCount") int offerCount, @Param("offerIds") String offerIds,
                        @Param("failCount") int failCount);

    int requeueEntry(@Param("chat") String chat);

    int requeueEntryKeys(@Param("keys") List<String> keys);

    int updateEntryResult(@Param("username") String username, @Param("localId") long localId,
                          @Param("status") int status, @Param("failCount") int failCount);

    List<WeChatMessage> queryTaskCandidates(@Param("since") String since, @Param("max") int max,
                                            @Param("chat") String chat, @Param("timeoutSec") int timeoutSec);

    int markTaskProcessing(@Param("username") String username, @Param("localId") long localId,
                           @Param("timeoutSec") int timeoutSec);

    int updateTaskState(@Param("username") String username, @Param("localId") long localId,
                        @Param("taskStatus") String taskStatus, @Param("taskMessage") String taskMessage);

    int markTraceAlerted(@Param("username") String username, @Param("localId") long localId);

    int markTraceAlertError(@Param("username") String username, @Param("localId") long localId,
                            @Param("error") String error);

    List<WeChatMessage> selectMessageList(WeChatMessage query);

    int updateMediaPath(@Param("username") String username, @Param("localId") long localId,
                        @Param("mediaPath") String mediaPath);

    int insertBlackBatch(@Param("list") List<WeChatMessage> list);

    int moveBlacklistedToBlack(@Param("keys") List<String> keys);

    int deleteBlacklistedFromMain(@Param("keys") List<String> keys);

    int purgeBefore(@Param("before") String before);
}
