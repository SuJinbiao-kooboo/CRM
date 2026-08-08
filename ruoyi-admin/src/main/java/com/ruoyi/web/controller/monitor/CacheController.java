package com.ruoyi.web.controller.monitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.system.domain.SysCache;

/**
 * 缓存监控
 *
 * <p>原实现基于 Redis 的 INFO / commandstats / dbSize 等命令采集指标，
 * 项目已移除 Redis 并改用 Hutool 本地缓存，此处改为采集本地缓存的容量、
 * 总条目数以及各业务命名空间（前缀）的条目分布，供前端监控页面展示。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/monitor/cache")
public class CacheController
{
    /** 本地缓存操作类（Hutool LFU 缓存，线程安全） */
    @Autowired
    private RedisCache redisCache;

    /** 各业务缓存命名空间（key 前缀）及中文说明，用于统计分布与列表展示 */
    private final static List<SysCache> caches = new ArrayList<SysCache>();
    {
        caches.add(new SysCache(CacheConstants.LOGIN_TOKEN_KEY, "用户信息"));
        caches.add(new SysCache(CacheConstants.SYS_CONFIG_KEY, "配置信息"));
        caches.add(new SysCache(CacheConstants.SYS_DICT_KEY, "数据字典"));
        caches.add(new SysCache(CacheConstants.CAPTCHA_CODE_KEY, "验证码"));
        caches.add(new SysCache(CacheConstants.REPEAT_SUBMIT_KEY, "防重提交"));
        caches.add(new SysCache(CacheConstants.RATE_LIMIT_KEY, "限流处理"));
        caches.add(new SysCache(CacheConstants.PWD_ERR_CNT_KEY, "密码错误次数"));
    }

    /**
     * 获取本地缓存基本信息：总容量、当前条目数、各命名空间条目分布
     *
     * @return 缓存监控信息
     */
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @GetMapping()
    public AjaxResult getInfo()
    {
        Map<String, Object> result = new HashMap<>(3);
        // 本地缓存总容量（条）与当前已使用条目数，用于计算使用率
        result.put("capacity", redisCache.getCapacity());
        result.put("size", redisCache.size());

        // 按业务命名空间（key 前缀）统计条目数，供前端展示分布图
        List<Map<String, Object>> namespaces = new ArrayList<>();
        for (SysCache cache : caches)
        {
            Map<String, Object> data = new HashMap<>(3);
            data.put("name", cache.getCacheName());
            data.put("remark", cache.getRemark());
            data.put("count", redisCache.keys(cache.getCacheName() + "*").size());
            namespaces.add(data);
        }
        result.put("namespaces", namespaces);
        return AjaxResult.success(result);
    }

    /**
     * 获取所有缓存名称列表（各业务命名空间）
     *
     * @return 缓存名称列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @GetMapping("/getNames")
    public AjaxResult cache()
    {
        return AjaxResult.success(caches);
    }

    /**
     * 根据缓存名称（前缀）查询其下所有缓存 key
     *
     * @param cacheName 缓存名称
     * @return key 集合（排序后返回）
     */
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @GetMapping("/getKeys/{cacheName}")
    public AjaxResult getCacheKeys(@PathVariable String cacheName)
    {
        Collection<String> cacheKeys = redisCache.keys(cacheName + "*");
        return AjaxResult.success(new TreeSet<>(cacheKeys));
    }

    /**
     * 根据缓存 key 查询缓存内容
     *
     * @param cacheName 缓存名称（仅用于回显）
     * @param cacheKey 缓存 key
     * @return 缓存内容
     */
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @GetMapping("/getValue/{cacheName}/{cacheKey}")
    public AjaxResult getCacheValue(@PathVariable String cacheName, @PathVariable String cacheKey)
    {
        Object cacheValue = redisCache.getCacheObject(cacheKey);
        String value = cacheValue == null ? null : cacheValue.toString();
        SysCache sysCache = new SysCache(cacheName, cacheKey, value);
        return AjaxResult.success(sysCache);
    }

    /**
     * 按缓存名称（前缀）批量清除缓存
     *
     * @param cacheName 缓存名称
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @DeleteMapping("/clearCacheName/{cacheName}")
    public AjaxResult clearCacheName(@PathVariable String cacheName)
    {
        Collection<String> cacheKeys = redisCache.keys(cacheName + "*");
        redisCache.deleteObject(cacheKeys);
        return AjaxResult.success();
    }

    /**
     * 按缓存 key 清除单条缓存
     *
     * @param cacheKey 缓存 key
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @DeleteMapping("/clearCacheKey/{cacheKey}")
    public AjaxResult clearCacheKey(@PathVariable String cacheKey)
    {
        redisCache.deleteObject(cacheKey);
        return AjaxResult.success();
    }

    /**
     * 清空全部本地缓存
     *
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    @DeleteMapping("/clearCacheAll")
    public AjaxResult clearCacheAll()
    {
        redisCache.clear();
        return AjaxResult.success();
    }
}
