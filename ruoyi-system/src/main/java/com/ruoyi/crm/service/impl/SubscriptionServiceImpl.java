package com.ruoyi.crm.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.ruoyi.crm.mapper.SubscriptionMapper;
import com.ruoyi.crm.service.ISubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 邮件订阅(portal.t_subscription) 业务实现。
 */
@Service
public class SubscriptionServiceImpl implements ISubscriptionService {

    private static final String DEFAULT_MODULE = "hk";
    private static final int SUBSCRIBED = 1;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    @Autowired
    private SubscriptionMapper subscriptionMapper;

    @Override
    public List<Map<String, Object>> selectSubscriptionList(String email, String module, Integer status) {
        return subscriptionMapper.selectSubscriptionList(email, module, status);
    }

    @Override
    public Map<String, Object> addSubscriptions(List<String> emails) {
        Map<String, Object> result = new LinkedHashMap<>();
        int total = 0;
        int success = 0;
        int fail = 0;
        List<String> failEmails = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (emails != null) {
            for (String raw : emails) {
                if (raw == null) {
                    continue;
                }
                String email = raw.trim();
                if (email.isEmpty()) {
                    continue;
                }
                total++;
                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    fail++;
                    failEmails.add(email + "（格式不正确）");
                    continue;
                }
                if (!seen.add(email.toLowerCase(Locale.ROOT))) {
                    fail++;
                    failEmails.add(email + "（本次重复）");
                    continue;
                }
                try {
                    Integer status = subscriptionMapper.selectStatusByEmail(email, DEFAULT_MODULE);
                    if (status == null) {
                        subscriptionMapper.insertSubscription(email, DEFAULT_MODULE, SUBSCRIBED);
                        success++;
                    } else if (status == 0) {
                        subscriptionMapper.reactivateSubscription(email, DEFAULT_MODULE);
                        success++;
                    } else {
                        fail++;
                        failEmails.add(email + "（已订阅）");
                    }
                } catch (Exception e) {
                    fail++;
                    failEmails.add(email + "（写入失败）");
                }
            }
        }
        result.put("total", total);
        result.put("success", success);
        result.put("fail", fail);
        result.put("failEmails", failEmails);
        return result;
    }

    @Override
    public int updateStatus(List<Long> ids, int status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return subscriptionMapper.updateSubscriptionStatus(ids, status);
    }

    @Override
    public int deleteSubscriptions(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return subscriptionMapper.deleteSubscriptionByIds(ids);
    }
}
