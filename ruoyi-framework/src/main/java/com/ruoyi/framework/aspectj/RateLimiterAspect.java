package com.ruoyi.framework.aspectj;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ip.IpUtils;

/**
 * 限流处理
 *
 * <p>项目为单机部署，已移除 Redis，改用本地内存实现固定窗口限流：
 * <ul>
 *     <li>窗口策略：以「窗口起始时间 + 窗口内请求数」为记录，同一窗口内请求数超过阈值则拒绝；</li>
 *     <li>线程安全：使用 ConcurrentHashMap 并发控制；</li>
 *     <li>内存回收：后台守护线程每分钟清理已过期的窗口记录，防止 Map 无限增长。</li>
 * </ul>
 * 若未来需要多实例部署，可替换回 Redis 脚本限流方案。
 *
 * @author ruoyi
 */
@Aspect
@Component
public class RateLimiterAspect
{
    private static final Logger log = LoggerFactory.getLogger(RateLimiterAspect.class);

    /** 过期窗口记录清理间隔（毫秒），每分钟执行一次 */
    private static final long CLEAN_PERIOD = 60 * 1000L;

    /**
     * 本地限流计数器：缓存键 -> [窗口起始时间戳, 窗口内请求数]（静态，便于后台线程直接清理）
     */
    private static final Map<String, long[]> counterMap = new ConcurrentHashMap<>();

    static
    {
        // 启动后台守护线程定时清理过期窗口记录，防止内存泄漏（守护线程不阻止 JVM 退出）
        ScheduledExecutorService cleanExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-clean");
            t.setDaemon(true);
            return t;
        });
        cleanExecutor.scheduleWithFixedDelay(RateLimiterAspect::cleanExpiredCounter, CLEAN_PERIOD, CLEAN_PERIOD, TimeUnit.MILLISECONDS);
    }

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) throws Throwable
    {
        int time = rateLimiter.time();
        int count = rateLimiter.count();

        String combineKey = getCombineKey(rateLimiter, point);
        try
        {
            long now = System.currentTimeMillis();
            // 原子更新窗口计数：新窗口则重置计数，否则窗口内计数 +1
            long[] counter = counterMap.compute(combineKey, (k, v) -> {
                if (v == null || now - v[0] >= time * 1000L)
                {
                    return new long[] { now, 1L };
                }
                v[1] = v[1] + 1;
                return v;
            });
            if (counter[1] > count)
            {
                throw new ServiceException("访问过于频繁，请稍候再试");
            }
            log.info("限制请求'{}',当前请求'{}',缓存key'{}'", count, counter[1], combineKey);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException("服务器限流异常，请稍候再试");
        }
    }

    public String getCombineKey(RateLimiter rateLimiter, JoinPoint point)
    {
        StringBuffer stringBuffer = new StringBuffer(rateLimiter.key());
        if (rateLimiter.limitType() == LimitType.IP)
        {
            stringBuffer.append(IpUtils.getIpAddr()).append("-");
        }
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();
        stringBuffer.append(targetClass.getName()).append("-").append(method.getName());
        return stringBuffer.toString();
    }

    /**
     * 清理已过期的窗口记录：窗口起始时间距现在超过一个窗口周期的记录已无意义，直接移除防止内存泄漏
     */
    private static void cleanExpiredCounter()
    {
        long now = System.currentTimeMillis();
        // 遍历计数器，移除长时间未活动的窗口记录（窗口跨度按最长 60 秒计算，超时 2 倍后清理）
        counterMap.entrySet().removeIf(entry -> now - entry.getValue()[0] > 120 * 1000L);
    }
}
