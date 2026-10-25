package com.ruoyi.wexinAuto.mapper;

import com.ruoyi.wexinAuto.model.WeChatOfferTask;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/** 微信询报价任务 Mapper。 */
public interface WeChatOfferTaskMapper {
    List<WeChatOfferTask> selectTaskList(WeChatOfferTask query);

    WeChatOfferTask selectTaskById(Long id);

    WeChatOfferTask selectByUnique(@Param("sourceUsername") String sourceUsername,
                                   @Param("sourceLocalId") Long sourceLocalId,
                                   @Param("taskType") String taskType,
                                   @Param("pnNormalized") String pnNormalized,
                                   @Param("sendType") String sendType);

    int insertTaskIgnore(WeChatOfferTask task);

    int updateApproval(WeChatOfferTask task);

    int markSendAttempt(@Param("id") Long id, @Param("attemptAt") Date attemptAt);

    int updateSendResult(@Param("id") Long id,
                         @Param("successJson") String successJson,
                         @Param("handleStatus") String handleStatus,
                         @Param("lastError") String lastError,
                         @Param("sentAt") Date sentAt);

    int markUnknown(@Param("id") Long id, @Param("error") String error);

    int confirmNotSent(@Param("id") Long id);

    List<WeChatOfferTask> selectSendCandidates(@Param("maxAttempts") int maxAttempts,
                                               @Param("max") int max);

    List<WeChatOfferTask> selectApprovedForTarget(@Param("taskType") String taskType,
                                                  @Param("sendType") String sendType,
                                                  @Param("targetName") String targetName,
                                                  @Param("max") int max);
}
