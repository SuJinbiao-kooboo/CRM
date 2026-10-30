package com.ruoyi.quartz.task;

import java.util.Calendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.quartz.service.ISysJobLogService;

/**
 * 调度日志保留任务：只保留最近三天。
 */
@Component("jobLogCleanTask")
public class JobLogCleanTask
{
    private static final Logger log = LoggerFactory.getLogger(JobLogCleanTask.class);
    private static final int RETENTION_DAYS = 3;

    @Autowired
    private ISysJobLogService jobLogService;

    public void cleanExpiredLogs()
    {
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.DAY_OF_MONTH, -RETENTION_DAYS);
        int deleted = jobLogService.deleteJobLogBefore(cutoff.getTime());
        log.info("已清理三天前的定时任务调度日志，共 {} 条", deleted);
    }
}
