package com.ruoyi.common.core.redis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.CacheObj;
import cn.hutool.cache.impl.LFUCache;

/**
 * 本地缓存工具类（原 RedisCache）
 *
 * <p>项目为单机部署，已移除 Redis 中间件，改用 Hutool 本地缓存实现：
 * <ul>
 *     <li>缓存策略：LFU（Least Frequently Used，最不经常使用）淘汰，容量 10000，防止缓存无限增长；</li>
 *     <li>过期机制：写入时可按 key 单独指定过期时间（put 方法的 timeout 参数），过期键在读取时自动剔除；</li>
 *     <li>定时清理：后台线程每小时清理一次过期键，避免内存堆积。</li>
 * </ul>
 * 类名与方法签名保持与原 RedisCache 一致，调用方无需任何改动。
 * 若未来需要多实例部署，可将本类替换回 Redis 实现。
 *
 * @author ruoyi
 */
@SuppressWarnings(value = { "unchecked", "rawtypes" })
@Component
public class RedisCache
{
    /** 本地缓存最大容量（条） */
    private static final int CAPACITY = 10000;

    /** 过期键定时清理间隔（毫秒），每小时执行一次 */
    private static final long PRUNE_PERIOD = 60 * 60 * 1000L;

    /** 本地 LFU 缓存实例（线程安全），容量满时自动淘汰低频键 */
    private static final LFUCache<String, Object> CACHE = CacheUtil.newLFUCache(CAPACITY);

