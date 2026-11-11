-- 2026-11-11 生产服务器迁移后的定时任务白名单修复（幂等）
-- 现象：Quartz 日志持续提示本机 10.1.0.11 不在白名单 172.16.31.74，所有微信自动化任务均不执行。
-- 注意：执行后需重启 ruoyi-admin，让 Quartz 重新加载 sys_job.allowed_ips。

UPDATE sys_job
SET allowed_ips = '10.1.0.11',
    update_by = 'repair_20261111',
    update_time = NOW()
WHERE TRIM(allowed_ips) = '172.16.31.74';

