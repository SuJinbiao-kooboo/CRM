package com.ruoyi.quartz.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.quartz.domain.SysJob;

/**
 * 定时任务本机 IPv4 白名单校验。
 */
public final class JobIpWhitelist
{
    private static final Logger log = LoggerFactory.getLogger(JobIpWhitelist.class);
    private static final long WARN_INTERVAL_MS = 60_000L;
    private static final ConcurrentMap<Long, Long> LAST_WARN_AT = new ConcurrentHashMap<>();

    private JobIpWhitelist()
    {
    }

    public static boolean isAllowed(SysJob job)
    {
        String allowedIps = job.getAllowedIps();
        if (allowedIps == null || allowedIps.trim().isEmpty())
        {
            return true;
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String ip : allowedIps.split("[,，;；\\s]+"))
        {
            if (StringUtils.isNotEmpty(ip))
            {
                allowed.add(ip.trim());
            }
        }
        List<String> localIps = localIpv4Addresses();
        for (String localIp : localIps)
        {
            if (allowed.contains(localIp))
            {
                return true;
            }
        }
        warnDenied(job, allowed, localIps);
        return false;
    }

    public static List<String> localIpv4Addresses()
    {
        Set<String> localIps = new LinkedHashSet<>();
        try
        {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements())
            {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback())
                {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements())
                {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress() && !address.isAnyLocalAddress())
                    {
                        localIps.add(address.getHostAddress());
                    }
                }
            }
        }
        catch (SocketException e)
        {
            log.warn("读取本机内网IP失败", e);
        }
        return new ArrayList<>(localIps);
    }

    private static void warnDenied(SysJob job, Set<String> allowed, List<String> localIps)
    {
        long now = System.currentTimeMillis();
        Long jobId = job.getJobId();
        Long lastWarnAt = jobId == null ? null : LAST_WARN_AT.get(jobId);
        if (lastWarnAt != null && now - lastWarnAt < WARN_INTERVAL_MS)
        {
            return;
        }
        if (jobId != null)
        {
            LAST_WARN_AT.put(jobId, now);
        }
        log.warn("定时任务[{}]未执行：本机IPv4={} 不在白名单 {} 中", job.getJobName(), localIps, allowed);
    }
}