    static
    {
        // 启动后台守护线程定时清理过期键，防止过期数据长期占用内存（守护线程不阻止 JVM 退出，无需手动关闭）
        ScheduledExecutorService pruneExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "local-cache-prune");
            t.setDaemon(true);
            return t;
        });
        pruneExecutor.scheduleWithFixedDelay(CACHE::prune, PRUNE_PERIOD, PRUNE_PERIOD, TimeUnit.MILLISECONDS);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等（不设置过期时间，常驻缓存）
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value)
    {
        CACHE.put(key, value);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等，并指定过期时间
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     * @param timeout 过期时间（数值）
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit)
    {
        CACHE.put(key, value, timeUnit.toMillis(timeout));
    }

    /**
     * 设置有效时间（对已存在的 key 刷新过期时间）
     *
     * @param key 缓存键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout)
    {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间（对已存在的 key 刷新过期时间）
     *
     * @param key 缓存键
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit)
    {
        if (CACHE.containsKey(key))
        {
            Object value = CACHE.get(key, false);
            CACHE.put(key, value, unit.toMillis(timeout));
            return true;
        }
        return false;
    }

    /**
     * 获取有效时间（返回剩余毫秒数，永不过期的键返回 -1，不存在的键返回 -2）
     *
     * @param key 缓存键
     * @return 剩余有效时间（毫秒）
     */
    public long getExpire(final String key)
    {
        Iterator<CacheObj<String, Object>> iterator = CACHE.cacheObjIterator();
        while (iterator.hasNext())
        {
            CacheObj<String, Object> cacheObj = iterator.next();
            if (cacheObj.getKey().equals(key))
            {
                Date expiredTime = cacheObj.getExpiredTime();
                // 未设置过期时间的键返回 -1（与 Redis 语义一致）
                return expiredTime == null ? -1L : expiredTime.getTime() - System.currentTimeMillis();
            }
        }
        // 键不存在返回 -2（与 Redis 语义一致）
        return -2L;
    }

    /**
     * 判断 key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key)
    {
        return CACHE.containsKey(key);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key)
    {
        return (T) CACHE.get(key);
    }

    /**
     * 删除单个对象
     *
     * @param key 缓存键
     * @return 是否删除成功
     */
    public boolean deleteObject(final String key)
    {
        boolean exists = CACHE.containsKey(key);
        CACHE.remove(key);
        return exists;
    }

    /**
     * 删除集合对象（批量删除）
     *
     * @param collection 多个缓存键
     * @return 是否存在被删除的键
     */
    public boolean deleteObject(final Collection collection)
    {
        if (collection == null || collection.isEmpty())
        {
            return false;
        }
        boolean removed = false;
        for (Object key : collection)
        {
            // 先判断存在再删除（Hutool 的 remove 无返回值）
            if (key != null && CACHE.containsKey(key.toString()))
            {
                CACHE.remove(key.toString());
                removed = true;
            }
        }
        return removed;
    }

    /**
     * 缓存List数据
     *
     * @param key 缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的数据条数
     */
    public <T> long setCacheList(final String key, final List<T> dataList)
    {
        CACHE.put(key, dataList);
        return dataList == null ? 0 : dataList.size();
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(final String key)
    {
        return (List<T>) CACHE.get(key);
    }

    /**
     * 缓存Map
     *
     * @param key 缓存的键值
     * @param dataMap 待缓存的Map数据
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap)
    {
        if (dataMap != null)
        {
            CACHE.put(key, dataMap);
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> Map<String, T> getCacheMap(final String key)
    {
        return (Map<String, T>) CACHE.get(key);
    }

    /**
     * 往Hash中存入数据（Map 已存在则追加字段，不存在则创建）
     *
     * @param key 缓存键
     * @param hKey Hash键
     * @param value 值
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value)
    {
        Map<String, T> dataMap = getCacheMap(key);
        if (dataMap == null)
        {
            dataMap = new java.util.HashMap<>();
        }
        dataMap.put(hKey, value);
        CACHE.put(key, dataMap);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key 缓存键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public <T> T getCacheMapValue(final String key, final String hKey)
    {
        Map<String, T> dataMap = getCacheMap(key);
        return dataMap == null ? null : dataMap.get(hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key 缓存键
     * @param hKeys Hash键集合
     * @return Hash对象集合（顺序与入参一致）
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys)
    {
        Map<String, T> dataMap = getCacheMap(key);
        List<T> values = new ArrayList<>();
        if (dataMap != null && hKeys != null)
        {
            for (Object hKey : hKeys)
            {
                values.add(dataMap.get(hKey));
            }
        }
        return values;
    }

    /**
     * 删除Hash中的某条数据
     *
     * @param key 缓存键
     * @param hKey Hash键
     * @return 是否成功
     */
    public boolean deleteCacheMapValue(final String key, final String hKey)
    {
        Map<String, Object> dataMap = getCacheMap(key);
        if (dataMap != null)
        {
            boolean removed = dataMap.remove(hKey) != null;
            CACHE.put(key, dataMap);
            return removed;
        }
        return false;
    }

    /**
     * 获得缓存的基本对象列表（按 key 前缀匹配，兼容原有 "prefix*" 用法）
     *
     * @param pattern 字符串前缀（支持末尾通配符 *）
     * @return 匹配的缓存键列表
     */
    public Collection<String> keys(final String pattern)
    {
        // 将 Redis 风格的通配符转换为正则：* 匹配任意字符
        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        Pattern keyPattern = Pattern.compile(regex);
        Set<String> keys = CACHE.keySet();
        List<String> matchedKeys = new ArrayList<>();
        for (String key : keys)
        {
            if (keyPattern.matcher(key).matches())
            {
                matchedKeys.add(key);
            }
        }
        return matchedKeys;
    }

    /**
     * 当前缓存中键的总数量（供缓存监控使用）
     *
     * @return 缓存条目数
     */
    public int size()
    {
        return CACHE.size();
    }

    /**
     * 本地缓存最大容量（条）（供缓存监控使用）
     *
     * @return 容量大小
     */
    public int getCapacity()
    {
        return CAPACITY;
    }

    /**
     * 清空全部缓存（供缓存监控使用）
     */
    public void clear()
    {
        CACHE.clear();
    }
}
